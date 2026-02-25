/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.state

import org.introskipper.segmenteditor.data.model.MediaItem

sealed class AlbumUiState {
    object Loading : AlbumUiState()
    data class Success(
        val album: MediaItem,
        val tracks: List<TrackWithSegments>
    ) : AlbumUiState()
    data class Error(val message: String) : AlbumUiState()
}

data class TrackWithSegments(
    val track: MediaItem,
    val segmentCount: Int = 0,
    val isLoadingSegments: Boolean = false
)
