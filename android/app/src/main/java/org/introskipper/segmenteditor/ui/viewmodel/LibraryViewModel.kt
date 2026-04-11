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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

private const val BATCH_SIZE = 250
private const val MAX_CONCURRENT_SERIES = 2
private const val MAX_CONCURRENT_EPISODE_SEGMENTS = 48

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

    fun shareLibrarySegments(libraryId: String, collectionType: String?) =
        launchLibraryOperation(
            libraryId = libraryId,
            collectionType = collectionType,
            errorRes = R.string.share_failed_collection_generic,
            onTvShows = { userId -> shareLibraryTvShows(libraryId, userId) },
            onMovies  = { userId -> shareLibraryMovies(libraryId, userId) }
        )

    private suspend fun shareLibraryTvShows(libraryId: String, userId: String) {
        val seriesResponse = mediaRepository.getSeries(
            userId = userId, 
            parentId = libraryId,
            fields = listOf("ProviderIds")
        )
        if (!seriesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
            return
        }

        val allSeries = seriesResponse.body()?.items ?: emptyList()
        var totalSubmitted = 0
        var firstFailCode: Int? = null
        
        val semaphore = Semaphore(MAX_CONCURRENT_SERIES)

        allSeries.forEachIndexed { seriesIndex, series ->
            val seriesRequests = semaphore.withPermit {
                val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                val episodesResponse = mediaRepository.getEpisodes(
                    seriesId = series.id,
                    userId = userId,
                    fields = JellyfinApiService.SHARING_FIELDS
                )
                if (!episodesResponse.isSuccessful) return@withPermit emptyList()

                val seasonsResponse = mediaRepository.getSeasons(series.id, userId, fields = listOf("ProviderIds"))
                val seasonTvdbIds = seasonsResponse.body()?.items
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

            if (seriesRequests.isNotEmpty()) {
                val response = skipMeApiService.submitSeason(seriesRequests)
                if (response.isSuccessful) {
                    totalSubmitted += response.body()?.submitted ?: 0
                } else if (firstFailCode == null) {
                    firstFailCode = response.code()
                }
            }
            updateSharingProgress((seriesIndex + 1f) / allSeries.size.coerceAtLeast(1))
        }

        reportResult(totalSubmitted, firstFailCode, R.string.share_success_collection, R.string.share_no_segments_found)
    }

    private suspend fun shareLibraryMovies(libraryId: String, userId: String) {
        val moviesResponse = mediaRepository.getMovies(
            userId = userId, 
            parentId = libraryId,
            fields = listOf("ProviderIds", "RunTimeTicks")
        )
        if (!moviesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_no_segments_found)))
            return
        }

        val allMovies = moviesResponse.body()?.items ?: emptyList()
        var totalSubmitted = 0
        var firstFailCode: Int? = null
        val semaphore = Semaphore(MAX_CONCURRENT_EPISODE_SEGMENTS)
        
        val currentBatch = mutableListOf<SkipMeSubmitRequest>()

        allMovies.forEachIndexed { movieIndex, movie ->
            val segments = semaphore.withPermit {
                val tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull() ?: return@withPermit emptyList<SkipMeSubmitRequest>()
                val durationMs = movie.runTimeTicks?.div(10_000) ?: return@withPermit emptyList()
                if (durationMs <= 0) return@withPermit emptyList()

                val segmentResult = segmentRepository.getSegmentsResult(movie.id)
                segmentResult.getOrNull()?.mapNotNull { segment ->
                    val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType() ?: return@mapNotNull null
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
                } ?: emptyList()
            }
            
            currentBatch.addAll(segments)
            
            if (currentBatch.size >= BATCH_SIZE) {
                val response = skipMeApiService.submitCollection(currentBatch.toList())
                if (response.isSuccessful) {
                    totalSubmitted += response.body()?.submitted ?: 0
                } else if (firstFailCode == null) {
                    firstFailCode = response.code()
                }
                currentBatch.clear()
            }
            updateSharingProgress((movieIndex + 1f) / allMovies.size.coerceAtLeast(1))
        }

        if (currentBatch.isNotEmpty()) {
            val response = skipMeApiService.submitCollection(currentBatch)
            if (response.isSuccessful) {
                totalSubmitted += response.body()?.submitted ?: 0
            } else if (firstFailCode == null) {
                firstFailCode = response.code()
            }
        }

        reportResult(totalSubmitted, firstFailCode, R.string.share_success_collection, R.string.share_no_segments_found)
    }

    fun submitLibraryMetadata(libraryId: String, collectionType: String?) =
        launchLibraryOperation(
            libraryId = libraryId,
            collectionType = collectionType,
            errorRes = R.string.backfill_failed_generic,
            onTvShows = { userId -> submitLibraryTvShowsMetadata(libraryId, userId) },
            onMovies  = { userId -> submitLibraryMoviesMetadata(libraryId, userId) }
        )

    private suspend fun submitLibraryTvShowsMetadata(libraryId: String, userId: String) {
        val seriesResponse = mediaRepository.getSeries(
            userId = userId, 
            parentId = libraryId,
            fields = listOf("ProviderIds")
        )
        if (!seriesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
            return
        }

        val allSeries = seriesResponse.body()?.items ?: emptyList()
        var totalUpdated = 0
        var firstFailCode: Int? = null
        val semaphore = Semaphore(MAX_CONCURRENT_SERIES)

        allSeries.forEachIndexed { seriesIndex, series ->
            val requests = semaphore.withPermit {
                val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                val episodesResponse = mediaRepository.getEpisodes(
                    seriesId = series.id,
                    userId = userId,
                    fields = listOf("ProviderIds", "ParentIndexNumber", "IndexNumber")
                )
                if (!episodesResponse.isSuccessful) return@withPermit emptyList()

                val seasonsResponse = mediaRepository.getSeasons(series.id, userId, fields = listOf("ProviderIds"))
                val seasonTvdbIds = seasonsResponse.body()?.items
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

            if (requests.isNotEmpty()) {
                for (chunk in requests.chunked(BATCH_SIZE)) {
                    val response = skipMeApiService.backfill(chunk)
                    if (response.isSuccessful) {
                        totalUpdated += response.body()?.updated ?: 0
                    } else if (firstFailCode == null) {
                        firstFailCode = response.code()
                    }
                }
            }
            updateSharingProgress((seriesIndex + 1f) / allSeries.size.coerceAtLeast(1))
        }

        reportResult(totalUpdated, firstFailCode, R.string.backfill_success, R.string.backfill_no_identifiers)
    }

    private suspend fun submitLibraryMoviesMetadata(libraryId: String, userId: String) {
        val moviesResponse = mediaRepository.getMovies(
            userId = userId, 
            parentId = libraryId,
            fields = listOf("ProviderIds")
        )
        if (!moviesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
            return
        }

        val requests = (moviesResponse.body()?.items ?: emptyList()).mapNotNull { movie ->
            val tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull() ?: return@mapNotNull null
            SkipMeBackfillRequest(tmdbId = tmdbId)
        }

        var totalUpdated = 0
        var firstFailCode: Int? = null
        val chunks = requests.chunked(BATCH_SIZE)
        chunks.forEachIndexed { chunkIndex, chunk ->
            val response = skipMeApiService.backfill(chunk)
            if (response.isSuccessful) {
                totalUpdated += response.body()?.updated ?: 0
            } else if (firstFailCode == null) {
                firstFailCode = response.code()
            }
            updateSharingProgress((chunkIndex + 1f) / chunks.size.coerceAtLeast(1))
        }

        reportResult(totalUpdated, firstFailCode, R.string.backfill_success, R.string.backfill_no_identifiers)
    }

    private fun launchLibraryOperation(
        libraryId: String,
        collectionType: String?,
        errorRes: Int,
        onTvShows: suspend (userId: String) -> Unit,
        onMovies: suspend (userId: String) -> Unit
    ) {
        val currentState = _uiState.value as? LibraryUiState.Success ?: return
        if (currentState.isSharingLibraryId != null) return
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
                _uiState.update { (it as? LibraryUiState.Success)?.copy(isSharingLibraryId = null, sharingProgress = null) ?: it }
            }
        }
    }

    private suspend fun reportResult(count: Int, failCode: Int?, successRes: Int, emptyRes: Int) {
        when {
            count > 0 -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(successRes, count)))
            failCode != null -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.share_failed_http, failCode)))
            else -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(emptyRes)))
        }
    }

    private suspend fun submitRequestsSequentially(
        requests: List<SkipMeSubmitRequest>
    ): Pair<Int, Int?> {
        var submitted = 0
        var firstFailCode: Int? = null
        for (request in requests) {
            val response = skipMeApiService.submitSegment(request)
            if (response.isSuccessful) {
                submitted++
            } else if (firstFailCode == null) {
                firstFailCode = response.code()
            }
        }
        return Pair(submitted, firstFailCode)
    }

    private fun updateSharingProgress(progress: Float) {
        _uiState.update { state ->
            (state as? LibraryUiState.Success)?.copy(sharingProgress = progress) ?: state
        }
    }

    private suspend fun buildSeasonRequests(
        episodes: List<MediaItem>,
        tvdbSeriesId: Int?,
        tmdbId: Int?,
        aniListId: Int?,
        tvdbSeasonIdFor: (MediaItem) -> Int?
    ): List<SkipMeSeasonSubmitRequest> = coroutineScope {
        if (tvdbSeriesId == null && tmdbId == null && aniListId == null) return@coroutineScope emptyList()

        data class SeasonKey(val seasonNumber: Int?, val tvdbSeasonId: Int?)
        val itemsBySeason = mutableMapOf<SeasonKey, MutableSet<SkipMeSeasonItem>>()

        val semaphore = Semaphore(MAX_CONCURRENT_EPISODE_SEGMENTS)

        val deferredResults = episodes.map { episode ->
            async {
                semaphore.withPermit {
                    val segments = segmentRepository.getSegmentsResult(episode.id).getOrNull() ?: return@withPermit null
                    val tvdbEpisodeId = episode.providerIds?.get("Tvdb")?.toIntOrNull()
                    val durationMs = episode.runTimeTicks?.div(10_000) ?: return@withPermit null
                    if (durationMs <= 0) return@withPermit null

                    val key = SeasonKey(episode.parentIndexNumber, tvdbSeasonIdFor(episode))
                    val items = segments.mapNotNull { segment ->
                        val skipMeType = SegmentType.fromString(segment.type)?.toSkipMeSegmentType() ?: return@mapNotNull null
                        val startMs = segment.startTicks / 10_000
                        val endMs = segment.endTicks / 10_000
                        if (startMs in 0..<endMs && endMs <= durationMs) {
                            SkipMeSeasonItem(
                                tvdbId = tvdbEpisodeId,
                                episode = episode.indexNumber,
                                segment = skipMeType,
                                durationMs = durationMs,
                                startMs = startMs,
                                endMs = endMs
                            )
                        } else null
                    }
                    if (items.isEmpty()) null else key to items
                }
            }
        }

        deferredResults.awaitAll().filterNotNull().forEach { (key, items) ->
            itemsBySeason.getOrPut(key) { mutableSetOf() }.addAll(items)
        }

        itemsBySeason.map { (key, items) ->
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
        val isSharingLibraryId: String? = null,
        val sharingProgress: Float? = null
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
