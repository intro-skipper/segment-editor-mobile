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
import org.introskipper.segmenteditor.api.SkipMeApiService
import org.introskipper.segmenteditor.data.local.MetadataSubmissionDao
import org.introskipper.segmenteditor.data.local.SubmissionDao
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.MetadataSubmission
import org.introskipper.segmenteditor.data.model.SegmentType
import org.introskipper.segmenteditor.data.model.SkipMeBackfillRequest
import org.introskipper.segmenteditor.data.model.SkipMeSeasonItem
import org.introskipper.segmenteditor.data.model.SkipMeSeasonSubmitRequest
import org.introskipper.segmenteditor.data.model.SkipMeSubmitRequest
import org.introskipper.segmenteditor.data.model.Submission
import org.introskipper.segmenteditor.data.model.toJellyfinMediaItem
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.data.repository.TvMazeRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.util.UiText
import org.introskipper.segmenteditor.utils.TranslationService
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val skipMeApiService: SkipMeApiService,
    private val submissionDao: SubmissionDao,
    private val metadataSubmissionDao: MetadataSubmissionDao,
    private val tvMazeRepository: TvMazeRepository,
    private val translationService: TranslationService
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
                            fields = listOf("ProviderIds", "ParentIndexNumber", "IndexNumber", "SeriesId", "SeasonId")
                        )

                        if (episodes.isSuccessful) {
                            val seasonTvdbIds = mutableMapOf<String, Int?>()
                            val seasons = mediaRepository.getSeasons(item.id, userId, fields = listOf("ProviderIds"))
                            if (seasons.isSuccessful) {
                                seasons.body()?.items?.forEach { 
                                    seasonTvdbIds[it.id] = it.getTvdbId()
                                }
                            }

                            val seriesTmdbId = series.getTmdbId()
                            var seriesTvdbId = series.getTvdbId()
                            var seriesImdbId = series.getImdbId()
                            val seriesAniListId = series.getAniListId()

                            // Try TVMaze to fill any missing series-level IDs
                            if (seriesTvdbId != null && seriesImdbId == null) {
                                val show = tvMazeRepository.lookupByTvdbId(seriesTvdbId!!)
                                if (show != null) seriesImdbId = show.imdbId
                            } else if (seriesImdbId != null && seriesTvdbId == null) {
                                val show = tvMazeRepository.lookupByImdbId(seriesImdbId!!)
                                if (show != null) seriesTvdbId = show.tvdbId
                            }

                            episodes.body()?.items?.filter { (it.parentIndexNumber ?: 0) != 0 }?.forEach { episode ->
                                val tvdbId = episode.getTvdbId()
                                val tvdbSeasonId = seasonTvdbIds[episode.seasonId ?: ""]
                                val aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null
                                val imdbId = episode.getImdbId()

                                // Check for duplicates
                                val existing = metadataSubmissionDao.getSubmission(
                                    seriesId = item.id,
                                    seasonNumber = episode.parentIndexNumber ?: 0,
                                    episodeNumber = episode.indexNumber ?: 0
                                )

                                if (existing != null && 
                                    existing.tmdbId == seriesTmdbId &&
                                    existing.imdbId == imdbId &&
                                    existing.tvdbId == tvdbId &&
                                    existing.tvdbSeriesId == seriesTvdbId &&
                                    existing.tvdbSeasonId == tvdbSeasonId &&
                                    existing.imdbSeriesId == seriesImdbId &&
                                    existing.aniListId == aniListId
                                ) {
                                    return@forEach // Skip duplicate
                                }

                                requests.add(
                                    SkipMeBackfillRequest(
                                        tvdbId = tvdbId,
                                        tmdbId = seriesTmdbId,
                                        imdbId = imdbId,
                                        tvdbSeasonId = tvdbSeasonId,
                                        tvdbSeriesId = seriesTvdbId,
                                        imdbSeriesId = seriesImdbId,
                                        aniListId = aniListId,
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
                            fields = listOf("ProviderIds", "ParentIndexNumber", "IndexNumber", "SeriesId", "SeasonId")
                        )
                        if (episodes.isSuccessful) {
                            val seriesTmdbId = series.getTmdbId()
                            var seriesTvdbId = series.getTvdbId()
                            var seriesImdbId = series.getImdbId()
                            val seriesAniListId = series.getAniListId()
                            val seasonTvdbId = season.getTvdbId()

                            // Try TVMaze to fill any missing series-level IDs
                            if (seriesTvdbId != null && seriesImdbId == null) {
                                val show = tvMazeRepository.lookupByTvdbId(seriesTvdbId!!)
                                if (show != null) seriesImdbId = show.imdbId
                            } else if (seriesImdbId != null && seriesTvdbId == null) {
                                val show = tvMazeRepository.lookupByImdbId(seriesImdbId!!)
                                if (show != null) seriesTvdbId = show.tvdbId
                            }

                            episodes.body()?.items?.forEach { episode ->
                                val tvdbId = episode.getTvdbId()
                                val aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null
                                val imdbId = episode.getImdbId()

                                // Check for duplicates
                                val existing = metadataSubmissionDao.getSubmission(
                                    seriesId = seriesId,
                                    seasonNumber = episode.parentIndexNumber ?: 0,
                                    episodeNumber = episode.indexNumber ?: 0
                                )

                                if (existing != null && 
                                    existing.tmdbId == seriesTmdbId &&
                                    existing.imdbId == imdbId &&
                                    existing.tvdbId == tvdbId &&
                                    existing.tvdbSeriesId == seriesTvdbId &&
                                    existing.tvdbSeasonId == seasonTvdbId &&
                                    existing.imdbSeriesId == seriesImdbId &&
                                    existing.aniListId == aniListId
                                ) {
                                    return@forEach // Skip duplicate
                                }

                                requests.add(
                                    SkipMeBackfillRequest(
                                        tvdbId = tvdbId,
                                        tmdbId = seriesTmdbId,
                                        imdbId = imdbId,
                                        tvdbSeasonId = seasonTvdbId,
                                        tvdbSeriesId = seriesTvdbId,
                                        imdbSeriesId = seriesImdbId,
                                        aniListId = aniListId,
                                        season = episode.parentIndexNumber,
                                        episode = episode.indexNumber
                                    )
                                )
                            }
                        }
                    }
                    "Movie" -> {
                        val movie = jellyfinRepository.getMediaItem(item.id)
                        val tmdbId = movie.getTmdbId()
                        val imdbId = movie.getImdbId()

                        // Check for duplicates
                        val existing = metadataSubmissionDao.getSubmission(
                            seriesId = item.id,
                            seasonNumber = 0,
                            episodeNumber = 0
                        )

                        if (existing != null && 
                            existing.tmdbId == tmdbId &&
                            existing.imdbId == imdbId
                        ) {
                            // Already submitted
                        } else {
                            requests.add(
                                SkipMeBackfillRequest(
                                    tmdbId = tmdbId,
                                    imdbId = imdbId
                                )
                            )
                        }
                    }
                }

                if (requests.isEmpty()) {
                    _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                    return@launch
                }

                val response = skipMeApiService.backfill(requests)
                if (response.isSuccessful) {
                    val updatedCount = response.body()?.updated ?: 0
                    
                    // Update local store
                    requests.forEach { request ->
                        // Find the relevant item ID for the local store
                        val storeItemId = if (item.type == "Movie") item.id else item.id // Simplified, ideally we'd have the episode ID here for series/season

                        metadataSubmissionDao.insert(MetadataSubmission(
                            seriesId = storeItemId,
                            seasonNumber = request.season ?: 0,
                            episodeNumber = request.episode ?: 0,
                            tmdbId = request.tmdbId,
                            imdbId = request.imdbId,
                            tvdbId = request.tvdbId,
                            tvdbSeriesId = request.tvdbSeriesId,
                            tvdbSeasonId = request.tvdbSeasonId,
                            imdbSeriesId = request.imdbSeriesId,
                            aniListId = request.aniListId
                        ))
                    }

                    _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updatedCount)))
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
                        val seasonTvdbIds = seasonsResponse.body()?.items?.associate { it.id to it.getTvdbId() } ?: emptyMap()

                        val seriesTvdbId = series.getTvdbId()
                        val seriesTmdbId = series.getTmdbId()
                        val seriesImdbId = series.getImdbId()
                        val seriesAniListId = series.getAniListId()

                        val (seasonRequests, duplicates) = buildSeasonRequestsWithDuplicates(
                            episodes = episodes,
                            tvdbSeriesId = seriesTvdbId,
                            tmdbId = seriesTmdbId,
                            imdbSeriesId = seriesImdbId,
                            aniListId = seriesAniListId,
                            tvdbSeasonIdFor = { episode -> seasonTvdbIds[episode.seasonId ?: ""] }
                        )

                        if (seasonRequests.isEmpty()) {
                            if (duplicates > 0) {
                                _events.emit(HomeEvent.ShowToast(UiText.DynamicString(translationService.getString(R.string.share_duplicates, duplicates))))
                            } else {
                                _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            }
                            return@launch
                        }

                        val response = skipMeApiService.submitSeason(seasonRequests)
                        if (response.isSuccessful) {
                            val count = response.body()?.submitted ?: 0
                            
                            // Log successfully submitted segments to local store
                            seasonRequests.forEach { seasonRequest ->
                                seasonRequest.items.forEach { segmentItem ->
                                    submissionDao.insert(Submission(
                                        tmdbId = seasonRequest.tmdbId,
                                        imdbId = segmentItem.imdbId,
                                        tvdbSeriesId = seasonRequest.tvdbSeriesId,
                                        imdbSeriesId = seasonRequest.imdbSeriesId,
                                        tvdbSeasonId = seasonRequest.tvdbSeasonId,
                                        tvdbId = segmentItem.tvdbId,
                                        aniListId = seasonRequest.aniListId,
                                        segmentType = segmentItem.segment,
                                        season = seasonRequest.season,
                                        episode = segmentItem.episode,
                                        durationMs = segmentItem.durationMs,
                                        startMs = segmentItem.startMs,
                                        endMs = segmentItem.endMs
                                    ))
                                }
                            }

                            val successMsg = if (duplicates > 0) {
                                "${translationService.getString(R.string.share_success_collection, count)}\n${translationService.getString(R.string.share_duplicates, duplicates)}"
                            } else {
                                translationService.getString(R.string.share_success_collection, count)
                            }
                            _events.emit(HomeEvent.ShowToast(UiText.DynamicString(successMsg)))
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
                        val seriesTvdbId = series.getTvdbId()
                        val seriesTmdbId = series.getTmdbId()
                        val seriesImdbId = series.getImdbId()
                        val seriesAniListId = series.getAniListId()
                        val seasonTvdbId = season.getTvdbId()

                        val (seasonRequests, duplicates) = buildSeasonRequestsWithDuplicates(
                            episodes = episodes,
                            tvdbSeriesId = seriesTvdbId,
                            tmdbId = seriesTmdbId,
                            imdbSeriesId = seriesImdbId,
                            aniListId = seriesAniListId,
                            tvdbSeasonIdFor = { _ -> seasonTvdbId }
                        )

                        if (seasonRequests.isEmpty()) {
                            if (duplicates > 0) {
                                _events.emit(HomeEvent.ShowToast(UiText.DynamicString(translationService.getString(R.string.share_duplicates, duplicates))))
                            } else {
                                _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            }
                            return@launch
                        }

                        val response = skipMeApiService.submitSeason(seasonRequests)
                        if (response.isSuccessful) {
                            val count = response.body()?.submitted ?: 0
                            
                            // Log successfully submitted segments to local store
                            seasonRequests.forEach { seasonRequest ->
                                seasonRequest.items.forEach { segmentItem ->
                                    submissionDao.insert(Submission(
                                        tmdbId = seasonRequest.tmdbId,
                                        imdbId = segmentItem.imdbId,
                                        tvdbSeriesId = seasonRequest.tvdbSeriesId,
                                        imdbSeriesId = seasonRequest.imdbSeriesId,
                                        tvdbSeasonId = seasonRequest.tvdbSeasonId,
                                        tvdbId = segmentItem.tvdbId,
                                        aniListId = seasonRequest.aniListId,
                                        segmentType = segmentItem.segment,
                                        season = seasonRequest.season,
                                        episode = segmentItem.episode,
                                        durationMs = segmentItem.durationMs,
                                        startMs = segmentItem.startMs,
                                        endMs = segmentItem.endMs
                                    ))
                                }
                            }

                            val successMsg = if (duplicates > 0) {
                                "${translationService.getString(R.string.share_success_collection, count)}\n${translationService.getString(R.string.share_duplicates, duplicates)}"
                            } else {
                                translationService.getString(R.string.share_success_collection, count)
                            }
                            _events.emit(HomeEvent.ShowToast(UiText.DynamicString(successMsg)))
                        } else {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, response.code())))
                        }
                    }
                    "Movie" -> {
                        val movie = jellyfinRepository.getMediaItem(item.id)
                        val segmentResult = segmentRepository.getSegmentsResult(item.id)
                        val segments = segmentResult.getOrNull() ?: emptyList()

                        val tmdbId = movie.getTmdbId()
                        val imdbId = movie.getImdbId()
                        val durationMs = movie.runTimeTicks?.div(10_000)

                        if ((tmdbId == null && imdbId == null) || durationMs == null || durationMs <= 0 || segments.isEmpty()) {
                            _events.emit(HomeEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                            return@launch
                        }

                        var submitted = 0
                        var duplicates = 0
                        var firstFailCode: Int? = null
                        val submissionsToInsert = mutableListOf<Submission>()

                        segments.forEach { segment ->
                            val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType() ?: return@forEach
                            val startMs = segment.startTicks / 10_000
                            val endMs = segment.endTicks / 10_000
                            if (startMs >= 0 && endMs > startMs && endMs <= durationMs) {
                                // Filter local duplicates
                                if (submissionDao.isDuplicate(
                                        segmentType = skipMeType,
                                        durationMs = durationMs,
                                        startMs = startMs,
                                        endMs = endMs,
                                        tmdbId = tmdbId,
                                        imdbId = imdbId
                                    )) {
                                    duplicates++
                                    return@forEach
                                }

                                val response = skipMeApiService.submitSegment(
                                    SkipMeSubmitRequest(
                                        tmdbId = tmdbId,
                                        imdbId = imdbId,
                                        segment = skipMeType,
                                        durationMs = durationMs,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                )
                                if (response.isSuccessful) {
                                    submitted++
                                    submissionsToInsert.add(Submission(
                                        tmdbId = tmdbId,
                                        imdbId = imdbId,
                                        segmentType = skipMeType,
                                        durationMs = durationMs,
                                        startMs = startMs,
                                        endMs = endMs
                                    ))
                                } else if (firstFailCode == null) {
                                    firstFailCode = response.code()
                                }
                            }
                        }

                        // Batch insert successful submissions
                        submissionsToInsert.forEach { submissionDao.insert(it) }

                        val message = when {
                            submitted > 0 -> {
                                val success = translationService.getString(R.string.share_success_collection, submitted)
                                if (duplicates > 0) {
                                    "$success\n${translationService.getString(R.string.share_duplicates, duplicates)}"
                                } else success
                            }
                            duplicates > 0 -> translationService.getString(R.string.share_duplicates, duplicates)
                            firstFailCode != null -> translationService.getString(R.string.share_failed_http, firstFailCode!!)
                            else -> translationService.getString(R.string.share_no_segments_found)
                        }
                        _events.emit(HomeEvent.ShowToast(UiText.DynamicString(message)))
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

    private suspend fun buildSeasonRequestsWithDuplicates(
        episodes: List<MediaItem>,
        tvdbSeriesId: Int?,
        tmdbId: Int?,
        imdbSeriesId: String?,
        aniListId: Int?,
        tvdbSeasonIdFor: (MediaItem) -> Int?
    ): Pair<List<SkipMeSeasonSubmitRequest>, Int> {
        if (tvdbSeriesId == null && tmdbId == null && imdbSeriesId == null && aniListId == null) return emptyList<SkipMeSeasonSubmitRequest>() to 0
        data class SeasonKey(val seasonNumber: Int?, val tvdbSeasonId: Int?)
        val itemsBySeason = mutableMapOf<SeasonKey, MutableSet<SkipMeSeasonItem>>()
        var totalDuplicates = 0

        for (episode in episodes) {
            val segmentResult = segmentRepository.getSegmentsResult(episode.id)
            val segments = segmentResult.getOrNull() ?: continue
            val tvdbEpisodeId = episode.getTvdbId()
            val imdbEpisodeId = episode.getImdbId()
            val durationMs = episode.runTimeTicks?.div(10_000) ?: continue
            if (durationMs <= 0) continue

            segments.forEach { segment ->
                val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType() ?: return@forEach
                val startMs = segment.startTicks / 10_000
                val endMs = segment.endTicks / 10_000
                if (startMs >= 0 && endMs > startMs && endMs <= durationMs) {
                    // Filter local duplicates
                    if (submissionDao.isDuplicate(
                            segmentType = skipMeType,
                            durationMs = durationMs,
                            startMs = startMs,
                            endMs = endMs,
                            tvdbId = tvdbEpisodeId,
                            imdbId = imdbEpisodeId,
                            tmdbId = tmdbId,
                            imdbSeriesId = imdbSeriesId,
                            aniListId = aniListId,
                            season = episode.parentIndexNumber,
                            episode = episode.indexNumber
                        )) {
                        totalDuplicates++
                        return@forEach
                    }

                    val key = SeasonKey(episode.parentIndexNumber, tvdbSeasonIdFor(episode))
                    itemsBySeason.getOrPut(key) { mutableSetOf() }.add(
                        SkipMeSeasonItem(
                            tvdbId = tvdbEpisodeId,
                            imdbId = imdbEpisodeId,
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

        val requests = itemsBySeason.map { (key, items) ->
            SkipMeSeasonSubmitRequest(
                tvdbSeriesId = tvdbSeriesId,
                tvdbSeasonId = key.tvdbSeasonId,
                tmdbId = tmdbId,
                imdbSeriesId = imdbSeriesId,
                aniListId = if (key.seasonNumber == 1) aniListId else null,
                season = key.seasonNumber,
                items = items.toList()
            )
        }.filter { request ->
            request.tmdbId != null ||
            request.imdbSeriesId != null ||
            request.aniListId != null ||
            request.items.any { it.tvdbId != null || it.imdbId != null }
        }
        
        return requests to totalDuplicates
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
