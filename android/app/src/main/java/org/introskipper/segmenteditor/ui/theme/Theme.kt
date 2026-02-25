/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.utils.TranslationService

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

val LocalAppTheme = compositionLocalOf { AppTheme.SYSTEM }
val LocalTranslationService = compositionLocalOf<TranslationService?> { null }

@Composable
fun SegmentEditorTheme(
    appTheme: AppTheme = LocalAppTheme.current,
    translationService: TranslationService? = LocalTranslationService.current,
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
            val seedColor = Color(dynamicColorsOptions.seedColor)
            val isDark = dynamicColorsOptions.isDark ?: darkTheme
            
            if (isDark) {
                val surfaceBase = Color(0xFF121212)
                val surface = seedColor.copy(alpha = 0.1f).compositeOver(surfaceBase)
                val onSurface = if (surface.luminance() > 0.5f) Color.Black else Color.White
                
                // Ensure primary contrasts with surface
                val primary = if (seedColor.luminance() < 0.4f) {
                    seedColor.copy(alpha = 0.6f).compositeOver(Color.White)
                } else {
                    seedColor
                }
                val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
                
                val secondaryContainer = seedColor.copy(alpha = 0.25f).compositeOver(surfaceBase)
                val onSecondaryContainer = if (secondaryContainer.luminance() > 0.5f) Color.Black else Color.White
                
                val surfaceVariant = seedColor.copy(alpha = 0.18f).compositeOver(surfaceBase)
                val onSurfaceVariant = if (surfaceVariant.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)

                darkColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primary,
                    onPrimaryContainer = onPrimary,
                    secondaryContainer = secondaryContainer,
                    onSecondaryContainer = onSecondaryContainer,
                    surface = surface,
                    onSurface = onSurface,
                    background = surface,
                    onBackground = onSurface,
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = onSurfaceVariant,
                    outline = onSurface.copy(alpha = 0.12f)
                )
            } else {
                val surfaceBase = Color(0xFFFEFEFE)
                val surface = seedColor.copy(alpha = 0.05f).compositeOver(surfaceBase)
                val onSurface = if (surface.luminance() > 0.5f) Color.Black else Color.White
                
                // Ensure primary contrasts with surface
                val primary = if (seedColor.luminance() > 0.6f) {
                    seedColor.copy(alpha = 0.6f).compositeOver(Color.Black)
                } else {
                    seedColor
                }
                val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
                
                val secondaryContainer = seedColor.copy(alpha = 0.15f).compositeOver(surfaceBase)
                val onSecondaryContainer = if (secondaryContainer.luminance() > 0.5f) Color.Black else Color.White
                
                val surfaceVariant = seedColor.copy(alpha = 0.12f).compositeOver(surfaceBase)
                val onSurfaceVariant = if (surfaceVariant.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)

                lightColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primary,
                    onPrimaryContainer = onPrimary,
                    secondaryContainer = secondaryContainer,
                    onSecondaryContainer = onSecondaryContainer,
                    surface = surface,
                    onSurface = onSurface,
                    background = surface,
                    onBackground = onSurface,
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = onSurfaceVariant,
                    outline = onSurface.copy(alpha = 0.12f)
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
                window.statusBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = colorScheme.surface.luminance() > 0.5f
            }
        }
    }

    CompositionLocalProvider(
        LocalAppTheme provides appTheme,
        LocalTranslationService provides translationService
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
