/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.ArtistUiState
import org.introskipper.segmenteditor.ui.state.TrackWithSegments
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState

    fun loadArtist(artistId: String) {
        viewModelScope.launch {
            _uiState.value = ArtistUiState.Loading
            try {
                val userId = securePreferences.getUserId() ?: throw Exception("User not authenticated")

                // Load artist info
                val artistResult = mediaRepository.getItemResult(
                    userId = userId,
                    itemId = artistId,
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                if (artistResult.isFailure) {
                    _uiState.value = ArtistUiState.Error(
                        artistResult.exceptionOrNull()?.message ?: "Failed to load artist"
                    )
                    return@launch
                }

                val artist = artistResult.getOrThrow()

                // Load albums by artist
                val albumsResult = mediaRepository.getItemsResult(
                    userId = userId,
                    includeItemTypes = listOf("MusicAlbum"),
                    recursive = true,
                    sortBy = "ProductionYear,SortName",
                    sortOrder = "Descending",
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                // Filter albums by artist (the API might not have direct artist filtering)
                val albums = albumsResult.getOrNull()?.items?.filter { album ->
                    album.albumArtist == artist.name || album.artists?.contains(artist.name) == true
                } ?: emptyList()

                // Load tracks by artist
                val tracksResult = mediaRepository.getItemsResult(
                    userId = userId,
                    includeItemTypes = listOf("Audio"),
                    recursive = true,
                    sortBy = "Album,ParentIndexNumber,IndexNumber",
                    sortOrder = "Ascending",
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                // Filter tracks by artist
                val tracks = tracksResult.getOrNull()?.items?.filter { track ->
                    track.artists?.contains(artist.name) == true
                }?.map { track ->
                    TrackWithSegments(track = track)
                } ?: emptyList()

                _uiState.value = ArtistUiState.Success(
                    artist = artist,
                    albums = albums,
                    tracks = tracks
                )

                // Load segment counts asynchronously for tracks
                loadSegmentCounts(tracks)

            } catch (e: Exception) {
                _uiState.value = ArtistUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadSegmentCounts(tracks: List<TrackWithSegments>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is ArtistUiState.Success) return@launch

            // Load segment counts for all tracks in parallel
            val updatedTracks = tracks.map { trackWithSegments ->
                async {
                    val segmentResult = segmentRepository.getSegmentsResult(trackWithSegments.track.id)
                    val segmentCount = segmentResult.getOrNull()?.size ?: 0
                    trackWithSegments.copy(segmentCount = segmentCount)
                }
            }.awaitAll()

            _uiState.value = currentState.copy(tracks = updatedTracks)
        }
    }

    fun refresh(artistId: String) {
        loadArtist(artistId)
    }
}
