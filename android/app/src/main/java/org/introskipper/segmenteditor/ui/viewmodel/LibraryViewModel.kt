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
import kotlinx.coroutines.coroutineScope
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
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.SegmentType
import org.introskipper.segmenteditor.data.model.SkipMeBackfillRequest
import org.introskipper.segmenteditor.data.model.SkipMeSeasonItem
import org.introskipper.segmenteditor.data.model.SkipMeSeasonSubmitRequest
import org.introskipper.segmenteditor.data.model.SkipMeSubmitRequest
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.util.UiText
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val jellyfinApiService: JellyfinApiService,
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val skipMeApiService: SkipMeApiService,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var lastHiddenLibraryIds: Set<String> = emptySet()

    private val hiddenLibraryIds: Set<String>
        get() = securePreferences.getHiddenLibraryIds()

    init {
        loadLibraries()
    }

    fun refresh() {
        loadLibraries()
    }
    
    /**
     * Gets the primary image URL for a library
     * @param itemId The ID of the library item (must not be blank)
     * @param imageTag The image tag for the primary image
     * @param maxWidth Optional maximum width for the image (default: 800px)
     * @return The URL string for the primary image
     * @throws IllegalArgumentException if itemId is blank (empty or whitespace)
     */
    fun getPrimaryImageUrl(itemId: String, imageTag: String, maxWidth: Int = 800): String {
        require(itemId.isNotBlank()) { "itemId must not be blank" }
        return jellyfinApiService.getPrimaryImageUrl(
            itemId = itemId,
            imageTag = imageTag,
            maxWidth = maxWidth
        )
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                val libraries = jellyfinRepository.getLibraries()
                val hiddenLibraryIds = securePreferences.getHiddenLibraryIds()
                
                val libraryList = libraries
                    .filter { !hiddenLibraryIds.contains(it.id) }
                    .map { mediaItem ->
                        Library(
                            id = mediaItem.id,
                            name = mediaItem.name ?: "Unknown Library",
                            collectionType = mediaItem.collectionType,
                            primaryImageTag = mediaItem.imageTags?.get("Primary")
                        )
                    }
                
                _uiState.value = if (libraryList.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Success(libraryList)
                }
            } catch (e: Exception) {
                _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refreshIfLibrariesChanged() {
        val currentLibraries = hiddenLibraryIds
        if (lastHiddenLibraryIds != currentLibraries) {
            loadLibraries()
            lastHiddenLibraryIds = currentLibraries
        }
    }

    /**
     * Shares all segments for the entire library to SkipMe.db.
     * TV show libraries submit via season batches; movie libraries submit per segment.
     */
    fun shareLibrarySegments(libraryId: String, collectionType: String?) =
        launchLibraryOperation(
            libraryId = libraryId,
            collectionType = collectionType,
            errorRes = R.string.share_failed_collection_generic,
            onTvShows = { userId -> shareLibraryTvShows(libraryId, userId) },
            onMovies  = { userId -> shareLibraryMovies(libraryId, userId) }
        )

    private suspend fun shareLibraryTvShows(libraryId: String, userId: String) {
        val seriesResponse = mediaRepository.getSeries(userId = userId, parentId = libraryId)
        if (!seriesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
            return
        }

        // Fetch episodes + seasons for all series in parallel, then build season requests
        val allRequests = coroutineScope {
            (seriesResponse.body()?.items ?: emptyList()).map { series ->
                async {
                    val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                    val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                    val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                    val episodesDeferred = async {
                        mediaRepository.getEpisodes(
                            seriesId = series.id,
                            userId = userId,
                            fields = JellyfinApiService.EPISODE_FIELDS
                        )
                    }
                    val seasonsDeferred = async {
                        mediaRepository.getSeasons(series.id, userId, fields = listOf("ProviderIds"))
                    }
                    val episodesResponse = episodesDeferred.await()
                    if (!episodesResponse.isSuccessful) return@async emptyList()

                    val seasonTvdbIds = seasonsDeferred.await().body()?.items
                        ?.associate { it.id to it.providerIds?.get("Tvdb")?.toIntOrNull() } ?: emptyMap()
                    val episodes = (episodesResponse.body()?.items ?: emptyList())
                        .filter { (it.parentIndexNumber ?: 0) != 0 }

                    buildSeasonRequests(
                        episodes = episodes,
                        tvdbSeriesId = seriesTvdbId,
                        tmdbId = seriesTmdbId,
                        aniListId = seriesAniListId,
                        tvdbSeasonIdFor = { episode -> seasonTvdbIds[episode.seasonId ?: ""] }
                    )
                }
            }.awaitAll().flatten()
        }

        if (allRequests.isEmpty()) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
            return
        }

        val response = skipMeApiService.submitSeason(allRequests)
        if (response.isSuccessful) {
            val count = response.body()?.submitted ?: 0
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_success_collection, count)))
        } else {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, response.code())))
        }
    }

    private suspend fun shareLibraryMovies(libraryId: String, userId: String) {
        val moviesResponse = mediaRepository.getMovies(userId = userId, parentId = libraryId)
        if (!moviesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
            return
        }

        // Fetch segments for all movies concurrently, then submit sequentially
        val allRequests = coroutineScope {
            (moviesResponse.body()?.items ?: emptyList()).mapNotNull { movie ->
                val tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull() ?: return@mapNotNull null
                val durationMs = movie.runTimeTicks?.div(10_000) ?: return@mapNotNull null
                if (durationMs <= 0) return@mapNotNull null
                async {
                    val segments = segmentRepository.getSegmentsResult(movie.id).getOrNull()
                        ?: return@async emptyList<SkipMeSubmitRequest>()
                    segments.mapNotNull { segment ->
                        val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType()
                            ?: return@mapNotNull null
                        val startMs = segment.startTicks / 10_000
                        val endMs = segment.endTicks / 10_000
                        if (startMs >= 0 && endMs > startMs && endMs <= durationMs) {
                            SkipMeSubmitRequest(
                                tmdbId = tmdbId,
                                segment = skipMeType,
                                durationMs = durationMs,
                                startMs = startMs,
                                endMs = endMs
                            )
                        } else null
                    }
                }
            }.awaitAll().flatten()
        }

        val (submitted, firstFailCode) = submitRequestsSequentially(allRequests)

        when {
            submitted > 0 -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_success_collection, submitted)))
            firstFailCode != null -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, firstFailCode)))
            else -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
        }
    }

    /**
     * Submits metadata backfill for the entire library to SkipMe.db.
     */
    fun submitLibraryMetadata(libraryId: String, collectionType: String?) =
        launchLibraryOperation(
            libraryId = libraryId,
            collectionType = collectionType,
            errorRes = R.string.backfill_failed_generic,
            onTvShows = { userId -> submitLibraryTvShowsMetadata(libraryId, userId) },
            onMovies  = { userId -> submitLibraryMoviesMetadata(libraryId, userId) }
        )

    private suspend fun submitLibraryTvShowsMetadata(libraryId: String, userId: String) {
        val seriesResponse = mediaRepository.getSeries(userId = userId, parentId = libraryId)
        if (!seriesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
            return
        }

        // Fetch episodes + seasons for all series in parallel, then accumulate backfill requests
        val requests = coroutineScope {
            (seriesResponse.body()?.items ?: emptyList()).map { series ->
                async {
                    val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                    val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                    val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                    val episodesDeferred = async {
                        mediaRepository.getEpisodes(
                            seriesId = series.id,
                            userId = userId,
                            fields = listOf("ProviderIds", "ParentIndexNumber", "IndexNumber")
                        )
                    }
                    val seasonsDeferred = async {
                        mediaRepository.getSeasons(series.id, userId, fields = listOf("ProviderIds"))
                    }
                    val episodesResponse = episodesDeferred.await()
                    if (!episodesResponse.isSuccessful) return@async emptyList()

                    val seasonTvdbIds = seasonsDeferred.await().body()?.items
                        ?.associate { it.id to it.providerIds?.get("Tvdb")?.toIntOrNull() } ?: emptyMap()

                    (episodesResponse.body()?.items ?: emptyList())
                        .filter { (it.parentIndexNumber ?: 0) != 0 }
                        .map { episode ->
                            SkipMeBackfillRequest(
                                tvdbId = episode.providerIds?.get("Tvdb")?.toIntOrNull(),
                                tmdbId = seriesTmdbId,
                                tvdbSeasonId = seasonTvdbIds[episode.seasonId ?: ""],
                                tvdbSeriesId = seriesTvdbId,
                                aniListId = if (episode.parentIndexNumber == 1) seriesAniListId else null,
                                season = episode.parentIndexNumber,
                                episode = episode.indexNumber
                            )
                        }
                }
            }.awaitAll().flatten()
        }

        if (requests.isEmpty()) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
            return
        }

        val response = skipMeApiService.backfill(requests)
        if (response.isSuccessful) {
            val updated = response.body()?.updated ?: 0
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updated)))
        } else {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_http, response.code())))
        }
    }

    private suspend fun submitLibraryMoviesMetadata(libraryId: String, userId: String) {
        val moviesResponse = mediaRepository.getMovies(userId = userId, parentId = libraryId)
        if (!moviesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
            return
        }

        val requests = (moviesResponse.body()?.items ?: emptyList()).mapNotNull { movie ->
            val tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull() ?: return@mapNotNull null
            SkipMeBackfillRequest(tmdbId = tmdbId)
        }

        if (requests.isEmpty()) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
            return
        }

        val response = skipMeApiService.backfill(requests)
        if (response.isSuccessful) {
            val updated = response.body()?.updated ?: 0
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updated)))
        } else {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_http, response.code())))
        }
    }

    /**
     * Shared scaffolding for library-level SkipMe.db operations.
     * Handles userId resolution, collectionType dispatch, isSharingLibraryId lifecycle,
     * and uniform error reporting so that both segment and metadata flows stay consistent.
     */
    private fun launchLibraryOperation(
        libraryId: String,
        collectionType: String?,
        errorRes: Int,
        onTvShows: suspend (userId: String) -> Unit,
        onMovies: suspend (userId: String) -> Unit
    ) {
        val currentState = _uiState.value as? LibraryUiState.Success ?: return
        _uiState.update { currentState.copy(isSharingLibraryId = libraryId) }

        viewModelScope.launch {
            try {
                val userId = securePreferences.getUserId() ?: run {
                    _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.auth_error_not_authenticated)))
                    return@launch
                }
                when (collectionType) {
                    "tvshows" -> onTvShows(userId)
                    "movies"  -> onMovies(userId)
                    else -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_unsupported_type)))
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error in library operation ($collectionType)", e)
                _events.emit(LibraryEvent.ShowToast(UiText.StringResource(errorRes, e.message ?: "")))
            } finally {
                _uiState.update { (it as? LibraryUiState.Success)?.copy(isSharingLibraryId = null) ?: it }
            }
        }
    }

    /**
     * Submits each [SkipMeSubmitRequest] sequentially, counting successes and recording
     * the first HTTP failure code. Sequential submission is intentional to avoid
     * hammering the SkipMe.db API.
     */
    private suspend fun submitRequestsSequentially(
        requests: List<SkipMeSubmitRequest>
    ): Pair<Int, Int?> {
        var submitted = 0
        var firstFailCode: Int? = null
        for (request in requests) {
            val response = skipMeApiService.submitSegment(request)
            if (response.isSuccessful) submitted++ else if (firstFailCode == null) firstFailCode = response.code()
        }
        return Pair(submitted, firstFailCode)
    }

    private suspend fun buildSeasonRequests(
        episodes: List<MediaItem>,
        tvdbSeriesId: Int?,
        tmdbId: Int?,
        aniListId: Int?,
        tvdbSeasonIdFor: (MediaItem) -> Int?
    ): List<SkipMeSeasonSubmitRequest> {
        if (tvdbSeriesId == null && tmdbId == null && aniListId == null) return emptyList()

        data class SeasonKey(val seasonNumber: Int?, val tvdbSeasonId: Int?)

        // Fetch segments for all episodes in parallel, then group by season
        val pairs: List<Pair<SeasonKey, SkipMeSeasonItem>> = coroutineScope {
            episodes.map { episode ->
                async {
                    val segments = segmentRepository.getSegmentsResult(episode.id).getOrNull()
                        ?: return@async emptyList<Pair<SeasonKey, SkipMeSeasonItem>>()
                    val tvdbEpisodeId = episode.providerIds?.get("Tvdb")?.toIntOrNull()
                    val durationMs = episode.runTimeTicks?.div(10_000) ?: return@async emptyList()
                    if (durationMs <= 0) return@async emptyList()

                    val key = SeasonKey(episode.parentIndexNumber, tvdbSeasonIdFor(episode))
                    segments.mapNotNull { segment ->
                        val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType()
                            ?: return@mapNotNull null
                        val startMs = segment.startTicks / 10_000
                        val endMs = segment.endTicks / 10_000
                        if (startMs >= 0 && endMs > startMs && endMs <= durationMs) {
                            key to SkipMeSeasonItem(
                                tvdbId = tvdbEpisodeId,
                                episode = episode.indexNumber,
                                segment = skipMeType,
                                durationMs = durationMs,
                                startMs = startMs,
                                endMs = endMs
                            )
                        } else null
                    }
                }
            }.awaitAll().flatten()
        }

        val itemsBySeason = mutableMapOf<SeasonKey, MutableSet<SkipMeSeasonItem>>()
        for ((key, item) in pairs) {
            itemsBySeason.getOrPut(key) { mutableSetOf() }.add(item)
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

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Empty : LibraryUiState()
    data class Success(
        val libraries: List<Library>,
        val isSharingLibraryId: String? = null
    ) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

sealed class LibraryEvent {
    data class ShowToast(val message: UiText) : LibraryEvent()
}

data class Library(
    val id: String,
    val name: String,
    val collectionType: String?,
    val primaryImageTag: String? = null
)
