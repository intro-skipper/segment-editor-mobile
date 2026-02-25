/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.framecapture

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.introskipper.segmenteditor.toPx
import org.introskipper.segmenteditor.ui.preview.TrickplayPreviewLoader
import org.introskipper.segmenteditor.ui.viewmodel.PlayerViewModel
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object FramePreview {
    private const val TAG = "PreviewFrames"
    var retriever: MediaMetadataRetriever? = null
    var frameCapture: AV_FrameCapture? = null

    private var initJob: Job? = null
    private val extractionMutex = Mutex()

    // Preview frame caching
    private val previewCache = LruCache<Long, Bitmap>(TrickplayPreviewLoader.MAX_PREVIEW_CACHE_SIZE)
    @Volatile private var latestPreviewKey: Long = -1

    fun PlayerViewModel.onPreviewsRequested(streamUrl: String) {
        // Clear previous state
        onReleasePreviews()

        Log.d(TAG, "Initializing preview extraction for $streamUrl")

        initJob = viewModelScope.launch(Dispatchers.IO) {
            // Start probes in parallel
            coroutineScope {

                val retrieverJob = async {
                    var retr: MediaMetadataRetriever? = null
                    try {
                        retr = MediaMetadataRetriever().apply { setDataSource(streamUrl) }
                        if (isActive) retr else {
                            retr.close(); null
                        }
                    } catch (_: Exception) {
                        retr?.close(); null
                    }
                }

                val frameJob = async {
                    try {
                        val fc = AV_FrameCapture()
                        fc.setDataSource(streamUrl)
                        fc.setTargetSize(176.toPx, 96.toPx)
                        if (fc.init()) {
                            fc
                        } else {
                            fc.release()
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                retrieverJob.await()?.let {
                    retriever = it
                }

                frameJob.await()?.let {
                    frameCapture = it
                }
            }
        }
    }

    suspend fun loadPreviewFrame(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        // Wait for initialization to complete if it's still running
        initJob?.join()

        val cacheKey = positionMs / TrickplayPreviewLoader.DEFAULT_INTERVAL_MS

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
                val bitmap =
                    retriever?.getScaledFrameAtTime(keyFrame.inWholeMicroseconds,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 176.toPx, 96.toPx)
                        ?: frameCapture?.getFrameAtTime(keyFrame.inWholeMicroseconds)

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

        frameCapture?.release()
        frameCapture = null

        retriever?.close()
        retriever = null

        // Clear cache and reset state
        previewCache.evictAll()
        latestPreviewKey = -1L
    }
}
