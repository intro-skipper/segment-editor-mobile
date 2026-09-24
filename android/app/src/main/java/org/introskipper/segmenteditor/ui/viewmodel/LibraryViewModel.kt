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
import org.introskipper.segmenteditor.data.export.SegmentExportItem
import org.introskipper.segmenteditor.data.export.SegmentExporter
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.util.UiText
import javax.inject.Inject

private const val MAX_CONCURRENT_SERIES = 4
private const val MAX_CONCURRENT_MOVIE_SEGMENTS = 16

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val jellyfinApiService: JellyfinApiService,
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val segmentExporter: SegmentExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var lastHiddenLibraryIds: Set<String> = emptySet()
    private var lastUserId: String? = null

    private val hiddenLibraryIds: Set<String>
        get() = securePreferences.getHiddenLibraryIds()

    init {
        loadLibraries()
    }

    fun refresh(force: Boolean = true) {
        if (force) {
            loadLibraries()
        } else {
            refreshIfLibrariesChanged()
        }
    }
    
    fun getPrimaryImageUrl(itemId: String, imageTag: String, maxWidth: Int = 800): String {
        require(itemId.isNotBlank()) { "itemId must not be blank" }
        return jellyfinApiService.getPrimaryImageUrl(
            itemId = itemId,
            imageTag = imageTag,
            maxWidth = maxWidth
        )
    }

    fun markContinueWatchingAsWatched(itemId: String) {
        val userId = securePreferences.getUserId() ?: run {
            viewModelScope.launch {
                _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.auth_error_not_authenticated)))
            }
            return
        }

        viewModelScope.launch {
            try {
                val response = mediaRepository.markItemPlayed(itemId = itemId, userId = userId)
                if (response.isSuccessful) {
                    _uiState.update { state ->
                        (state as? LibraryUiState.Success)?.let { success ->
                            success.copy(
                                continueWatching = success.continueWatching.filterNot { it.id == itemId }
                            )
                        } ?: state
                    }
                    // Reconcile both watch lists with Jellyfin before finishing
                    // the action so Next Up is refreshed at the same time.
                    val currentState = _uiState.value
                    if (currentState is LibraryUiState.Success) {
                        refreshWatchLists(currentState.libraries)
                    }
                    _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.library_marked_watched)))
                } else {
                    _events.emit(
                        LibraryEvent.ShowToast(
                            UiText.StringResource(R.string.library_mark_watched_failed, response.code())
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Failed to mark item as watched", e)
                _events.emit(
                    LibraryEvent.ShowToast(
                        UiText.StringResource(R.string.library_mark_watched_failed_generic)
                    )
                )
            }
        }
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                val libraries = jellyfinRepository.getLibraries()
                val hiddenLibraryIds = securePreferences.getHiddenLibraryIds()
                
                // Track current state for conditional refreshes
                lastHiddenLibraryIds = hiddenLibraryIds
                lastUserId = securePreferences.getUserId()

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

                val continueWatching = fetchContinueWatching(libraryList)
                val nextUp = fetchNextUp(libraryList)
                
                _uiState.value = if (libraryList.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Success(
                        libraries = libraryList,
                        continueWatching = continueWatching,
                        nextUp = nextUp
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun fetchContinueWatching(libraryList: List<Library>): List<ContinueWatchingItem> {
        return if (
            !securePreferences.getIsApiKeyLogin() || securePreferences.getHasExplicitUserSelection()
        ) {
            val userId = securePreferences.getUserId()
            if (userId != null && libraryList.isNotEmpty()) {
                // Fetch resume items per visible library in parallel so that items from
                // hidden libraries are never included.
                coroutineScope {
                    libraryList
                        .map { library ->
                            async {
                                runCatching {
                                    mediaRepository.getContinueWatching(
                                        userId = userId,
                                        limit = 20,
                                        parentId = library.id
                                    )
                                }.getOrNull()
                                    ?.takeIf { it.isSuccessful }
                                    ?.body()
                                    ?.items
                                    ?: emptyList()
                                }
                            }
                            .awaitAll()
                    }
                    .flatten()
                    // Deduplicate by item ID (an item can only belong to one library)
                    .distinctBy { it.id }
                    // Sort by last played descending
                    .sortedByDescending { it.userData?.lastPlayedDate }
                    .mapNotNull { item ->
                        val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
                        if (playbackPositionTicks <= 0L) return@mapNotNull null
                        ContinueWatchingItem(
                            id = item.id,
                            name = item.name ?: "Unknown",
                            type = item.type,
                            seriesName = item.seriesName,
                            seasonNumber = item.parentIndexNumber,
                            episodeNumber = item.indexNumber,
                            primaryImageTag = item.imageTags?.get("Primary"),
                            playbackPositionTicks = playbackPositionTicks,
                            runTimeTicks = item.runTimeTicks ?: 0L
                        )
                    }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private suspend fun fetchNextUp(libraryList: List<Library>): List<ContinueWatchingItem> {
        return if (
            !securePreferences.getIsApiKeyLogin() || securePreferences.getHasExplicitUserSelection()
        ) {
            val userId = securePreferences.getUserId()
            if (userId != null && libraryList.isNotEmpty()) {
                coroutineScope {
                    libraryList
                        .map { library ->
                            async {
                                runCatching {
                                    mediaRepository.getNextUp(
                                        userId = userId,
                                        limit = 20,
                                        parentId = library.id
                                    )
                                }.getOrNull()
                                    ?.takeIf { it.isSuccessful }
                                    ?.body()
                                    ?.items
                                    ?: emptyList()
                            }
                        }
                        .awaitAll()
                }
                    .flatten()
                    .distinctBy { it.id }
                    .sortedByDescending { it.userData?.lastPlayedDate }
                    .map { item ->
                        ContinueWatchingItem(
                            id = item.id,
                            name = item.name ?: "Unknown",
                            type = item.type,
                            seriesName = item.seriesName,
                            seasonNumber = item.parentIndexNumber,
                            episodeNumber = item.indexNumber,
                            primaryImageTag = item.imageTags?.get("Primary"),
                            playbackPositionTicks = 0L,
                            runTimeTicks = item.runTimeTicks ?: 0L
                        )
                    }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Refreshes the library list only if the user or hidden libraries have changed.
     * Always refreshes the Continue Watching and Next Up sections if the UI state is Success.
     */
    fun refreshIfLibrariesChanged() {
        val currentHidden = hiddenLibraryIds
        val currentUserId = securePreferences.getUserId()
        val librariesChanged = lastHiddenLibraryIds != currentHidden || lastUserId != currentUserId
        
        if (librariesChanged) {
            loadLibraries()
        } else {
            val currentState = _uiState.value
            if (currentState is LibraryUiState.Success) {
                viewModelScope.launch {
                    try {
                        refreshWatchLists(currentState.libraries)
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "Failed to refresh continue watching and next up", e)
                    }
                }
            }
        }
    }

    private suspend fun refreshWatchLists(libraries: List<Library>) {
        val updatedContinueWatching = fetchContinueWatching(libraries)
        val updatedNextUp = fetchNextUp(libraries)
        _uiState.update { state ->
            if (state is LibraryUiState.Success) {
                state.copy(
                    continueWatching = updatedContinueWatching,
                    nextUp = updatedNextUp
                )
            } else state
        }
    }

    fun shareLibrarySegments(libraryId: String, collectionType: String?) =
        launchLibraryOperation(
            libraryId = libraryId,
            collectionType = collectionType,
            errorRes = R.string.export_failed_collection,
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
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.export_no_segments)))
            return
        }

        val allSeries = seriesResponse.body()?.items.orEmpty()
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENT_SERIES)
        val exportItems = coroutineScope {
            allSeries.map { series ->
                async {
                    semaphore.withPermit {
                        val episodesResponse = mediaRepository.getEpisodes(
                            seriesId = series.id,
                            userId = userId,
                            fields = JellyfinApiService.SHARING_FIELDS
                        )
                        val episodes = if (episodesResponse.isSuccessful) {
                            episodesResponse.body()?.items.orEmpty()
                                .filter { (it.parentIndexNumber ?: 0) != 0 }
                        } else {
                            emptyList()
                        }
                        episodes.flatMap { episode ->
                            segmentRepository.getSegmentsResult(episode.id).getOrNull().orEmpty()
                                .map { segment -> SegmentExportItem.from(segment, episode, series) }
                        }
                    }.also {
                        updateSharingProgress(completed.incrementAndGet().toFloat() / allSeries.size.coerceAtLeast(1))
                    }
                }
            }.awaitAll().flatten()
        }

        if (exportItems.isEmpty()) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.export_no_segments)))
        } else {
            _events.emit(
                LibraryEvent.ShareExport(
                    segmentExporter.export(exportItems, "$libraryId")
                )
            )
        }
    }

    private suspend fun shareLibraryMovies(libraryId: String, userId: String) {
        val moviesResponse = mediaRepository.getMovies(
            userId = userId,
            parentId = libraryId,
            fields = listOf("ProviderIds", "RunTimeTicks")
        )
        if (!moviesResponse.isSuccessful) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.export_no_segments)))
            return
        }

        val allMovies = moviesResponse.body()?.items.orEmpty()
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENT_MOVIE_SEGMENTS)
        val exportItems = coroutineScope {
            allMovies.map { movie ->
                async {
                    semaphore.withPermit {
                        segmentRepository.getSegmentsResult(movie.id).getOrNull().orEmpty()
                            .map { segment -> SegmentExportItem.from(segment, movie) }
                    }.also {
                        updateSharingProgress(completed.incrementAndGet().toFloat() / allMovies.size.coerceAtLeast(1))
                    }
                }
            }.awaitAll().flatten()
        }

        if (exportItems.isEmpty()) {
            _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.export_no_segments)))
        } else {
            _events.emit(
                LibraryEvent.ShareExport(
                    segmentExporter.export(exportItems, "$libraryId")
                )
            )
        }
    }

    private fun launchLibraryOperation(
        libraryId: String,
        collectionType: String?,
        errorRes: Int,
        onTvShows: suspend (userId: String) -> Unit,
        onMovies: suspend (userId: String) -> Unit
    ) {
        val currentState = _uiState.value as? LibraryUiState.Success ?: return
        if (currentState.isSharingLibraryId != null) {
            viewModelScope.launch {
                _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.export_already_in_progress)))
            }
            return
        }
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
                    else -> _events.emit(LibraryEvent.ShowToast(UiText.StringResource(R.string.export_unsupported_type)))
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error in library operation ($collectionType)", e)
                _events.emit(LibraryEvent.ShowToast(UiText.StringResource(errorRes, e.message ?: "")))
            } finally {
                _uiState.update { (it as? LibraryUiState.Success)?.copy(isSharingLibraryId = null, sharingProgress = null) ?: it }
            }
        }
    }

    private fun updateSharingProgress(progress: Float) {
        _uiState.update { state ->
            (state as? LibraryUiState.Success)?.copy(sharingProgress = progress) ?: state
        }
    }

}

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Empty : LibraryUiState()
    data class Success(
        val libraries: List<Library>,
        val continueWatching: List<ContinueWatchingItem> = emptyList(),
        val nextUp: List<ContinueWatchingItem> = emptyList(),
        val isSharingLibraryId: String? = null,
        val sharingProgress: Float? = null
    ) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

sealed class LibraryEvent {
    data class ShowToast(val message: UiText) : LibraryEvent()
    data class ShareExport(val file: org.introskipper.segmenteditor.data.export.SegmentExportFile) : LibraryEvent()
}

data class Library(
    val id: String,
    val name: String,
    val collectionType: String?,
    val primaryImageTag: String? = null
)

data class ContinueWatchingItem(
    val id: String,
    val name: String,
    val type: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val primaryImageTag: String?,
    val playbackPositionTicks: Long,
    val runTimeTicks: Long
) {
    val progress: Float
        get() = if (runTimeTicks <= 0L) {
            0f
        } else {
            (playbackPositionTicks.toDouble() / runTimeTicks.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
}
