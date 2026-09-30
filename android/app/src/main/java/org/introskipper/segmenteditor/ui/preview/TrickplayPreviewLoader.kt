package org.introskipper.segmenteditor.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.google.gson.JsonParser
import org.introskipper.segmenteditor.framecapture.FramePreview.loadPreviewFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Loads preview images from Jellyfin's trickplay functionality
 */
class TrickplayPreviewLoader(
    private val serverUrl: String,
    private val apiKey: String,
    private val userId: String,
    private val itemId: String,
    private val requestedMediaSourceId: String?,
    private val httpClient: OkHttpClient,
    scope: CoroutineScope
) : PreviewLoader {
    
    private var trickplayInfo: TrickplayInfo? = null
    private val initJob: Job = scope.launch(Dispatchers.IO) { trickplayInfo = loadTrickplayInfo() }
    private val previewCache = LruCache<Long, Bitmap>(MAX_PREVIEW_CACHE_SIZE)
    private val tileSheetCache = LruCache<Int, Bitmap>(MAX_TILE_SHEET_CACHE_SIZE)

    override val requiresWarmup: Boolean get() = true

    companion object {
        private const val TAG = "TrickplayPreviewLoader"
        const val DEFAULT_INTERVAL_MS = 1000L // 10 seconds
        const val MAX_PREVIEW_CACHE_SIZE = 100 // Cache more thumbnails
        private const val MAX_TILE_SHEET_CACHE_SIZE = 10 // Cache tile sheets
    }

    data class TrickplayInfo(
        val width: Int,
        val height: Int,
        val tileWidth: Int,
        val tileHeight: Int,
        val thumbnailCount: Int,
        val interval: Int,
        val bandwidth: Long,
        val mediaSourceId: String
    )
    
    override suspend fun loadPreview(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        initJob.join()

        val info =
            trickplayInfo ?: // Fallback to local frame extraction if trickplay is not available
            return@withContext loadPreviewFrame(positionMs)

        try {
            // Round position to nearest interval boundary for better cache hits
            val roundedPositionMs = (positionMs / info.interval) * info.interval.toLong()

            // Check cache first with rounded position
            previewCache.get(roundedPositionMs)?.let { return@withContext it }

            // Calculate which tile image and position within the tile
            // Note: info.interval is in milliseconds (per Jellyfin API spec)
            val thumbnailIndex = (roundedPositionMs / info.interval).toInt()
            val tilesPerImage = info.tileWidth * info.tileHeight
            val imageIndex = thumbnailIndex / tilesPerImage
            val tileIndexInImage = thumbnailIndex % tilesPerImage

            // Load the tile sheet image (with caching)
            val tileSheet = loadTileSheet(imageIndex, info.width, info.mediaSourceId)
            tileSheet ?: run {
                Log.w(TAG, "Failed to load tile sheet $imageIndex")
                return@withContext loadPreviewFrame(positionMs)
            }

            // Extract the specific thumbnail from the tile sheet
            val tileX = tileIndexInImage % info.tileWidth
            val tileY = tileIndexInImage / info.tileWidth
            val thumbnailWidth = tileSheet.width / info.tileWidth
            val thumbnailHeight = tileSheet.height / info.tileHeight

            val thumbnail = Bitmap.createBitmap(
                tileSheet,
                tileX * thumbnailWidth,
                tileY * thumbnailHeight,
                thumbnailWidth,
                thumbnailHeight
            )

            // Cache the result with rounded position
            previewCache.put(roundedPositionMs, thumbnail)

            thumbnail
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preview for position $positionMs", e)
            loadPreviewFrame(positionMs)
        }
    }
    
    private suspend fun loadTrickplayInfo(): TrickplayInfo? = withContext(Dispatchers.IO) {
        try {
            // Jellyfin Items API endpoint with Trickplay field
            // The Trickplay metadata is embedded in the item's details
            val url = "$serverUrl/Users/$userId/Items/$itemId"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "MediaBrowser Token=\"$apiKey\"")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Item request failed: ${response.code}")
                    return@withContext null
                }

                response.body.string().let { parseTrickplayInfo(it) }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading trickplay info", e)
            null
        }
    }
    
    private fun parseTrickplayInfo(json: String): TrickplayInfo? {
        try {
            // Parse the JSON response from /Items/{itemId}
            // The format is: { "Trickplay": { "mediaSourceId": { "width": {...} } } }
            // Select the requested media source so alternate versions never reuse
            // another version's tile sheets.
            val model = JsonParser.parseString(json).asJsonObject
            val trickplay = model.getAsJsonObject("Trickplay")
            if (trickplay == null) {
                Log.w(TAG, "No Trickplay field found in item response")
                return null
            }

            val sourceEntry = if (requestedMediaSourceId != null) {
                trickplay.entrySet().firstOrNull {
                    it.key.equals(requestedMediaSourceId, ignoreCase = true)
                } ?: run {
                    Log.w(TAG, "No trickplay metadata for media source $requestedMediaSourceId")
                    return null
                }
            } else {
                trickplay.entrySet().firstOrNull() ?: return null
            }

            val sourceInfo = sourceEntry.value.asJsonObject
            val info = if (sourceInfo.has("Width")) {
                sourceInfo
            } else {
                // Jellyfin exposes multiple thumbnail widths per source. Use the
                // largest available width, matching the previous behavior while
                // keeping the selection scoped to the requested source.
                sourceInfo.entrySet()
                    .filter { it.value.isJsonObject }
                    .maxByOrNull { it.key.toIntOrNull() ?: Int.MIN_VALUE }
                    ?.value
                    ?.asJsonObject
            } ?: return null

            return extractTrickplayInfo(info, sourceEntry.key)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing trickplay info", e)
            return null
        }
    }
    
    private fun extractTrickplayInfo(info: com.google.gson.JsonObject, mediaSourceId: String): TrickplayInfo? {
        try {
            val width = info.get("Width").asInt
            val height = info.get("Height").asInt
            val tileWidth = info.get("TileWidth").asInt
            val tileHeight = info.get("TileHeight").asInt
            val thumbnailCount = info.get("ThumbnailCount").asInt
            val interval = info.get("Interval").asInt.coerceAtLeast(1)
            val bandwidth = info.get("Bandwidth").asLong
            
            return TrickplayInfo(
                width = width.toInt(),
                height = height.toInt(),
                tileWidth = tileWidth.toInt(),
                tileHeight = tileHeight.toInt(),
                thumbnailCount = thumbnailCount.toInt(),
                interval = interval.toInt(),
                bandwidth = bandwidth.toLong(),
                mediaSourceId = mediaSourceId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting trickplay info fields", e)
            return null
        }
    }
    
    private suspend fun loadTileSheet(imageIndex: Int, width: Int, mediaSourceId: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check tile sheet cache first
            tileSheetCache.get(imageIndex)?.let { 
                Log.d(TAG, "Using cached tile sheet $imageIndex")
                return@withContext it 
            }
            
            // Jellyfin trickplay tile image endpoint
            val url = "$serverUrl/Videos/$itemId/Trickplay/$width/$imageIndex.jpg?mediaSourceId=$mediaSourceId"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "MediaBrowser Token=\"$apiKey\"")
                .build()
            
            Log.d(TAG, "Loading tile sheet $imageIndex from network")
            val tileSheet = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Tile sheet request failed: ${response.code}")
                    return@withContext null
                }

                response.body.bytes().let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
            }
            
            // Cache the tile sheet
            tileSheet?.let { tileSheetCache.put(imageIndex, it) }
            
            tileSheet
        } catch (e: IOException) {
            Log.e(TAG, "Error loading tile sheet $imageIndex", e)
            null
        }
    }
    
    override suspend fun preloadPreviews(positionMs: Long, count: Int) {
        try {
            initJob.join()
            val info = trickplayInfo ?: return
            val interval = info.interval.toLong()
            
            coroutineScope {
                // Preload previews in both directions
                for (i in -count..count) {
                    val preloadPosition = positionMs + (i * interval)
                    if (preloadPosition >= 0) {
                        // Only preload if not already in cache
                        val roundedPosition = (preloadPosition / interval) * interval
                        if (previewCache.get(roundedPosition) == null) {
                            launch(Dispatchers.IO) {
                                loadPreview(preloadPosition)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading previews", e)
        }
    }
    
    override fun getPreviewInterval(): Long {
        // interval is in milliseconds
        return trickplayInfo?.interval?.toLong() ?: DEFAULT_INTERVAL_MS
    }
    
    override fun release() {
        initJob.cancel()
        previewCache.evictAll()
        tileSheetCache.evictAll()
    }
}
