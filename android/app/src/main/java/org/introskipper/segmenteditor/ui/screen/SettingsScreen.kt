package org.introskipper.segmenteditor.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.component.settings.ClickableSettingItem
import org.introskipper.segmenteditor.ui.component.settings.DropdownSettingsItem
import org.introskipper.segmenteditor.ui.component.settings.SettingsSection
import org.introskipper.segmenteditor.ui.component.settings.SwitchSettingItem
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import org.introskipper.segmenteditor.ui.viewmodel.SettingsViewModel
import org.introskipper.segmenteditor.ui.component.translatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onRestartConnection: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAboutDialog by remember { mutableStateOf(false) }
    var showChangeServerDialog by remember { mutableStateOf(false) }
    
    // Refresh preferences when returning to the screen (e.g. from system language settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadPreferences()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(translatedString(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = translatedString(R.string.back)
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
                SettingsSection(title = translatedString(R.string.settings_section_connection)) {
                    ClickableSettingItem(
                        title = translatedString(R.string.settings_change_server),
                        subtitle = translatedString(R.string.settings_change_server_subtitle),
                        onClick = { showChangeServerDialog = true }
                    )
                }
            }

            // Theme Section
            item {
                SettingsSection(title = translatedString(R.string.settings_section_appearance)) {
                    DropdownSettingsItem(
                        title = translatedString(R.string.settings_theme),
                        subtitle = translatedString(R.string.settings_theme_subtitle),
                        options = listOf(
                            AppTheme.LIGHT to translatedString(R.string.settings_theme_light),
                            AppTheme.DARK to translatedString(R.string.settings_theme_dark),
                            AppTheme.SYSTEM to translatedString(R.string.settings_theme_system)
                        ),
                        selectedOption = uiState.theme,
                        onOptionSelected = { theme ->
                            viewModel.setTheme(theme)
                            onThemeChanged(theme)
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ClickableSettingItem(
                            title = translatedString(R.string.settings_language),
                            subtitle = uiState.currentLocaleName.ifEmpty { translatedString(R.string.settings_language_subtitle) },
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    SwitchSettingItem(
                        title = translatedString(R.string.settings_dynamic_translation),
                        subtitle = translatedString(R.string.settings_dynamic_translation_subtitle),
                        checked = uiState.dynamicTranslationEnabled,
                        onCheckedChange = viewModel::setDynamicTranslationEnabled
                    )
                }
            }

            // Pagination Section
            item {
                SettingsSection(title = translatedString(R.string.settings_section_browsing)) {
                    DropdownSettingsItem(
                        title = translatedString(R.string.settings_items_per_page),
                        subtitle = translatedString(R.string.settings_items_per_page_subtitle),
                        options = listOf(
                            10 to "10",
                            20 to "20",
                            30 to "30",
                            50 to "50",
                            100 to "100",
                            Int.MAX_VALUE to translatedString(R.string.settings_show_all)
                        ),
                        selectedOption = uiState.itemsPerPage,
                        onOptionSelected = viewModel::setItemsPerPage
                    )
                }
            }
            
            // Hidden Libraries Section
            item {
                if (uiState.availableLibraries.isNotEmpty()) {
                    SettingsSection(title = translatedString(R.string.settings_hidden_libraries)) {
                        Text(
                            text = translatedString(R.string.settings_hidden_libraries_subtitle),
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
                SettingsSection(title = translatedString(R.string.settings_section_playback)) {
                    SwitchSettingItem(
                        title = translatedString(R.string.settings_prefer_direct_play),
                        subtitle = translatedString(R.string.settings_prefer_direct_play_subtitle),
                        checked = uiState.preferDirectPlay,
                        onCheckedChange = viewModel::setPreferDirectPlay
                    )
                    
                    SwitchSettingItem(
                        title = translatedString(R.string.settings_autoplay_next),
                        subtitle = translatedString(R.string.settings_autoplay_next_subtitle),
                        checked = uiState.autoPlayNextEpisode,
                        onCheckedChange = viewModel::setAutoPlayNextEpisode
                    )
                }
            }
            
            // Export Section
            item {
                SettingsSection(title = translatedString(R.string.settings_section_export)) {
                    DropdownSettingsItem(
                        title = translatedString(R.string.settings_export_format),
                        subtitle = translatedString(R.string.settings_export_format_subtitle),
                        options = listOf(
                            ExportFormat.JSON to translatedString(R.string.settings_export_json),
                            ExportFormat.CSV to translatedString(R.string.settings_export_csv),
                            ExportFormat.XML to translatedString(R.string.settings_export_xml)
                        ),
                        selectedOption = uiState.exportFormat,
                        onOptionSelected = viewModel::setExportFormat
                    )
                    
                    if (uiState.exportFormat == ExportFormat.JSON) {
                        SwitchSettingItem(
                            title = translatedString(R.string.settings_pretty_print),
                            subtitle = translatedString(R.string.settings_pretty_print_subtitle),
                            checked = uiState.prettyPrintJson,
                            onCheckedChange = viewModel::setPrettyPrintJson
                        )
                    }
                }
            }
            
            // About Section
            item {
                SettingsSection(title = translatedString(R.string.settings_section_about)) {
                    ClickableSettingItem(
                        title = translatedString(R.string.settings_about),
                        subtitle = translatedString(R.string.settings_about_subtitle),
                        onClick = { showAboutDialog = true }
                    )
                    
                    ClickableSettingItem(
                        title = translatedString(R.string.settings_github),
                        subtitle = translatedString(R.string.settings_github_subtitle),
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
            title = { Text(translatedString(R.string.settings_change_server)) },
            text = { Text(translatedString(R.string.settings_change_server_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChangeServerDialog = false
                        viewModel.clearAuthenticationAndRestart()
                        onRestartConnection()
                    }
                ) {
                    Text(translatedString(R.string.continue_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeServerDialog = false }) {
                    Text(translatedString(R.string.cancel))
                }
            }
        )
    }
}
