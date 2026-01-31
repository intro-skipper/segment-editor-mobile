package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.ui.validation.SegmentValidator
import kotlin.math.abs

@Composable
fun TimelineScrubber(
    duration: Double,
    startTime: Double,
    endTime: Double,
    onStartTimeChanged: (Double) -> Unit,
    onEndTimeChanged: (Double) -> Unit,
    currentPosition: Double? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    
    var dragTarget by remember { mutableStateOf<DragTarget?>(null) }
    
    Column(modifier = modifier) {
        // Timeline labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Start: ${SegmentValidator.formatTimeString(startTime)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Duration: ${SegmentValidator.formatTimeString(endTime - startTime)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "End: ${SegmentValidator.formatTimeString(endTime)}",
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Timeline scrubber
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val handleRadius = 30f
                            val startX = (startTime / duration * size.width).toFloat()
                            val endX = (endTime / duration * size.width).toFloat()
                            val centerY = size.height / 2f
                            
                            dragTarget = when {
                                abs(offset.x - startX) < handleRadius && abs(offset.y - centerY) < handleRadius -> {
                                    DragTarget.START
                                }
                                abs(offset.x - endX) < handleRadius && abs(offset.y - centerY) < handleRadius -> {
                                    DragTarget.END
                                }
                                offset.x > startX && offset.x < endX -> {
                                    DragTarget.BOTH
                                }
                                else -> null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val target = dragTarget ?: return@detectDragGestures
                            
                            val deltaTime = (dragAmount.x / size.width) * duration
                            
                            when (target) {
                                DragTarget.START -> {
                                    val newStart = (startTime + deltaTime).coerceIn(0.0, endTime - 0.1)
                                    onStartTimeChanged(newStart)
                                }
                                DragTarget.END -> {
                                    val newEnd = (endTime + deltaTime).coerceIn(startTime + 0.1, duration)
                                    onEndTimeChanged(newEnd)
                                }
                                DragTarget.BOTH -> {
                                    val segmentDuration = endTime - startTime
                                    var newStart = startTime + deltaTime
                                    var newEnd = endTime + deltaTime
                                    
                                    // Keep segment within bounds
                                    if (newStart < 0) {
                                        newStart = 0.0
                                        newEnd = segmentDuration
                                    }
                                    if (newEnd > duration) {
                                        newEnd = duration
                                        newStart = duration - segmentDuration
                                    }
                                    
                                    onStartTimeChanged(newStart)
                                    onEndTimeChanged(newEnd)
                                }
                            }
                        },
                        onDragEnd = {
                            dragTarget = null
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            
            // Draw background track
            drawRect(
                color = surfaceVariant,
                topLeft = Offset(0f, centerY - 4f),
                size = Size(width, 8f)
            )
            
            // Draw segment range
            val startX = (startTime / duration * width).toFloat()
            val endX = (endTime / duration * width).toFloat()
            
            drawRect(
                color = secondaryContainer,
                topLeft = Offset(startX, centerY - 4f),
                size = Size(endX - startX, 8f)
            )
            
            // Draw current position indicator
            currentPosition?.let { pos ->
                if (pos in 0.0..duration) {
                    val posX = (pos / duration * width).toFloat()
                    drawLine(
                        color = primaryColor,
                        start = Offset(posX, 0f),
                        end = Offset(posX, height),
                        strokeWidth = 2f
                    )
                }
            }
            
            // Draw start handle
            drawHandle(
                this,
                Offset(startX, centerY),
                primaryColor,
                onSurface
            )
            
            // Draw end handle
            drawHandle(
                this,
                Offset(endX, centerY),
                primaryColor,
                onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Duration labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = SegmentValidator.formatTimeString(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun drawHandle(
    scope: DrawScope,
    center: Offset,
    primaryColor: Color,
    onSurface: Color
) {
    scope.apply {
        // Outer circle
        drawCircle(
            color = primaryColor,
            radius = 12f,
            center = center
        )
        // Inner circle
        drawCircle(
            color = onSurface,
            radius = 6f,
            center = center
        )
    }
}

private enum class DragTarget {
    START,
    END,
    BOTH
}
