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
import org.introskipper.segmenteditor.player.preview.PreviewLoader

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
                
                // Custom preview implementation for scrubbing
                // If a previewLoader is provided, we can hook into the TimeBar for preview display
                if (previewLoader != null) {
                    // Use ViewTreeObserver to ensure view is fully laid out before finding TimeBar
                    viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            // Remove listener to avoid multiple calls
                            viewTreeObserver.removeOnGlobalLayoutListener(this)
                            
                            // Find the TimeBar in the PlayerView
                            val timeBarView = this@apply.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress)
                            if (timeBarView is androidx.media3.ui.TimeBar) {
                                android.util.Log.d("VideoPlayer", "TimeBar found, preview loader available for integration")
                                // Note: This basic VideoPlayer doesn't display preview overlay
                                // For full preview support, use VideoPlayerWithPreview component instead
                                // The previewLoader is available here for custom implementations
                            } else {
                                android.util.Log.d("VideoPlayer", "TimeBar not found, but previewLoader is available")
                            }
                        }
                    })
                } else {
                    android.util.Log.d("VideoPlayer", "No previewLoader provided")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
fun ExoPlayer.selectAudioTrack(trackIndex: Int?) {
    if (trackIndex == null) return
    
    val trackSelector = this.trackSelector as? DefaultTrackSelector ?: return
    
    trackSelector.parameters = trackSelector.buildUponParameters()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .build()
}

@androidx.annotation.OptIn(UnstableApi::class)
fun ExoPlayer.selectSubtitleTrack(trackIndex: Int?) {
    val trackSelector = this.trackSelector as? DefaultTrackSelector ?: return
    
    trackSelector.parameters = trackSelector.buildUponParameters()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, trackIndex == null)
        .build()
}
