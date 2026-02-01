package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.introskipper.segmenteditor.ui.component.settings.*
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            // Theme Section
            item {
                SettingsSection(title = "Appearance") {
                    RadioGroupSettingItem(
                        title = "Theme",
                        subtitle = "Choose app theme",
                        options = listOf(
                            AppTheme.LIGHT to "☀️ Light",
                            AppTheme.DARK to "🌙 Dark",
                            AppTheme.SYSTEM to "📱 System Default"
                        ),
                        selectedOption = uiState.theme,
                        onOptionSelected = { theme ->
                            viewModel.setTheme(theme)
                            onThemeChanged(theme)
                        }
                    )
                }
            }
            
            // Language Section
            item {
                SettingsSection(title = "Language") {
                    RadioGroupSettingItem(
                        title = "App Language",
                        subtitle = "Select your preferred language",
                        options = listOf(
                            "en" to "🇺🇸 English",
                            "de" to "🇩🇪 Deutsch",
                            "fr" to "🇫🇷 Français"
                        ),
                        selectedOption = uiState.language,
                        onOptionSelected = viewModel::setLanguage
                    )
                }
            }
            
            // Playback Section
            item {
                SettingsSection(title = "Playback") {
                    SwitchSettingItem(
                        title = "Auto-play Next Episode",
                        subtitle = "Automatically play the next episode",
                        checked = uiState.autoPlayNextEpisode,
                        onCheckedChange = viewModel::setAutoPlayNextEpisode
                    )
                    
                    SwitchSettingItem(
                        title = "Skip Intro Automatically",
                        subtitle = "Auto-skip intros when detected",
                        checked = uiState.skipIntroAutomatically,
                        onCheckedChange = viewModel::setSkipIntroAutomatically
                    )
                    
                    SwitchSettingItem(
                        title = "Skip Credits Automatically",
                        subtitle = "Auto-skip end credits when detected",
                        checked = uiState.skipCreditsAutomatically,
                        onCheckedChange = viewModel::setSkipCreditsAutomatically
                    )
                }
            }
            
            // Export Section
            item {
                SettingsSection(title = "Export") {
                    RadioGroupSettingItem(
                        title = "Default Export Format",
                        subtitle = "Format for exporting segments",
                        options = listOf(
                            ExportFormat.JSON to "JSON",
                            ExportFormat.CSV to "CSV",
                            ExportFormat.XML to "XML"
                        ),
                        selectedOption = uiState.exportFormat,
                        onOptionSelected = viewModel::setExportFormat
                    )
                    
                    if (uiState.exportFormat == ExportFormat.JSON) {
                        SwitchSettingItem(
                            title = "Pretty Print JSON",
                            subtitle = "Format JSON with indentation",
                            checked = uiState.prettyPrintJson,
                            onCheckedChange = viewModel::setPrettyPrintJson
                        )
                    }
                }
            }

            // Pagination Section
            item {
                SettingsSection(title = "Browsing") {
                    RadioGroupSettingItem(
                        title = "Items Per Page",
                        subtitle = "Number of items to show per page",
                        options = listOf(
                            10 to "10",
                            20 to "20",
                            30 to "30",
                            50 to "50",
                            100 to "100",
                            Int.MAX_VALUE to "Show All"
                        ),
                        selectedOption = uiState.itemsPerPage,
                        onOptionSelected = viewModel::setItemsPerPage
                    )
                }
            }

            // Connection Section
            item {
                SettingsSection(title = "Connection") {
                    ClickableSettingItem(
                        title = "Change Server",
                        subtitle = "Connect to a different Jellyfin server",
                        onClick = { showChangeServerDialog = true }
                    )
                }
            }
            
            // About Section
            item {
                SettingsSection(title = "About") {
                    ClickableSettingItem(
                        title = "About Segment Editor",
                        subtitle = "Version info and credits",
                        onClick = { showAboutDialog = true }
                    )
                    
                    ClickableSettingItem(
                        title = "GitHub Repository",
                        subtitle = "View source code and contribute",
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
            title = { Text("Change Server") },
            text = { Text("This will disconnect from the current server and return to the connection wizard. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChangeServerDialog = false
                        viewModel.clearAuthenticationAndRestart()
                        onRestartConnection()
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
