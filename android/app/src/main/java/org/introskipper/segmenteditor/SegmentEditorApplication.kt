package org.introskipper.segmenteditor

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.webkit.ChromeIntegration
import javax.inject.Inject

@HiltAndroidApp
class SegmentEditorApplication : Application() {

    @Inject
    lateinit var securePreferences: SecurePreferences

    override fun onCreate() {
        super.onCreate()

        preferences = securePreferences

        val uiModeManager: UiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isAndroidTV = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        CoroutineScope(Dispatchers.IO).launch {
            ChromeIntegration.hasCustomTabs = ChromeIntegration.bindCustomTabService(this@SegmentEditorApplication)
        }
    }

    companion object {
        var isAndroidTV = false

        var preferences: SecurePreferences? = null
    }
}

val Number.toPx get() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
).toInt()
