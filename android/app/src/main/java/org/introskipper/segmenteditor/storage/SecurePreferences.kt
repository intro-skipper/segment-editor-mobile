package org.introskipper.segmenteditor.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat
import org.introskipper.segmenteditor.ui.state.VideoQuality

class SecurePreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "jellyfin_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecurePreferences", "Encryption failed, falling back to unencrypted storage", e)
        context.getSharedPreferences("jellyfin_prefs", Context.MODE_PRIVATE)
    }
    
    // ========== Connection Settings ==========
    
    fun saveServerUrl(url: String) {
        sharedPreferences.edit { putString(KEY_SERVER_URL, url) }
    }
    
    fun getServerUrl(): String? {
        return sharedPreferences.getString(KEY_SERVER_URL, null)
    }
    
    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit { putString(KEY_API_KEY, apiKey) }
    }
    
    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }
    
    fun saveUserId(userId: String) {
        sharedPreferences.edit { putString(KEY_USER_ID, userId) }
    }
    
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    fun saveUsername(username: String) {
        sharedPreferences.edit { putString(KEY_USERNAME, username) }
    }
    
    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }
    
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit { putString(KEY_DEVICE_ID, deviceId) }
    }
    
    fun getDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }
    
    // ========== Playback Settings ==========
    
    fun setAutoPlayNextEpisode(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_AUTO_PLAY_NEXT, enabled) }
    }
    
    fun getAutoPlayNextEpisode(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_PLAY_NEXT, false)
    }

    fun setSkipIntroAuto(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SKIP_INTRO_AUTO, enabled) }
    }

    fun getSkipIntroAuto(): Boolean {
        return sharedPreferences.getBoolean(KEY_SKIP_INTRO_AUTO, false)
    }

    fun setSkipCreditsAuto(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SKIP_CREDITS_AUTO, enabled) }
    }

    fun getSkipCreditsAuto(): Boolean {
        return sharedPreferences.getBoolean(KEY_SKIP_CREDITS_AUTO, false)
    }

    fun setShowSkipButtons(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SHOW_SKIP_BUTTONS, enabled) }
    }

    fun getShowSkipButtons(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_SKIP_BUTTONS, true)
    }
    
    // ========== UI Settings ==========
    
    fun setTheme(theme: AppTheme) {
        sharedPreferences.edit { putString(KEY_THEME, theme.name) }
    }
    
    fun getTheme(): AppTheme {
        val themeName = sharedPreferences.getString(KEY_THEME, AppTheme.SYSTEM.name)
        return try {
            AppTheme.valueOf(themeName ?: AppTheme.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }
    
    // ========== Segment Editor Settings ==========
    
    fun setDefaultSegmentDuration(seconds: Double) {
        sharedPreferences.edit { putFloat(KEY_DEFAULT_SEGMENT_DURATION, seconds.toFloat()) }
    }
    
    fun getDefaultSegmentDuration(): Double {
        return sharedPreferences.getFloat(KEY_DEFAULT_SEGMENT_DURATION, 90f).toDouble()
    }
    
    fun setShowTimestampsInTicks(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SHOW_TICKS, enabled) }
    }
    
    fun getShowTimestampsInTicks(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_TICKS, false)
    }
    
    fun setConfirmBeforeDelete(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_CONFIRM_DELETE, enabled) }
    }
    
    fun getConfirmBeforeDelete(): Boolean {
        return sharedPreferences.getBoolean(KEY_CONFIRM_DELETE, true)
    }
    
    // ========== Export Settings ==========
    
    fun setExportFormat(format: ExportFormat) {
        sharedPreferences.edit { putString(KEY_EXPORT_FORMAT, format.name) }
    }
    
    fun getExportFormat(): ExportFormat {
        val formatName = sharedPreferences.getString(KEY_EXPORT_FORMAT, ExportFormat.JSON.name)
        return try {
            ExportFormat.valueOf(formatName ?: ExportFormat.JSON.name)
        } catch (e: IllegalArgumentException) {
            ExportFormat.JSON
        }
    }
    
    fun setIncludeMetadata(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_INCLUDE_METADATA, enabled) }
    }
    
    fun getIncludeMetadata(): Boolean {
        return sharedPreferences.getBoolean(KEY_INCLUDE_METADATA, true)
    }
    
    fun setPrettyPrintJson(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_PRETTY_PRINT_JSON, enabled) }
    }
    
    fun getPrettyPrintJson(): Boolean {
        return sharedPreferences.getBoolean(KEY_PRETTY_PRINT_JSON, true)
    }
    
    // ========== Media Library Settings ==========
    
    fun setShowContinueWatching(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SHOW_CONTINUE_WATCHING, enabled) }
    }
    
    fun getShowContinueWatching(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_CONTINUE_WATCHING, true)
    }
    
    fun setShowNextUp(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SHOW_NEXT_UP, enabled) }
    }
    
    fun getShowNextUp(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_NEXT_UP, true)
    }
    
    fun setItemsPerPage(count: Int) {
        sharedPreferences.edit { putInt(KEY_ITEMS_PER_PAGE, count) }
    }
    
    fun getItemsPerPage(): Int {
        return sharedPreferences.getInt(KEY_ITEMS_PER_PAGE, 20)
    }
    
    fun setHiddenLibraryIds(libraryIds: Set<String>) {
        sharedPreferences.edit { putStringSet(KEY_HIDDEN_LIBRARY_IDS, libraryIds) }
    }
    
    fun getHiddenLibraryIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_HIDDEN_LIBRARY_IDS, emptySet()) ?: emptySet()
    }
    
    // ========== Video Player Settings ==========
    
    fun setPreferredVideoQuality(quality: VideoQuality) {
        sharedPreferences.edit { putString(KEY_VIDEO_QUALITY, quality.name) }
    }
    
    fun getPreferredVideoQuality(): VideoQuality {
        val qualityName = sharedPreferences.getString(KEY_VIDEO_QUALITY, VideoQuality.AUTO.name)
        return try {
            VideoQuality.valueOf(qualityName ?: VideoQuality.AUTO.name)
        } catch (e: IllegalArgumentException) {
            VideoQuality.AUTO
        }
    }
    
    fun setPreferDirectPlay(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_PREFER_DIRECT_PLAY, enabled) }
    }
    
    fun getPreferDirectPlay(): Boolean {
        return sharedPreferences.getBoolean(KEY_PREFER_DIRECT_PLAY, true)
    }
    
    fun setPreferredAudioLanguage(language: String) {
        sharedPreferences.edit { putString(KEY_PREFERRED_AUDIO_LANG, language) }
    }
    
    fun getPreferredAudioLanguage(): String {
        return sharedPreferences.getString(KEY_PREFERRED_AUDIO_LANG, "") ?: ""
    }
    
    fun setPreferredSubtitleLanguage(language: String) {
        sharedPreferences.edit { putString(KEY_PREFERRED_SUBTITLE_LANG, language) }
    }
    
    fun getPreferredSubtitleLanguage(): String {
        return sharedPreferences.getString(KEY_PREFERRED_SUBTITLE_LANG, "") ?: ""
    }
    
    fun setPreviewSource(source: org.introskipper.segmenteditor.ui.state.PreviewSource) {
        sharedPreferences.edit { putString(KEY_PREVIEW_SOURCE, source.name) }
    }
    
    fun getPreviewSource(): org.introskipper.segmenteditor.ui.state.PreviewSource {
        val sourceName = sharedPreferences.getString(KEY_PREVIEW_SOURCE, org.introskipper.segmenteditor.ui.state.PreviewSource.TRICKPLAY.name)
        return try {
            org.introskipper.segmenteditor.ui.state.PreviewSource.valueOf(sourceName ?: org.introskipper.segmenteditor.ui.state.PreviewSource.TRICKPLAY.name)
        } catch (e: IllegalArgumentException) {
            org.introskipper.segmenteditor.ui.state.PreviewSource.TRICKPLAY
        }
    }
    
    // ========== Tracking Preferences ==========
    
    fun setTrackSegmentEdits(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_TRACK_EDITS, enabled) }
    }
    
    fun getTrackSegmentEdits(): Boolean {
        return sharedPreferences.getBoolean(KEY_TRACK_EDITS, true)
    }
    
    fun setSendCrashReports(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SEND_CRASH_REPORTS, enabled) }
    }
    
    fun getSendCrashReports(): Boolean {
        return sharedPreferences.getBoolean(KEY_SEND_CRASH_REPORTS, false)
    }
    
    fun setSendAnalytics(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SEND_ANALYTICS, enabled) }
    }
    
    fun getSendAnalytics(): Boolean {
        return sharedPreferences.getBoolean(KEY_SEND_ANALYTICS, false)
    }
    
    // ========== Utility Methods ==========
    
    fun isConfigured(): Boolean {
        return !getServerUrl().isNullOrBlank() && !getApiKey().isNullOrBlank()
    }
    
    fun isLoggedIn(): Boolean {
        return isConfigured() && !getUserId().isNullOrBlank()
    }
    
    fun clear() {
        sharedPreferences.edit { clear() }
    }
    
    fun clearAuthentication() {
        sharedPreferences.edit {
            remove(KEY_API_KEY)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
        }
    }
    
    companion object {
        // Connection keys
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DEVICE_ID = "device_id"
        
        // Playback keys
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_SKIP_INTRO_AUTO = "skip_intro_auto"
        private const val KEY_SKIP_CREDITS_AUTO = "skip_credits_auto"
        private const val KEY_SHOW_SKIP_BUTTONS = "show_skip_buttons"
        
        // UI keys
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_USE_SYSTEM_LANGUAGE = "use_system_language"
        
        // Segment editor keys
        private const val KEY_DEFAULT_SEGMENT_DURATION = "default_segment_duration"
        private const val KEY_SHOW_TICKS = "show_ticks"
        private const val KEY_CONFIRM_DELETE = "confirm_delete"
        
        // Export keys
        private const val KEY_EXPORT_FORMAT = "export_format"
        private const val KEY_INCLUDE_METADATA = "include_metadata"
        private const val KEY_PRETTY_PRINT_JSON = "pretty_print_json"
        
        // Media library keys
        private const val KEY_SHOW_CONTINUE_WATCHING = "show_continue_watching"
        private const val KEY_SHOW_NEXT_UP = "show_next_up"
        private const val KEY_ITEMS_PER_PAGE = "items_per_page"
        private const val KEY_HIDDEN_LIBRARY_IDS = "hidden_library_ids"
        
        // Video player keys
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_PREFER_DIRECT_PLAY = "prefer_direct_play"
        private const val KEY_PREFERRED_AUDIO_LANG = "preferred_audio_lang"
        private const val KEY_PREFERRED_SUBTITLE_LANG = "preferred_subtitle_lang"
        private const val KEY_PREVIEW_SOURCE = "preview_source"
        
        // Tracking keys
        private const val KEY_TRACK_EDITS = "track_edits"
        private const val KEY_SEND_CRASH_REPORTS = "send_crash_reports"
        private const val KEY_SEND_ANALYTICS = "send_analytics"
    }
}
