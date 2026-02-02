package org.introskipper.segmenteditor.ui.component

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import org.introskipper.segmenteditor.player.preview.PreviewLoader

/**
 * Enhanced VideoPlayer with scrub preview support
 * Shows thumbnail previews when dragging the video timeline
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerWithPreview(
    streamUrl: String,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    previewLoader: PreviewLoader? = null,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onPlaybackStateChanged: (isPlaying: Boolean, currentPosition: Long, bufferedPosition: Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    
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
    
    Box(modifier = modifier.fillMaxSize()) {
        // Video player
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
                    
                    
                    // Hook into the TimeBar to detect scrubbing
                    // Note: This depends on media3 library's internal view structure
                    // The exo_progress ID may not exist in custom layouts or future versions
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
                            
                            // Now the view hierarchy is fully laid out
                            val timeBarView = this@apply.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress)
                            if (timeBarView is TimeBar) {
                                android.util.Log.d("VideoPlayerWithPreview", "TimeBar found, attaching scrub listener")
                                timeBarView.addListener(object : TimeBar.OnScrubListener {
                                    override fun onScrubStart(timeBar: TimeBar, position: Long) {
                                        android.util.Log.d("VideoPlayerWithPreview", "Scrub started at position: $position")
                                        exoPlayer.playWhenReady = false
                                        isScrubbing = true
                                        scrubPosition = position
                                    }
                                    
                                    override fun onScrubMove(timeBar: TimeBar, position: Long) {
                                        android.util.Log.d("VideoPlayerWithPreview", "Scrub moved to position: $position")
                                        exoPlayer.playWhenReady = false
                                        scrubPosition = position
                                    }
                                    
                                    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                                        android.util.Log.d("VideoPlayerWithPreview", "Scrub stopped at position: $position")
                                        isScrubbing = false
                                        scrubPosition = position
                                        exoPlayer.playWhenReady = true
                                    }
                                })
                            } else {
                                // TimeBar not found - preview scrubbing won't work but video playback will
                                android.util.Log.w("VideoPlayerWithPreview", "TimeBar (exo_progress) not found in PlayerView. Found: ${timeBarView?.javaClass?.name}")
                            }
                        }
                    }
                    viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Preview overlay (shown when scrubbing)
        if (isScrubbing) {
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .zIndex(10f)
            ) {
                ScrubPreviewOverlay(
                    previewLoader = previewLoader,
                    positionMs = scrubPosition!!,
                    isVisible = true
                )
            }
        }
    }
}
