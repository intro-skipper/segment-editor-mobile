package org.introskipper.segmenteditor

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import org.introskipper.segmenteditor.update.UpdateManager

class MainActivity : ReactActivity() {
    var updateManager: UpdateManager? = null
    lateinit var onRequestInstall: ActivityResultLauncher<android.content.Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the activity result launcher
        onRequestInstall = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (packageManager.canRequestPackageInstalls()) updateManager?.onUpdateRequested()
        }
        
        // Initialize update manager
        updateManager = UpdateManager(this)
    }

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override fun getMainComponentName(): String = "SegmentEditor"

    /**
     * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
     * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
