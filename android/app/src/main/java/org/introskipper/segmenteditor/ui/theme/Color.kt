/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary colors - Orange/Yellow tone (from reference repository)
val PrimaryLight = Color(0xFFD87943)
val SecondaryLight = Color(0xFF527575)

val PrimaryDark = Color(0xFFE78A53)
val SecondaryDark = Color(0xFF5F8787)

// Segment type colors - Light mode
val SegmentIntroLight = Color(0xFF50C249)      // Green
val SegmentOutroLight = Color(0xFFAD46FF)      // Purple
val SegmentPreviewLight = Color(0xFFC5DB00)    // Yellow-Green
val SegmentRecapLight = Color(0xFFF0B100)      // Orange
val SegmentCommercialLight = Color(0xFFFB2C36) // Red
val SegmentUnknownLight = Color(0xFF6A7282)    // Gray

// Segment type colors - Dark mode (slightly brighter)
val SegmentIntroDark = Color(0xFF60CA5A)       // Brighter Green
val SegmentOutroDark = Color(0xFFBA64FF)       // Brighter Purple
val SegmentPreviewDark = Color(0xFFCCE000)     // Brighter Yellow-Green
val SegmentRecapDark = Color(0xFFF5BA00)       // Brighter Orange
val SegmentCommercialDark = Color(0xFFFF4C4C)  // Brighter Red
val SegmentUnknownDark = Color(0xFF798090)     // Brighter Gray

/**
 * Get the appropriate segment color based on segment type and current theme
 */
@Composable
fun getSegmentColor(type: String): Color {
    val isDark = isSystemInDarkTheme()
    return getSegmentColor(type, isDark)
}

/**
 * Get the appropriate segment color based on segment type and theme state
 * Non-composable version for performance optimization with remember
 * 
 * @param type The segment type (intro, outro, preview, recap, commercial, etc.)
 * @param isDark Whether dark theme is active
 */
fun getSegmentColor(type: String, isDark: Boolean): Color {
    return when (type.lowercase()) {
        "intro" -> if (isDark) SegmentIntroDark else SegmentIntroLight
        "outro", "credits" -> if (isDark) SegmentOutroDark else SegmentOutroLight
        "preview" -> if (isDark) SegmentPreviewDark else SegmentPreviewLight
        "recap" -> if (isDark) SegmentRecapDark else SegmentRecapLight
        "commercial" -> if (isDark) SegmentCommercialDark else SegmentCommercialLight
        else -> if (isDark) SegmentUnknownDark else SegmentUnknownLight
    }
}
