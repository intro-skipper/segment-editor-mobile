/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.state

import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.util.UiText

sealed class SeriesUiState {
    object Loading : SeriesUiState()
    data class Success(
        val series: MediaItem,
        val episodesBySeason: Map<Int, List<EpisodeWithSegments>>,
        val seasonNames: Map<Int, String?> = emptyMap(),
        val isSharing: Boolean = false,
        val isShared: Boolean = false,
        val isLoadingSegments: Boolean = false
    ) : SeriesUiState()
    data class Error(val message: UiText) : SeriesUiState()
}

data class EpisodeWithSegments(
    val episode: MediaItem,
    val segments: List<Segment>? = null,
    val segmentCount: Int = 0,
    val isLoadingSegments: Boolean = false
)

sealed class SeriesEvent {
    data class ShowToast(val message: UiText) : SeriesEvent()
}
