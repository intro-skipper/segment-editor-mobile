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
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.api.SkipMeApiService
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.SegmentType
import org.introskipper.segmenteditor.data.model.SkipMeBackfillRequest
import org.introskipper.segmenteditor.data.model.SkipMeSeasonItem
import org.introskipper.segmenteditor.data.model.SkipMeSeasonSubmitRequest
import org.introskipper.segmenteditor.data.model.SkipMeSubmitRequest
import org.introskipper.segmenteditor.data.model.toJellyfinMediaItem
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.util.UiText
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val skipMeApiService: SkipMeApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showAllItems = MutableStateFlow(false)
    val showAllItems: StateFlow<Boolean> = _showAllItems

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val _libraryName = MutableStateFlow<String?>(null)
    val libraryName: StateFlow<String?> = _libraryName

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
        viewModelScope.launch {
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

    fun submitMetadata(item: JellyfinMediaItem) {
        viewModelScope.launch {
            val currentState = _uiState.value as? HomeUiState.Success ?: return@launch
            _uiState.update { currentState.copy(submittingItemId = item.id) }
            
            try {
                val userId = securePreferences.getUserId() ?: return@launch
                val requests = mutableListOf<SkipMeBackfillRequest>()

                when (item.type) {
                    "Series" -> {
                        // Load full series info to get episodes
                        val series = jellyfinRepository.getMediaItem(item.id)
                        val episodes = mediaRepository.getEpisodes(
                            seriesId = item.id,
                            userId = userId,
                            fields = listOf("ProviderIds", "ParentIndexNumber", "IndexNumber")
                        )

                        if (episodes.isSuccessful) {
                            val seasonTvdbIds = mutableMapOf<String, Int?>()
                            val seasons = mediaRepository.getSeasons(item.id, userId, fields = listOf("ProviderIds"))
                            if (seasons.isSuccessful) {
                                seasons.body()?.items?.forEach { 
                                    seasonTvdbIds[it.id] = it.providerIds?.get("Tvdb")?.toIntOrNull()
                                }
                            }

                            val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                            val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                            val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                            episodes.body()?.items?.filter { (it.parentIndexNumber ?: 0) != 0 }?.forEach { episode ->
                                requests.add(
                                    SkipMeBackfillRequest(
                                        tvdbId = episode.providerIds?.get("Tvdb")?.toIntOrNull(),
                                        tmdbId = seriesTmdbId,
                                        tvdbSeasonId = seasonTvdbIds[episode.seasonId ?: ""],
                                        tvdbSeriesId = seriesTvdbId,
                                        aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null,
                                        season = episode.parentIndexNumber,
                                        episode = episode.indexNumber
                                    )
                                )
                            }
                        }
                    }
                    "Season" -> {
                        val season = jellyfinRepository.getMediaItem(item.id)
                        val seriesId = season.seriesId ?: return@launch
                        val series = jellyfinRepository.getMediaItem(seriesId)
                        val episodes = mediaRepository.getEpisodes(
                            seriesId = seriesId,
                            userId = userId,
                            seasonId = item.id,
                            fields = listOf("ProviderIds", "ParentIndexNumber", "IndexNumber")
                        )
                        if (episodes.isSuccessful) {
                            val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                            val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                            val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()
                            val seasonTvdbId = season.providerIds?.get("Tvdb")?.toIntOrNull()

                            episodes.body()?.items?.forEach { episode ->
                                requests.add(
                                    SkipMeBackfillRequest(
                                        tvdbId = episode.providerIds?.get("Tvdb")?.toIntOrNull(),
                                        tmdbId = seriesTmdbId,
                                        tvdbSeasonId = seasonTvdbId,
                                        tvdbSeriesId = seriesTvdbId,
                                        aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null,
                                        season = episode.parentIndexNumber,
                                        episode = episode.indexNumber
                                    )
                                )
                            }
                        }
                    }
                    "Movie" -> {
                        val movie = jellyfinRepository.getMediaItem(item.id)
                        requests.add(
                            SkipMeBackfillRequest(
                                tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull()
                            )
                        )
                    }
                }

                if (requests.isEmpty()) {
                    _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                    return@launch
                }

                val response = skipMeApiService.backfill(requests)
                if (response.isSuccessful) {
                    val updated = response.body()?.updated ?: 0
                    _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updated)))
                } else {
                    _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_http, response.code())))
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error submitting metadata", e)
                _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_generic, e.message ?: "")))
            } finally {
                _uiState.update { (it as? HomeUiState.Success)?.copy(submittingItemId = null) ?: it }
            }
        }
    }

    fun shareSegments(item: JellyfinMediaItem) {
        viewModelScope.launch {
            val currentState = _uiState.value as? HomeUiState.Success ?: return@launch
            _uiState.update { currentState.copy(submittingItemId = item.id) }
            
            try {
                val userId = securePreferences.getUserId() ?: return@launch

                when (item.type) {
                    "Series" -> {
                        val series = jellyfinRepository.getMediaItem(item.id)
                        val episodesResponse = mediaRepository.getEpisodes(
                            seriesId = item.id,
                            userId = userId,
                            fields = JellyfinApiService.EPISODE_FIELDS
                        )

                        if (!episodesResponse.isSuccessful) {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            return@launch
                        }

                        val episodes = (episodesResponse.body()?.items ?: emptyList())
                            .filter { (it.parentIndexNumber ?: 0) != 0 }

                        val seasonsResponse = mediaRepository.getSeasons(item.id, userId, fields = listOf("ProviderIds"))
                        val seasonTvdbIds = seasonsResponse.body()?.items?.associate { it.id to it.providerIds?.get("Tvdb")?.toIntOrNull() } ?: emptyMap()

                        val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                        val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                        val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                        val seasonRequests = buildSeasonRequests(
                            episodes = episodes,
                            tvdbSeriesId = seriesTvdbId,
                            tmdbId = seriesTmdbId,
                            aniListId = seriesAniListId,
                            tvdbSeasonIdFor = { episode -> seasonTvdbIds[episode.seasonId ?: ""] }
                        )

                        if (seasonRequests.isEmpty()) {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            return@launch
                        }

                        val response = skipMeApiService.submitSeason(seasonRequests)
                        if (response.isSuccessful) {
                            val count = response.body()?.submitted ?: 0
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_success_collection, count)))
                        } else {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, response.code())))
                        }
                    }
                    "Season" -> {
                        val season = jellyfinRepository.getMediaItem(item.id)
                        val seriesId = season.seriesId ?: return@launch
                        val series = jellyfinRepository.getMediaItem(seriesId)
                        val episodesResponse = mediaRepository.getEpisodes(
                            seriesId = seriesId,
                            userId = userId,
                            seasonId = item.id,
                            fields = JellyfinApiService.EPISODE_FIELDS
                        )

                        if (!episodesResponse.isSuccessful) {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            return@launch
                        }

                        val episodes = episodesResponse.body()?.items ?: emptyList()
                        val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                        val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                        val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()
                        val seasonTvdbId = season.providerIds?.get("Tvdb")?.toIntOrNull()

                        val seasonRequests = buildSeasonRequests(
                            episodes = episodes,
                            tvdbSeriesId = seriesTvdbId,
                            tmdbId = seriesTmdbId,
                            aniListId = seriesAniListId,
                            tvdbSeasonIdFor = { _ -> seasonTvdbId }
                        )

                        if (seasonRequests.isEmpty()) {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            return@launch
                        }

                        val response = skipMeApiService.submitSeason(seasonRequests)
                        if (response.isSuccessful) {
                            val count = response.body()?.submitted ?: 0
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_success_collection, count)))
                        } else {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, response.code())))
                        }
                    }
                    "Movie" -> {
                        val movie = jellyfinRepository.getMediaItem(item.id)
                        val segmentResult = segmentRepository.getSegmentsResult(item.id)
                        val segments = segmentResult.getOrNull() ?: emptyList()

                        val tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull()
                        val durationMs = movie.runTimeTicks?.div(10_000)

                        if (tmdbId == null || durationMs == null || durationMs <= 0 || segments.isEmpty()) {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            return@launch
                        }

                        var submitted = 0
                        var firstFailCode: Int? = null
                        segments.forEach { segment ->
                            val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType() ?: return@forEach
                            val startMs = segment.startTicks / 10_000
                            val endMs = segment.endTicks / 10_000
                            if (startMs >= 0 && endMs > startMs && endMs <= durationMs) {
                                val response = skipMeApiService.submitSegment(
                                    SkipMeSubmitRequest(
                                        tmdbId = tmdbId,
                                        segment = skipMeType,
                                        durationMs = durationMs,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                )
                                if (response.isSuccessful) submitted++ else if (firstFailCode == null) firstFailCode = response.code()
                            }
                        }

                        when {
                            submitted > 0 -> _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_success_collection, submitted)))
                            firstFailCode != null -> _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, firstFailCode)))
                            else -> _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error sharing segments", e)
                _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_failed_collection_generic, e.message ?: "")))
            } finally {
                _uiState.update { (it as? HomeUiState.Success)?.copy(submittingItemId = null) ?: it }
            }
        }
    }

    /**
     * Groups episodes by (season number, tvdb season id) and builds the list of
     * [SkipMeSeasonSubmitRequest] objects for POST /v1/submit/season.
     */
    private suspend fun buildSeasonRequests(
        episodes: List<MediaItem>,
        tvdbSeriesId: Int?,
        tmdbId: Int?,
        aniListId: Int?,
        tvdbSeasonIdFor: (MediaItem) -> Int?
    ): List<SkipMeSeasonSubmitRequest> {
        if (tvdbSeriesId == null && tmdbId == null && aniListId == null) return emptyList()

        data class SeasonKey(val seasonNumber: Int?, val tvdbSeasonId: Int?)
        val itemsBySeason = mutableMapOf<SeasonKey, MutableSet<SkipMeSeasonItem>>()

        for (episode in episodes) {
            val segmentResult = segmentRepository.getSegmentsResult(episode.id)
            val segments = segmentResult.getOrNull() ?: continue
            val tvdbEpisodeId = episode.providerIds?.get("Tvdb")?.toIntOrNull()
            val durationMs = episode.runTimeTicks?.div(10_000) ?: continue
            if (durationMs <= 0) continue

            segments.forEach { segment ->
                val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType() ?: return@forEach
                val startMs = segment.startTicks / 10_000
                val endMs = segment.endTicks / 10_000
                if (startMs >= 0 && endMs > startMs && endMs <= durationMs) {
                    val key = SeasonKey(episode.parentIndexNumber, tvdbSeasonIdFor(episode))
                    itemsBySeason.getOrPut(key) { mutableSetOf() }.add(
                        SkipMeSeasonItem(
                            tvdbId = tvdbEpisodeId,
                            episode = episode.indexNumber,
                            segment = skipMeType,
                            durationMs = durationMs,
                            startMs = startMs,
                            endMs = endMs
                        )
                    )
                }
            }
        }

        return itemsBySeason.map { (key, items) ->
            SkipMeSeasonSubmitRequest(
                tvdbSeriesId = tvdbSeriesId,
                tvdbSeasonId = key.tvdbSeasonId,
                tmdbId = tmdbId,
                aniListId = if (key.seasonNumber == 1) aniListId else null,
                season = key.seasonNumber,
                items = items.toList()
            )
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
}
