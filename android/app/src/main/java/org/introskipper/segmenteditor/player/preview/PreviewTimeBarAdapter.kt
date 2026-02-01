package org.introskipper.segmenteditor.player.preview

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import com.github.rubensousa.previewseekbar.PreviewLoader
import com.github.rubensousa.previewseekbar.PreviewView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Adapter to connect our PreviewLoader implementations with PreviewSeekBar's PreviewLoader interface
 */
class PreviewTimeBarAdapter(
    private val previewLoader: org.introskipper.segmenteditor.player.preview.PreviewLoader,
    private val scope: CoroutineScope
) : PreviewLoader {
    
    private var currentJob: Job? = null
    
    override fun loadPreview(currentPosition: Long, max: Long) {
        currentJob?.cancel()
        // PreviewSeekBar handles the callback through the PreviewView
    }
    
    /**
     * Loads a preview image for the given position and sets it on the imageView
     */
    fun loadPreviewImage(positionMs: Long, imageView: ImageView, previewView: PreviewView) {
        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.Main) {
            try {
                val bitmap = previewLoader.loadPreview(positionMs)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    previewView.visibility = View.VISIBLE
                } else {
                    previewView.visibility = View.GONE
                }
            } catch (e: Exception) {
                previewView.visibility = View.GONE
            }
        }
    }
    
    fun release() {
        currentJob?.cancel()
        previewLoader.release()
    }
}
