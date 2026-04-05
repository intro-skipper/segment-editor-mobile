/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import android.media.MediaCodecList
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.api.SkipMeApiService
import org.introskipper.segmenteditor.data.model.MediaItemType
import org.introskipper.segmenteditor.data.model.SkipMeBackfillRequest
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import org.introskipper.segmenteditor.ui.util.UiText
import org.introskipper.segmenteditor.utils.TranslationService
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicTranslationEnabled: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val preferDirectPlay: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val preferLocalPreviews: Boolean = false,
    val disableSkipMeSegments: Boolean = true,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val prettyPrintJson: Boolean = true,
    val itemsPerPage: Int = 20,
    val hiddenLibraryIds: Set<String> = emptySet(),
    val availableLibraries: List<LibraryInfo> = emptyList(),
    val isLoadingLibraries: Boolean = false,
    val currentLocaleName: String = "",
    val serverUrl: String = "",
    val serverVersion: String = "",
    val serverName: String = "",
    val supportedVideoCodecs: List<String> = emptyList(),
    val supportedAudioCodecs: List<String> = emptyList(),
    val showBackfillDialog: Boolean = false,
    val mediaForBackfill: List<MediaInfo> = emptyList(),
    val isLoadingMediaForBackfill: Boolean = false,
    val isSubmittingBackfill: Boolean = false
)

data class LibraryInfo(
    val id: String,
    val name: UiText
)

data class MediaInfo(
    val id: String,
    val name: String,
    val type: MediaItemType
)

sealed class SettingsEvent {
    data class ShowToast(val message: UiText) : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val jellyfinRepository: JellyfinRepository,
    private val authRepository: AuthRepository,
    private val translationService: TranslationService,
    private val skipMeApiService: SkipMeApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadPreferences()
        loadAvailableLibraries()
        loadServerInfo()
        loadSupportedCodecs()
        
        // Keep UI state in sync with translation service
        viewModelScope.launch {
            translationService.isDynamicTranslationEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(dynamicTranslationEnabled = enabled)
            }
        }
        
        viewModelScope.launch {
            translationService.isDownloadingModel.collectLatest { isDownloading ->
                _uiState.value = _uiState.value.copy(isDownloadingModel = isDownloading)
            }
        }
    }

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                theme = securePreferences.getTheme(),
                dynamicTranslationEnabled = securePreferences.isDynamicTranslationEnabled(),
                preferDirectPlay = securePreferences.getPreferDirectPlay(),
                autoPlayNextEpisode = securePreferences.getAutoPlayNextEpisode(),
                preferLocalPreviews = securePreferences.getPreferLocalPreviews(),
                disableSkipMeSegments = securePreferences.getDisableSkipMeSegments(),
                exportFormat = securePreferences.getExportFormat(),
                prettyPrintJson = securePreferences.getPrettyPrintJson(),
                itemsPerPage = securePreferences.getItemsPerPage(),
                hiddenLibraryIds = securePreferences.getHiddenLibraryIds(),
                currentLocaleName = translationService.getCurrentLocaleName(),
                serverUrl = securePreferences.getServerUrl() ?: ""
            )
        }
    }

    private fun loadServerInfo() {
        viewModelScope.launch {
            try {
                val response = authRepository.getServerInfo()
                if (response.isSuccessful) {
                    response.body()?.let { info ->
                        _uiState.value = _uiState.value.copy(
                            serverVersion = info.version,
                            serverName = info.serverName
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore errors, info will just be empty
            }
        }
    }

    private fun loadSupportedCodecs() {
        viewModelScope.launch {
            val (video, audio) = withContext(Dispatchers.Default) {
                val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
                val codecInfos = codecList.codecInfos
                
                val videoMimes = mutableSetOf<String>()
                val audioMimes = mutableSetOf<String>()
                
                for (info in codecInfos) {
                    if (info.isEncoder) continue
                    
                    for (type in info.supportedTypes) {
                        if (type.startsWith("video/")) {
                            videoMimes.add(type.substringAfter("video/"))
                        } else if (type.startsWith("audio/")) {
                            audioMimes.add(type.substringAfter("audio/"))
                        }
                    }
                }
                
                // Map to friendly names and sort
                val videoFriendly = videoMimes.map { it.uppercase() }.sorted()
                val audioFriendly = audioMimes.map { it.uppercase() }.sorted()
                
                videoFriendly to audioFriendly
            }
            
            _uiState.value = _uiState.value.copy(
                supportedVideoCodecs = video,
                supportedAudioCodecs = audio
            )
        }
    }

    private fun loadAvailableLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLibraries = true)
            try {
                val libraries = jellyfinRepository.getLibraries()
                val libraryInfos = libraries.map { mediaItem ->
                    LibraryInfo(
                        id = mediaItem.id,
                        name = mediaItem.name?.let { UiText.DynamicString(it) } 
                            ?: UiText.StringResource(R.string.library_unknown)
                    )
                }
                _uiState.value = _uiState.value.copy(
                    availableLibraries = libraryInfos,
                    isLoadingLibraries = false
                )
            } catch (e: Exception) {
                // If loading libraries fails, just keep the empty list
                _uiState.value = _uiState.value.copy(isLoadingLibraries = false)
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        securePreferences.setTheme(theme)
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun setDynamicTranslationEnabled(enabled: Boolean) {
        translationService.setDynamicTranslationEnabled(enabled)
    }

    fun setPreferDirectPlay(enabled: Boolean) {
        securePreferences.setPreferDirectPlay(enabled)
        _uiState.value = _uiState.value.copy(preferDirectPlay = enabled)
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        securePreferences.setAutoPlayNextEpisode(enabled)
        _uiState.value = _uiState.value.copy(autoPlayNextEpisode = enabled)
    }

    fun setPreferLocalPreviews(prefer: Boolean) {
        securePreferences.setPreferLocalPreviews(prefer)
        _uiState.value = _uiState.value.copy(preferLocalPreviews = prefer)
    }

    fun setDisableSkipMeSegments(disabled: Boolean) {
        securePreferences.setDisableSkipMeSegments(disabled)
        _uiState.value = _uiState.value.copy(disableSkipMeSegments = disabled)
    }

    fun setExportFormat(format: ExportFormat) {
        securePreferences.setExportFormat(format)
        _uiState.value = _uiState.value.copy(exportFormat = format)
    }

    fun setPrettyPrintJson(enabled: Boolean) {
        securePreferences.setPrettyPrintJson(enabled)
        _uiState.value = _uiState.value.copy(prettyPrintJson = enabled)
    }

    fun setItemsPerPage(count: Int) {
        securePreferences.setItemsPerPage(count)
        _uiState.value = _uiState.value.copy(itemsPerPage = count)
    }

    fun toggleLibraryVisibility(libraryId: String) {
        val currentHiddenIds = _uiState.value.hiddenLibraryIds.toMutableSet()
        if (currentHiddenIds.contains(libraryId)) {
            currentHiddenIds.remove(libraryId)
        } else {
            currentHiddenIds.add(libraryId)
        }
        securePreferences.setHiddenLibraryIds(currentHiddenIds)
        _uiState.value = _uiState.value.copy(hiddenLibraryIds = currentHiddenIds)
    }

    fun clearAuthenticationAndRestart() {
        securePreferences.clearAuthentication()
    }

    fun showBackfillDialog() {
        _uiState.value = _uiState.value.copy(showBackfillDialog = true)
        loadMediaForBackfill()
    }

    fun dismissBackfillDialog() {
        _uiState.value = _uiState.value.copy(showBackfillDialog = false)
    }

    private fun hasAnyIdentifier(vararg ids: Int?): Boolean = ids.any { it != null }

    private fun loadMediaForBackfill() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMediaForBackfill = true, mediaForBackfill = emptyList())
            try {
                // Use a generous limit; most home servers have far fewer than 1000 series/movies
                val response = jellyfinRepository.getMediaItems(
                    includeItemTypes = listOf("Series", "Movie"),
                    limit = 1000
                )
                val items = response.items
                    .filter { it.type == "Series" || it.type == "Movie" }
                    .mapNotNull { mediaItem ->
                        val type = mediaItem.itemType
                        if (type != MediaItemType.SERIES && type != MediaItemType.MOVIE) return@mapNotNull null
                        MediaInfo(
                            id = mediaItem.id,
                            name = mediaItem.name ?: return@mapNotNull null,
                            type = type
                        )
                    }
                    .sortedBy { it.name }
                _uiState.value = _uiState.value.copy(
                    mediaForBackfill = items,
                    isLoadingMediaForBackfill = false
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to load media for backfill", e)
                _uiState.value = _uiState.value.copy(isLoadingMediaForBackfill = false)
                _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_generic, e.message ?: "")))
            }
        }
    }

    fun submitMetadataForItem(item: MediaInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingBackfill = true, showBackfillDialog = false)
            try {
                val requests = mutableListOf<SkipMeBackfillRequest>()

                when (item.type) {
                    MediaItemType.MOVIE -> {
                        val movie = jellyfinRepository.getMediaItem(item.id)
                        val tvdbId = movie.providerIds?.get("Tvdb")?.toIntOrNull()
                        val tmdbId = movie.providerIds?.get("Tmdb")?.toIntOrNull()
                        if (!hasAnyIdentifier(tvdbId, tmdbId)) {
                            _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                            return@launch
                        }
                        requests.add(SkipMeBackfillRequest(tvdbId = tvdbId, tmdbId = tmdbId))
                    }
                    MediaItemType.SERIES -> {
                        val series = jellyfinRepository.getMediaItem(item.id)
                        val seriesTvdbId = series.providerIds?.get("Tvdb")?.toIntOrNull()
                        val seriesTmdbId = series.providerIds?.get("Tmdb")?.toIntOrNull()
                        val seriesAniListId = series.providerIds?.get("AniList")?.toIntOrNull()

                        if (!hasAnyIdentifier(seriesTvdbId, seriesTmdbId, seriesAniListId)) {
                            _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                            return@launch
                        }

                        val seasonsResponse = jellyfinRepository.getSeasons(item.id)
                        val seasons = seasonsResponse.items.filter { it.indexNumber != 0 }
                        val seasonTvdbIds = seasons.associate { it.id to it.providerIds?.get("Tvdb")?.toIntOrNull() }

                        for (season in seasons) {
                            val pageSize = 100
                            var startIndex = 0
                            var hasMore = true
                            while (hasMore) {
                                val episodesResponse = jellyfinRepository.getEpisodes(
                                    seriesId = item.id,
                                    seasonId = season.id,
                                    startIndex = startIndex,
                                    limit = pageSize
                                )
                                for (episode in episodesResponse.items) {
                                    val tvdbEpisodeId = episode.providerIds?.get("Tvdb")?.toIntOrNull()
                                    val tvdbSeasonId = seasonTvdbIds[episode.seasonId ?: ""]
                                    requests.add(
                                        SkipMeBackfillRequest(
                                            tvdbId = tvdbEpisodeId,
                                            tmdbId = seriesTmdbId,
                                            tvdbSeasonId = tvdbSeasonId,
                                            tvdbSeriesId = seriesTvdbId,
                                            aniListId = seriesAniListId,
                                            season = episode.parentIndexNumber,
                                            episode = episode.indexNumber
                                        )
                                    )
                                }
                                startIndex += episodesResponse.items.size
                                hasMore = episodesResponse.items.isNotEmpty() &&
                                    startIndex < episodesResponse.totalRecordCount
                            }
                        }
                    }
                    else -> {
                        _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                        return@launch
                    }
                }

                if (requests.isEmpty()) {
                    _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_no_identifiers)))
                    return@launch
                }

                val response = skipMeApiService.backfill(requests)
                if (response.isSuccessful) {
                    val updated = response.body()?.updated ?: 0
                    _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_success, updated)))
                } else {
                    _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_http, response.code())))
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error submitting metadata backfill", e)
                val message = e.message ?: ""
                _events.emit(SettingsEvent.ShowToast(UiText.StringResource(R.string.backfill_failed_generic, message)))
            } finally {
                _uiState.value = _uiState.value.copy(isSubmittingBackfill = false)
            }
        }
    }

}
