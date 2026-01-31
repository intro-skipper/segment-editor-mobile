package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.component.EpisodeCard
import org.introskipper.segmenteditor.ui.component.MediaHeader
import org.introskipper.segmenteditor.ui.state.SeriesUiState
import org.introskipper.segmenteditor.ui.viewmodel.SeriesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    seriesId: String,
    navController: NavController,
    viewModel: SeriesViewModel = hiltViewModel(),
    securePreferences: SecurePreferences
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl = securePreferences.getServerUrl() ?: ""

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = uiState is SeriesUiState.Loading),
            onRefresh = { viewModel.refresh(seriesId) },
            modifier = Modifier.padding(paddingValues)
        ) {
            when (val state = uiState) {
                is SeriesUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SeriesUiState.Error -> {
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
                            Button(onClick = { viewModel.refresh(seriesId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is SeriesUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Series header
                        item {
                            val series = state.series
                            val imageUrl = series.getPrimaryImageTag()?.let { tag ->
                                "$serverUrl/Items/${series.id}/Images/Primary?maxWidth=300&tag=$tag&quality=90"
                            }
                            val backdropUrl = series.backdropImageTags?.firstOrNull()?.let { tag ->
                                "$serverUrl/Items/${series.id}/Images/Backdrop/0?maxWidth=800"
                            }

                            MediaHeader(
                                title = series.name ?: "Unknown Series",
                                subtitle = buildString {
                                    series.productionYear?.let { append(it.toString()) }
                                    val totalEpisodes = state.episodesBySeason.values.sumOf { it.size }
                                    if (totalEpisodes > 0) {
                                        if (isNotEmpty()) append(" • ")
                                        append("$totalEpisodes episodes")
                                    }
                                },
                                imageUrl = imageUrl,
                                backdropUrl = backdropUrl
                            )
                        }

                        // Episodes grouped by season
                        if (state.episodesBySeason.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No episodes found",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        } else {
                            state.episodesBySeason.forEach { (seasonNumber, episodes) ->
                                // Season header
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "Season $seasonNumber",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                // Episodes in this season
                                items(episodes) { episode ->
                                    EpisodeCard(
                                        episode = episode,
                                        serverUrl = serverUrl,
                                        onClick = {
                                            navController.navigate("player/${episode.episode.id}")
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Bottom padding
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
