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
    }
    
    data class TrickplayInfo(
        val width: Int,
        val height: Int,
        val tileWidth: Int,
        val tileHeight: Int,
        val thumbnailCount: Int,
        val interval: Int,
        val bandwidth: Long
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
            // Note: info.interval is already in milliseconds (per Jellyfin API spec)
            val thumbnailIndex = (positionMs / info.interval).toInt()
            val tilesPerImage = info.tileWidth * info.tileHeight
            val imageIndex = thumbnailIndex / tilesPerImage
            val tileIndexInImage = thumbnailIndex % tilesPerImage
            
            // Load the tile sheet image
            val tileSheet = loadTileSheet(imageIndex, info.width)
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
                
                val body = response.body?.string()
                body?.let { parseTrickplayInfo(it) }
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
            val trickplayPattern = """"Trickplay"\s*:\s*\{""".toRegex()
            if (!trickplayPattern.containsMatchIn(json)) {
                Log.w(TAG, "No Trickplay field found in item response")
                return null
            }
            
            // Extract the trickplay section
            val trickplayStartIdx = json.indexOf("\"Trickplay\"")
            val trickplaySection = json.substring(trickplayStartIdx)
            
            // Find first mediaSourceId section (any UUID pattern - case insensitive for hex digits)
            val mediaSourcePattern = """"[a-fA-F0-9-]{36}"\s*:\s*\{""".toRegex()
            val mediaSourceMatch = mediaSourcePattern.find(trickplaySection)
            
            if (mediaSourceMatch == null) {
                // Try finding any width directly (for simpler structure)
                val widthPattern = """"(\d+)"\s*:\s*\{""".toRegex()
                val widthMatch = widthPattern.find(trickplaySection) ?: return null
                val width = widthMatch.groupValues[1].toInt()
                
                // Extract TrickplayInfoDto fields for this width
                return extractTrickplayInfo(trickplaySection, width)
            }
            
            // Extract from mediaSource section
            val mediaSourceIdx = trickplaySection.indexOf(mediaSourceMatch.value)
            val mediaSourceSection = trickplaySection.substring(mediaSourceIdx)
            
            // Find first width in this mediaSource section
            val widthPattern = """"(\d+)"\s*:\s*\{""".toRegex()
            val widthMatch = widthPattern.find(mediaSourceSection) ?: return null
            val width = widthMatch.groupValues[1].toInt()
            
            // Extract TrickplayInfoDto fields
            return extractTrickplayInfo(mediaSourceSection, width)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing trickplay info", e)
            return null
        }
    }
    
    private fun extractTrickplayInfo(jsonSection: String, width: Int): TrickplayInfo? {
        try {
            // Extract TrickplayInfoDto fields
            val infoPattern = """"Width"\s*:\s*(\d+).*?"Height"\s*:\s*(\d+).*?"TileWidth"\s*:\s*(\d+).*?"TileHeight"\s*:\s*(\d+).*?"ThumbnailCount"\s*:\s*(\d+).*?"Interval"\s*:\s*(\d+).*?"Bandwidth"\s*:\s*(\d+)""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = infoPattern.find(jsonSection) ?: return null
            
            return TrickplayInfo(
                width = match.groupValues[1].toInt(),
                height = match.groupValues[2].toInt(),
                tileWidth = match.groupValues[3].toInt(),
                tileHeight = match.groupValues[4].toInt(),
                thumbnailCount = match.groupValues[5].toInt(),
                interval = match.groupValues[6].toInt(),
                bandwidth = match.groupValues[7].toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting trickplay info fields", e)
            return null
        }
    }
    
    private suspend fun loadTileSheet(imageIndex: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Jellyfin trickplay tile image endpoint
            val url = "$serverUrl/Videos/$itemId/Trickplay/$width/$imageIndex.jpg"
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Token", apiKey)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Tile sheet request failed: ${response.code}")
                    return@withContext null
                }
                
                val bytes = response.body?.bytes()
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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
