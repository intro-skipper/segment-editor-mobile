package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val preferDirectPlay: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val prettyPrintJson: Boolean = true,
    val itemsPerPage: Int = 20,
    val hiddenLibraryIds: Set<String> = emptySet(),
    val availableLibraries: List<LibraryInfo> = emptyList(),
    val isLoadingLibraries: Boolean = false
)

data class LibraryInfo(
    val id: String,
    val name: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadAvailableLibraries()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                theme = securePreferences.getTheme(),
                preferDirectPlay = securePreferences.getPreferDirectPlay(),
                autoPlayNextEpisode = securePreferences.getAutoPlayNextEpisode(),
                exportFormat = securePreferences.getExportFormat(),
                prettyPrintJson = securePreferences.getPrettyPrintJson(),
                itemsPerPage = securePreferences.getItemsPerPage(),
                hiddenLibraryIds = securePreferences.getHiddenLibraryIds()
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

    fun setPreferDirectPlay(enabled: Boolean) {
        securePreferences.setPreferDirectPlay(enabled)
        _uiState.value = _uiState.value.copy(preferDirectPlay = enabled)
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        securePreferences.setAutoPlayNextEpisode(enabled)
        _uiState.value = _uiState.value.copy(autoPlayNextEpisode = enabled)
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
