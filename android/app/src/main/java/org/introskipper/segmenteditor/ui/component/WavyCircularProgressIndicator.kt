/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An indeterminate circular progress indicator with a wavy/squiggly stroke,
 * replicating the style used by Google Play for indeterminate progress.
 */
@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavyProgress")

    // Rotation of the whole arc
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Wave phase offset to make the squiggle animate along the path
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Arc sweep length oscillates so the arc stretches and contracts
    val sweepFraction by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepFraction"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val radius = (this.size.minDimension - strokePx * 2) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        val waveAmplitude = strokePx * 0.55f
        val waveCount = 4.5f
        val sweepDegrees = sweepFraction * 360f
        val numPoints = 120

        val path = Path()
        for (i in 0..numPoints) {
            val t = i.toFloat() / numPoints
            val angleDeg = rotation + t * sweepDegrees
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val wave = waveAmplitude * sin(t * 2.0 * PI * waveCount + wavePhase).toFloat()
            val r = radius + wave
            val x = center.x + cos(angleRad).toFloat() * r
            val y = center.y + sin(angleRad).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}
