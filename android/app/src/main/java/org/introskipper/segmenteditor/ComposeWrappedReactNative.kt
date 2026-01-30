package org.introskipper.segmenteditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.facebook.react.ReactRootView

@Composable
fun ComposeWrappedReactNative() {
    val context = LocalContext.current
    val activity = context as MainActivity

    AndroidView(
        factory = { ctx ->
            ReactRootView(ctx).apply {
                startReactApplication(
                    activity.application.reactNativeHost.reactInstanceManager,
                    "segment-editor-mobile",
                    null
                )
            }
        },
        update = { }
    )
}
