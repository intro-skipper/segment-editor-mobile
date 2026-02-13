package org.introskipper.segmenteditor

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.pip.PictureInPictureDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.navigation.AppNavigation
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.theme.SegmentEditorTheme
import org.introskipper.segmenteditor.update.CustomDialog
import org.introskipper.segmenteditor.update.UpdateManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PictureInPictureDelegate.OnPictureInPictureEventListener {
    var updateManager: UpdateManager? = null

    @Inject
    lateinit var securePreferences: SecurePreferences

    @Inject
    lateinit var apiService: JellyfinApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = UpdateManager(this)

        setSystemBarTheme()

        // Determine start destination based on whether user is already logged in
        val startDestination = if (securePreferences.isLoggedIn()) {
            Screen.Main.route
        } else {
            Screen.ConnectionWizard.route
        }

        setContent {
            var currentTheme by remember { mutableStateOf(securePreferences.getTheme()) }
            val openDialogCustom = remember { mutableStateOf(false) }

            SegmentEditorTheme(appTheme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        startDestination = startDestination,
                        securePreferences = securePreferences,
                        apiService = apiService,
                        onThemeChanged = { theme ->
                            currentTheme = theme
                            setSystemBarTheme()
                        }
                    )
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        updateManager?.setUpdateListener(object : UpdateManager.UpdateListener {
                            override fun onUpdateFound() {
                                openDialogCustom.value = true
                            }
                        })
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            if (openDialogCustom.value) {
                CustomDialog(openDialogCustom = openDialogCustom)
            }
        }
    }

    val onRequestInstall = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (packageManager.canRequestPackageInstalls()) updateManager?.onUpdateRequested()
    }

    private fun setSystemBarTheme() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onPictureInPictureEvent(
        event: PictureInPictureDelegate.Event,
        config: Configuration?,
    ) { }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9)).build()
        enterPictureInPictureMode(params)
    }
}
