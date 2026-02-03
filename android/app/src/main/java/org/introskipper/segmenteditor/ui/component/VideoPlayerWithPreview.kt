package org.introskipper.segmenteditor.ui.component

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.preview.ScrubPreviewOverlay
import java.util.Locale

// Minimum position to restore when reloading stream (avoids restoring during initial load)
private const val MIN_POSITION_TO_RESTORE_MS = 1000L

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
    var scrubPosition by remember { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    
    // Track the playback position across stream URL changes
    var lastKnownPosition by remember { mutableStateOf(0L) }
    var lastKnownPlayWhenReady by remember { mutableStateOf(true) }
    
    // Create a new ExoPlayer when streamUrl changes (e.g., when user selects different audio/subtitle track)
    // The old player will be released by DisposableEffect cleanup
    val exoPlayer = remember(streamUrl) {
        // Get the current position from any existing player before creating new one
        val positionToRestore = lastKnownPosition
        val playWhenReadyToRestore = lastKnownPlayWhenReady
        
        android.util.Log.d("VideoPlayerWithPreview", "Creating new player with URL: $streamUrl, restoring position: $positionToRestore")
        
        ExoPlayer.Builder(context)
            .setRenderersFactory(NextRenderersFactory(context))
            .setTrackSelector(DefaultTrackSelector(context).apply {
                setParameters(buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setSelectUndeterminedTextLanguage(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowMultipleAdaptiveSelections(true)
                    .setPreferredTextLanguage(Locale.getDefault().language)
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .setRendererDisabled(TRACK_TYPE_VIDEO, false)
                    .setRendererDisabled(TRACK_TYPE_AUDIO, false)
                    .setRendererDisabled(TRACK_TYPE_TEXT, false)
                    .setMaxVideoSize(1, 1)
                    .setPreferredAudioLanguage(Locale.getDefault().language)
                    .setExceedRendererCapabilitiesIfNecessary(true))
            })
            .build().apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                
                // Seek to saved position before preparing if we're reloading
                // MIN_POSITION_TO_RESTORE_MS threshold avoids restoring during initial load
                if (positionToRestore > MIN_POSITION_TO_RESTORE_MS) {
                    seekTo(positionToRestore)
                }
                
                prepare()
                playWhenReady = playWhenReadyToRestore
            }
    }
    
    // Update lastKnown values using player listener callbacks instead of polling
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                lastKnownPosition = newPosition.positionMs
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                lastKnownPlayWhenReady = playWhenReady
            }
        }
        
        exoPlayer.addListener(listener)
        
        onDispose {
            // Save position one last time before player is disposed
            lastKnownPosition = exoPlayer.currentPosition
            lastKnownPlayWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(listener)
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

            override fun onTracksChanged(tracks: Tracks) {
                // Track selection is now handled by regenerating the stream URL
                // No need to process track changes here
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
                                        
                                        // Preload adjacent previews for smoother scrubbing
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            previewLoader?.preloadPreviews(position, count = 3)
                                        }
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
            update = { playerView ->
                // Update the player when exoPlayer changes (e.g., when stream URL changes for track selection)
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        SideEffect {
            CoroutineScope(Dispatchers.IO).launch {
                // Load initial preview and preload adjacent ones
                previewLoader?.loadPreview(0)
                previewLoader?.preloadPreviews(0, count = 3)
            }
        }
        
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
                    positionMs = scrubPosition,
                    isVisible = true
                )
            }
        }
    }
}
