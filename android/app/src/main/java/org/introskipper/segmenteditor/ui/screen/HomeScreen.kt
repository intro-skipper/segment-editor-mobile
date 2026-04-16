/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.data.model.isContainerType
import org.introskipper.segmenteditor.ui.component.MediaGrid
import org.introskipper.segmenteditor.ui.component.PaginationControls
import org.introskipper.segmenteditor.ui.component.SearchBar
import org.introskipper.segmenteditor.ui.component.WavyCircularProgressIndicator
import org.introskipper.segmenteditor.ui.viewmodel.HomeUiState
import org.introskipper.segmenteditor.ui.viewmodel.HomeViewModel
import org.introskipper.segmenteditor.ui.component.translatedString
import org.introskipper.segmenteditor.ui.viewmodel.HomeEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    libraryId: String,
    collectionType: String? = null,
    onMediaItemClick: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showAllItems by viewModel.showAllItems.collectAsState()
    val libraryName by viewModel.libraryName.collectAsState()
    val context = LocalContext.current
    
    // Refresh data when screen resumes if settings might have changed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check if page size setting has changed and refresh if needed
                viewModel.refreshIfPageSizeChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Load items for the selected library
    LaunchedEffect(libraryId, collectionType) {
        viewModel.setLibraryId(libraryId, collectionType)
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.ShowToast -> {
                    Toast.makeText(context, event.message.asString(context), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Smart navigation based on media type.
    // Type-specific routes are checked first so that items like Series (which Jellyfin
    // marks as IsFolder=true) are not accidentally treated as generic containers.
    val navigateToMedia: (JellyfinMediaItem) -> Unit = { item ->
        when (item.type) {
            "Series" -> onMediaItemClick("series/${item.id}")
            "MusicAlbum" -> onMediaItemClick("album/${item.id}")
            "MusicArtist" -> onMediaItemClick("artist/${item.id}")
            else -> if (item.isContainerType()) {
                onMediaItemClick("home/${item.id}") // BoxSet, CollectionFolder, etc.
            } else {
                onMediaItemClick("player/${item.id}") // Movies, Episodes, Audio, etc.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(libraryName ?: translatedString(R.string.home_media))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = translatedString(R.string.back)
                        )
                    }
                },
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
            if (uiState !is HomeUiState.Loading) {
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
            modifier = Modifier.padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        if (!isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                            WavyCircularProgressIndicator()
                            }
                        }
                    }
                    is HomeUiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = translatedString(R.string.home_no_media),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is HomeUiState.Success -> {
                        var showShareDialog by remember { mutableStateOf(false) }
                        var selectedItem by remember { mutableStateOf<JellyfinMediaItem?>(null) }

                        MediaGrid(
                            items = state.items,
                            onItemClick = navigateToMedia,
                            onItemLongClick = { item ->
                                selectedItem = item
                                showShareDialog = true
                            },
                            submittingItemId = state.submittingItemId,
                            modifier = Modifier.weight(1f)
                        )

                        if (showShareDialog && selectedItem != null) {
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
                                        text = selectedItem?.name ?: "",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.shareSegments(selectedItem!!)
                                                showShareDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(translatedString(R.string.share_segments))
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.submitMetadata(selectedItem!!)
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

                        // Pagination controls - only show navigation when not displaying all items
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isShowingAll = showAllItems || viewModel.totalPages == 1
                            
                            if (isShowingAll) {
                                // Show count when displaying all items
                                Text(
                                    text = translatedString(R.string.home_showing_all, state.totalItems),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Show pagination controls when not displaying all items
                                PaginationControls(
                                    currentPage = viewModel.currentPage,
                                    totalPages = viewModel.totalPages,
                                    onPreviousPage = viewModel::previousPage,
                                    onNextPage = viewModel::nextPage,
                                    onGoToPage = viewModel::goToPage,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    is HomeUiState.Error -> {
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
}
