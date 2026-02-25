/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.component.MediaHeader
import org.introskipper.segmenteditor.ui.component.TrackCard
import org.introskipper.segmenteditor.ui.component.translatedString
import org.introskipper.segmenteditor.ui.state.AlbumUiState
import org.introskipper.segmenteditor.ui.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel(),
    securePreferences: SecurePreferences
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl = securePreferences.getServerUrl() ?: ""

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(translatedString(R.string.album_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = translatedString(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = uiState is AlbumUiState.Loading),
            onRefresh = { viewModel.refresh(albumId) },
            modifier = Modifier.padding(paddingValues)
        ) {
            when (val state = uiState) {
                is AlbumUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AlbumUiState.Error -> {
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
                            Button(onClick = { viewModel.refresh(albumId) }) {
                                Text(translatedString(R.string.retry))
                            }
                        }
                    }
                }
                is AlbumUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Album header
                        item {
                            val album = state.album
                            val imageUrl = album.getPrimaryImageTag()?.let { tag ->
                                "$serverUrl/Items/${album.id}/Images/Primary?maxWidth=300&tag=$tag&quality=90"
                            }
                            val backdropUrl = album.backdropImageTags?.firstOrNull()?.let { tag ->
                                "$serverUrl/Items/${album.id}/Images/Backdrop/0?maxWidth=800"
                            }

                            MediaHeader(
                                title = album.name ?: translatedString(R.string.player_unknown),
                                subtitle = buildString {
                                    album.albumArtist?.let { append(it) }
                                    album.productionYear?.let {
                                        if (isNotEmpty()) append(" • ")
                                        append(it.toString())
                                    }
                                    val trackCount = state.tracks.size
                                    if (trackCount > 0) {
                                        if (isNotEmpty()) append(" • ")
                                        append("$trackCount tracks")
                                    }
                                },
                                imageUrl = imageUrl,
                                backdropUrl = backdropUrl
                            )
                        }

                        // Tracks
                        if (state.tracks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = translatedString(R.string.album_no_tracks),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        } else {
                            items(state.tracks) { track ->
                                TrackCard(
                                    track = track,
                                    onClick = {
                                        navController.navigate("player/${track.track.id}")
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
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
