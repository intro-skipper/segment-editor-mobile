package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.theme.getSegmentColor

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
    // Cache theme state and segment colors for performance
    // Only recalculates when segments list or theme changes
    val isDark = isSystemInDarkTheme()
    val segmentColors = remember(segments, isDark) {
        segments.map { segment ->
            segment to getSegmentColor(segment.type, isDark)
        }
    }
    
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
            segmentColors.forEach { (segment, color) ->
                // Convert segment times (in seconds) to milliseconds, then calculate position
                val startMs = segment.getStartSeconds() * 1000
                val endMs = segment.getEndSeconds() * 1000
                val startPos = (startMs / duration * width).toFloat()
                val endPos = (endMs / duration * width).toFloat()
                
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
