package org.introskipper.segmenteditor.player.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
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
    private val itemId: String,
    private val httpClient: OkHttpClient
) : PreviewLoader {
    
    private var trickplayInfo: TrickplayInfo? = null
    private val previewCache = mutableMapOf<Long, Bitmap>()
    
    companion object {
        private const val TAG = "TrickplayPreviewLoader"
        private const val DEFAULT_INTERVAL_MS = 10000L // 10 seconds
        
        // Regex patterns for JSON parsing - compiled once for efficiency
        private val TRICKPLAY_PATTERN = """"Trickplay"\s*:\s*\{""".toRegex()
        private val MEDIA_SOURCE_ID_PATTERN = """"([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})"\s*:\s*\{""".toRegex()
        private val WIDTH_PATTERN = """"(\d+)"\s*:\s*\{""".toRegex()
        private val WIDTH_FIELD_PATTERN = """"Width"\s*:\s*(\d+)""".toRegex()
        private val HEIGHT_FIELD_PATTERN = """"Height"\s*:\s*(\d+)""".toRegex()
        private val TILE_WIDTH_FIELD_PATTERN = """"TileWidth"\s*:\s*(\d+)""".toRegex()
        private val TILE_HEIGHT_FIELD_PATTERN = """"TileHeight"\s*:\s*(\d+)""".toRegex()
        private val THUMBNAIL_COUNT_FIELD_PATTERN = """"ThumbnailCount"\s*:\s*(\d+)""".toRegex()
        private val INTERVAL_FIELD_PATTERN = """"Interval"\s*:\s*(\d+)""".toRegex()
        private val BANDWIDTH_FIELD_PATTERN = """"Bandwidth"\s*:\s*(\d+)""".toRegex()
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
            val url = "$serverUrl/Items/$itemId"
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
            
            // Find the Trickplay field
            if (!TRICKPLAY_PATTERN.containsMatchIn(json)) {
                Log.w(TAG, "No Trickplay field found in item response")
                return null
            }
            
            // Extract the trickplay section - find the section between "Trickplay": { and its closing }
            val trickplayStartIdx = json.indexOf("\"Trickplay\"")
            if (trickplayStartIdx == -1) return null
            
            val jsonFromTrickplay = json.substring(trickplayStartIdx)
            
            // Find the first media source ID (UUID format)
            val mediaSourceMatch = MEDIA_SOURCE_ID_PATTERN.find(jsonFromTrickplay)
            
            if (mediaSourceMatch == null) {
                Log.w(TAG, "No media source ID found in Trickplay data")
                return null
            }
            
            val mediaSourceId = mediaSourceMatch.groupValues[1]
            Log.d(TAG, "Found media source ID: $mediaSourceId")
            
            // Extract the section for this media source
            val mediaSourceIdx = jsonFromTrickplay.indexOf(mediaSourceMatch.value)
            val mediaSourceSection = jsonFromTrickplay.substring(mediaSourceIdx)
            
            // Find the first width (numeric key followed by colon and opening brace)
            val widthMatch = WIDTH_PATTERN.find(mediaSourceSection)
            
            if (widthMatch == null) {
                Log.w(TAG, "No width found in media source section")
                return null
            }
            
            val width = widthMatch.groupValues[1].toInt()
            Log.d(TAG, "Found width: $width")
            
            // Extract TrickplayInfoDto fields for this width
            return extractTrickplayInfo(mediaSourceSection, width, mediaSourceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing trickplay info", e)
            return null
        }
    }
    
    private fun extractTrickplayInfo(jsonSection: String, width: Int, mediaSourceId: String): TrickplayInfo? {
        try {
            // Extract TrickplayInfoDto fields - they might appear in any order
            val widthMatch = WIDTH_FIELD_PATTERN.find(jsonSection)
            if (widthMatch == null) {
                Log.w(TAG, "Failed to find Width field in trickplay data")
                return null
            }
            
            val heightMatch = HEIGHT_FIELD_PATTERN.find(jsonSection)
            if (heightMatch == null) {
                Log.w(TAG, "Failed to find Height field in trickplay data")
                return null
            }
            
            val tileWidthMatch = TILE_WIDTH_FIELD_PATTERN.find(jsonSection)
            if (tileWidthMatch == null) {
                Log.w(TAG, "Failed to find TileWidth field in trickplay data")
                return null
            }
            
            val tileHeightMatch = TILE_HEIGHT_FIELD_PATTERN.find(jsonSection)
            if (tileHeightMatch == null) {
                Log.w(TAG, "Failed to find TileHeight field in trickplay data")
                return null
            }
            
            val thumbnailCountMatch = THUMBNAIL_COUNT_FIELD_PATTERN.find(jsonSection)
            if (thumbnailCountMatch == null) {
                Log.w(TAG, "Failed to find ThumbnailCount field in trickplay data")
                return null
            }
            
            val intervalMatch = INTERVAL_FIELD_PATTERN.find(jsonSection)
            if (intervalMatch == null) {
                Log.w(TAG, "Failed to find Interval field in trickplay data")
                return null
            }
            
            val bandwidthMatch = BANDWIDTH_FIELD_PATTERN.find(jsonSection)
            if (bandwidthMatch == null) {
                Log.w(TAG, "Failed to find Bandwidth field in trickplay data")
                return null
            }
            
            return TrickplayInfo(
                width = widthMatch.groupValues[1].toInt(),
                height = heightMatch.groupValues[1].toInt(),
                tileWidth = tileWidthMatch.groupValues[1].toInt(),
                tileHeight = tileHeightMatch.groupValues[1].toInt(),
                thumbnailCount = thumbnailCountMatch.groupValues[1].toInt(),
                interval = intervalMatch.groupValues[1].toInt(),
                bandwidth = bandwidthMatch.groupValues[1].toLong(),
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
