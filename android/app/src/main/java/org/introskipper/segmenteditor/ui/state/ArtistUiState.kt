package org.introskipper.segmenteditor.ui.state

import org.introskipper.segmenteditor.data.model.MediaItem

sealed class ArtistUiState {
    object Loading : ArtistUiState()
    data class Success(
        val artist: MediaItem,
        val albums: List<MediaItem>,
        val tracks: List<TrackWithSegments>
    ) : ArtistUiState()
    data class Error(val message: String) : ArtistUiState()
}

enum class ArtistTab {
    ALBUMS, TRACKS
}
