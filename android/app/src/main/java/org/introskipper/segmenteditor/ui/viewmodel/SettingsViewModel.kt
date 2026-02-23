package org.introskipper.segmenteditor.ui.viewmodel

import android.media.MediaCodecList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import org.introskipper.segmenteditor.utils.TranslationService
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicTranslationEnabled: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val preferDirectPlay: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val preferLocalPreviews: Boolean = false,
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
    val supportedAudioCodecs: List<String> = emptyList()
)

data class LibraryInfo(
    val id: String,
    val name: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val jellyfinRepository: JellyfinRepository,
    private val authRepository: AuthRepository,
    private val translationService: TranslationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
                        name = mediaItem.name ?: "Unknown Library"
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
}
