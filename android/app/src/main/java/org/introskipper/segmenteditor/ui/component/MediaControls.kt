package org.introskipper.segmenteditor.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.preview.ScrubPreviewOverlay
import org.introskipper.segmenteditor.ui.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Media controls overlay with scrubbing preview support
 * Provides play/pause, seek, and preview-on-scrub functionality
 */
@Composable
fun MediaControls(
    uiState: org.introskipper.segmenteditor.ui.state.PlayerUiState,
    viewModel: PlayerViewModel,
    player: Player?,
    modifier: Modifier = Modifier,
    previewLoader: PreviewLoader? = null,
    onScrubStart: (Long) -> Unit = {},
    onScrubMove: (Long) -> Unit = {},
    onScrubEnd: (Long) -> Unit = {}
) {
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    
    // Coroutine scope for preview loading, tied to composable lifecycle
    val coroutineScope = rememberCoroutineScope()
    var preloadJob by remember { mutableStateOf<Job?>(null) }
    
    // Update playback state
    LaunchedEffect(player) {
        while (isActive && player != null) {
            isPlaying = player.isPlaying
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            isLoading = player.playbackState == Player.STATE_BUFFERING
            delay(100) // Update every 100ms for smooth progress
        }
    }
    
    // Auto-hide controls after 3 seconds of playback
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            }
    ) {
        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color.White
            )
        }
        
        // Scrub preview overlay (shown when scrubbing)
        if (isScrubbing && previewLoader != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 18.dp)
                    .zIndex(10f)
            ) {
                ScrubPreviewOverlay(
                    previewLoader = previewLoader,
                    positionMs = scrubPosition,
                    isVisible = true
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            IconButton(
                onClick = { viewModel.nextContentScale() },
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Toggle content scale",
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            IconButton(
                onClick = { viewModel.toggleFullscreen() },
            ) {
                Icon(
                    imageVector = if (uiState.isFullscreen) {
                        Icons.Default.FullscreenExit
                    } else {
                        Icons.Default.Fullscreen
                    },
                    contentDescription = "Toggle fullscreen",
                    tint = Color.White
                )
            }
        }
        
        // Play/Pause button centered in the player
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {  },
                    modifier = Modifier.size(60.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp).onTouchHeldAnimated(
                            onTouchHeld = { player?.seekBack() }
                        )
                    )
                }
                IconButton(
                    onClick = {
                        player?.let {
                            if (it.isPlaying) {
                                it.pause()
                            } else {
                                it.play()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(
                    onClick = {  },
                    modifier = Modifier.size(60.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Fast-forward",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp).onTouchHeldAnimated(
                            onTouchHeld = { player?.seekForward() }
                        )
                    )
                }
            }
        }
        
        // Time bar and time labels at the bottom
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Seek bar
                if (duration > 0) {
                    var sliderPosition by remember { mutableFloatStateOf(0f) }
                    
                    LaunchedEffect(currentPosition, isScrubbing) {
                        if (!isScrubbing) {
                            sliderPosition = currentPosition.toFloat()
                        }
                    }
                    
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            sliderPosition = newValue
                            val position = newValue.toLong()
                            scrubPosition = position
                            
                            if (!isScrubbing) {
                                // First drag event - start scrubbing
                                isScrubbing = true
                                player?.playWhenReady = false
                                onScrubStart(position)
                                
                                // Cancel any previous preload job and start new one
                                preloadJob?.cancel()
                                preloadJob = coroutineScope.launch {
                                    previewLoader?.preloadPreviews(position, count = 3)
                                }
                            } else {
                                // Continue scrubbing
                                onScrubMove(position)
                            }
                        },
                        onValueChangeFinished = {
                            val finalPosition = sliderPosition.toLong()
                            player?.seekTo(finalPosition)
                            player?.playWhenReady = true
                            onScrubEnd(finalPosition)
                            isScrubbing = false
                            preloadJob?.cancel()
                            preloadJob = null
                        },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun Modifier.onTouchHeldAnimated(
    easing: Easing = FastOutSlowInEasing,
    pollDelay: Duration = 1.seconds,
    targetPollDelay: Duration = 1.milliseconds,
    animationDuration: Duration = 10.seconds,
    onTouchHeld: () -> Unit
) = composed {
    val scope = rememberCoroutineScope()
    pointerInput(onTouchHeld) {
        val animationSpec: FloatAnimationSpec = FloatTweenSpec(
            animationDuration.inWholeMilliseconds.toInt(),
            0,
            easing
        )
        awaitEachGesture {
            val initialDown = awaitFirstDown(requireUnconsumed = false)
            val initialTouchHeldJob = scope.launch {
                var currentPlayTime = 0.milliseconds
                var delay = pollDelay
                while (initialDown.pressed) {
                    onTouchHeld()
                    delay(delay.inWholeMilliseconds)
                    currentPlayTime += delay
                    delay = animationSpec.getValueFromNanos(
                        currentPlayTime.inWholeNanoseconds,
                        pollDelay.inWholeMilliseconds.toFloat(),
                        targetPollDelay.inWholeMilliseconds.toFloat(),
                        0F
                    ).toInt().milliseconds
                }
            }
            waitForUpOrCancellation()
            initialTouchHeldJob.cancel()
        }
    }
}
