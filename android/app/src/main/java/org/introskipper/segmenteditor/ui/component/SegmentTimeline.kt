/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.theme.getSegmentColor

private const val MIN_VISIBLE_WIDTH = 0.008

/** A read-only timeline showing where an episode's segments fall in its runtime. */
@Composable
fun SegmentTimeline(
    segments: List<Segment>?,
    runtimeSeconds: Double?,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    currentPositionSeconds: Double? = null
) {
    val segmentColors = segments.orEmpty().map { segment ->
        segment to getSegmentColor(segment.type)
    }
    val duration = runtimeSeconds?.takeIf { it > 0 }
        ?: segments.orEmpty().maxOfOrNull { it.getEndSeconds() }?.takeIf { it > 0 }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .semantics {
                contentDescription = when {
                    isLoading -> "Loading segments"
                    segments.isNullOrEmpty() -> "No segments"
                    else -> "${segments.size} segments"
                }
            }
    ) {
        if (!isLoading && duration != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawSegments(segmentColors, duration)
                currentPositionSeconds?.let { position ->
                    drawProgress(position, duration)
                }
            }
        }
    }
}

/** Compatibility overload used by the player, whose timing values are milliseconds. */
@Composable
fun SegmentTimeline(
    segments: List<Segment>,
    duration: Long,
    currentPosition: Long,
    modifier: Modifier = Modifier
) {
    SegmentTimeline(
        segments = segments,
        runtimeSeconds = duration / 1_000.0,
        modifier = modifier,
        currentPositionSeconds = currentPosition / 1_000.0
    )
}

private fun DrawScope.drawSegments(
    segments: List<Pair<Segment, Color>>,
    duration: Double
) {
    segments.forEach { (segment, color) ->
        val startSeconds = segment.getStartSeconds()
        val endSeconds = segment.getEndSeconds()
        if (endSeconds <= startSeconds || startSeconds >= duration) return@forEach

        val start = (startSeconds / duration).coerceIn(0.0, 1.0)
        val end = (endSeconds / duration).coerceIn(0.0, 1.0)
        val actualWidth = end - start
        if (actualWidth <= 0) return@forEach

        val width = maxOf(actualWidth, MIN_VISIBLE_WIDTH).coerceAtMost(1.0)
        val left = minOf(start, 1.0 - width)
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width * left).toFloat(), 0f),
            size = Size((size.width * width).toFloat(), size.height),
            cornerRadius = CornerRadius(size.height / 2f)
        )
    }
}

private fun DrawScope.drawProgress(positionSeconds: Double, duration: Double) {
    val progress = (positionSeconds / duration).coerceIn(0.0, 1.0)
    val x = (size.width * progress).toFloat()
    drawLine(
        color = Color.White,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 2.dp.toPx()
    )
}
