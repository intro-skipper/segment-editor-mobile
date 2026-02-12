package org.introskipper.segmenteditor.webkit

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri
import org.introskipper.segmenteditor.BuildConfig
import org.introskipper.segmenteditor.SegmentEditorApplication
import org.introskipper.segmenteditor.toPx
import org.introskipper.segmenteditor.ui.state.AppTheme

object ChromeIntegration {
    private var mClient: CustomTabsClient? = null
    private var mSession: CustomTabsSession? = null
    var hasCustomTabs = true

    private val mConnection: CustomTabsServiceConnection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(
            name: ComponentName,
            client: CustomTabsClient
        ) {
            mClient = client.apply {
                warmup(0 /* placeholder for future use */)
                mSession = newSession(CustomTabsCallback())?.apply {
                    mayLaunchUrl("https://github.com/".toUri(), null, listOf(
                        Bundle().apply {
                            putParcelable(
                                CustomTabsService.KEY_URL,
                                "https://jellyfin.org/".toUri()
                            )
                        }
                    ))
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mClient = null
            mSession = null
        }
    }

    fun bindCustomTabService(context: Context) : Boolean {
        if (mClient != null || BuildConfig.GOOGLE_PLAY) return false

        val browserPackages: ArrayList<String> = arrayListOf()
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, "https://github.com/".toUri()),
            PackageManager.MATCH_DEFAULT_ONLY
        ).forEach {
            browserPackages.add(it.resolvePackageName)
        }

        val packageName = CustomTabsClient.getPackageName(
            context, browserPackages.ifEmpty { null }
        ) ?: return false
        return CustomTabsClient.bindCustomTabsService(context, packageName, mConnection)
    }

    fun openLinkInBrowser(context: Context, link: String?) {
        link?.let {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        data = link.toUri()
                        selector = Intent(Intent.ACTION_VIEW).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            data = Uri.fromParts("http", "", null)
                        }
                    }
                )
            } catch (_: ActivityNotFoundException) {

            } catch (_: Exception) {

            }
        }
    }

    fun openBrowserTab(context: Context, link: String?) {
        link?.let {
            if (SegmentEditorApplication.isAndroidTV || !hasCustomTabs) {
                openLinkInBrowser(context, it)
                return
            }
            try {
                val builder = CustomTabsIntent.Builder(mSession)
                    .setBackgroundInteractionEnabled(true)
                    .setInitialActivityHeightPx(
                        720.toPx,
                        CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
                    )
                    .setToolbarCornerRadiusDp(16)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .setShowTitle(false)
                    .setUrlBarHidingEnabled(true)
                SegmentEditorApplication.preferences?.getTheme()?.let {
                    builder.setColorScheme(when (it) {
                        AppTheme.LIGHT -> CustomTabsIntent.COLOR_SCHEME_LIGHT
                        AppTheme.DARK -> CustomTabsIntent.COLOR_SCHEME_DARK
                        else -> CustomTabsIntent.COLOR_SCHEME_SYSTEM
                    })
                }
                builder.build().launchUrl(context, it.toUri())
            } catch (_: ActivityNotFoundException) {
                openLinkInBrowser(context, link)
            }
        }
    }
}