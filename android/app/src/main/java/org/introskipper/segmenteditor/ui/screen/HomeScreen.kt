package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.ui.component.*
import org.introskipper.segmenteditor.ui.viewmodel.HomeUiState
import org.introskipper.segmenteditor.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    libraryId: String,
    onMediaItemClick: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showAllItems by viewModel.showAllItems.collectAsState()
    
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
    LaunchedEffect(libraryId) {
        viewModel.setLibraryId(libraryId)
    }

    // Smart navigation based on media type
    val navigateToMedia: (JellyfinMediaItem) -> Unit = { item ->
        when (item.type) {
            "Series" -> onMediaItemClick("series/${item.id}")
            "MusicAlbum" -> onMediaItemClick("album/${item.id}")
            "MusicArtist" -> onMediaItemClick("artist/${item.id}")
            else -> onMediaItemClick("player/${item.id}") // Movies, Episodes, Audio, etc.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TV Shows") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = uiState is HomeUiState.Loading),
            onRefresh = { viewModel.refresh() },
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is HomeUiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No media items found",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is HomeUiState.Success -> {
                        MediaGrid(
                            items = state.items,
                            onItemClick = navigateToMedia,
                            modifier = Modifier.weight(1f)
                        )

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
                                    text = "Showing all ${state.totalItems} items",
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
                                    text = "Error: ${state.message}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = { viewModel.refresh() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
