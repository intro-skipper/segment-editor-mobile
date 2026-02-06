package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.component.settings.ClickableSettingItem
import org.introskipper.segmenteditor.ui.component.settings.DropdownSettingsItem
import org.introskipper.segmenteditor.ui.component.settings.RadioGroupSettingItem
import org.introskipper.segmenteditor.ui.component.settings.SettingsSection
import org.introskipper.segmenteditor.ui.component.settings.SwitchSettingItem
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import org.introskipper.segmenteditor.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onRestartConnection: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showChangeServerDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connection Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_connection)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.settings_change_server),
                        subtitle = stringResource(R.string.settings_change_server_subtitle),
                        onClick = { showChangeServerDialog = true }
                    )
                }
            }

            // Theme Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                    RadioGroupSettingItem(
                        title = stringResource(R.string.settings_theme),
                        subtitle = stringResource(R.string.settings_theme_subtitle),
                        options = listOf(
                            AppTheme.LIGHT to stringResource(R.string.settings_theme_light),
                            AppTheme.DARK to stringResource(R.string.settings_theme_dark),
                            AppTheme.SYSTEM to stringResource(R.string.settings_theme_system)
                        ),
                        selectedOption = uiState.theme,
                        onOptionSelected = { theme ->
                            viewModel.setTheme(theme)
                            onThemeChanged(theme)
                        }
                    )
                }
            }

            // Pagination Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_browsing)) {
                    DropdownSettingsItem(
                        title = stringResource(R.string.settings_items_per_page),
                        subtitle = stringResource(R.string.settings_items_per_page_subtitle),
                        options = listOf(
                            10 to "10",
                            20 to "20",
                            30 to "30",
                            50 to "50",
                            100 to "100",
                            Int.MAX_VALUE to stringResource(R.string.settings_show_all)
                        ),
                        selectedOption = uiState.itemsPerPage,
                        onOptionSelected = viewModel::setItemsPerPage
                    )
                }
            }
            
            // Hidden Libraries Section
            item {
                if (uiState.availableLibraries.isNotEmpty()) {
                    SettingsSection(title = stringResource(R.string.settings_hidden_libraries)) {
                        Text(
                            text = stringResource(R.string.settings_hidden_libraries_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        uiState.availableLibraries.forEach { library ->
                            SwitchSettingItem(
                                title = library.name,
                                subtitle = null,
                                checked = !uiState.hiddenLibraryIds.contains(library.id),
                                onCheckedChange = { viewModel.toggleLibraryVisibility(library.id) }
                            )
                        }
                    }
                }
            }
            
            // Playback Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_playback)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.settings_prefer_direct_play),
                        subtitle = stringResource(R.string.settings_prefer_direct_play_subtitle),
                        checked = uiState.preferDirectPlay,
                        onCheckedChange = viewModel::setPreferDirectPlay
                    )
                    
                    SwitchSettingItem(
                        title = stringResource(R.string.settings_autoplay_next),
                        subtitle = stringResource(R.string.settings_autoplay_next_subtitle),
                        checked = uiState.autoPlayNextEpisode,
                        onCheckedChange = viewModel::setAutoPlayNextEpisode
                    )
                }
            }
            
            // Export Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_export)) {
                    RadioGroupSettingItem(
                        title = stringResource(R.string.settings_export_format),
                        subtitle = stringResource(R.string.settings_export_format_subtitle),
                        options = listOf(
                            ExportFormat.JSON to stringResource(R.string.settings_export_json),
                            ExportFormat.CSV to stringResource(R.string.settings_export_csv),
                            ExportFormat.XML to stringResource(R.string.settings_export_xml)
                        ),
                        selectedOption = uiState.exportFormat,
                        onOptionSelected = viewModel::setExportFormat
                    )
                    
                    if (uiState.exportFormat == ExportFormat.JSON) {
                        SwitchSettingItem(
                            title = stringResource(R.string.settings_pretty_print),
                            subtitle = stringResource(R.string.settings_pretty_print_subtitle),
                            checked = uiState.prettyPrintJson,
                            onCheckedChange = viewModel::setPrettyPrintJson
                        )
                    }
                }
            }
            
            // About Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_subtitle),
                        onClick = { showAboutDialog = true }
                    )
                    
                    ClickableSettingItem(
                        title = stringResource(R.string.settings_github),
                        subtitle = stringResource(R.string.settings_github_subtitle),
                        onClick = { showAboutDialog = true }
                    )
                }
            }
            
            // Add bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    
    if (showChangeServerDialog) {
        AlertDialog(
            onDismissRequest = { showChangeServerDialog = false },
            title = { Text(stringResource(R.string.settings_change_server)) },
            text = { Text(stringResource(R.string.settings_change_server_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChangeServerDialog = false
                        viewModel.clearAuthenticationAndRestart()
                        onRestartConnection()
                    }
                ) {
                    Text(stringResource(R.string.continue_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeServerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
