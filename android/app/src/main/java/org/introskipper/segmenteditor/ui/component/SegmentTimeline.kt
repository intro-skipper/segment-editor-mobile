package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.data.model.Segment

/**
 * Timeline component that displays segment markers over a video timeline.
 * 
 * @param segments List of segments to display
 * @param duration Video duration in milliseconds
 * @param currentPosition Current playback position in milliseconds
 * @param modifier Modifier for the component
 */
@Composable
fun SegmentTimeline(
    segments: List<Segment>,
    duration: Long,
    currentPosition: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val width = size.width
        val height = size.height
        
        // Draw background
        drawRect(
            color = Color.Gray.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = Size(width, height)
        )
        
        // Draw segment markers
        if (duration > 0) {
            segments.forEach { segment ->
                // Convert segment times (in seconds) to milliseconds, then calculate position
                val startMs = segment.getStartSeconds() * 1000
                val endMs = segment.getEndSeconds() * 1000
                val startPos = (startMs / duration * width).toFloat()
                val endPos = (endMs / duration * width).toFloat()
                
                val color = when (segment.type.lowercase()) {
                    "intro" -> Color(0xFF4CAF50) // Green
                    "credits" -> Color(0xFF2196F3) // Blue
                    "commercial" -> Color(0xFFF44336) // Red
                    "recap" -> Color(0xFFFF9800) // Orange
                    "preview" -> Color(0xFF9C27B0) // Purple
                    else -> Color(0xFFFFEB3B) // Yellow
                }
                
                drawRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset(startPos, 0f),
                    size = Size(maxOf(endPos - startPos, 2f), height)
                )
            }
        }
        
        // Draw current position indicator
        if (duration > 0) {
            val progressPos = (currentPosition.toFloat() / duration * width)
            drawLine(
                color = Color.White,
                start = Offset(progressPos, 0f),
                end = Offset(progressPos, height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
