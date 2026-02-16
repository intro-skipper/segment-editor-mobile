package org.introskipper.segmenteditor.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.state.PlayerEvent
import org.introskipper.segmenteditor.ui.viewmodel.PlayerViewModel
import kotlin.time.DurationUnit
import kotlin.time.toDuration


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    itemId: String,
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    
    // Determine if we should use direct play (no HLS transcoding)
    // Allow fallback to HLS if direct play fails (with confirmation)
    var useDirectPlay by remember(itemId) { mutableStateOf(viewModel.shouldUseDirectPlay()) }
    var showDirectPlayFailedDialog by remember(itemId) { mutableStateOf(false) }
    var hasShownErrorDialog by remember(itemId) { mutableStateOf(false) }
    
    // Stream URL with track parameters built in (changes when tracks or mode changes)
    val streamUrl = remember(uiState.mediaItem, useDirectPlay, uiState.selectedAudioTrack, uiState.selectedSubtitleTrack) {
        viewModel.getStreamUrl(useHls = !useDirectPlay)
    }
    
    // Preview loader
    val previewLoader = remember {
        viewModel.createPreviewLoader(itemId)
    }
    
    // Handle events from ViewModel
    LaunchedEffect(events) {
        when (val event = events) {
            is PlayerEvent.NavigateToPlayer -> {
                viewModel.clearEvent()
                navController.navigate(Screen.Player.createRoute(event.itemId)) {
                    // Pop current player from backstack to avoid loops
                    popUpTo(Screen.Player.route) { inclusive = true }
                }
            }
            is PlayerEvent.PlaybackEnded -> {
                viewModel.clearEvent()
                navigateBack(navController, uiState.mediaItem)
            }
            else -> {}
        }
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
    var activeSegmentIndex by remember(itemId) { mutableIntStateOf(0) }
    
    // Local editing state for segments (unsaved changes)
    var editingSegments by remember(itemId) { mutableStateOf<List<Segment>>(emptyList()) }
    var segmentHasChanges by remember(itemId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    // Sync editing segments from server when data changes
    LaunchedEffect(uiState.segments) {
        if (editingSegments.isEmpty() || uiState.segments != editingSegments) {
            editingSegments = uiState.segments
            segmentHasChanges = emptyMap()
        }
    }
    
    // Delete confirmation state
    var showDeleteConfirmation by remember(itemId) { mutableStateOf(false) }
    var segmentToDelete by remember(itemId) { mutableStateOf<Segment?>(null) }
    
    // FAB segment type menu state
    var showFabMenu by remember(itemId) { mutableStateOf(false) }
    
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
        activity?.window?.let { window ->
            val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
            if (uiState.isFullscreen) {
                window.decorView.keepScreenOn = true
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                window.decorView.keepScreenOn = false
            }
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                window.decorView.keepScreenOn = false
            }
        }
    }

    // Handle orientation change
    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> {
            viewModel.setUserLandscape()
            if (!uiState.isFullscreen) {
                val activity = context as? Activity
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, window.decorView)
                        .hide(WindowInsetsCompat.Type.systemBars())
                    window.decorView.keepScreenOn = true
                }
            }
        }
        else -> {
            viewModel.setUserPortrait()
            if (!uiState.isFullscreen) {
                val activity = context as? Activity
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, window.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                    window.decorView.keepScreenOn = false
                }
            }
        }
    }

    // Apply playback speed
    LaunchedEffect(uiState.playbackSpeed) {
        player?.setPlaybackSpeed(uiState.playbackSpeed)
    }
    
    Scaffold(
        topBar = {
            if (!uiState.isFullscreen && !uiState.isUserLandscape) {
                TopAppBar(
                    title = { Text(uiState.mediaItem?.name ?: stringResource(R.string.player_title)) },
                    navigationIcon = {
                        IconButton(onClick = { 
                            navigateBack(navController, uiState.mediaItem)
                        }) {
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
        },
        floatingActionButton = {
            if (!uiState.isFullscreen && !uiState.isUserLandscape) {
                Box {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.player_create_segment))
                    }
                    
                    androidx.compose.material3.DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        org.introskipper.segmenteditor.data.model.SegmentType.entries.forEach { type ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(type.value) },
                                onClick = {
                                    showFabMenu = false
                                    val durationSeconds = uiState.duration / 1000.0
                                    val newSegment = Segment(
                                        id = null,
                                        itemId = itemId,
                                        type = type.value,
                                        startTicks = 0L,
                                        endTicks = Segment.secondsToTicks(durationSeconds)
                                    )
                                    editingSegments = editingSegments + newSegment
                                    activeSegmentIndex = editingSegments.size - 1
                                    segmentHasChanges = segmentHasChanges + (newSegment.toString() to true)
                                }
                            )
                        }
                    }
                }
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
                editingSegments = editingSegments,
                segmentHasChanges = segmentHasChanges,
                useDirectPlay = useDirectPlay,
                onPlayerReady = { player = it },
                onAudioTracksClick = { showAudioTracks = true },
                onSubtitleTracksClick = { showSubtitleTracks = true },
                onUpdateSegment = { segment ->
                    val index = editingSegments.indexOfFirst { 
                        it.id == segment.id || (it.id == null && it === editingSegments[activeSegmentIndex])
                    }
                    if (index != -1) {
                        editingSegments = editingSegments.toMutableList().apply {
                            set(index, segment)
                        }
                        // Mark as having changes
                        val key = segment.id ?: segment.toString()
                        segmentHasChanges = segmentHasChanges + (key to true)
                    }
                },
                onSaveSegment = { segment ->
                    // Save individual segment
                    viewModel.saveSegment(segment) { result ->
                        result.fold(
                            onSuccess = { savedSegment ->
                                // Update the local segment with the server ID
                                val index = editingSegments.indexOfFirst {
                                    (it.id == null && it === segment) || it.id == segment.id
                                }
                                if (index != -1) {
                                    editingSegments = editingSegments.toMutableList().apply {
                                        set(index, savedSegment)
                                    }
                                }
                                // Clear change flag
                                val key = segment.id ?: segment.toString()
                                segmentHasChanges = segmentHasChanges - key
                                
                                // Refresh from server
                                viewModel.refreshSegments()
                            },
                            onFailure = { error ->
                                Log.e("PlayerScreen", "Failed to save segment", error)
                            }
                        )
                    }
                },
                onSaveAll = {
                    // Save all segments with changes
                    viewModel.saveAllSegments(editingSegments, uiState.segments) { result ->
                        result.fold(
                            onSuccess = { savedSegments ->
                                // Clear all change flags
                                segmentHasChanges = emptyMap()
                                
                                // Refresh from server
                                viewModel.refreshSegments()
                            },
                            onFailure = { error ->
                                Log.e("PlayerScreen", "Failed to save all segments", error)
                            }
                        )
                    }
                },
                onDeleteSegment = { segment ->
                    // If segment has no ID (unsaved), remove it directly from the list
                    if (segment.id == null) {
                        editingSegments = editingSegments.filter { it !== segment }
                        // Clear change flag
                        segmentHasChanges = segmentHasChanges - segment.toString()
                        // Adjust active index if needed
                        if (activeSegmentIndex >= editingSegments.size) {
                            activeSegmentIndex = (editingSegments.size - 1).coerceAtLeast(0)
                        }
                    } else {
                        // For saved segments, show confirmation dialog
                        segmentToDelete = segment
                        showDeleteConfirmation = true
                    }
                },
                onSetActiveSegment = { index ->
                    activeSegmentIndex = index
                },
                onSetStartFromPlayer = { index ->
                    player?.currentPosition?.let { positionMs ->
                        val segment = editingSegments.getOrNull(index)
                        if (segment != null) {
                            val updatedSegment = segment.copy(
                                startTicks = Segment.secondsToTicks(positionMs / 1000.0)
                            )
                            editingSegments = editingSegments.toMutableList().apply {
                                set(index, updatedSegment)
                            }
                            // Mark as having changes
                            val key = segment.id ?: segment.toString()
                            segmentHasChanges = segmentHasChanges + (key to true)
                        }
                    }
                },
                onSetEndFromPlayer = { index ->
                    player?.currentPosition?.let { positionMs ->
                        val segment = editingSegments.getOrNull(index)
                        if (segment != null) {
                            val updatedSegment = segment.copy(
                                endTicks = Segment.secondsToTicks(positionMs / 1000.0)
                            )
                            editingSegments = editingSegments.toMutableList().apply {
                                set(index, updatedSegment)
                            }
                            // Mark as having changes
                            val key = segment.id ?: segment.toString()
                            segmentHasChanges = segmentHasChanges + (key to true)
                        }
                    }
                },
                onPlaybackError = { error ->
                    // Handle playback error - prompt user to switch to HLS if direct play fails
                    if (useDirectPlay && !hasShownErrorDialog) {
                        Log.w("PlayerScreen", "Direct play decoder error, prompting user to switch to HLS: errorCode=${error.errorCode}")
                        hasShownErrorDialog = true
                        showDirectPlayFailedDialog = true
                    }
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
    
    // Delete confirmation dialog
    if (showDeleteConfirmation && segmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmation = false
                segmentToDelete = null
            },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.segment_delete_title)) },
            text = { 
                Text(stringResource(R.string.segment_delete_message, segmentToDelete?.type ?: ""))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        segmentToDelete?.let { segment ->
                            viewModel.deleteSegment(segment)
                            showDeleteConfirmation = false
                            segmentToDelete = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmation = false
                    segmentToDelete = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Direct play failed dialog
    if (showDirectPlayFailedDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDirectPlayFailedDialog = false
            },
            title = { Text(stringResource(R.string.player_direct_play_failed_title)) },
            text = { 
                Text(stringResource(R.string.player_direct_play_failed_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        useDirectPlay = false
                        showDirectPlayFailedDialog = false
                    }
                ) {
                    Text(stringResource(R.string.player_switch_to_hls))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDirectPlayFailedDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun navigateBack(navController: NavController, mediaItem: org.introskipper.segmenteditor.data.model.MediaItem?) {
    if (mediaItem?.seriesId != null) {
        // If it's an episode, go back to the series screen
        // We use popBackStack with the route template to find any existing SeriesScreen in the backstack
        val seriesRouteTemplate = "${Screen.Series.route}/{seriesId}"
        val popped = navController.popBackStack(seriesRouteTemplate, false)
        
        if (!popped) {
            // If the series screen wasn't in the backstack, navigate to it explicitly
            navController.navigate("${Screen.Series.route}/${mediaItem.seriesId}") {
                popUpTo(Screen.Player.route) { inclusive = true }
            }
        }
    } else {
        navController.popBackStack()
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
    editingSegments: List<Segment>,
    segmentHasChanges: Map<String, Boolean>,
    useDirectPlay: Boolean,
    onPlayerReady: (ExoPlayer) -> Unit,
    onAudioTracksClick: () -> Unit,
    onSubtitleTracksClick: () -> Unit,
    onUpdateSegment: (Segment) -> Unit,
    onSaveSegment: (Segment) -> Unit,
    onSaveAll: () -> Unit,
    onDeleteSegment: (Segment) -> Unit,
    onSetActiveSegment: (Int) -> Unit,
    onSetStartFromPlayer: (Int) -> Unit,
    onSetEndFromPlayer: (Int) -> Unit,
    onPlaybackError: (androidx.media3.common.PlaybackException) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use rememberUpdatedState to capture the current useDirectPlay value
    // This ensures callbacks always use the latest value even if they were captured before a state change
    val currentUseDirectPlay by rememberUpdatedState(useDirectPlay)
    
    Column(
        modifier = modifier.then(
            if (uiState.isFullscreen || uiState.isUserLandscape) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth()
            }
        )
    ) {
        // Video player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black).then(
                    if (uiState.isFullscreen || uiState.isUserLandscape) {
                        Modifier
                    } else {
                        Modifier.aspectRatio(16f / 9f)
                    }
                )
        ) {
            if (streamUrl != null) {
                VideoPlayerWithPreview(
                    streamUrl = streamUrl,
                    useController = true,
                    uiState = uiState,
                    viewModel = viewModel,
                    previewLoader = previewLoader,
                    useDirectPlay = useDirectPlay,
                    selectedAudioTrack = uiState.selectedAudioTrack,
                    selectedSubtitleTrack = uiState.selectedSubtitleTrack,
                    onPlayerReady = onPlayerReady,
                    onPlaybackStateChanged = { isPlaying, currentPos, bufferedPos ->
                        viewModel.updatePlaybackState(isPlaying, currentPos, bufferedPos)
                    },
                    onTracksChanged = { tracks ->
                        viewModel.updateTracksFromPlayer(tracks, currentUseDirectPlay)
                    },
                    onPlaybackError = onPlaybackError
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
        
        if (!uiState.isFullscreen && !uiState.isUserLandscape) {
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
                if (editingSegments.isNotEmpty()) {
                    // uiState.duration is already in milliseconds, convert to seconds
                    val runtimeSeconds = uiState.duration / 1000.0
                    
                    items(editingSegments.size) { index ->
                        val segment = editingSegments[index]
                        val segmentKey = segment.id ?: segment.toString()
                        val hasChanges = segmentHasChanges[segmentKey] ?: false
                        
                        SegmentSlider(
                            segment = segment,
                            isActive = index == activeSegmentIndex,
                            runtimeSeconds = runtimeSeconds,
                            onUpdate = { updatedSegment ->
                                // Update local state only
                                onUpdateSegment(updatedSegment)
                            },
                            onDelete = {
                                // Trigger delete confirmation directly
                                onDeleteSegment(segment)
                            },
                            onSeekTo = { timeSeconds ->
                                player?.seekTo(timeSeconds.toDuration(DurationUnit.SECONDS).inWholeMilliseconds)
                            },
                            onSetActive = {
                                onSetActiveSegment(index)
                            },
                            onSetStartFromPlayer = {
                                onSetStartFromPlayer(index)
                            },
                            onSetEndFromPlayer = {
                                onSetEndFromPlayer(index)
                            },
                            onSave = {
                                onSaveSegment(segment)
                            },
                            hasUnsavedChanges = hasChanges,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "No segments found for this media item. Use the + button to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                
                // Save All button - only show if there are more than one unsaved changes
                if (segmentHasChanges.values.count { it } > 1) {
                    item {
                        Button(
                            onClick = onSaveAll,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.segment_save_all))
                        }
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