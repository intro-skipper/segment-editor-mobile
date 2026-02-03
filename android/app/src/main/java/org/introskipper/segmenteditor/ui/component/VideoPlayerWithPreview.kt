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
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.preview.ScrubPreviewOverlay

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
    onPlaybackStateChanged: (isPlaying: Boolean, currentPosition: Long, bufferedPosition: Long) -> Unit = { _, _, _ -> },
    onTracksChanged: (Tracks) -> Unit = {}
) {
    val context = LocalContext.current
    var scrubPosition by remember { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    
    // Create ExoPlayer instance once - don't recreate on track changes
    val trackSelector = remember { DefaultTrackSelector(context) }
    val exoPlayer = remember {
        android.util.Log.d("VideoPlayerWithPreview", "Creating ExoPlayer instance")
        
        ExoPlayer.Builder(context)
            .setRenderersFactory(NextRenderersFactory(context))
            .setTrackSelector(trackSelector.apply {
                setParameters(buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowMultipleAdaptiveSelections(true)
                    .setRendererDisabled(TRACK_TYPE_VIDEO, false)
                    .setRendererDisabled(TRACK_TYPE_AUDIO, false)
                    .setRendererDisabled(TRACK_TYPE_TEXT, false)
                    .setExceedRendererCapabilitiesIfNecessary(true))
            })
            .build()
    }
    
    // Update media item when streamUrl changes (without recreating player)
    LaunchedEffect(streamUrl) {
        android.util.Log.d("VideoPlayerWithPreview", "Stream URL changed: $streamUrl")
        val currentPosition = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.playWhenReady
        
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        
        // Restore position and play state if this is a track switch (position > threshold)
        if (currentPosition > MIN_POSITION_TO_RESTORE_MS) {
            exoPlayer.seekTo(currentPosition)
            exoPlayer.playWhenReady = wasPlaying  // Preserve play state on track change
        } else {
            // On initial load, start playback automatically
            exoPlayer.playWhenReady = true
        }
    }
    
    // Player event listeners for playback state and track changes
    DisposableEffect(exoPlayer, previewLoader) {
        val tracksListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                android.util.Log.d("VideoPlayerWithPreview", "onTracksChanged: ${tracks.groups.size} track groups")
                
                // Extract available audio tracks from ExoPlayer
                val availableAudioTracks = mutableListOf<Pair<Int, String>>()
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        android.util.Log.d("VideoPlayerWithPreview", "  Audio Group $groupIndex: ${group.length} tracks")
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val language = format.language ?: "Unknown"
                            val label = format.label ?: "Audio ${trackIndex + 1}"
                            val info = "$label ($language)"
                            availableAudioTracks.add(Pair(trackIndex, info))
                            android.util.Log.d("VideoPlayerWithPreview", "    Track $trackIndex: $info, codec=${format.sampleMimeType}")
                        }
                    }
                }
                
                // Extract available subtitle tracks from ExoPlayer
                val availableSubtitleTracks = mutableListOf<Pair<Int, String>>()
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        android.util.Log.d("VideoPlayerWithPreview", "  Subtitle Group $groupIndex: ${group.length} tracks")
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val language = format.language ?: "Unknown"
                            val label = format.label ?: "Subtitle ${trackIndex + 1}"
                            val info = "$label ($language)"
                            availableSubtitleTracks.add(Pair(trackIndex, info))
                            android.util.Log.d("VideoPlayerWithPreview", "    Track $trackIndex: $info, codec=${format.sampleMimeType}")
                        }
                    }
                }
                
                // Log video tracks for completeness
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        android.util.Log.d("VideoPlayerWithPreview", "  Video Group $groupIndex: ${group.length} tracks")
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            android.util.Log.d("VideoPlayerWithPreview", "    Track $trackIndex: ${format.width}x${format.height}, codec=${format.sampleMimeType}")
                        }
                    }
                }
                
                android.util.Log.d("VideoPlayerWithPreview", "Available tracks - Audio: ${availableAudioTracks.size}, Subtitles: ${availableSubtitleTracks.size}")
                
                // Notify callback with tracks
                onTracksChanged(tracks)
            }
        }
        
        val playbackListener = object : Player.Listener {
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
        
        exoPlayer.addListener(tracksListener)
        exoPlayer.addListener(playbackListener)
        onPlayerReady(exoPlayer)
        
        onDispose {
            exoPlayer.removeListener(tracksListener)
            exoPlayer.removeListener(playbackListener)
            exoPlayer.release()
            // Note: previewLoader is released by PlayerScreen, not here
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Video player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
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
                                        // Get player from PlayerView instead of capturing from closure
                                        val currentPlayer = this@apply.player
                                        if (currentPlayer != null) {
                                            currentPlayer.playWhenReady = false
                                        } else {
                                            android.util.Log.w("VideoPlayerWithPreview", "Player is null during scrub start")
                                        }
                                        isScrubbing = true
                                        scrubPosition = position
                                        
                                        // Preload adjacent previews for smoother scrubbing
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            previewLoader?.preloadPreviews(position, count = 3)
                                        }
                                    }
                                    
                                    override fun onScrubMove(timeBar: TimeBar, position: Long) {
                                        android.util.Log.d("VideoPlayerWithPreview", "Scrub moved to position: $position")
                                        // Get player from PlayerView instead of capturing from closure
                                        val currentPlayer = this@apply.player
                                        if (currentPlayer != null) {
                                            currentPlayer.playWhenReady = false
                                        } else {
                                            android.util.Log.w("VideoPlayerWithPreview", "Player is null during scrub move")
                                        }
                                        scrubPosition = position
                                    }
                                    
                                    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                                        android.util.Log.d("VideoPlayerWithPreview", "Scrub stopped at position: $position")
                                        isScrubbing = false
                                        scrubPosition = position
                                        // Get player from PlayerView instead of capturing from closure
                                        val currentPlayer = this@apply.player
                                        if (currentPlayer != null) {
                                            currentPlayer.playWhenReady = true
                                        } else {
                                            android.util.Log.w("VideoPlayerWithPreview", "Player is null during scrub stop")
                                        }
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
