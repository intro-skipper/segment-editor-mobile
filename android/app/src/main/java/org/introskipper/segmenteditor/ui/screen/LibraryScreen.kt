/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.component.translatedString
import org.introskipper.segmenteditor.ui.component.WavyCircularProgressIndicator
import org.introskipper.segmenteditor.ui.state.ThemeState
import org.introskipper.segmenteditor.ui.util.getDominantColor
import org.introskipper.segmenteditor.ui.viewmodel.ContinueWatchingItem
import org.introskipper.segmenteditor.ui.viewmodel.Library
import org.introskipper.segmenteditor.ui.viewmodel.LibraryEvent
import org.introskipper.segmenteditor.ui.viewmodel.LibraryUiState
import org.introskipper.segmenteditor.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onLibraryClick: (String, String?) -> Unit,
    onContinueWatchingClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    themeState: ThemeState
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    // Keep the screen on while a library-level sharing operation is in progress
    val isSharingActive = (uiState as? LibraryUiState.Success)?.isSharingLibraryId != null
    DisposableEffect(isSharingActive) {
        view.keepScreenOn = isSharingActive
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ShowToast -> {
                    Toast.makeText(context, event.message.asString(context), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Always refresh when returning to the library screen.
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(translatedString(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = translatedString(R.string.home_settings)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        var isRefreshing by remember { mutableStateOf(false) }
        val pullToRefreshState = rememberPullToRefreshState()

        LaunchedEffect(uiState) {
            if (uiState !is LibraryUiState.Loading) {
                isRefreshing = false
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
            },
            state = pullToRefreshState,
            indicator = {
                if (isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        WavyCircularProgressIndicator()
                    }
                }
            },
            modifier = Modifier.padding(paddingValues)
        ) {
            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    if (!isRefreshing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            WavyCircularProgressIndicator()
                        }
                    }
                }
                is LibraryUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = translatedString(R.string.library_no_libraries),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is LibraryUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = translatedString(R.string.library_select),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            )
                        }
                        items(state.libraries.count()) { item ->
                            val library = state.libraries[item]
                            val isSharing = state.isSharingLibraryId == library.id
                            LibraryCard(
                                library = library,
                                isSharing = isSharing,
                                sharingProgress = if (isSharing) state.sharingProgress else null,
                                onClick = { 
                                    onLibraryClick(library.id, library.collectionType) 
                                },
                                onShareSegments = {
                                    viewModel.shareLibrarySegments(library.id, library.collectionType)
                                },
                                onShareMetadata = {
                                    viewModel.submitLibraryMetadata(library.id, library.collectionType)
                                },
                                getPrimaryImageUrl = { itemId, imageTag -> viewModel.getPrimaryImageUrl(itemId, imageTag) },
                                onColorSampled = { color ->
                                    themeState.globalSeedColor = color
                                }
                            )
                        }

                        if (state.continueWatching.isNotEmpty()) {
                            item { Spacer(modifier = Modifier.height(12.dp)) }
                            item {
                                Text(
                                    text = translatedString(R.string.library_continue_watching),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                )
                            }
                            items(state.continueWatching.count()) { item ->
                                val mediaItem = state.continueWatching[item]
                                ContinueWatchingCard(
                                    item = mediaItem,
                                    getPrimaryImageUrl = { itemId, imageTag -> viewModel.getPrimaryImageUrl(itemId, imageTag) },
                                    onClick = { onContinueWatchingClick(mediaItem.id) }
                                )
                            }
                        }
                    }
                }
                is LibraryUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = translatedString(R.string.error_prefix, state.message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                                
                            )
                            Button(onClick = { viewModel.refresh() }) {
                                Text(translatedString(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    getPrimaryImageUrl: (String, String) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = item.primaryImageTag?.let { getPrimaryImageUrl(item.id, it) }
    val subtitle = if (item.seasonNumber != null && item.episodeNumber != null) {
        "${item.seriesName ?: item.name} • S${item.seasonNumber}E${item.episodeNumber}"
    } else {
        item.seriesName ?: item.type ?: ""
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(96.dp)
                        .height(56.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(
    library: Library,
    isSharing: Boolean,
    sharingProgress: Float?,
    onClick: () -> Unit,
    onShareSegments: () -> Unit,
    onShareMetadata: () -> Unit,
    getPrimaryImageUrl: (String, String) -> String,
    onColorSampled: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryImageUrl = if (library.primaryImageTag != null) {
        getPrimaryImageUrl(library.id, library.primaryImageTag)
    } else {
        null
    }

    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(primaryImageUrl) {
        if (primaryImageUrl != null) {
            val color = getDominantColor(context, primaryImageUrl)
            onColorSampled(color)
        }
    }

    // Only show long press for supported library types
    val supportsSharing = library.collectionType == "tvshows" || library.collectionType == "movies"
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (supportsSharing) {
                    { showShareDialog = true }
                } else null
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
            if (primaryImageUrl != null) {
                AsyncImage(
                    model = primaryImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )
            }
            
            // Library info
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (primaryImageUrl != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    library.collectionType?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (primaryImageUrl != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isSharing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        WavyCircularProgressIndicator(
                            size = 24.dp,
                            strokeWidth = 2.dp,
                            color = if (primaryImageUrl != null) Color.White else MaterialTheme.colorScheme.primary
                        )
                        sharingProgress?.let { progress ->
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (primaryImageUrl != null) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onShareSegments()
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(translatedString(R.string.share_segments))
                    }
                    Button(
                        onClick = {
                            onShareMetadata()
                            showShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(translatedString(R.string.share_metadata))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text(translatedString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LibraryButton(
    name: String,
    collectionType: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            collectionType?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($it)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
