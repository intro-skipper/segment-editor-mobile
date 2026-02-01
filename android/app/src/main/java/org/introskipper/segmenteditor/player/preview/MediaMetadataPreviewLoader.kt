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
        private const val MAX_CACHE_SIZE = 20 // Maximum number of cached preview frames
        private const val CACHE_CLEANUP_COUNT = 5 // Number of oldest entries to remove when cache is full
    }
    
    init {
        try {
            val ret = MediaMetadataRetriever()
            
            // For network URLs, we need to use setDataSource with headers
            if (videoUri.startsWith("http://") || videoUri.startsWith("https://")) {
                // Use empty headers map for network streams
                // Note: This may not work for all network streams (e.g., HLS with authentication)
                ret.setDataSource(videoUri, mapOf())
            } else {
                // Local file path
                ret.setDataSource(videoUri)
            }
            
            // Get video duration - validate that retriever is properly initialized
            val durationStr = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
            
            // Only set retriever if initialization was successful
            retriever = ret
            
            Log.d(TAG, "Initialized MediaMetadataRetriever for video: $videoUri, duration: ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaMetadataRetriever for $videoUri", e)
            retriever?.release()
            retriever = null
            // Re-throw the exception so the caller knows initialization failed
            throw IllegalStateException("Failed to initialize MediaMetadataRetriever for $videoUri", e)
        }
    }
    
    override suspend fun loadPreview(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = previewCache[positionMs]
            if (cached != null) {
                Log.d(TAG, "Returning cached preview for position $positionMs")
                return@withContext cached
            }
            
            // Retriever should always be non-null if init succeeded
            val ret = retriever ?: run {
                Log.e(TAG, "MediaMetadataRetriever is null - this should not happen if init succeeded")
                return@withContext null
            }
            
            // Ensure position is within bounds
            val clampedPosition = positionMs.coerceIn(0, durationMs)
            Log.d(TAG, "Extracting frame at position $clampedPosition (original: $positionMs, duration: $durationMs)")
            
            // Extract frame at the given position
            // Note: getFrameAtTime uses microseconds
            // Try OPTION_CLOSEST_SYNC first (faster, uses keyframes) then fallback to OPTION_CLOSEST for accuracy
            val frame = ret.getFrameAtTime(
                clampedPosition * 1000, // Convert ms to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: run {
                // If OPTION_CLOSEST_SYNC fails, try OPTION_CLOSEST as fallback (slower but more accurate)
                Log.d(TAG, "OPTION_CLOSEST_SYNC failed, trying OPTION_CLOSEST")
                ret.getFrameAtTime(
                    clampedPosition * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
            }
            
            if (frame == null) {
                Log.w(TAG, "Failed to extract frame - getFrameAtTime returned null")
                return@withContext null
            }
            
            Log.d(TAG, "Frame extracted successfully: ${frame.width}x${frame.height}")
            
            // Scale the frame to a smaller size for preview
            val scaledFrame = try {
                Bitmap.createScaledBitmap(
                    frame,
                    FRAME_WIDTH,
                    FRAME_HEIGHT,
                    true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scale bitmap", e)
                // If scaling fails, use the original frame
                frame
            }
            
            // Recycle the original frame to free memory (only if it's different from scaled)
            if (frame != scaledFrame) {
                frame.recycle()
            }
            
            // Cache the result
            previewCache[positionMs] = scaledFrame
            Log.d(TAG, "Cached preview for position $positionMs (cache size: ${previewCache.size})")
            
            // Limit cache size to avoid memory issues
            if (previewCache.size > MAX_CACHE_SIZE) {
                // Remove oldest entries
                previewCache.keys.take(CACHE_CLEANUP_COUNT).forEach { key ->
                    previewCache.remove(key)?.recycle()
                }
                Log.d(TAG, "Cleaned up cache")
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
