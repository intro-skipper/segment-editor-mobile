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
            val isDark = dynamicColorsOptions.isDark ?: darkTheme

            // Calculate a surface color based on the seed
            // For dark: very dark version of the color. For light: very light version.
            val surfaceColor = if (isDark) {
                // Mix with black for dark surface
                Color.Black.copy(alpha = 0.9f).compositeOver(color.copy(alpha = 0.1f))
            } else {
                // Mix with white for light surface
                Color.White.copy(alpha = 0.9f).compositeOver(color.copy(alpha = 0.1f))
            }

            val onSurfaceColor = if (surfaceColor.luminance() > 0.5f) Color.Black else Color.White
            val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
            
            if (isDark) {
                darkColorScheme(
                    primary = color,
                    onPrimary = onColor,
                    primaryContainer = color,
                    onPrimaryContainer = onColor,
                    secondaryContainer = color.copy(alpha = 0.2f),
                    onSecondaryContainer = onSurfaceColor,
                    surface = surfaceColor,
                    onSurface = onSurfaceColor,
                    background = surfaceColor,
                    onBackground = onSurfaceColor,
                    surfaceVariant = surfaceColor.copy(alpha = 0.8f).compositeOver(onSurfaceColor.copy(alpha = 0.1f)),
                    onSurfaceVariant = onSurfaceColor.copy(alpha = 0.7f)
                )
            } else {
                lightColorScheme(
                    primary = color,
                    onPrimary = onColor,
                    primaryContainer = color,
                    onPrimaryContainer = onColor,
                    secondaryContainer = color.copy(alpha = 0.2f),
                    onSecondaryContainer = onSurfaceColor,
                    surface = surfaceColor,
                    onSurface = onSurfaceColor,
                    background = surfaceColor,
                    onBackground = onSurfaceColor,
                    surfaceVariant = surfaceColor.copy(alpha = 0.8f).compositeOver(onSurfaceColor.copy(alpha = 0.1f)),
                    onSurfaceVariant = onSurfaceColor.copy(alpha = 0.7f)
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
            if (dynamicColorsOptions.seedColor == null) {
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            } else {
                // When using dynamic colors, use surface color for status bar to blend in
                window.statusBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = colorScheme.surface.luminance() > 0.5f
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Helper to composite colors since it's missing in basic Color class without extension
private fun Color.compositeOver(background: Color): Color {
    val alpha = this.alpha
    val invAlpha = 1.0f - alpha
    return Color(
        red = (this.red * alpha) + (background.red * invAlpha),
        green = (this.green * alpha) + (background.green * invAlpha),
        blue = (this.blue * alpha) + (background.blue * invAlpha),
        alpha = 1.0f
    )
}
