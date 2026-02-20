package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.ui.component.MediaGrid
import org.introskipper.segmenteditor.ui.component.PaginationControls
import org.introskipper.segmenteditor.ui.component.SearchBar
import org.introskipper.segmenteditor.ui.viewmodel.HomeUiState
import org.introskipper.segmenteditor.ui.viewmodel.HomeViewModel
import org.introskipper.segmenteditor.ui.component.translatedString

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

    // Smart navigation based on media type
    val navigateToMedia: (JellyfinMediaItem) -> Unit = { item ->
        when(item.type) {
            "Series" -> onMediaItemClick("series/${item.id}")
            "MusicAlbum" -> onMediaItemClick("album/${item.id}")
            "MusicArtist" -> onMediaItemClick("artist/${item.id}")
            else -> onMediaItemClick("player/${item.id}") // Movies, Episodes, Audio, etc.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val title = when (collectionType) {
                        "movies" -> translatedString(R.string.home_movies)
                        "tvshows" -> translatedString(R.string.home_tv_shows)
                        else -> translatedString(R.string.home_media)
                    }
                    Text(title)
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
                                text = translatedString(R.string.home_no_media),
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
