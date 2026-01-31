package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.toJellyfinMediaItem
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.component.MediaCard
import org.introskipper.segmenteditor.ui.component.MediaHeader
import org.introskipper.segmenteditor.ui.component.TrackCard
import org.introskipper.segmenteditor.ui.state.ArtistTab
import org.introskipper.segmenteditor.ui.state.ArtistUiState
import org.introskipper.segmenteditor.ui.viewmodel.ArtistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: String,
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
    securePreferences: SecurePreferences
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl = securePreferences.getServerUrl() ?: ""
    var selectedTab by remember { mutableStateOf(ArtistTab.ALBUMS) }

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artist") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = uiState is ArtistUiState.Loading),
            onRefresh = { viewModel.refresh(artistId) },
            modifier = Modifier.padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ArtistUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ArtistUiState.Error -> {
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
                            Button(onClick = { viewModel.refresh(artistId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is ArtistUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Artist header
                        val artist = state.artist
                        val imageUrl = artist.getPrimaryImageTag()?.let { tag ->
                            "$serverUrl/Items/${artist.id}/Images/Primary?maxWidth=300&tag=$tag&quality=90"
                        }
                        val backdropUrl = artist.backdropImageTags?.firstOrNull()?.let { tag ->
                            "$serverUrl/Items/${artist.id}/Images/Backdrop/0?maxWidth=800"
                        }

                        MediaHeader(
                            title = artist.name ?: "Unknown Artist",
                            subtitle = buildString {
                                val albumCount = state.albums.size
                                val trackCount = state.tracks.size
                                if (albumCount > 0) {
                                    append("$albumCount albums")
                                }
                                if (trackCount > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("$trackCount tracks")
                                }
                            },
                            imageUrl = imageUrl,
                            backdropUrl = backdropUrl
                        )

                        // Tabs
                        TabRow(selectedTabIndex = selectedTab.ordinal) {
                            Tab(
                                selected = selectedTab == ArtistTab.ALBUMS,
                                onClick = { selectedTab = ArtistTab.ALBUMS },
                                text = { Text("Albums") }
                            )
                            Tab(
                                selected = selectedTab == ArtistTab.TRACKS,
                                onClick = { selectedTab = ArtistTab.TRACKS },
                                text = { Text("Tracks") }
                            )
                        }

                        // Tab content
                        when (selectedTab) {
                            ArtistTab.ALBUMS -> {
                                if (state.albums.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No albums found",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(state.albums) { album ->
                                            MediaCard(
                                                item = album.toJellyfinMediaItem(serverUrl),
                                                onClick = {
                                                    // Check if it's an album or should navigate to album screen
                                                    if (album.type == "MusicAlbum") {
                                                        navController.navigate("album/${album.id}")
                                                    } else {
                                                        navController.navigate("player/${album.id}")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            ArtistTab.TRACKS -> {
                                if (state.tracks.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No tracks found",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(state.tracks) { track ->
                                            TrackCard(
                                                track = track,
                                                onClick = {
                                                    navController.navigate("player/${track.track.id}")
                                                }
                                            )
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
}
