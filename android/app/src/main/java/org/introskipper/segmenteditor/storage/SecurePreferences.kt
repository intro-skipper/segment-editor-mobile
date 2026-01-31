package org.introskipper.segmenteditor.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
        sharedPreferences.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun getServerUrl(): String? {
        return sharedPreferences.getString(KEY_SERVER_URL, null)
    }
    
    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }
    
    fun saveUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }
    
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    fun saveUsername(username: String) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
    }
    
    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }
    
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }
    
    fun getDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }
    
    // ========== Playback Settings ==========
    
    fun setAutoPlayNextEpisode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
    }
    
    fun getAutoPlayNextEpisode(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_PLAY_NEXT, true)
    }
    
    fun setSkipIntroAutomatically(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SKIP_INTRO_AUTO, enabled).apply()
    }
    
    fun getSkipIntroAutomatically(): Boolean {
        return sharedPreferences.getBoolean(KEY_SKIP_INTRO_AUTO, true)
    }
    
    fun setSkipCreditsAutomatically(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SKIP_CREDITS_AUTO, enabled).apply()
    }
    
    fun getSkipCreditsAutomatically(): Boolean {
        return sharedPreferences.getBoolean(KEY_SKIP_CREDITS_AUTO, false)
    }
    
    fun setShowSkipButtons(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_SKIP_BUTTONS, enabled).apply()
    }
    
    fun getShowSkipButtons(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_SKIP_BUTTONS, true)
    }
    
    // ========== UI Settings ==========
    
    fun setTheme(theme: AppTheme) {
        sharedPreferences.edit().putString(KEY_THEME, theme.name).apply()
    }
    
    fun getTheme(): AppTheme {
        val themeName = sharedPreferences.getString(KEY_THEME, AppTheme.SYSTEM.name)
        return try {
            AppTheme.valueOf(themeName ?: AppTheme.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }
    
    fun setLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language).apply()
    }
    
    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, "en") ?: "en"
    }
    
    fun setUseSystemLanguage(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_USE_SYSTEM_LANGUAGE, enabled).apply()
    }
    
    fun getUseSystemLanguage(): Boolean {
        return sharedPreferences.getBoolean(KEY_USE_SYSTEM_LANGUAGE, true)
    }
    
    // ========== Segment Editor Settings ==========
    
    fun setDefaultSegmentDuration(seconds: Double) {
        sharedPreferences.edit().putFloat(KEY_DEFAULT_SEGMENT_DURATION, seconds.toFloat()).apply()
    }
    
    fun getDefaultSegmentDuration(): Double {
        return sharedPreferences.getFloat(KEY_DEFAULT_SEGMENT_DURATION, 90f).toDouble()
    }
    
    fun setShowTimestampsInTicks(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_TICKS, enabled).apply()
    }
    
    fun getShowTimestampsInTicks(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_TICKS, false)
    }
    
    fun setConfirmBeforeDelete(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_CONFIRM_DELETE, enabled).apply()
    }
    
    fun getConfirmBeforeDelete(): Boolean {
        return sharedPreferences.getBoolean(KEY_CONFIRM_DELETE, true)
    }
    
    // ========== Export Settings ==========
    
    fun setExportFormat(format: ExportFormat) {
        sharedPreferences.edit().putString(KEY_EXPORT_FORMAT, format.name).apply()
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
        sharedPreferences.edit().putBoolean(KEY_INCLUDE_METADATA, enabled).apply()
    }
    
    fun getIncludeMetadata(): Boolean {
        return sharedPreferences.getBoolean(KEY_INCLUDE_METADATA, true)
    }
    
    fun setPrettyPrintJson(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PRETTY_PRINT_JSON, enabled).apply()
    }
    
    fun getPrettyPrintJson(): Boolean {
        return sharedPreferences.getBoolean(KEY_PRETTY_PRINT_JSON, true)
    }
    
    // ========== Media Library Settings ==========
    
    fun setShowContinueWatching(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_CONTINUE_WATCHING, enabled).apply()
    }
    
    fun getShowContinueWatching(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_CONTINUE_WATCHING, true)
    }
    
    fun setShowNextUp(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_NEXT_UP, enabled).apply()
    }
    
    fun getShowNextUp(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_NEXT_UP, true)
    }
    
    fun setItemsPerPage(count: Int) {
        sharedPreferences.edit().putInt(KEY_ITEMS_PER_PAGE, count).apply()
    }
    
    fun getItemsPerPage(): Int {
        return sharedPreferences.getInt(KEY_ITEMS_PER_PAGE, 20)
    }
    
    // ========== Video Player Settings ==========
    
    fun setPreferredVideoQuality(quality: VideoQuality) {
        sharedPreferences.edit().putString(KEY_VIDEO_QUALITY, quality.name).apply()
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
        sharedPreferences.edit().putBoolean(KEY_PREFER_DIRECT_PLAY, enabled).apply()
    }
    
    fun getPreferDirectPlay(): Boolean {
        return sharedPreferences.getBoolean(KEY_PREFER_DIRECT_PLAY, true)
    }
    
    fun setPreferredAudioLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_PREFERRED_AUDIO_LANG, language).apply()
    }
    
    fun getPreferredAudioLanguage(): String {
        return sharedPreferences.getString(KEY_PREFERRED_AUDIO_LANG, "") ?: ""
    }
    
    fun setPreferredSubtitleLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_PREFERRED_SUBTITLE_LANG, language).apply()
    }
    
    fun getPreferredSubtitleLanguage(): String {
        return sharedPreferences.getString(KEY_PREFERRED_SUBTITLE_LANG, "") ?: ""
    }
    
    // ========== Tracking Preferences ==========
    
    fun setTrackSegmentEdits(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_TRACK_EDITS, enabled).apply()
    }
    
    fun getTrackSegmentEdits(): Boolean {
        return sharedPreferences.getBoolean(KEY_TRACK_EDITS, true)
    }
    
    fun setSendCrashReports(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SEND_CRASH_REPORTS, enabled).apply()
    }
    
    fun getSendCrashReports(): Boolean {
        return sharedPreferences.getBoolean(KEY_SEND_CRASH_REPORTS, false)
    }
    
    fun setSendAnalytics(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SEND_ANALYTICS, enabled).apply()
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
        sharedPreferences.edit().clear().apply()
    }
    
    fun clearAuthentication() {
        sharedPreferences.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
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
        
        // Video player keys
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_PREFER_DIRECT_PLAY = "prefer_direct_play"
        private const val KEY_PREFERRED_AUDIO_LANG = "preferred_audio_lang"
        private const val KEY_PREFERRED_SUBTITLE_LANG = "preferred_subtitle_lang"
        
        // Tracking keys
        private const val KEY_TRACK_EDITS = "track_edits"
        private const val KEY_SEND_CRASH_REPORTS = "send_crash_reports"
        private const val KEY_SEND_ANALYTICS = "send_analytics"
    }
}
