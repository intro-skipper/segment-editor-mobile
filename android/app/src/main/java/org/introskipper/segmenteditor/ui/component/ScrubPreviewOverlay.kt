package org.introskipper.segmenteditor.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.introskipper.segmenteditor.data.model.TimeUtils
import org.introskipper.segmenteditor.player.preview.PreviewLoader

/**
 * Overlay that displays a preview thumbnail and timestamp when scrubbing the video timeline
 * 
 * @param previewLoader The loader to fetch preview images
 * @param positionMs Current scrub position in milliseconds
 * @param isVisible Whether the overlay should be visible
 * @param modifier Modifier for styling
 */
@Composable
fun ScrubPreviewOverlay(
    previewLoader: PreviewLoader?,
    positionMs: Long,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) {
        android.util.Log.d("ScrubPreviewOverlay", "Overlay not visible")
        return
    }
    if (previewLoader == null) {
        android.util.Log.w("ScrubPreviewOverlay", "PreviewLoader is null, cannot show preview")
        return
    }
    
    android.util.Log.d("ScrubPreviewOverlay", "Showing preview overlay at position: $positionMs")
    
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Load preview image when position changes
    LaunchedEffect(positionMs, previewLoader) {
        isLoading = true
        android.util.Log.d("ScrubPreviewOverlay", "Loading preview for position: $positionMs")
        try {
            val bitmap = withContext(Dispatchers.IO) {
                previewLoader.loadPreview(positionMs)
            }
            previewBitmap = bitmap
            if (bitmap != null) {
                android.util.Log.d("ScrubPreviewOverlay", "Preview loaded successfully: ${bitmap.width}x${bitmap.height}")
            } else {
                android.util.Log.w("ScrubPreviewOverlay", "Preview loader returned null bitmap")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation to allow proper coroutine cancellation
            android.util.Log.d("ScrubPreviewOverlay", "Preview loading cancelled for position: $positionMs")
            throw e
        } catch (e: Exception) {
            // Silently fail - preview is optional
            android.util.Log.e("ScrubPreviewOverlay", "Failed to load preview", e)
            previewBitmap = null
        } finally {
            isLoading = false
        }
    }
    
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Preview image
            previewBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Video preview at ${TimeUtils.formatMilliseconds(positionMs)}",
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                )
            } ?: run {
                // Placeholder when no preview available
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Timestamp
            Text(
                text = TimeUtils.formatMilliseconds(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
