package org.introskipper.segmenteditor.ui.preview

import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.data.model.TimeUtils

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
        Log.d("ScrubPreviewOverlay", "Overlay not visible")
        return
    }
    if (previewLoader == null) {
        Log.w("ScrubPreviewOverlay", "PreviewLoader is null, cannot show preview")
        return
    }

    Log.d("ScrubPreviewOverlay", "Showing preview overlay at position: $positionMs")

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Track the last requested position to detect changes without using it as a LaunchedEffect key
    var lastRequestedPosition by remember { mutableLongStateOf(-1L) }
    
    // Channel to send position requests without cancelling the loader coroutine
    val positionChannel = remember { Channel<Long>(Channel.CONFLATED) }

    // Load preview images - this effect never restarts, only when previewLoader changes
    LaunchedEffect(previewLoader) {
        // Process position requests from the channel
        for (position in positionChannel) {
            // Launch each load as an independent job
            launch {
                isLoading = true
                Log.d("ScrubPreviewOverlay", "Loading preview for position: $position")
                try {
                    val bitmap = previewLoader.loadPreview(position)
                    previewBitmap = bitmap
                } catch (e: Exception) {
                    // Silently fail - preview is optional
                    Log.e("ScrubPreviewOverlay", "Failed to load preview", e)
                    previewBitmap = null
                } finally {
                    isLoading = false
                }
            }
        }
    }
    
    // Detect position changes and send to channel
    // Use SideEffect to ensure this runs only once per successful composition
    SideEffect {
        if (positionMs != lastRequestedPosition) {
            lastRequestedPosition = positionMs
            // Send to channel - this won't block and will replace previous unconsumed value
            positionChannel.trySend(positionMs)
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
