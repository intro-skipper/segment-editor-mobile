package org.introskipper.segmenteditor.ui.state

import org.introskipper.segmenteditor.data.model.MediaItem

sealed class SeriesUiState {
    object Loading : SeriesUiState()
    data class Success(
        val series: MediaItem,
        val episodesBySeason: Map<Int, List<EpisodeWithSegments>>,
        val seasonNames: Map<Int, String> = emptyMap()
    ) : SeriesUiState()
    data class Error(val message: String) : SeriesUiState()
}

data class EpisodeWithSegments(
    val episode: MediaItem,
    val segmentCount: Int = 0,
    val isLoadingSegments: Boolean = false
)
