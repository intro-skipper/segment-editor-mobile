package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.component.EpisodeCard
import org.introskipper.segmenteditor.ui.component.MediaHeader
import org.introskipper.segmenteditor.ui.navigation.Screen
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
                title = { Text(stringResource(R.string.series_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                is SeriesUiState.Success -> {
                    // Track selected season with state
                    var selectedSeasonIndex by remember { 
                        mutableStateOf(0)
                    }
                    
                    // Get sorted season numbers
                    val sortedSeasons = remember(state.episodesBySeason) {
                        state.episodesBySeason.keys.sorted()
                    }
                    
                    // Get episodes for selected season - handle empty case
                    val selectedSeasonNumber = if (sortedSeasons.isEmpty()) {
                        null
                    } else {
                        sortedSeasons.getOrNull(selectedSeasonIndex) ?: sortedSeasons.first()
                    }
                    val selectedEpisodes = selectedSeasonNumber?.let { 
                        state.episodesBySeason[it] 
                    } ?: emptyList()
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Series header
                        val series = state.series
                        val imageUrl = series.getPrimaryImageTag()?.let { tag ->
                            "$serverUrl/Items/${series.id}/Images/Primary?maxWidth=300&tag=$tag&quality=90"
                        }
                        val backdropUrl = series.backdropImageTags?.firstOrNull()?.let { tag ->
                            "$serverUrl/Items/${series.id}/Images/Backdrop/0?maxWidth=800"
                        }

                        MediaHeader(
                            title = series.name ?: stringResource(R.string.series_unknown),
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
                        
                        // Season tabs
                        if (state.episodesBySeason.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No episodes found",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else if (sortedSeasons.size > 1) {
                            // Show tabs for multiple seasons
                            ScrollableTabRow(
                                selectedTabIndex = selectedSeasonIndex,
                                modifier = Modifier.fillMaxWidth(),
                                edgePadding = 8.dp
                            ) {
                                sortedSeasons.forEachIndexed { index, seasonNumber ->
                                    Tab(
                                        selected = selectedSeasonIndex == index,
                                        onClick = { selectedSeasonIndex = index },
                                        text = { 
                                            Text(
                                                text = state.seasonNames[seasonNumber] ?: "Season $seasonNumber",
                                                style = MaterialTheme.typography.titleMedium
                                            ) 
                                        }
                                    )
                                }
                            }
                            
                            // Episodes for selected season
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                items(selectedEpisodes) { episode ->
                                    EpisodeCard(
                                        episode = episode,
                                        serverUrl = serverUrl,
                                        onClick = {
                                            navController.navigate("player/${episode.episode.id}")
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                
                                // Bottom padding
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        } else {
                            // Single season - no tabs needed
                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Single season header
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = state.seasonNames[selectedSeasonNumber] ?: "Season ${selectedSeasonNumber ?: 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                
                                items(selectedEpisodes) { episode ->
                                    EpisodeCard(
                                        episode = episode,
                                        serverUrl = serverUrl,
                                        onClick = {
                                            navController.navigate("player/${episode.episode.id}")
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
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
    }
}
