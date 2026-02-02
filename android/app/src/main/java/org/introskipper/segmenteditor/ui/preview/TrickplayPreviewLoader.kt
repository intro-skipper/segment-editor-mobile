package org.introskipper.segmenteditor.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
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
    private val httpClient: OkHttpClient
) : PreviewLoader {
    
    private var trickplayInfo: TrickplayInfo? = null
    private val previewCache = mutableMapOf<Long, Bitmap>()
    
    companion object {
        private const val TAG = "TrickplayPreviewLoader"
        private const val DEFAULT_INTERVAL_MS = 10000L // 10 seconds
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
        try {
            // Check cache first
            previewCache[positionMs]?.let { return@withContext it }
            
            // Load trickplay info if not already loaded
            if (trickplayInfo == null) {
                trickplayInfo = loadTrickplayInfo()
            }
            
            val info = trickplayInfo ?: run {
                Log.w(TAG, "Trickplay info not available")
                return@withContext null
            }
            
            // Calculate which tile image and position within the tile
            // Note: info.interval is in milliseconds (per Jellyfin API spec)
            val thumbnailIndex = (positionMs / info.interval).toInt()
            val tilesPerImage = info.tileWidth * info.tileHeight
            val imageIndex = thumbnailIndex / tilesPerImage
            val tileIndexInImage = thumbnailIndex % tilesPerImage
            
            // Load the tile sheet image
            val tileSheet = loadTileSheet(imageIndex, info.width, info.mediaSourceId)
            tileSheet ?: run {
                Log.w(TAG, "Failed to load tile sheet $imageIndex")
                return@withContext null
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
            
            // Cache the result
            previewCache[positionMs] = thumbnail
            
            // Limit cache size
            if (previewCache.size > 20) {
                previewCache.keys.take(5).forEach { previewCache.remove(it) }
            }
            
            thumbnail
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preview for position $positionMs", e)
            null
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
            // Jellyfin trickplay tile image endpoint
            val url = "$serverUrl/Videos/$itemId/Trickplay/$width/$imageIndex.jpg?mediaSourceId=$mediaSourceId"
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Token", apiKey)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Tile sheet request failed: ${response.code}")
                    return@withContext null
                }

                response.body.bytes().let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading tile sheet $imageIndex", e)
            null
        }
    }
    
    override fun getPreviewInterval(): Long {
        // interval is in milliseconds
        return trickplayInfo?.interval?.toLong() ?: DEFAULT_INTERVAL_MS
    }
    
    override fun release() {
        previewCache.clear()
    }
}