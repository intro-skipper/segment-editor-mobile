/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.data.model.toJellyfinMediaItem
import org.introskipper.segmenteditor.data.export.SegmentExportItem
import org.introskipper.segmenteditor.data.export.SegmentExporter
import org.introskipper.segmenteditor.data.export.SegmentExportFile
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.BrowseLayout
import org.introskipper.segmenteditor.ui.util.UiText
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val segmentExporter: SegmentExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showAllItems = MutableStateFlow(false)
    val showAllItems: StateFlow<Boolean> = _showAllItems

    private val _browseLayout = MutableStateFlow(BrowseLayout.CARD)
    val browseLayout: StateFlow<BrowseLayout> = _browseLayout

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val _libraryName = MutableStateFlow<String?>(null)
    val libraryName: StateFlow<String?> = _libraryName

    private var libraryNameJob: Job? = null

    private var currentLibraryId: String? = null
    private var currentCollectionType: String? = null

    // Track the last known page size to detect changes
    private var lastPageSize: Int = 0

    var currentPage by mutableStateOf(1)
        private set

    var totalPages by mutableStateOf(1)
        private set

    private val pageSize: Int
        get() = securePreferences.getItemsPerPage()
    
    companion object {
        // Maximum items to load when "show all" is enabled
        // This prevents performance issues with extremely large libraries
        private const val SHOW_ALL_LIMIT = 10000
    }

    init {
        viewModelScope.launch {
            _searchQuery.debounce(500).collect {
                currentPage = 1
                loadMediaItems()
            }
        }
        _browseLayout.value = securePreferences.getBrowseLayout()
    }
    
    fun setLibraryId(libraryId: String, collectionType: String? = null) {
        if (currentLibraryId != libraryId || currentCollectionType != collectionType) {
            currentLibraryId = libraryId
            currentCollectionType = collectionType
            currentPage = 1
            _showAllItems.value = false
            loadLibraryName(libraryId)
            loadMediaItems()
        }
    }

    private fun loadLibraryName(libraryId: String) {
        libraryNameJob?.cancel()
        libraryNameJob = viewModelScope.launch {
            try {
                val item = jellyfinRepository.getMediaItem(libraryId)
                _libraryName.value = item.name
            } catch (e: Exception) {
                // Library name is best-effort; leave current value on failure
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowAllItems() {
        _showAllItems.value = !_showAllItems.value
        currentPage = 1
        loadMediaItems()
    }

    fun nextPage() {
        if (currentPage < totalPages) {
            currentPage++
            loadMediaItems()
        }
    }

    fun previousPage() {
        if (currentPage > 1) {
            currentPage--
            loadMediaItems()
        }
    }
    
    fun goToPage(page: Int) {
        if (page in 1..totalPages && page != currentPage) {
            currentPage = page
            loadMediaItems()
        }
    }

    fun refresh() {
        currentPage = 1
        loadMediaItems()
    }
    
    fun refreshIfPageSizeChanged() {
        val currentPageSize = pageSize
        if (lastPageSize != 0 && lastPageSize != currentPageSize) {
            // Page size has changed, refresh the data
            currentPage = 1
            _showAllItems.value = false
            loadMediaItems()
        }
        _browseLayout.value = securePreferences.getBrowseLayout()
    }

    private fun loadMediaItems() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val libraryId = currentLibraryId
                if (libraryId == null) {
                    _uiState.value = HomeUiState.Empty
                    return@launch
                }

                val currentPageSize = pageSize
                
                // Track the page size for detecting changes
                lastPageSize = currentPageSize
                
                // Use a large limit for "show all", otherwise use pageSize
                val limit = if (_showAllItems.value || currentPageSize == Int.MAX_VALUE) {
                    SHOW_ALL_LIMIT
                } else {
                    currentPageSize
                }
                
                val startIndex = if (_showAllItems.value || currentPageSize == Int.MAX_VALUE) {
                    0
                } else {
                    (currentPage - 1) * currentPageSize
                }

                val includeItemTypes: List<String>? = when (currentCollectionType) {
                    "movies" -> listOf("Movie")
                    "tvshows" -> listOf("Series")
                    "music" -> listOf("MusicAlbum", "MusicArtist")
                    "boxsets" -> listOf("BoxSet")
                    else -> null // Mixed/unknown/container: let the API return all item types
                }

                val result = jellyfinRepository.getMediaItems(
                    searchTerm = _searchQuery.value.ifBlank { null },
                    parentIds = listOf(libraryId),
                    startIndex = startIndex,
                    limit = limit,
                    includeItemTypes = includeItemTypes
                )

                totalPages = if (_showAllItems.value || currentPageSize == Int.MAX_VALUE) {
                    1
                } else {
                    (result.totalRecordCount + currentPageSize - 1) / currentPageSize
                }

                val serverUrl = securePreferences.getServerUrl() ?: ""
                val jellyfinItems = result.items.map { it.toJellyfinMediaItem(serverUrl) }

                _uiState.value = if (jellyfinItems.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Success(jellyfinItems, result.totalRecordCount)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Exports every segment belonging to the selected home item. */
    fun shareSegments(item: JellyfinMediaItem) {
        viewModelScope.launch {
            val currentState = _uiState.value as? HomeUiState.Success ?: return@launch
            _uiState.update { currentState.copy(submittingItemId = item.id) }

            try {
                val userId = securePreferences.getUserId() ?: return@launch
                val exportItems = when (item.type) {
                    "Series" -> {
                        val series = jellyfinRepository.getMediaItem(item.id)
                        val response = mediaRepository.getEpisodes(
                            seriesId = item.id,
                            userId = userId,
                            fields = JellyfinApiService.EPISODE_FIELDS
                        )
                        if (!response.isSuccessful) emptyList() else response.body()?.items
                            .orEmpty()
                            .filter { (it.parentIndexNumber ?: 0) != 0 }
                            .flatMap { episode ->
                                segmentRepository.getSegmentsResult(episode.id).getOrNull().orEmpty()
                                    .map { segment -> SegmentExportItem.from(segment, episode, series) }
                            }
                    }
                    "Season" -> {
                        val season = jellyfinRepository.getMediaItem(item.id)
                        val seriesId = season.seriesId
                        if (seriesId == null) {
                            emptyList()
                        } else {
                            val series = jellyfinRepository.getMediaItem(seriesId)
                            val response = mediaRepository.getEpisodes(
                                seriesId = seriesId,
                                userId = userId,
                                seasonId = item.id,
                                fields = JellyfinApiService.EPISODE_FIELDS
                            )
                            if (!response.isSuccessful) emptyList() else response.body()?.items
                                .orEmpty()
                                .flatMap { episode ->
                                    segmentRepository.getSegmentsResult(episode.id).getOrNull().orEmpty()
                                        .map { segment -> SegmentExportItem.from(segment, episode, series) }
                                }
                        }
                    }
                    "Movie" -> {
                        val movie = jellyfinRepository.getMediaItem(item.id)
                        segmentRepository.getSegmentsResult(movie.id).getOrNull().orEmpty()
                            .map { segment -> SegmentExportItem.from(segment, movie) }
                    }
                    else -> emptyList()
                }

                if (exportItems.isEmpty()) {
                    _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.export_no_segments)))
                } else {
                    _events.emit(HomeEvent.ShareExport(segmentExporter.export(exportItems, item.name)))
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error exporting segments", e)
                _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.export_failed)))
            } finally {
                _uiState.update { (it as? HomeUiState.Success)?.copy(submittingItemId = null) ?: it }
            }
        }
    }

}

sealed class HomeUiState {
    object Loading : HomeUiState()
    object Empty : HomeUiState()
    data class Success(
        val items: List<JellyfinMediaItem>,
        val totalItems: Int,
        val submittingItemId: String? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

sealed class HomeEvent {
    data class ShowToast(val message: UiText) : HomeEvent()
    data class ShareExport(val file: SegmentExportFile) : HomeEvent()
}
