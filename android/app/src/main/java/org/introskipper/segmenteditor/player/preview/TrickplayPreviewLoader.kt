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
            val thumbnailIndex = (positionMs / (info.interval * 1000)).toInt()
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
            // Jellyfin trickplay API endpoint
            val url = "$serverUrl/Videos/$itemId/Trickplay"
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Token", apiKey)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Trickplay info request failed: ${response.code}")
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
            // Parse the JSON response
            // The format is typically: { "width": {...} }
            // We'll take the first available width
            val widthPattern = """"(\d+)"\s*:\s*\{""".toRegex()
            val widthMatch = widthPattern.find(json) ?: return null
            val width = widthMatch.groupValues[1].toInt()
            
            // Extract info for this width
            val infoPattern = """"Width"\s*:\s*(\d+).*?"Height"\s*:\s*(\d+).*?"TileWidth"\s*:\s*(\d+).*?"TileHeight"\s*:\s*(\d+).*?"ThumbnailCount"\s*:\s*(\d+).*?"Interval"\s*:\s*(\d+).*?"Bandwidth"\s*:\s*(\d+)""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = infoPattern.find(json) ?: return null
            
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
            Log.e(TAG, "Error parsing trickplay info", e)
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
        return (trickplayInfo?.interval?.toLong() ?: DEFAULT_INTERVAL_MS / 1000) * 1000
    }
    
    override fun release() {
        previewCache.clear()
    }
}
