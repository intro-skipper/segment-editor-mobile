package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.introskipper.segmenteditor.ui.component.*
import org.introskipper.segmenteditor.ui.viewmodel.HomeUiState
import org.introskipper.segmenteditor.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMediaItemClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCollections by viewModel.selectedCollections.collectAsState()
    val availableCollections by viewModel.availableCollections.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Library") },
                actions = {
                    Badge(
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (selectedCollections.isNotEmpty()) {
                            Text("${selectedCollections.size}")
                        }
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter collections"
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
                            onItemClick = onMediaItemClick,
                            modifier = Modifier.weight(1f)
                        )

                        PaginationControls(
                            currentPage = viewModel.currentPage,
                            totalPages = viewModel.totalPages,
                            onPreviousPage = viewModel::previousPage,
                            onNextPage = viewModel::nextPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
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

    if (showFilterSheet) {
        CollectionFilterSheet(
            collections = availableCollections,
            selectedCollections = selectedCollections,
            onToggleCollection = viewModel::toggleCollection,
            onClearFilter = viewModel::clearCollectionFilter,
            onDismiss = { showFilterSheet = false }
        )
    }
}
