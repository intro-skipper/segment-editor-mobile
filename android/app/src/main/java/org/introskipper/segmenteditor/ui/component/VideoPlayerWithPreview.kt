package org.introskipper.segmenteditor.ui.component

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.compose.ContentFrame
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.ui.preview.PreviewLoader

// Minimum position to restore when reloading stream (avoids restoring during initial load)
private const val MIN_POSITION_TO_RESTORE_MS = 1000L

/**
 * Enhanced VideoPlayer with scrub preview support using Media3 Compose
 * Shows thumbnail previews when dragging the video timeline
 * Uses ContentFrame from media3-ui-compose instead of AndroidView
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerWithPreview(
    streamUrl: String,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    previewLoader: PreviewLoader? = null,
    useDirectPlay: Boolean = false,
    selectedAudioTrack: Int? = null,
    selectedSubtitleTrack: Int? = null,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onPlaybackStateChanged: (isPlaying: Boolean, currentPosition: Long, bufferedPosition: Long) -> Unit = { _, _, _ -> },
    onTracksChanged: (Tracks) -> Unit = {},
    onPlaybackError: (error: androidx.media3.common.PlaybackException) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Create ExoPlayer instance once - don't recreate on track changes
    val trackSelector = remember { DefaultTrackSelector(context) }

    // Create simple data source factory without ResolvingDataSource
    // Track parameters are now built into the URL upfront in PlayerViewModel.getStreamUrl()
    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
    }

    val mediaFactory = remember {
        DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }
    
    val exoPlayer = remember {
        Log.d("VideoPlayerWithPreview", "Creating ExoPlayer instance")
        
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
            .setMediaSourceFactory(mediaFactory)
            .build()
    }
    
    // Helper function to load media and restore playback state
    val loadMedia = { logMessage: String ->
        Log.d("VideoPlayerWithPreview", logMessage)
        val currentPosition = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.playWhenReady

        exoPlayer.setMediaSource(mediaFactory.createMediaSource(MediaItem.fromUri(streamUrl)))
        exoPlayer.prepare()
        
        // Restore position and play state if this is a reload (position > threshold)
        if (currentPosition > MIN_POSITION_TO_RESTORE_MS) {
            exoPlayer.seekTo(currentPosition)
            exoPlayer.playWhenReady = wasPlaying  // Preserve play state on reload
            Log.d("VideoPlayerWithPreview", "Restored position: $currentPosition ms, playing: $wasPlaying")
        } else {
            // On initial load, start playback automatically
            exoPlayer.playWhenReady = true
        }
    }
    
    // Load media when streamUrl changes (initial load or media item change)
    LaunchedEffect(streamUrl) {
        loadMedia("Loading media URL: $streamUrl")
    }
    
    // Handle track selection changes for direct play mode
    // In HLS mode, track changes are handled via streamUrl changes that trigger reload above
    // Keep useDirectPlay in the key to ensure proper cleanup when switching modes
    LaunchedEffect(selectedAudioTrack, selectedSubtitleTrack, useDirectPlay) {
        // Skip in HLS mode - track changes are handled via streamUrl changes
        if (!useDirectPlay) {
            Log.d("VideoPlayerWithPreview", "Skipping track change in HLS mode - handled via streamUrl")
            return@LaunchedEffect
        }
        
        // Skip on initial composition (when exoPlayer is not prepared yet)
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            Log.d("VideoPlayerWithPreview", "Skipping track change - player not ready")
            return@LaunchedEffect
        }
        // Direct play mode: Use ExoPlayer's native track selection API
        // The index here is the relativeIndex (0-based position within tracks of same type)
        Log.d("VideoPlayerWithPreview", "Direct play mode - applying track selection (Audio relativeIndex: $selectedAudioTrack, Subtitle relativeIndex: $selectedSubtitleTrack)")
        
        val currentTracks = exoPlayer.currentTracks
        val parametersBuilder = trackSelector.parameters.buildUpon()
        
        // Handle audio track selection using relativeIndex
        if (selectedAudioTrack != null) {
            var foundAudio = false
            var currentRelativeIndex = 0
            
            // Iterate through all track groups to find audio track at relativeIndex
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == TRACK_TYPE_AUDIO) {
                    for (trackIndex in 0 until trackGroup.length) {
                        if (currentRelativeIndex == selectedAudioTrack) {
                            // Found the track at the relative index
                            parametersBuilder.setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(
                                    trackGroup.mediaTrackGroup,
                                    listOf(trackIndex)
                                )
                            )
                            Log.d("VideoPlayerWithPreview", "Selected audio track: relativeIndex=$selectedAudioTrack, trackIndex=$trackIndex in group")
                            foundAudio = true
                            break
                        }
                        currentRelativeIndex++
                    }
                    if (foundAudio) break
                }
            }
            
            if (!foundAudio) {
                Log.w("VideoPlayerWithPreview", "Audio track with relativeIndex $selectedAudioTrack not found")
            }
        }
        
        // Handle subtitle track selection using relativeIndex
        if (selectedSubtitleTrack != null) {
            var foundSubtitle = false
            var currentRelativeIndex = 0
            
            // Iterate through all track groups to find subtitle track at relativeIndex
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == TRACK_TYPE_TEXT) {
                    for (trackIndex in 0 until trackGroup.length) {
                        if (currentRelativeIndex == selectedSubtitleTrack) {
                            // Found the track at the relative index
                            parametersBuilder.setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(
                                    trackGroup.mediaTrackGroup,
                                    listOf(trackIndex)
                                )
                            )
                            Log.d("VideoPlayerWithPreview", "Selected subtitle track: relativeIndex=$selectedSubtitleTrack, trackIndex=$trackIndex in group")
                            foundSubtitle = true
                            break
                        }
                        currentRelativeIndex++
                    }
                    if (foundSubtitle) break
                }
            }
            
            if (!foundSubtitle) {
                Log.w("VideoPlayerWithPreview", "Subtitle track with relativeIndex $selectedSubtitleTrack not found")
            }
        } else {
            // Disable subtitles if null
            parametersBuilder.setTrackTypeDisabled(TRACK_TYPE_TEXT, true)
            Log.d("VideoPlayerWithPreview", "Disabled subtitles")
        }
        
        // Apply the track selection parameters
        trackSelector.setParameters(parametersBuilder)
    }
    
    // Player event listeners for playback state and track changes
    DisposableEffect(exoPlayer, previewLoader) {
        val tracksListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                Log.d("VideoPlayerWithPreview", "onTracksChanged: ${tracks.groups.size} track groups")
                
                // Extract available audio tracks from ExoPlayer
                val availableAudioTracks = mutableListOf<Pair<Int, String>>()
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == TRACK_TYPE_AUDIO) {
                        Log.d("VideoPlayerWithPreview", "  Audio Group $groupIndex: ${group.length} tracks")
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val language = format.language ?: "Unknown"
                            val label = format.label ?: "Audio ${trackIndex + 1}"
                            val info = "$label ($language)"
                            availableAudioTracks.add(Pair(trackIndex, info))
                            Log.d("VideoPlayerWithPreview", "    Track $trackIndex: $info, codec=${format.sampleMimeType}")
                        }
                    }
                }
                
                // Extract available subtitle tracks from ExoPlayer
                val availableSubtitleTracks = mutableListOf<Pair<Int, String>>()
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == TRACK_TYPE_TEXT) {
                        Log.d("VideoPlayerWithPreview", "  Subtitle Group $groupIndex: ${group.length} tracks")
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val language = format.language ?: "Unknown"
                            val label = format.label ?: "Subtitle ${trackIndex + 1}"
                            val info = "$label ($language)"
                            availableSubtitleTracks.add(Pair(trackIndex, info))
                            Log.d("VideoPlayerWithPreview", "    Track $trackIndex: $info, codec=${format.sampleMimeType}")
                        }
                    }
                }
                
                // Log video tracks for completeness
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == TRACK_TYPE_VIDEO) {
                        Log.d("VideoPlayerWithPreview", "  Video Group $groupIndex: ${group.length} tracks")
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            Log.d("VideoPlayerWithPreview", "    Track $trackIndex: ${format.width}x${format.height}, codec=${format.sampleMimeType}")
                        }
                    }
                }
                
                Log.d("VideoPlayerWithPreview", "Available tracks - Audio: ${availableAudioTracks.size}, Subtitles: ${availableSubtitleTracks.size}")
                
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
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("VideoPlayerWithPreview", "Player error occurred: errorCode=${error.errorCode}", error)
                
                // Notify the error handler if in direct play mode and error is codec capability related
                if (useDirectPlay) {
                    // IO - 2000
                    // PARSING - 3000
                    // DECODER - 4000
                    // AUDIO - 5000
                    // DRM - 6000
                    val isCodecError = error.errorCode in 4000..5000
                    
                    if (isCodecError) {
                        Log.w("VideoPlayerWithPreview", "Codec error in direct play mode, notifying error handler")
                        onPlaybackError(error)
                    }
                }
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
        // Video player using Media3 Compose ContentFrame
        ContentFrame(
            player = exoPlayer,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        
        // Media controls overlay with scrubbing preview support
        if (useController) {
            MediaControls(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                previewLoader = previewLoader
            )
        }

        SideEffect {
            CoroutineScope(Dispatchers.IO).launch {
                // Load initial preview and preload adjacent ones
                previewLoader?.loadPreview(0)
                previewLoader?.preloadPreviews(0, count = 3)
            }
        }
    }
}
