package org.introskipper.segmenteditor.player.preview

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads preview images using MediaMetadataRetriever to extract frames from local video
 * Note: This works best with local file URIs. For network streams, consider using TrickplayPreviewLoader instead.
 */
class MediaMetadataPreviewLoader(
    private val videoUri: String
) : PreviewLoader {
    
    private var retriever: MediaMetadataRetriever? = null
    private val previewCache = mutableMapOf<Long, Bitmap>()
    private var durationMs: Long = 0
    
    companion object {
        private const val TAG = "MediaMetadataPreviewLoader"
        private const val DEFAULT_INTERVAL_MS = 10000L // 10 seconds
        private const val FRAME_WIDTH = 320 // Width of preview frames
        private const val FRAME_HEIGHT = 180 // Height of preview frames (16:9 aspect ratio)
    }
    
    init {
        try {
            retriever = MediaMetadataRetriever()
            
            // For network URLs, we need to use setDataSource with headers
            if (videoUri.startsWith("http://") || videoUri.startsWith("https://")) {
                // Use empty headers map for network streams
                // Note: This may not work for all network streams (e.g., HLS with authentication)
                retriever?.setDataSource(videoUri, HashMap<String, String>())
            } else {
                // Local file path
                retriever?.setDataSource(videoUri)
            }
            
            // Get video duration
            val durationStr = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
            
            Log.d(TAG, "Initialized MediaMetadataRetriever for video: $videoUri, duration: ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaMetadataRetriever", e)
            retriever?.release()
            retriever = null
        }
    }
    
    override suspend fun loadPreview(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            previewCache[positionMs]?.let { return@withContext it }
            
            val ret = retriever ?: run {
                Log.w(TAG, "MediaMetadataRetriever not initialized")
                return@withContext null
            }
            
            // Ensure position is within bounds
            val clampedPosition = positionMs.coerceIn(0, durationMs)
            
            // Extract frame at the given position
            // Note: getFrameAtTime uses microseconds
            val frame = ret.getFrameAtTime(
                clampedPosition * 1000, // Convert ms to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            frame ?: run {
                Log.w(TAG, "Failed to extract frame at position $positionMs")
                return@withContext null
            }
            
            // Scale the frame to a smaller size for preview
            val scaledFrame = Bitmap.createScaledBitmap(
                frame,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                true
            )
            
            // Recycle the original frame to free memory
            if (frame != scaledFrame) {
                frame.recycle()
            }
            
            // Cache the result
            previewCache[positionMs] = scaledFrame
            
            // Limit cache size to avoid memory issues
            if (previewCache.size > 20) {
                // Remove oldest entries
                previewCache.keys.take(5).forEach { key ->
                    previewCache.remove(key)?.recycle()
                }
            }
            
            scaledFrame
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preview for position $positionMs", e)
            null
        }
    }
    
    override fun getPreviewInterval(): Long {
        return DEFAULT_INTERVAL_MS
    }
    
    override fun release() {
        try {
            // Clean up cache
            previewCache.values.forEach { it.recycle() }
            previewCache.clear()
            
            // Release retriever
            retriever?.release()
            retriever = null
            
            Log.d(TAG, "Released MediaMetadataRetriever resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
        }
    }
}
