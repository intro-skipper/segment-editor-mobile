/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.state

/**
 * Data class representing user preferences for the application
 */
data class AppPreferences(
    // Connection settings
    val serverUrl: String = "",
    val apiKey: String = "",
    val userId: String = "",
    val username: String = "",
    
    // Playback settings
    val autoPlayNextEpisode: Boolean = true,
    val skipBehavior: SkipBehavior = SkipBehavior.SHOW_BUTTON,
    val watchProgressMode: WatchProgressMode = WatchProgressMode.CONTINUE_WATCHING,
    
    // UI settings
    val theme: AppTheme = AppTheme.SYSTEM,
    
    // Segment editor settings
    val defaultSegmentDuration: Double = 90.0, // seconds
    val showTimestampsInTicks: Boolean = false,
    val confirmBeforeDelete: Boolean = true,
    
    // Export settings
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val includeMetadata: Boolean = true,
    val prettyPrintJson: Boolean = true,
    
    // Media library settings
    val showContinueWatching: Boolean = true,
    val showNextUp: Boolean = true,
    val itemsPerPage: Int = 20,
    val browseLayout: BrowseLayout = BrowseLayout.CARD,
    
    // Video player settings
    val preferredVideoQuality: VideoQuality = VideoQuality.AUTO,
    val preferDirectPlay: Boolean = true,
    val preferredAudioLanguage: String = "",
    val preferredSubtitleLanguage: String = "",
    
    // Tracking preferences
    val trackSegmentEdits: Boolean = true,
    val sendCrashReports: Boolean = false,
    val sendAnalytics: Boolean = false
) {
    /**
     * Checks if server connection is configured
     */
    fun isConfigured(): Boolean {
        return serverUrl.isNotBlank() && apiKey.isNotBlank()
    }
    
    /**
     * Checks if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return isConfigured() && userId.isNotBlank()
    }
}

/**
 * Behavior for handling segments during playback
 */
enum class SkipBehavior {
    SHOW_BUTTON,
    AUTO_SKIP,
    NONE
}

enum class WatchProgressMode {
    ALL_PLAYBACK,
    CONTINUE_WATCHING,
    NONE
}

/**
 * Theme options for the application
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Export format options
 */
enum class ExportFormat {
    JSON,
    CSV,
    XML
}

/**
 * Video quality preferences
 */
enum class VideoQuality {
    AUTO,
    LOW,
    MEDIUM,
    HIGH,
    ORIGINAL
}

/**
 * Browse layout options
 */
enum class BrowseLayout {
    CARD,
    LIST
}
