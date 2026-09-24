/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.export.SegmentExportItem
import org.introskipper.segmenteditor.data.export.SegmentExporter
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.SegmentCreateRequest
import org.introskipper.segmenteditor.data.model.filterSkipMe
import org.introskipper.segmenteditor.data.model.SegmentType
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.EpisodeWithSegments
import org.introskipper.segmenteditor.ui.state.SeriesEvent
import org.introskipper.segmenteditor.ui.state.SeriesUiState
import org.introskipper.segmenteditor.ui.util.SeasonSortUtil
import org.introskipper.segmenteditor.ui.util.UiText
import org.introskipper.segmenteditor.utils.getTranslatedString
import javax.inject.Inject

@Serializable
data class ImportJson(
    val items: List<ImportItem>
)

@Serializable
data class ImportItem(
    val imdb_series_id: String? = null,
    val segment_type: String,
    val season: Int,
    val episode: Int,
    val start_sec: Double,
    val end_sec: Double
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val segmentExporter: SegmentExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val uiState: StateFlow<SeriesUiState> = _uiState

    private val _events = MutableSharedFlow<SeriesEvent>()
    val events: SharedFlow<SeriesEvent> = _events.asSharedFlow()

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

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
                        seasonTvdbIds[season.id] = season.getTvdbId()
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

    /** Exports all segments for the specified season using the configured format. */
    fun shareSeasonSegments(seasonNumber: Int) {
        val currentState = _uiState.value
        if (currentState !is SeriesUiState.Success) return

        val episodes = currentState.episodesBySeason[seasonNumber] ?: return
        _uiState.update { currentState.copy(isSharing = true, submittingSeasonNumber = seasonNumber) }
        exportEpisodes(episodes, "${currentState.series.name.orEmpty()}_season_$seasonNumber") {
            _uiState.update { (it as? SeriesUiState.Success)?.copy(isSharing = false, submittingSeasonNumber = null) ?: it }
        }
    }

    /** Exports all segments for all seasons (excluding specials). */
    fun shareEntireSeries() {
        val currentState = _uiState.value
        if (currentState !is SeriesUiState.Success) return

        val allEpisodes = currentState.episodesBySeason
            .filter { it.key != 0 }
            .values
            .flatten()

        _uiState.update { currentState.copy(isSharing = true) }
        exportEpisodes(allEpisodes, currentState.series.name.orEmpty()) {
            _uiState.update { (it as? SeriesUiState.Success)?.copy(isSharing = false) ?: it }
        }
    }

    private fun exportEpisodes(
        episodes: List<EpisodeWithSegments>,
        title: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value as? SeriesUiState.Success ?: run {
                onComplete()
                return@launch
            }

            try {
                val exportItems = episodes.flatMap { episodeWithSegments ->
                    episodeWithSegments.segments.orEmpty().map { segment ->
                        SegmentExportItem.from(segment, episodeWithSegments.episode, currentState.series)
                    }
                }
                if (exportItems.isEmpty()) {
                    _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.export_no_segments)))
                    return@launch
                }

                _events.emit(SeriesEvent.ShareExport(segmentExporter.export(exportItems, title)))
            } catch (e: Exception) {
                Log.e("SeriesViewModel", "Error exporting segments", e)
                _events.emit(SeriesEvent.ShowToast(UiText.StringResource(R.string.export_failed)))
            } finally {
                onComplete()
            }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value as? SeriesUiState.Success ?: return@launch
            _uiState.update { currentState.copy(isLoadingSegments = true) }
            
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { 
                    it.bufferedReader().readText() 
                } ?: throw Exception("Could not read file")
                
                val importData = json.decodeFromString<ImportJson>(content)
                val seriesImdbId = currentState.series.getImdbId()
                
                var successCount = 0
                var failCount = 0
                var skipCount = 0
                
                importData.items.forEach { item ->
                    // Validate IMDb ID if provided
                    if (item.imdb_series_id != null && seriesImdbId != null && item.imdb_series_id != seriesImdbId) {
                        skipCount++
                        return@forEach
                    }
                    
                    // Find matching episode
                    val episode = currentState.episodesBySeason[item.season]?.find { it.episode.indexNumber == item.episode }
                    if (episode == null) {
                        skipCount++
                        return@forEach
                    }
                    
                    val segmentType = SegmentType.fromString(item.segment_type)
                    if (segmentType == null) {
                        skipCount++
                        return@forEach
                    }
                    
                    val startTicks = Segment.secondsToTicks(item.start_sec)
                    val endTicks = Segment.secondsToTicks(item.end_sec)
                    
                    val request = SegmentCreateRequest(
                        itemId = episode.episode.id,
                        type = segmentType.apiValue,
                        startTicks = startTicks,
                        endTicks = endTicks
                    )
                    
                    val result = segmentRepository.createSegmentResult(episode.episode.id, request)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                        Log.e("SeriesViewModel", "Failed to import segment: ${result.exceptionOrNull()?.message}")
                    }
                }
                
                _events.emit(SeriesEvent.ShowToast(UiText.DynamicString(
                    buildString {
                        append(context.getTranslatedString(R.string.import_success, successCount))
                        if (failCount > 0) append("\n").append(context.getTranslatedString(R.string.import_failed, failCount))
                        if (skipCount > 0) append("\n").append(context.getTranslatedString(R.string.import_skipped, skipCount))
                    }
                )))
                
                // Refresh segments after import
                loadSegmentsForEpisodes(currentState.episodesBySeason)
                
            } catch (e: Exception) {
                Log.e("SeriesViewModel", "Error importing JSON", e)
                _events.emit(SeriesEvent.ShowToast(UiText.DynamicString("Error: ${e.message}")))
                _uiState.update { currentState.copy(isLoadingSegments = false) }
            }
        }
    }

    fun refresh(seriesId: String) {
        loadSeries(seriesId)
    }
}
