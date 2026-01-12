package org.introskipper.segmenteditor

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.introskipper.segmenteditor.ui.theme.ReactInMobileTheme
import org.introskipper.segmenteditor.update.CustomDialog
import org.introskipper.segmenteditor.update.UpdateManager

class MainActivity : ComponentActivity() {
    var updateManager: UpdateManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReactInMobileTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComposeWrappedWebView()
                }
            }
        }
    }

    val onRequestInstall = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (packageManager.canRequestPackageInstalls())
            updateManager?.onUpdateRequested()
    }

    override fun onStart() {
        super.onStart()
        updateManager = UpdateManager(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
