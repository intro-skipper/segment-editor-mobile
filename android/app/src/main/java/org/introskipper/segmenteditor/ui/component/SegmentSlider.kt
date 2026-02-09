package org.introskipper.segmenteditor.ui.component

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.TimeUtils
import org.introskipper.segmenteditor.ui.component.segment.TimeInputField
import org.introskipper.segmenteditor.ui.validation.SegmentValidator
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive segment slider component with dual-handle editing.
 * Displays a segment with draggable start/end handles for precise timing adjustments.
 */
@Composable
fun SegmentSlider(
    segment: Segment,
    isActive: Boolean,
    runtimeSeconds: Double,
    onUpdate: (Segment) -> Unit,
    onDelete: () -> Unit,
    onSeekTo: (Double) -> Unit,
    onSetActive: () -> Unit,
    onSetStartFromPlayer: (() -> Unit)? = null,
    onSetEndFromPlayer: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    hasUnsavedChanges: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboard.current.nativeClipboard
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var localStartSeconds by remember(segment) { mutableDoubleStateOf(segment.getStartSeconds()) }
    var localEndSeconds by remember(segment) { mutableDoubleStateOf(segment.getEndSeconds()) }
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    
    val segmentColor = getSegmentColor(segment.type)
    val duration = localEndSeconds - localStartSeconds
    val minGap = 0.5 // Minimum gap between start and end in seconds
    
    // Validate segment boundaries
    val validation = remember(localStartSeconds, localEndSeconds, runtimeSeconds) {
        SegmentValidator.validate(localStartSeconds, localEndSeconds, runtimeSeconds, context)
    }
    
    // Update local state when segment prop changes
    LaunchedEffect(segment.startTicks, segment.endTicks) {
        localStartSeconds = segment.getStartSeconds()
        localEndSeconds = segment.getEndSeconds()
    }
    
    // Commit changes when dragging ends
    LaunchedEffect(isDraggingStart, isDraggingEnd) {
        if (!isDraggingStart && !isDraggingEnd) {
            val newStartTicks = Segment.secondsToTicks(localStartSeconds)
            val newEndTicks = Segment.secondsToTicks(localEndSeconds)
            if (newStartTicks != segment.startTicks || newEndTicks != segment.endTicks) {
                onUpdate(segment.copy(startTicks = newStartTicks, endTicks = newEndTicks))
            }
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSetActive),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segment type badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = segmentColor,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = segment.type,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    
                    // Duration display
                    Text(
                        text = TimeUtils.formatDurationFromSeconds(duration),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy button
                    IconButton(
                        onClick = {
                            val segmentText = "${segment.type}: ${TimeUtils.formatDurationFromSeconds(localStartSeconds)} - ${TimeUtils.formatDurationFromSeconds(localEndSeconds)}"
                            clipboardManager.setPrimaryClip(ClipData.newPlainText(segment.type, AnnotatedString(segmentText)))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy segment",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Save button (if callback provided)
                    if (onSave != null) {
                        IconButton(
                            onClick = onSave,
                            modifier = Modifier.size(36.dp),
                            enabled = hasUnsavedChanges
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save segment",
                                tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete segment",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Interactive slider track
            SliderTrack(
                startPercent = if (runtimeSeconds > 0) (localStartSeconds / runtimeSeconds).toFloat() else 0f,
                endPercent = if (runtimeSeconds > 0) (localEndSeconds / runtimeSeconds).toFloat() else 1f,
                segmentColor = segmentColor,
                onStartDrag = { delta ->
                    isDraggingStart = true
                    val newStart = max(0.0, min(localStartSeconds + delta, localEndSeconds - minGap))
                    localStartSeconds = newStart
                },
                onEndDrag = { delta ->
                    isDraggingEnd = true
                    val newEnd = min(runtimeSeconds, max(localEndSeconds + delta, localStartSeconds + minGap))
                    localEndSeconds = newEnd
                },
                onDragEnd = {
                    isDraggingStart = false
                    isDraggingEnd = false
                },
                runtimeSeconds = runtimeSeconds
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Time inputs and seek buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start time
                TimeInputRow(
                    label = "Start:",
                    timeSeconds = localStartSeconds,
                    onTimeChanged = { newTime ->
                        isDraggingEnd = true
                        localStartSeconds = newTime.coerceIn(0.0, localEndSeconds - minGap)
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            isDraggingStart = false
                            isDraggingEnd = false
                        }
                    ),
                    onSeek = { onSeekTo(localStartSeconds) },
                    onSetFromPlayer = onSetStartFromPlayer,
                    modifier = Modifier.weight(1f)
                )
                
                // End time
                TimeInputRow(
                    label = "End:",
                    timeSeconds = localEndSeconds,
                    onTimeChanged = { newTime ->
                        isDraggingEnd = true
                        localEndSeconds = newTime.coerceIn(localStartSeconds + minGap, runtimeSeconds)
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            isDraggingStart = false
                            isDraggingEnd = false
                        }
                    ),
                    onSeek = { onSeekTo(localEndSeconds) },
                    onSetFromPlayer = onSetEndFromPlayer,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Validation error message
            if (!validation.isValid && validation.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        text = validation.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderTrack(
    startPercent: Float,
    endPercent: Float,
    segmentColor: Color,
    onStartDrag: (Float) -> Unit,
    onEndDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    runtimeSeconds: Double,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackHeight = 40.dp
    val handleWidth = 14.dp
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        
        // Segment range - drawn using Canvas for precise positioning
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw segment range
            val startX = startPercent * width
            val endX = endPercent * width
            val segmentWidth = max(endX - startX, 2f)
            
            drawRect(
                color = segmentColor.copy(alpha = 0.7f),
                topLeft = Offset(startX, 0f),
                size = Size(segmentWidth, height)
            )
        }
        
        // Start handle
        Box(
            modifier = Modifier
                .offset(x = ((maxWidth * startPercent) - (handleWidth / 2)).coerceAtLeast(0.dp))
                .width(handleWidth)
                .fillMaxHeight()
                .background(segmentColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dragDelta = (dragAmount.x / maxWidthPx) * runtimeSeconds.toFloat()
                            onStartDrag(dragDelta)
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.8f))
                    .align(Alignment.Center)
            )
        }
        
        // End handle
        Box(
            modifier = Modifier
                .offset(x = ((maxWidth * endPercent) - (handleWidth / 2)).coerceAtLeast(0.dp))
                .width(handleWidth)
                .fillMaxHeight()
                .background(segmentColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dragDelta = (dragAmount.x / maxWidthPx) * runtimeSeconds.toFloat()
                            onEndDrag(dragDelta)
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.8f))
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun TimeInputRow(
    label: String,
    timeSeconds: Double,
    onTimeChanged: (Double) -> Unit,
    keyboardActions: KeyboardActions,
    onSeek: () -> Unit,
    onSetFromPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSeek,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Seek to $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Capture from player button (if callback provided)
        if (onSetFromPlayer != null) {
            IconButton(
                onClick = onSetFromPlayer,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Set $label from player",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        TimeInputField(
            label = label.removeSuffix(":"),
            timeInSeconds = timeSeconds,
            onTimeChanged = onTimeChanged,
            keyboardActions = keyboardActions,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun getSegmentColor(type: String): Color {
    return org.introskipper.segmenteditor.ui.theme.getSegmentColor(type)
}
