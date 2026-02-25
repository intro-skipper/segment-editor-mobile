/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.state.PlayerUiState

/**
 * Overlay that displays a "Skip" button when the player is within a defined segment.
 */
@Composable
fun SkipOverlay(
    player: Player?,
    uiState: PlayerUiState,
    modifier: Modifier = Modifier
) {
    var activeSegment by remember { mutableStateOf<Segment?>(null) }
    
    // Periodically check if we are in a segment
    LaunchedEffect(uiState.currentPosition, uiState.segments) {
        val currentMs = uiState.currentPosition
        val currentTicks = currentMs * 10_000L
        
        activeSegment = uiState.segments.find { segment ->
            currentTicks >= segment.startTicks && currentTicks < segment.endTicks
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = activeSegment != null,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            activeSegment?.let { segment ->
                Button(
                    onClick = {
                        val endMs = segment.endTicks / 10_000L
                        player?.seekTo(endMs)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(R.string.skip_segment, segment.type),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
