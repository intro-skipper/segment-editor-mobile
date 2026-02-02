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
     * Gets the interval between preview images in milliseconds
     */
    fun getPreviewInterval(): Long
    
    /**
     * Cleans up any resources used by the loader
     */
    fun release()
}
