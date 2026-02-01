package org.introskipper.segmenteditor.ui.component

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.github.rubensousa.previewseekbar.PreviewView
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.player.preview.PreviewLoader
import org.introskipper.segmenteditor.R

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
    val coroutineScope = rememberCoroutineScope()
    
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
            previewLoader?.release()
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
                
                // Set up preview functionality if previewLoader is provided
                if (previewLoader != null && useController) {
                    try {
                        setupPreviewTimeBar(this, previewLoader, coroutineScope)
                    } catch (e: Exception) {
                        // If preview setup fails, continue without previews
                        android.util.Log.w("VideoPlayer", "Failed to setup preview: ${e.message}")
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@UnstableApi
private fun setupPreviewTimeBar(
    playerView: PlayerView,
    previewLoader: PreviewLoader,
    scope: kotlinx.coroutines.CoroutineScope
) {
    try {
        // Try to find the PreviewTimeBar in the controller
        val controllerView = playerView.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_controller)
        val timeBar = controllerView?.findViewById<PreviewTimeBar>(R.id.exo_progress)
        
        if (timeBar != null) {
            // Set up preview frame layout
            val previewFrameLayout = playerView.findViewById<com.github.rubensousa.previewseekbar.PreviewView>(R.id.previewFrame)
            val previewImageView = previewFrameLayout?.findViewById<ImageView>(R.id.previewImageView)
            
            if (previewFrameLayout != null && previewImageView != null) {
                // Set up preview loader callback
                timeBar.addPreviewListener(object : PreviewTimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: PreviewTimeBar, position: Long) {
                        scope.launch {
                            val bitmap = previewLoader.loadPreview(position)
                            if (bitmap != null) {
                                previewImageView.setImageBitmap(bitmap)
                                previewFrameLayout.show()
                            }
                        }
                    }
                    
                    override fun onScrubMove(timeBar: PreviewTimeBar, position: Long) {
                        scope.launch {
                            val bitmap = previewLoader.loadPreview(position)
                            if (bitmap != null) {
                                previewImageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                    
                    override fun onScrubStop(timeBar: PreviewTimeBar, position: Long, canceled: Boolean) {
                        previewFrameLayout.hide()
                    }
                })
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoPlayer", "Error setting up PreviewTimeBar", e)
    }
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
