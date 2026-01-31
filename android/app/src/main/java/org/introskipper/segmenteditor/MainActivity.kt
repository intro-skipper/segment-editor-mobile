package org.introskipper.segmenteditor

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.navigation.AppNavigation
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.theme.ReactInMobileTheme
import org.introskipper.segmenteditor.update.UpdateManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    var updateManager: UpdateManager? = null
    
    @Inject
    lateinit var securePreferences: SecurePreferences
    
    @Inject
    lateinit var apiService: JellyfinApiService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = UpdateManager(this)
        
        // Determine start destination based on whether user is already configured
        val startDestination = if (securePreferences.isConfigured()) {
            Screen.Main.route
        } else {
            Screen.ConnectionWizard.route
        }
        
        setContent {
            var currentTheme by remember { mutableStateOf(securePreferences.getTheme()) }
            
            ReactInMobileTheme(appTheme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        startDestination = startDestination,
                        securePreferences = securePreferences,
                        apiService = apiService,
                        onThemeChanged = { theme -> currentTheme = theme }
                    )
                }
            }
        }
    }

    val onRequestInstall = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (packageManager.canRequestPackageInstalls()) updateManager?.onUpdateRequested()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
