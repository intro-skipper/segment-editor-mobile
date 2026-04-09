/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.api.SkipMeApiService
import org.introskipper.segmenteditor.data.model.filterSkipMe
import org.introskipper.segmenteditor.data.model.SegmentType
import org.introskipper.segmenteditor.data.model.SkipMeBackfillRequest
import org.introskipper.segmenteditor.data.model.SkipMeSeasonItem
import org.introskipper.segmenteditor.data.model.SkipMeSeasonSubmitRequest
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.EpisodeWithSegments
import org.introskipper.segmenteditor.ui.state.SeriesEvent
import org.introskipper.segmenteditor.ui.state.SeriesUiState
import org.introskipper.segmenteditor.ui.util.SeasonSortUtil
import org.introskipper.segmenteditor.ui.util.UiText
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val skipMeApiService: SkipMeApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val uiState: StateFlow<SeriesUiState> = _uiState

    private val _events = MutableSharedFlow<SeriesEvent>()
    val events: SharedFlow<SeriesEvent> = _events.asSharedFlow()

    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            _uiState.value = SeriesUiState.Loading
            try {
                val userId = securePreferences.getUserId() ?: run {
                    _uiState.value = SeriesUiState.Error(UiText.StringResource(R.string.auth_error_not_authenticated))
                    return@launch
                }

                // Load series info
                val seriesResult = mediaRepository.getItemResult(
                    userId = userId,
                    itemId = seriesId,
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                if (seriesResult.isFailure) {
                    val message = seriesResult.exceptionOrNull()?.message
                    _uiState.value = SeriesUiState.Error(
                        if (message != null) UiText.DynamicString(message)
                        else UiText.StringResource(R.string.series_error_load)
                    )
                    return@launch
                }

                val series = seriesResult.getOrThrow()

                // Load all episodes (all seasons)
                val episodesResult = mediaRepository.getItemsResult(
                    userId = userId,
                    parentId = seriesId,
                    includeItemTypes = listOf("Episode"),
                    recursive = true,
                    sortBy = "ParentIndexNumber,IndexNumber",
                    sortOrder = "Ascending",
                    fields = JellyfinApiService.EPISODE_FIELDS
                )

                if (episodesResult.isFailure) {
                    val message = episodesResult.exceptionOrNull()?.message
                    _uiState.value = SeriesUiState.Error(
                        if (message != null) UiText.DynamicString(message)
                        else UiText.StringResource(R.string.series_episodes_error_load)
                    )
                    return@launch
                }

                val episodes = episodesResult.getOrThrow().items

                // Load seasons to get their provider IDs (TVDB IDs)
                val seasonsResult = mediaRepository.getSeasons(seriesId, userId, fields = listOf("ProviderIds"))
                val seasonTvdbIds = mutableMapOf<String, Int?>()
                val seasonIdsByNumber = mutableMapOf<Int, String>()
                
                if (seasonsResult.isSuccessful) {
                    seasonsResult.body()?.items?.forEach { season ->
                        seasonTvdbIds[season.id] = season.providerIds?.get("Tvdb")?.toIntOrNull()
                        season.indexNumber?.let { num -> seasonIdsByNumber[num] = season.id }
                    }
                }

                // Group episodes by season
                val episodesBySeason = episodes
                    .groupBy { it.parentIndexNumber ?: 0 }
                    .mapValues { (_, episodeList) ->
                        episodeList.map { episode ->
                            EpisodeWithSegments(episode = episode)
                        }
                    }
                    .toSortedMap(SeasonSortUtil.seasonComparator)

                // Create season name mapping from the first episode of each season
                val seasonNames = episodesBySeason.mapValues { (_, episodeList) ->
                    episodeList.firstOrNull()?.episode?.seasonName
                }

                _uiState.value = SeriesUiState.Success(
                    series = series,
                    episodesBySeason = episodesBySeason,
                    seasonNames = seasonNames,
                    seasonTvdbIds = seasonTvdbIds,
                    seasonIdsByNumber = seasonIdsByNumber,
                    isLoadingSegments = true
                )

                // Load segments asynchronously
                loadSegmentsForEpisodes(episodesBySeason)

            } catch (e: Exception) {
                val message = e.message
                _uiState.value = SeriesUiState.Error(
                    if (message != null) UiText.DynamicString(message)
                    else UiText.StringResource(R.string.error_unknown)
                )
            }
        }
    }

    private fun loadSegmentsForEpisodes(episodesBySeason: Map<Int, List<EpisodeWithSegments>>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is SeriesUiState.Success) return@launch

            val disableSkipMeSegments = securePreferences.getDisableSkipMeSegments()

            // Load segments for all episodes in parallel
            val updatedEpisodesBySeason = episodesBySeason.mapValues { (_, episodes) ->
                episodes.map { episodeWithSegments ->
                    async {
                        val segmentResult = segmentRepository.getSegmentsResult(episodeWithSegments.episode.id)
                        val segments = (segmentResult.getOrNull() ?: emptyList()).let { list ->
                            if (disableSkipMeSegments) list.filterSkipMe() else list
                        }
                        episodeWithSegments.copy(
                            segments = segments,
                            segmentCount = segments.size
                        )
                    }
                }
            }.mapValues { (_, deferredList) ->
                deferredList.awaitAll()
            }

            _uiState.update { state ->
                if (state is SeriesUiState.Success) {
                    state.copy(
                        episodesBySeason = updatedEpisodesBySeason,
                        isLoadingSegments = false
                    )
                } else state
            }
        }
    }

    /**
     * Shares all segments for the specified season to SkipMe.db
     */
    fun shareSeasonSegments(seasonNumber: Int) {
        val currentState = _uiState.value
        if (currentState !is SeriesUiState.Success) return

        val episodes = currentState.episodesBySeason[seasonNumber] ?: return
        _uiState.update { currentState.copy(isSharing = true, submittingSeasonNumber = seasonNumber) }
        shareEpisodes(episodes, hideAfterSuccess = false) {
             _uiState.update { (it as? SeriesUiState.Success)?.copy(isSharing = false, submittingSeasonNumber = null) ?: it }
        }
    }

    /**
     * Shares all segments for all seasons (excluding specials) to SkipMe.db
     */
    fun shareEntireSeries() {
        val currentState = _uiState.value
        if (currentState !is SeriesUiState.Success) return

        val allEpisodes = currentState.episodesBySeason
            .filter { it.key != 0 } // Exclude specials
            .values
            .flatten()
        
        _uiState.update { currentState.copy(isSharing = true) }
        shareEpisodes(allEpisodes, hideAfterSuccess = true) {
             _uiState.update { (it as? SeriesUiState.Success)?.copy(isSharing = false) ?: it }
        }
    }

    private fun shareEpisodes(
        episodes: List<EpisodeWithSegments>, 
        hideAfterSuccess: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value as? SeriesUiState.Success ?: run { onComplete(); return@launch }
            
            try {
                val seriesTvdbId = currentState.series.providerIds?.get("Tvdb")?.toIntOrNull()
                val seriesTmdbId = currentState.series.providerIds?.get("Tmdb")?.toIntOrNull()
                val seriesAniListId = currentState.series.providerIds?.get("AniList")?.toIntOrNull()

                // Group deduplicated items by (season number, tvdb season id)
                data class SeasonKey(val seasonNumber: Int?, val tvdbSeasonId: Int?)
                val itemsBySeason = mutableMapOf<SeasonKey, MutableSet<SkipMeSeasonItem>>()

                episodes.forEach { episodeWithSegments ->
                    val episode = episodeWithSegments.episode
                    val segments = episodeWithSegments.segments ?: return@forEach

                    val tvdbEpisodeId = episode.providerIds?.get("Tvdb")?.toIntOrNull()
                    val tvdbSeasonId = currentState.seasonTvdbIds[episode.seasonId ?: ""]
                    val durationMs = episode.runTimeTicks?.div(10_000)

                    if ((seriesTmdbId != null || seriesTvdbId != null || seriesAniListId != null) && durationMs != null && durationMs > 0) {
                        segments.forEach { segment ->
                            val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType()
                            if (skipMeType == null) return@forEach

                            val startMs = segment.startTicks / 10_000
                            val endMs = segment.endTicks / 10_000
                            if (startMs < 0 || endMs <= startMs || endMs > durationMs) {
                                return@forEach
                            }

                            val key = SeasonKey(episode.parentIndexNumber, tvdbSeasonId)
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

                if (itemsBySeason.isEmpty()) {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
                    return@launch
                }

                val seasonRequests = itemsBySeason.map { (key, items) ->
                    SkipMeSeasonSubmitRequest(
                        tvdbSeriesId = seriesTvdbId,
                        tvdbSeasonId = key.tvdbSeasonId,
                        tmdbId = seriesTmdbId,
                        aniListId = if (key.seasonNumber == 1) seriesAniListId else null,
                        season = key.seasonNumber,
                        items = items.toList()
                    )
                }

                val response = skipMeApiService.submitSeason(seasonRequests)
                if (response.isSuccessful) {
                    val count = response.body()?.submitted ?: 0
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.share_success_collection, count)))
                    if (hideAfterSuccess) {
                        _uiState.update { (it as SeriesUiState.Success).copy(isShared = true) }
                    }
                } else {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, response.code())))
                }
            } catch (e: Exception) {
                Log.e("SeriesViewModel", "Error sharing segments", e)
                val message = e.message ?: "Unknown error"
                _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.share_failed_collection_generic, message)))
            } finally {
                onComplete()
            }
        }
    }

    fun submitSeasonMetadata(seasonNumber: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value as? SeriesUiState.Success ?: return@launch
            val episodes = currentState.episodesBySeason[seasonNumber] ?: return@launch
            
            _uiState.update { currentState.copy(submittingSeasonNumber = seasonNumber) }
            try {
                val requests = mutableListOf<SkipMeBackfillRequest>()
                val seriesTmdbId = currentState.series.providerIds?.get("Tmdb")?.toIntOrNull()
                val seriesTvdbId = currentState.series.providerIds?.get("Tvdb")?.toIntOrNull()
                val seriesAniListId = currentState.series.providerIds?.get("AniList")?.toIntOrNull()

                episodes.forEach { episodeWithSegments ->
                    val episode = episodeWithSegments.episode
                    requests.add(
                        SkipMeBackfillRequest(
                            tvdbId = episode.providerIds?.get("Tvdb")?.toIntOrNull(),
                            tmdbId = seriesTmdbId,
                            tvdbSeasonId = currentState.seasonTvdbIds[episode.seasonId ?: ""],
                            tvdbSeriesId = seriesTvdbId,
                            aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null,
                            season = episode.parentIndexNumber,
                            episode = episode.indexNumber
                        )
                    )
                }

                if (requests.isEmpty()) {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                    return@launch
                }

                val response = skipMeApiService.backfill(requests)
                if (response.isSuccessful) {
                    val updated = response.body()?.updated ?: 0
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updated)))
                } else {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_http, response.code())))
                }
            } catch (e: Exception) {
                Log.e("SeriesViewModel", "Error submitting metadata", e)
                _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_generic, e.message ?: "")))
            } finally {
                _uiState.update { (it as? SeriesUiState.Success)?.copy(submittingSeasonNumber = null) ?: it }
            }
        }
    }

    fun submitSeriesMetadata() {
        viewModelScope.launch {
            val currentState = _uiState.value as? SeriesUiState.Success ?: return@launch
            _uiState.update { currentState.copy(isSharing = true) }
            try {
                val requests = mutableListOf<SkipMeBackfillRequest>()
                val seriesTmdbId = currentState.series.providerIds?.get("Tmdb")?.toIntOrNull()
                val seriesTvdbId = currentState.series.providerIds?.get("Tvdb")?.toIntOrNull()
                val seriesAniListId = currentState.series.providerIds?.get("AniList")?.toIntOrNull()

                currentState.episodesBySeason.values.flatten().forEach { episodeWithSegments ->
                    val episode = episodeWithSegments.episode
                    if ((episode.parentIndexNumber ?: 0) == 0) return@forEach
                    
                    requests.add(
                        SkipMeBackfillRequest(
                            tvdbId = episode.providerIds?.get("Tvdb")?.toIntOrNull(),
                            tmdbId = seriesTmdbId,
                            tvdbSeasonId = currentState.seasonTvdbIds[episode.seasonId ?: ""],
                            tvdbSeriesId = seriesTvdbId,
                            aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null,
                            season = episode.parentIndexNumber,
                            episode = episode.indexNumber
                        )
                    )
                }

                if (requests.isEmpty()) {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                    return@launch
                }

                val response = skipMeApiService.backfill(requests)
                if (response.isSuccessful) {
                    val updated = response.body()?.updated ?: 0
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updated)))
                } else {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_http, response.code())))
                }
            } catch (e: Exception) {
                Log.e("SeriesViewModel", "Error submitting metadata", e)
                _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_generic, e.message ?: "")))
            } finally {
                _uiState.update { (it as? SeriesUiState.Success)?.copy(isSharing = false) ?: it }
            }
        }
    }

    fun refresh(seriesId: String) {
        loadSeries(seriesId)
    }
}
