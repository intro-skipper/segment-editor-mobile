package org.introskipper.segmenteditor.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.TimeUtils
import org.introskipper.segmenteditor.ui.component.*
import org.introskipper.segmenteditor.ui.component.segment.SegmentEditorDialog
import org.introskipper.segmenteditor.ui.viewmodel.PlayerViewModel
import org.introskipper.segmenteditor.ui.component.SegmentSlider

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
    
    // Track selection state
    var showAudioTracks by remember { mutableStateOf(false) }
    var showSubtitleTracks by remember { mutableStateOf(false) }
    
    // Segment editor state
    var showSegmentEditor by remember { mutableStateOf(false) }
    var editingSegment by remember { mutableStateOf<Segment?>(null) }
    var activeSegmentIndex by remember { mutableStateOf(0) }
    
    // Load media item on first composition
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                    title = { Text(uiState.mediaItem?.name ?: "Player") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                        Text("Retry")
                    }
                }
            }
        } else {
            PlayerContent(
                uiState = uiState,
                viewModel = viewModel,
                player = player,
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
            title = "Audio Tracks",
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
            title = "Subtitle Tracks",
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
        val durationSeconds = TimeUtils.ticksToMilliseconds(uiState.duration) / 1000.0
        
        SegmentEditorDialog(
            itemId = itemId,
            duration = durationSeconds,
            existingSegments = uiState.segments,
            initialStartTime = uiState.capturedStartTime?.let { it / 1000.0 },
            initialEndTime = uiState.capturedEndTime?.let { it / 1000.0 },
            editSegment = editingSegment,
            currentPosition = uiState.currentPosition / 1000.0,
            onDismiss = { 
                showSegmentEditor = false
                editingSegment = null
            },
            onSaved = {
                viewModel.refreshSegments()
                viewModel.clearCapturedTimes()
            }
        )
    }
}

@Composable
private fun PlayerContent(
    uiState: org.introskipper.segmenteditor.ui.state.PlayerUiState,
    viewModel: PlayerViewModel,
    player: ExoPlayer?,
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
            val streamUrl = viewModel.getStreamUrl(useHls = true)
            if (streamUrl != null) {
                VideoPlayer(
                    streamUrl = streamUrl,
                    useController = true,
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
                        text = "Unable to load video",
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
                    contentDescription = "Toggle fullscreen",
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
                
                // Timestamp capture
                item {
                    TimestampCaptureBar(
                        capturedStartTime = uiState.capturedStartTime,
                        capturedEndTime = uiState.capturedEndTime,
                        onCaptureStart = { viewModel.captureStartTime() },
                        onCaptureEnd = { viewModel.captureEndTime() },
                        onClear = { viewModel.clearCapturedTimes() }
                    )
                }
                
                // Segments list - now appears before create button
                if (uiState.segments.isNotEmpty()) {
                    item {
                        Text(
                            text = "Segments (${uiState.segments.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    val runtimeSeconds = TimeUtils.ticksToMilliseconds(uiState.duration) / 1000.0
                    
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
                }
                
                // Create segment button - now appears after segments
                item {
                    Button(
                        onClick = onCreateSegment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Segment")
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
            Text("${playbackSpeed}x")
        }
        
        OutlinedButton(
            onClick = onAudioTracksClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Audio")
        }
        
        OutlinedButton(
            onClick = onSubtitleTracksClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Subs")
        }
    }
}

private fun selectAudioTrack(player: ExoPlayer, trackIndex: Int?) {
    player.selectAudioTrack(trackIndex)
}

private fun selectSubtitleTrack(player: ExoPlayer, trackIndex: Int?) {
    player.selectSubtitleTrack(trackIndex)
}
