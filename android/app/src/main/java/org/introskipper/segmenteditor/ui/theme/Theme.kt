package org.introskipper.segmenteditor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.introskipper.segmenteditor.ui.state.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = SegmentRecapDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = SegmentRecapLight

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

data class DynamicColorsOptions(
    val seedColor: Int? = null,
    val isDark: Boolean? = null
)

@Composable
fun SegmentEditorTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    dynamicColorsOptions: DynamicColorsOptions = DynamicColorsOptions(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    
    val context = LocalContext.current
    
    val colorScheme = when {
        dynamicColorsOptions.seedColor != null -> {
            val color = Color(dynamicColorsOptions.seedColor)
            val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
            
            if (dynamicColorsOptions.isDark ?: darkTheme) {
                darkColorScheme(
                    primary = color,
                    primaryContainer = color,
                    onPrimaryContainer = onColor,
                    secondaryContainer = color.copy(alpha = 0.2f),
                    onSecondaryContainer = onColor
                )
            } else {
                lightColorScheme(
                    primary = color,
                    primaryContainer = color,
                    onPrimaryContainer = onColor,
                    secondaryContainer = color.copy(alpha = 0.2f),
                    onSecondaryContainer = onColor
                )
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Only update status bar if we're not using a per-item theme
            if (dynamicColorsOptions.seedColor == null) {
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
