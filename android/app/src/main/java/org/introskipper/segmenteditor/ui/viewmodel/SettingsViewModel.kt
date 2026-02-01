package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val autoPlayNextEpisode: Boolean = true,
    val skipIntroAutomatically: Boolean = true,
    val skipCreditsAutomatically: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val prettyPrintJson: Boolean = true,
    val itemsPerPage: Int = 20
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                theme = securePreferences.getTheme(),
                autoPlayNextEpisode = securePreferences.getAutoPlayNextEpisode(),
                skipIntroAutomatically = securePreferences.getSkipIntroAutomatically(),
                skipCreditsAutomatically = securePreferences.getSkipCreditsAutomatically(),
                exportFormat = securePreferences.getExportFormat(),
                prettyPrintJson = securePreferences.getPrettyPrintJson(),
                itemsPerPage = securePreferences.getItemsPerPage()
            )
        }
    }

    fun setTheme(theme: AppTheme) {
        securePreferences.setTheme(theme)
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        securePreferences.setAutoPlayNextEpisode(enabled)
        _uiState.value = _uiState.value.copy(autoPlayNextEpisode = enabled)
    }

    fun setSkipIntroAutomatically(enabled: Boolean) {
        securePreferences.setSkipIntroAutomatically(enabled)
        _uiState.value = _uiState.value.copy(skipIntroAutomatically = enabled)
    }

    fun setSkipCreditsAutomatically(enabled: Boolean) {
        securePreferences.setSkipCreditsAutomatically(enabled)
        _uiState.value = _uiState.value.copy(skipCreditsAutomatically = enabled)
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

    fun clearAuthenticationAndRestart() {
        securePreferences.clearAuthentication()
    }
}
