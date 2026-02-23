package org.introskipper.segmenteditor.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import org.introskipper.segmenteditor.framecapture.PreviewFrames.loadPreviewFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.introskipper.segmenteditor.utils.KotlinxGenericMapSerializer
import java.io.IOException

/**
 * Loads preview images from Jellyfin's trickplay functionality
 */
class TrickplayPreviewLoader(
    private val serverUrl: String,
    private val apiKey: String,
    private val userId: String,
    private val itemId: String,
    private val httpClient: OkHttpClient,
    private val useFallback: Boolean,
    scope: CoroutineScope
) : PreviewLoader {
    
    private var trickplayInfo: TrickplayInfo? = null
    private val initJob: Job = scope.launch(Dispatchers.IO) { trickplayInfo = loadTrickplayInfo() }
    private val previewCache = LruCache<Long, Bitmap>(MAX_PREVIEW_CACHE_SIZE)
    private val tileSheetCache = LruCache<Int, Bitmap>(MAX_TILE_SHEET_CACHE_SIZE)
    
    companion object {
        private const val TAG = "TrickplayPreviewLoader"
        private const val DEFAULT_INTERVAL_MS = 10000L // 10 seconds
        private const val MAX_PREVIEW_CACHE_SIZE = 100 // Cache more thumbnails
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
            return@withContext if (useFallback) loadPreviewFrame(positionMs) else null

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
                return@withContext if (useFallback) loadPreviewFrame(positionMs) else null
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
            if (useFallback) loadPreviewFrame(positionMs) else null
        }
    }
    
    private suspend fun loadTrickplayInfo(): TrickplayInfo? = withContext(Dispatchers.IO) {
        try {
            // Jellyfin Items API endpoint with Trickplay field
            // The Trickplay metadata is embedded in the item's details
            val url = "$serverUrl/Users/$userId/Items/$itemId"
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Token", apiKey)
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
            // We need to extract the first available trickplay info

            val model: Map<String, Any?> = Json.decodeFromString(KotlinxGenericMapSerializer, json)
            // Find the Trickplay field
            if (!model.keys.contains("Trickplay")) {
                Log.w(TAG, "No Trickplay field found in item response")
                return null
            }
            val trickplay = model["Trickplay"].toString()
            Log.w(TAG, trickplay)

            val mediaSourceId = trickplay.removePrefix("{").substringBefore("=")
            Log.w(TAG, mediaSourceId)
            val mediaSourceSection = "{${trickplay.substringAfterLast("={").substringBefore("}")}}"
            Log.w(TAG, mediaSourceSection)
            
            // Extract TrickplayInfoDto fields for this width
            return extractTrickplayInfo(mediaSourceSection, mediaSourceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing trickplay info", e)
            return null
        }
    }
    
    private fun extractTrickplayInfo(jsonSection: String, mediaSourceId: String): TrickplayInfo? {
        try {
            // Extract TrickplayInfoDto fields - they might appear in any order
            val width = jsonSection.substringAfter("Width=").substringBefore(",").substringBefore("}")
            val height = jsonSection.substringAfter("Height=").substringBefore(",").substringBefore("}")
            val tileWidth = jsonSection.substringAfter("TileWidth=").substringBefore(",").substringBefore("}")
            val tileHeight = jsonSection.substringAfter("TileHeight=").substringBefore(",").substringBefore("}")
            val thumbnailCount = jsonSection.substringAfter("ThumbnailCount=").substringBefore(",").substringBefore("}")
            val interval = jsonSection.substringAfter("Interval=").substringBefore(",").substringBefore("}")
            val bandwidth = jsonSection.substringAfter("Bandwidth=").substringBefore(",").substringBefore("}")
            
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
                .header("X-Emby-Token", apiKey)
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