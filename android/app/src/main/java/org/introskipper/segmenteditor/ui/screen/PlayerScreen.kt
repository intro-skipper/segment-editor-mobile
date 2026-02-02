package org.introskipper.segmenteditor.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.component.PlaybackSpeedDialog
import org.introskipper.segmenteditor.ui.component.SegmentSlider
import org.introskipper.segmenteditor.ui.component.SegmentTimeline
import org.introskipper.segmenteditor.ui.component.TrackSelectionSheet
import org.introskipper.segmenteditor.ui.component.VideoPlayerWithPreview
import org.introskipper.segmenteditor.ui.component.segment.SegmentEditorDialog
import org.introskipper.segmenteditor.ui.component.selectAudioTrack
import org.introskipper.segmenteditor.ui.component.selectSubtitleTrack
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    itemId: String,
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    
    // Preview loader
    val streamUrl = viewModel.getStreamUrl(useHls = true)
    val previewLoader = remember {
        viewModel.createPreviewLoader(itemId)
    }
    
    // Clean up preview loader on dispose
    DisposableEffect(previewLoader) {
        onDispose {
            previewLoader?.release()
        }
    }
    
    // Track selection state - keyed by itemId to reset when navigating
    var showAudioTracks by remember(itemId) { mutableStateOf(false) }
    var showSubtitleTracks by remember(itemId) { mutableStateOf(false) }
    
    // Segment editor state - keyed by itemId to reset when navigating
    var showSegmentEditor by remember(itemId) { mutableStateOf(false) }
    var editingSegment by remember(itemId) { mutableStateOf<Segment?>(null) }
    var activeSegmentIndex by remember(itemId) { mutableStateOf(0) }
    
    // Load media item when itemId changes
    LaunchedEffect(itemId) {
        viewModel.loadMediaItem(itemId)
    }
    
    // Update playback state periodically
    LaunchedEffect(player) {
        player?.let { exoPlayer ->
            while (true) {
                viewModel.updatePlaybackState(
                    isPlaying = exoPlayer.isPlaying,
                    currentPosition = exoPlayer.currentPosition,
                    bufferedPosition = exoPlayer.bufferedPosition
                )
                delay(500)
            }
        }
    }
    
    // Handle fullscreen mode
    DisposableEffect(uiState.isFullscreen) {
        val activity = context as? Activity
        if (uiState.isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
    
    // Apply playback speed
    LaunchedEffect(uiState.playbackSpeed) {
        player?.setPlaybackSpeed(uiState.playbackSpeed)
    }
    
    Scaffold(
        topBar = {
            if (!uiState.isFullscreen) {
                TopAppBar(
                    title = { Text(uiState.mediaItem?.name ?: stringResource(R.string.player_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            navController.navigate(Screen.Settings.route)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.home_settings)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.loadMediaItem(itemId) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            PlayerContent(
                uiState = uiState,
                viewModel = viewModel,
                player = player,
                streamUrl = streamUrl,
                previewLoader = previewLoader,
                activeSegmentIndex = activeSegmentIndex,
                onPlayerReady = { player = it },
                onAudioTracksClick = { showAudioTracks = true },
                onSubtitleTracksClick = { showSubtitleTracks = true },
                onCreateSegment = { 
                    editingSegment = null
                    showSegmentEditor = true 
                },
                onEditSegment = { segment ->
                    editingSegment = segment
                    showSegmentEditor = true
                },
                onSetActiveSegment = { index ->
                    activeSegmentIndex = index
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
    
    // Audio track selection sheet
    if (showAudioTracks) {
        TrackSelectionSheet(
            title = stringResource(R.string.player_audio_tracks),
            tracks = uiState.audioTracks,
            selectedTrackIndex = uiState.selectedAudioTrack,
            onTrackSelected = { trackIndex ->
                viewModel.selectAudioTrack(trackIndex)
                player?.let { 
                    selectAudioTrack(it, trackIndex)
                }
            },
            onDismiss = { showAudioTracks = false },
            allowDisable = false
        )
    }
    
    // Subtitle track selection sheet
    if (showSubtitleTracks) {
        TrackSelectionSheet(
            title = stringResource(R.string.player_subtitle_tracks),
            tracks = uiState.subtitleTracks,
            selectedTrackIndex = uiState.selectedSubtitleTrack,
            onTrackSelected = { trackIndex ->
                viewModel.selectSubtitleTrack(trackIndex)
                player?.let { 
                    selectSubtitleTrack(it, trackIndex)
                }
            },
            onDismiss = { showSubtitleTracks = false },
            allowDisable = true
        )
    }
    
    // Playback speed dialog
    if (uiState.showSpeedSelection) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { viewModel.showSpeedSelection(false) }
        )
    }
    
    // Segment editor dialog
    if (showSegmentEditor) {
        // uiState.duration is already in milliseconds, convert to seconds
        val durationSeconds = uiState.duration / 1000.0
        
        SegmentEditorDialog(
            itemId = itemId,
            duration = durationSeconds,
            existingSegments = uiState.segments,
            initialStartTime = null,
            initialEndTime = null,
            editSegment = editingSegment,
            currentPosition = uiState.currentPosition / 1000.0,
            onDismiss = { 
                showSegmentEditor = false
                editingSegment = null
            },
            onSaved = {
                viewModel.refreshSegments()
            }
        )
    }
}

@Composable
private fun PlayerContent(
    uiState: org.introskipper.segmenteditor.ui.state.PlayerUiState,
    viewModel: PlayerViewModel,
    player: ExoPlayer?,
    streamUrl: String?,
    previewLoader: PreviewLoader?,
    activeSegmentIndex: Int,
    onPlayerReady: (ExoPlayer) -> Unit,
    onAudioTracksClick: () -> Unit,
    onSubtitleTracksClick: () -> Unit,
    onCreateSegment: () -> Unit,
    onEditSegment: (Segment) -> Unit,
    onSetActiveSegment: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Video player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (streamUrl != null) {
                VideoPlayerWithPreview(
                    streamUrl = streamUrl,
                    useController = true,
                    previewLoader = previewLoader,
                    onPlayerReady = onPlayerReady,
                    onPlaybackStateChanged = { isPlaying, currentPos, bufferedPos ->
                        viewModel.updatePlaybackState(isPlaying, currentPos, bufferedPos)
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.player_error_load),
                        color = Color.White
                    )
                }
            }
            
            // Fullscreen toggle
            IconButton(
                onClick = { viewModel.toggleFullscreen() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isFullscreen) {
                        Icons.Default.FullscreenExit
                    } else {
                        Icons.Default.Fullscreen
                    },
                    contentDescription = stringResource(R.string.player_toggle_fullscreen),
                    tint = Color.White
                )
            }
        }
        
        // Segment timeline
        if (uiState.segments.isNotEmpty()) {
            SegmentTimeline(
                segments = uiState.segments,
                duration = uiState.duration,
                currentPosition = uiState.currentPosition,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        if (!uiState.isFullscreen) {
            // Controls and content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Control buttons
                item {
                    PlayerControlsRow(
                        playbackSpeed = uiState.playbackSpeed,
                        onSpeedClick = { viewModel.showSpeedSelection(true) },
                        onAudioTracksClick = onAudioTracksClick,
                        onSubtitleTracksClick = onSubtitleTracksClick
                    )
                }
                
                // Segments list or empty message
                if (uiState.segments.isNotEmpty()) {
                    // uiState.duration is already in milliseconds, convert to seconds
                    val runtimeSeconds = uiState.duration / 1000.0
                    
                    items(uiState.segments.size) { index ->
                        val segment = uiState.segments[index]
                        
                        SegmentSlider(
                            segment = segment,
                            index = index,
                            isActive = index == activeSegmentIndex,
                            runtimeSeconds = runtimeSeconds,
                            onUpdate = { updatedSegment ->
                                // For now, trigger the edit dialog for persistence
                                onEditSegment(updatedSegment)
                            },
                            onDelete = {
                                // Trigger edit dialog with delete option
                                onEditSegment(segment)
                            },
                            onSeekTo = { timeSeconds ->
                                player?.seekTo((timeSeconds * 1000).toLong())
                            },
                            onSetActive = {
                                onSetActiveSegment(index)
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "No segments found for this media item. Create one below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                
                // Create segment button - now appears after segments
                item {
                    Button(
                        onClick = onCreateSegment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.player_create_segment))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsRow(
    playbackSpeed: Float,
    onSpeedClick: () -> Unit,
    onAudioTracksClick: () -> Unit,
    onSubtitleTracksClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onSpeedClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.player_playback_speed, playbackSpeed))
        }
        
        OutlinedButton(
            onClick = onAudioTracksClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.player_audio))
        }
        
        OutlinedButton(
            onClick = onSubtitleTracksClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.player_subtitles))
        }
    }
}

private fun selectAudioTrack(player: ExoPlayer, trackIndex: Int?) {
    player.selectAudioTrack(trackIndex)
}

private fun selectSubtitleTrack(player: ExoPlayer, trackIndex: Int?) {
    player.selectSubtitleTrack(trackIndex)
}
