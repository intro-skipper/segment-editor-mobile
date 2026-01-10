package org.introskipper.segmenteditor

// The built in Android WebView
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import org.introskipper.segmenteditor.ui.theme.ReactInMobileTheme

@Composable
fun ComposeWrappedWebView() {
    val inPreview = LocalInspectionMode.current
    val activity = LocalActivity.current as ComponentActivity
    var backEnabled by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }
    AndroidView(
        factory = { context ->

            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", AssetsPathHandler(context))
                .build()

            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Compose previews don't fully support legacy Android views
                // https://github.com/google/accompanist/issues/1326#issuecomment-1251355470
                if (!inPreview) {
                    /**
                     * Enable JavaScript in the WebView. This is required to load JS in the WebView.
                     * The compiler will warn you that this can cause XSS security issues but since we
                     * are loading our own assets, this is not a concern hence the
                     * `@Suppress("SetJavaScriptEnabled")` annotation.
                     *
                     * See https://developer.android.com/reference/android/webkit/WebSettings#setJavaScriptEnabled(boolean)
                     */
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                }

                webChromeClient = object : WebChromeClient() {
                    private var customView: View? = null
                    private var customViewCallback: CustomViewCallback? = null

                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        if (customView != null) {
                            callback.onCustomViewHidden()
                            return
                        }

                        // Store the custom view and callback
                        customView = view.apply { keepScreenOn = false }
                        customViewCallback = callback

                        // Add the custom view to the activity's root view
                        val decorView = activity.window.decorView as FrameLayout
                        decorView.addView(view, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))

                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }

                    override fun onHideCustomView() {
                        val decorView = activity.window.decorView as FrameLayout
                        customView?.let {
                            decorView.removeView(it)
                        }
                        customView = null
                        customViewCallback?.onCustomViewHidden()

                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                    }
                }

                webViewClient =  object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean
                    ) {
                        backEnabled = view?.canGoBack() ?: false
                    }
                }

                /**
                 * This is the URL that will be loaded when the WebView is first
                 * The assets directory is served by a domain `https://appassets.androidplatform.net`
                 * Learn more about the WebViewAssetLoader here:
                 * https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader
                 */
                loadUrl("https://appassets.androidplatform.net/assets/dist/index.html")
                webView = this
            }
        },
        update = {
            webView = it
        }
    )

    BackHandler(enabled = backEnabled) {
        webView?.goBack()
    }
}

@Preview(showBackground = true, apiLevel = 33)
@Composable
fun ComposeWrappedWebViewPreview() {
    ReactInMobileTheme {
        ComposeWrappedWebView()
    }
}
