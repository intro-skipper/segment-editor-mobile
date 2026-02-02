package org.introskipper.segmenteditor.ui.component

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import org.introskipper.segmenteditor.ui.preview.PreviewLoader

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    previewLoader: PreviewLoader? = null,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onPlaybackStateChanged: (isPlaying: Boolean, currentPosition: Long, bufferedPosition: Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        val trackSelector = DefaultTrackSelector(context)
        ExoPlayer.Builder(context)
            .setRenderersFactory(NextRenderersFactory(context))
            .setTrackSelector(trackSelector)
            .build().apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = true
            }
    }
    
    DisposableEffect(exoPlayer, previewLoader) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChanged(
                    isPlaying,
                    exoPlayer.currentPosition,
                    exoPlayer.bufferedPosition
                )
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                onPlaybackStateChanged(
                    exoPlayer.isPlaying,
                    exoPlayer.currentPosition,
                    exoPlayer.bufferedPosition
                )
            }
        }
        
        exoPlayer.addListener(listener)
        onPlayerReady(exoPlayer)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            // Note: previewLoader is released by PlayerScreen, not here
        }
    }
    
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                this.useController = useController
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                
                // Note: This basic VideoPlayer component accepts previewLoader for API compatibility,
                // but does not display preview overlays. For full preview support with overlay UI,
                // use VideoPlayerWithPreview component instead.
                // The code below detects the TimeBar for potential custom implementations.
                if (previewLoader != null) {
                    // Use ViewTreeObserver to ensure view is fully laid out before finding TimeBar
                    val layoutListener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            // Remove listener to avoid multiple calls and prevent memory leaks
                            if (viewTreeObserver.isAlive) {
                                viewTreeObserver.removeOnGlobalLayoutListener(this)
                            }
                            
                            // Check if view is still attached
                            if (!isAttachedToWindow) {
                                return
                            }
                            
                            // Find the TimeBar in the PlayerView
                            val timeBarView = this@apply.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress)
                            if (timeBarView is androidx.media3.ui.TimeBar) {
                                android.util.Log.d("VideoPlayer", "TimeBar found, preview loader available for custom integration")
                                // Custom preview implementations can hook into the TimeBar here
                                // For example: add scrub listener and integrate with previewLoader
                                // See VideoPlayerWithPreview.kt for a complete implementation
                            } else {
                                android.util.Log.d("VideoPlayer", "TimeBar not found, but previewLoader is available for custom use")
                            }
                        }
                    }
                    viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                } else {
                    android.util.Log.d("VideoPlayer", "No previewLoader provided, preview features disabled")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
fun ExoPlayer.selectAudioTrack(trackIndex: Int?) {
    val trackSelector = this.trackSelector as? DefaultTrackSelector ?: return
    
    if (trackIndex == null) {
        // Disable audio track selection (use default)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
    } else {
        // Select specific audio track by accumulating indices across all groups
        val currentTracks = this.currentTracks
        val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        
        var accumulatedIndex = 0
        var foundTrack = false
        
        for (group in audioGroups) {
            for (trackIndexInGroup in 0 until group.length) {
                if (accumulatedIndex == trackIndex) {
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup,
                                trackIndexInGroup
                            )
                        )
                        .build()
                    foundTrack = true
                    break
                }
                accumulatedIndex++
            }
            if (foundTrack) break
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
fun ExoPlayer.selectSubtitleTrack(trackIndex: Int?) {
    val trackSelector = this.trackSelector as? DefaultTrackSelector ?: return
    
    if (trackIndex == null) {
        // Disable subtitles
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    } else {
        // Select specific subtitle track by accumulating indices across all groups
        val currentTracks = this.currentTracks
        val textGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        
        var accumulatedIndex = 0
        var foundTrack = false
        
        for (group in textGroups) {
            for (trackIndexInGroup in 0 until group.length) {
                if (accumulatedIndex == trackIndex) {
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup,
                                trackIndexInGroup
                            )
                        )
                        .build()
                    foundTrack = true
                    break
                }
                accumulatedIndex++
            }
            if (foundTrack) break
        }
    }
}
