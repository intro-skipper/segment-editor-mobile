/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.state

import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.Segment

/**
 * Represents temporary session state that is not persisted
 */
data class SessionState(
    // Current playback state
    val currentMediaItem: MediaItem? = null,
    val currentPosition: Long = 0, // in milliseconds
    val isPlaying: Boolean = false,
    val duration: Long = 0, // in milliseconds
    
    // Current editing state
    val editingSegment: Segment? = null,
    val segmentDraftStart: Double? = null, // in seconds
    val segmentDraftEnd: Double? = null, // in seconds
    
    // Navigation state
    val selectedLibraryId: String? = null,
    val currentRoute: String = "home",
    val navigationHistory: List<String> = emptyList(),
    
    // Loading states
    val isLoadingMedia: Boolean = false,
    val isLoadingSegments: Boolean = false,
    val isSavingSegment: Boolean = false,
    
    // Error states
    val lastError: String? = null,
    val errorTimestamp: Long? = null,
    
    // Search state
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    
    // Selection state
    val selectedItems: Set<String> = emptySet(),
    val multiSelectMode: Boolean = false,
    
    // Filter state
    val activeFilters: Map<String, Any> = emptyMap(),
    val sortBy: String = "SortName",
    val sortOrder: String = "Ascending",
    
    // Pagination state
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalItems: Int = 0
) {
    /**
     * Checks if there is an active error
     */
    fun hasError(): Boolean = lastError != null
    
    /**
     * Checks if any loading operation is in progress
     */
    fun isAnyLoading(): Boolean = isLoadingMedia || isLoadingSegments || isSavingSegment || isSearching
    
    /**
     * Checks if media is currently being played
     */
    fun hasActivePlayback(): Boolean = currentMediaItem != null && isPlaying
    
    /**
     * Checks if a segment is being edited
     */
    fun isEditingSegment(): Boolean = editingSegment != null || segmentDraftStart != null
    
    /**
     * Gets the current playback progress as a percentage
     */
    fun getPlaybackProgress(): Float {
        return if (duration > 0) {
            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    /**
     * Checks if there are selected items
     */
    fun hasSelectedItems(): Boolean = selectedItems.isNotEmpty()
    
    /**
     * Checks if a specific item is selected
     */
    fun isItemSelected(itemId: String): Boolean = itemId in selectedItems
    
    /**
     * Checks if there are active filters
     */
    fun hasActiveFilters(): Boolean = activeFilters.isNotEmpty()
    
    /**
     * Checks if there are more pages available
     */
    fun hasMorePages(): Boolean = currentPage < totalPages - 1
    
    /**
     * Checks if navigation history is available
     */
    fun canNavigateBack(): Boolean = navigationHistory.isNotEmpty()
}

/**
 * Common routes for navigation
 */
object Routes {
    const val HOME = "home"
    const val LIBRARIES = "libraries"
    const val LIBRARY_DETAIL = "library_detail"
    const val MEDIA_DETAIL = "media_detail"
    const val PLAYER = "player"
    const val SEGMENT_EDITOR = "segment_editor"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val SERVER_SELECT = "server_select"
}
