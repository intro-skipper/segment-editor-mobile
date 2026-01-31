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
import org.introskipper.segmenteditor.ui.state.AlbumUiState
import org.introskipper.segmenteditor.ui.state.TrackWithSegments
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val uiState: StateFlow<AlbumUiState> = _uiState

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _uiState.value = AlbumUiState.Loading
            try {
                val userId = securePreferences.getUserId() ?: throw Exception("User not authenticated")

                // Load album info
                val albumResult = mediaRepository.getItemResult(
                    userId = userId,
                    itemId = albumId,
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                if (albumResult.isFailure) {
                    _uiState.value = AlbumUiState.Error(
                        albumResult.exceptionOrNull()?.message ?: "Failed to load album"
                    )
                    return@launch
                }

                val album = albumResult.getOrThrow()

                // Load tracks
                val tracksResult = mediaRepository.getItemsResult(
                    userId = userId,
                    parentId = albumId,
                    includeItemTypes = listOf("Audio"),
                    recursive = false,
                    sortBy = "ParentIndexNumber,IndexNumber",
                    sortOrder = "Ascending",
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                if (tracksResult.isFailure) {
                    _uiState.value = AlbumUiState.Error(
                        tracksResult.exceptionOrNull()?.message ?: "Failed to load tracks"
                    )
                    return@launch
                }

                val tracks = tracksResult.getOrThrow().items.map { track ->
                    TrackWithSegments(track = track)
                }

                _uiState.value = AlbumUiState.Success(
                    album = album,
                    tracks = tracks
                )

                // Load segment counts asynchronously
                loadSegmentCounts(tracks)

            } catch (e: Exception) {
                _uiState.value = AlbumUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadSegmentCounts(tracks: List<TrackWithSegments>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is AlbumUiState.Success) return@launch

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

    fun refresh(albumId: String) {
        loadAlbum(albumId)
    }
}
