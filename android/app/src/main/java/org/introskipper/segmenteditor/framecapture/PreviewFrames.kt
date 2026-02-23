/*
 * Copyright (c) 2026 AbandonedCart.  All rights reserved.
 */

package org.introskipper.segmenteditor.framecapture

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.scale
import androidx.lifecycle.viewModelScope
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfo
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.introskipper.segmenteditor.toPx
import org.introskipper.segmenteditor.ui.viewmodel.PlayerViewModel
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object PreviewFrames {
    private const val TAG = "PreviewFrames"
    var frameCapture: AV_FrameCapture? = null
    var mediaInfo: MediaInfo? = null
    var retriever: MediaMetadataRetriever? = null
    
    private var initJob: Job? = null
    private val extractionMutex = Mutex()

    // Preview frame caching
    private val previewCache = LruCache<Long, Bitmap>(300)
    @Volatile private var latestPreviewKey: Long = -1

    fun PlayerViewModel.onPreviewsRequested(streamUrl: String) {
        // Clear previous state
        onReleasePreviews()
        
        Log.d(TAG, "Initializing preview extraction for $streamUrl")

        initJob = viewModelScope.launch(Dispatchers.IO) {
            // Start probes in parallel
            val fcJob = async {
                try {
                    AV_FrameCapture().apply {
                        setDataSource(streamUrl)
                        setTargetSize(176.toPx, 96.toPx)
                        if (!init()) { release(); return@async null }
                    }
                } catch (_: Exception) { null }
            }

            val retrieverJob = async {
                try {
                    MediaMetadataRetriever().apply { setDataSource(streamUrl) }
                } catch (_: Exception) { null }
            }

            val mediaInfoJob = async {
                try {
                    MediaInfoBuilder().from(streamUrl).build().takeIf { it.supportsFrameLoading }
                } catch (_: Exception) { null }
            }

            // Await them in priority order
            val fc = fcJob.await()
            if (fc != null) {
                Log.d(TAG, "Using AV_FrameCapture for previews")
                frameCapture = fc
                retrieverJob.cancel()
                mediaInfoJob.cancel()
                return@launch
            }

            val retr = try { retrieverJob.await() } catch (_: Exception) { null }
            if (retr != null) {
                Log.d(TAG, "Using MediaMetadataRetriever for previews")
                retriever = retr
                mediaInfoJob.cancel()
                return@launch
            }

            val info = try { mediaInfoJob.await() } catch (_: Exception) { null }
            if (info != null) {
                Log.d(TAG, "Using MediaInfo for previews")
                mediaInfo = info
                return@launch
            }
            
            Log.w(TAG, "No suitable preview extractor found")
        }
    }

    suspend fun loadPreviewFrame(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        // Wait for initialization to complete if it's still running
        initJob?.join()
        
        val cacheKey = positionMs / 1000
        
        // Always check cache first to avoid redundant extraction work
        previewCache.get(cacheKey)?.let { return@withContext it }

        // Track the latest requested key to skip intermediate frames during scrubbing
        latestPreviewKey = cacheKey

        extractionMutex.withLock {
            // Check cache again after obtaining the lock
            previewCache.get(cacheKey)?.let { return@withLock it }

            // Only proceed if we are still the latest requested frame
            if (cacheKey != latestPreviewKey) return@withLock null

            try {
                val keyFrame = cacheKey.toDuration(DurationUnit.SECONDS)
                val bitmap = frameCapture?.getFrameAtTime(keyFrame.inWholeMicroseconds)
                    ?: retriever?.getScaledFrameAtTime(keyFrame.inWholeMicroseconds,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 176.toPx, 96.toPx)
                    ?: mediaInfo?.getFrameAt(keyFrame.inWholeMilliseconds)?.let { originalBitmap ->
                        originalBitmap.scale(176.toPx, 96.toPx, false).apply { originalBitmap.recycle() }
                    }
                    
                bitmap?.let {
                    previewCache.put(cacheKey, it)
                }
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting frame for $positionMs", e)
                null
            }
        }
    }

    fun onReleasePreviews() {
        initJob?.cancel()
        initJob = null
        
        mediaInfo?.release()
        mediaInfo = null
        
        retriever?.close()
        retriever = null
        
        frameCapture?.release()
        frameCapture = null

        // Clear cache and reset state
        previewCache.evictAll()
        latestPreviewKey = -1L
    }
}
