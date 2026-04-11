/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavController
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.component.EpisodeCard
import org.introskipper.segmenteditor.ui.component.MediaHeader
import org.introskipper.segmenteditor.ui.component.WavyCircularProgressIndicator
import org.introskipper.segmenteditor.ui.component.translatedString
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.state.SeriesEvent
import org.introskipper.segmenteditor.ui.state.SeriesUiState
import org.introskipper.segmenteditor.ui.theme.DynamicColorsOptions
import org.introskipper.segmenteditor.ui.theme.SegmentEditorTheme
import org.introskipper.segmenteditor.ui.util.SeasonSortUtil
import org.introskipper.segmenteditor.ui.util.getDominantColor
import org.introskipper.segmenteditor.ui.viewmodel.SeriesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesScreen(
    seriesId: String,
    navController: NavController,
    viewModel: SeriesViewModel = hiltViewModel(),
    securePreferences: SecurePreferences,
    initialSeason: Int? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl = securePreferences.getServerUrl() ?: ""
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SeriesEvent.ShowToast -> {
                    Toast.makeText(context, event.message.asString(context), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Extract dominant color from series image
    LaunchedEffect(uiState) {
        if (uiState is SeriesUiState.Success) {
            val series = (uiState as SeriesUiState.Success).series
            series.getPrimaryImageTag()?.let { tag ->
                val imageUrl = "$serverUrl/Items/${series.id}/Images/Primary?maxWidth=300&tag=$tag&quality=90"
                dominantColor = getDominantColor(context, imageUrl)
            }
        }
    }

    SegmentEditorTheme(
        dynamicColorsOptions = DynamicColorsOptions(seedColor = dominantColor)
    ) {
        // Track selected season with state
        var selectedSeasonIndex by remember { 
            mutableStateOf(0)
        }

        // Observe season communicated back from the player via savedStateHandle (e.g. after
        // auto-play crosses a season boundary and the user navigates back).
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        val targetSeason by remember(savedStateHandle) {
            savedStateHandle?.getStateFlow("targetSeason", -1)
                ?: kotlinx.coroutines.flow.MutableStateFlow(-1)
        }.collectAsState()

        // initialSeason (route param) is consumed at most once; once a targetSeason is
        // applied it is nulled out so that a subsequent targetSeason clearing cannot
        // accidentally re-apply the stale route param.
        var pendingInitialSeason by remember { mutableStateOf(initialSeason) }

        // When the series data is loaded, jump to the correct season tab.
        // Priority: targetSeason (from player back-navigation) > pendingInitialSeason (route param).
        // Both values are consumed on use to prevent them from being re-applied on
        // subsequent LaunchedEffect re-runs triggered by unrelated state changes.
        LaunchedEffect(uiState, targetSeason) {
            val state = uiState as? SeriesUiState.Success ?: return@LaunchedEffect
            val season = when {
                targetSeason != -1 -> {
                    // Consume targetSeason and nullify pendingInitialSeason so the route-param
                    // value cannot re-apply after this targetSeason is cleared.
                    savedStateHandle?.set("targetSeason", -1)
                    pendingInitialSeason = null
                    targetSeason
                }
                pendingInitialSeason != null -> {
                    val s = pendingInitialSeason ?: return@LaunchedEffect
                    pendingInitialSeason = null
                    s
                }
                else -> return@LaunchedEffect
            }
            val sortedSeasons = state.episodesBySeason.keys.sortedWith(SeasonSortUtil.seasonComparator)
            val index = sortedSeasons.indexOf(season)
            if (index >= 0) {
                selectedSeasonIndex = index
            }
        }

        var showShareMenu by remember { mutableStateOf(false) }

        // Mirror the back-icon behavior for the device back button.
        BackHandler {
            navigateBackFromSeries(navController)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val seriesName = (uiState as? SeriesUiState.Success)?.series?.name
                        Text(seriesName ?: translatedString(R.string.series_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigateBackFromSeries(navController) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = translatedString(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            navController.navigate(Screen.Settings.route)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = translatedString(R.string.home_settings),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                val state = uiState
                if (state is SeriesUiState.Success && !state.isLoadingSegments && !state.isShared) {
                    val seasonsToShare = state.episodesBySeason.keys.filter { it != 0 }
                    
                    Box {
                        FloatingActionButton(
                            onClick = { showShareMenu = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            if (state.isSharing) {
                                WavyCircularProgressIndicator(
                                    size = 24.dp,
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = translatedString(R.string.share_season_segments)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            seasonsToShare.forEach { seasonNumber ->
                                DropdownMenuItem(
                                    text = {
                                        Text(state.seasonNames[seasonNumber] ?: translatedString(R.string.series_season_format, seasonNumber))
                                    },
                                    onClick = {
                                        showShareMenu = false
                                        viewModel.shareSeasonSegments(seasonNumber)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            var isRefreshing by remember { mutableStateOf(false) }
            val pullToRefreshState = rememberPullToRefreshState()

            LaunchedEffect(uiState) {
                if (uiState !is SeriesUiState.Loading) {
                    isRefreshing = false
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refresh(seriesId)
                },
                state = pullToRefreshState,
                modifier = Modifier.padding(paddingValues)
            ) {
                when (val state = uiState) {
                    is SeriesUiState.Loading -> {
                        if (!isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                WavyCircularProgressIndicator()
                            }
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
                                    text = translatedString(R.string.error_prefix, state.message.asString()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = { viewModel.refresh(seriesId) }) {
                                    Text(translatedString(R.string.retry))
                                }
                            }
                        }
                    }
                    is SeriesUiState.Success -> {
                        // Get sorted season numbers
                        val sortedSeasons = remember(state.episodesBySeason) {
                            state.episodesBySeason.keys.toList()
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

                            val totalEpisodesCount = state.episodesBySeason.values.sumOf { it.size }
                            val totalEpisodesText = if (totalEpisodesCount > 0) {
                                translatedString(R.string.series_total_episodes, totalEpisodesCount)
                            } else null

                            MediaHeader(
                                title = series.name ?: translatedString(R.string.series_unknown),
                                subtitle = buildString {
                                    series.productionYear?.let { append(it.toString()) }
                                    if (totalEpisodesText != null) {
                                        if (isNotEmpty()) append(" • ")
                                        append(totalEpisodesText)
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
                                        text = translatedString(R.string.series_no_episodes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else if (sortedSeasons.size > 1) {
                                // Show tabs for multiple seasons
                                ScrollableTabRow(
                                    selectedTabIndex = selectedSeasonIndex,
                                    modifier = Modifier.fillMaxWidth(),
                                    edgePadding = 8.dp,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ) {
                                    sortedSeasons.forEachIndexed { index, seasonNumber ->
                                        var showSeasonShareDialog by remember { mutableStateOf(false) }

                                        Tab(
                                            selected = selectedSeasonIndex == index,
                                            onClick = { selectedSeasonIndex = index },
                                            modifier = Modifier.combinedClickable(
                                                onClick = { selectedSeasonIndex = index },
                                                onLongClick = { 
                                                    selectedSeasonIndex = index
                                                    showSeasonShareDialog = true 
                                                }
                                            ),
                                            text = { 
                                                Box {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = state.seasonNames[seasonNumber] ?: translatedString(R.string.series_season_format, seasonNumber),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = if (selectedSeasonIndex == index) 
                                                                MaterialTheme.colorScheme.primary 
                                                            else 
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (state.submittingSeasonNumber == seasonNumber) {
                                                            Spacer(modifier = Modifier.size(8.dp))
                                                            WavyCircularProgressIndicator(
                                                                size = 12.dp,
                                                                strokeWidth = 2.dp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }

                                                    if (showSeasonShareDialog) {
                                                        AlertDialog(
                                                            onDismissRequest = { showSeasonShareDialog = false },
                                                            icon = {
                                                                Icon(
                                                                    imageVector = Icons.Default.Share,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            },
                                                            title = { 
                                                                val seriesName = state.series.name ?: ""
                                                                val seasonName = state.seasonNames[seasonNumber] ?: translatedString(R.string.series_season_format, seasonNumber)
                                                                Text(
                                                                    text = if (seriesName.isNotEmpty()) "$seriesName - $seasonName" else seasonName,
                                                                    style = MaterialTheme.typography.headlineSmall
                                                                )
                                                            },
                                                            text = {
                                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Button(
                                                                        onClick = {
                                                                            viewModel.shareSeasonSegments(seasonNumber)
                                                                            showSeasonShareDialog = false
                                                                        },
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Text(translatedString(R.string.share_segments))
                                                                    }
                                                                    Button(
                                                                        onClick = {
                                                                            viewModel.submitSeasonMetadata(seasonNumber)
                                                                            showSeasonShareDialog = false
                                                                        },
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Text(translatedString(R.string.share_metadata))
                                                                    }
                                                                }
                                                            },
                                                            confirmButton = {
                                                                TextButton(onClick = { showSeasonShareDialog = false }) {
                                                                    Text(translatedString(R.string.cancel))
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
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
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                        var showSeasonShareDialog by remember { mutableStateOf(false) }
                                        val seasonNumber = selectedSeasonNumber ?: 1

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .combinedClickable(
                                                    onClick = { },
                                                    onLongClick = { showSeasonShareDialog = true }
                                                ),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Box {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = state.seasonNames[selectedSeasonNumber] ?: translatedString(R.string.series_season_format, seasonNumber),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (state.submittingSeasonNumber == seasonNumber) {
                                                        WavyCircularProgressIndicator(
                                                            size = 16.dp,
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }

                                                if (showSeasonShareDialog) {
                                                    AlertDialog(
                                                        onDismissRequest = { showSeasonShareDialog = false },
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Default.Share,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        },
                                                        title = { 
                                                            val seriesName = state.series.name ?: ""
                                                            val seasonName = state.seasonNames[seasonNumber] ?: translatedString(R.string.series_season_format, seasonNumber)
                                                            Text(
                                                                text = if (seriesName.isNotEmpty()) "$seriesName - $seasonName" else seasonName,
                                                                style = MaterialTheme.typography.headlineSmall
                                                            )
                                                        },
                                                        text = {
                                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Button(
                                                                    onClick = {
                                                                        viewModel.shareSeasonSegments(seasonNumber)
                                                                        showSeasonShareDialog = false
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Text(translatedString(R.string.share_segments))
                                                                }
                                                                Button(
                                                                    onClick = {
                                                                        viewModel.submitSeasonMetadata(seasonNumber)
                                                                        showSeasonShareDialog = false
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Text(translatedString(R.string.share_metadata))
                                                                }
                                                            }
                                                        },
                                                        confirmButton = {
                                                            TextButton(onClick = { showSeasonShareDialog = false }) {
                                                                Text(translatedString(R.string.cancel))
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    items(selectedEpisodes) { episode ->
                                        EpisodeCard(
                                            episode = episode,
                                            serverUrl = serverUrl,
                                            onClick = {
                                                navController.navigate("player/${episode.episode.id}")
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
}

private fun navigateBackFromSeries(navController: NavController) {
    // Navigate back to the library items screen, skipping any video player entries that may
    // have been left in the backstack. Falls back to the library list screen if needed.
    val homeRouteTemplate = "${Screen.Home.route}/{libraryId}?type={collectionType}"
    if (!navController.popBackStack(homeRouteTemplate, false)) {
        if (!navController.popBackStack(Screen.Library.route, false)) {
            navController.popBackStack(Screen.Main.route, false)
        }
    }
}
