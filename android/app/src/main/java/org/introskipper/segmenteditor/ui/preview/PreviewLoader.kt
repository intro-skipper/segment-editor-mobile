/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.preview

import android.graphics.Bitmap

/**
 * Interface for loading preview images for video scrubbing
 */
interface PreviewLoader {
    /**
     * Loads a preview image for the given position in the video
     * @param positionMs Position in the video in milliseconds
     * @return Bitmap of the preview image, or null if not available
     */
    suspend fun loadPreview(positionMs: Long): Bitmap?
    
    /**
     * Preloads preview images around the given position for smoother scrubbing
     * @param positionMs Center position in milliseconds
     * @param count Number of previews to preload in each direction
     */
    suspend fun preloadPreviews(positionMs: Long, count: Int = 5) {
        // Default implementation - subclasses can override for optimization
    }
    
    /**
     * Gets the interval between preview images in milliseconds
     */
    fun getPreviewInterval(): Long
    
    /**
     * Whether this loader benefits from a warmup call at position 0 before the user scrubs.
     * Should return true only for remote sources (e.g. Trickplay) that benefit from pre-fetching.
     */
    val requiresWarmup: Boolean get() = false

    /**
     * Cleans up any resources used by the loader
     */
    fun release()
}
