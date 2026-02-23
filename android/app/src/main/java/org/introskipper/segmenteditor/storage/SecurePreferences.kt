package org.introskipper.segmenteditor.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.introskipper.segmenteditor.ui.state.AppTheme
import org.introskipper.segmenteditor.ui.state.ExportFormat

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

    fun setDynamicTranslationEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DYNAMIC_TRANSLATION, enabled) }
    }

    fun isDynamicTranslationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DYNAMIC_TRANSLATION, true)
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
    
    fun setPrettyPrintJson(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_PRETTY_PRINT_JSON, enabled) }
    }
    
    fun getPrettyPrintJson(): Boolean {
        return sharedPreferences.getBoolean(KEY_PRETTY_PRINT_JSON, true)
    }
    
    // ========== Media Library Settings ==========
    
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
    
    fun setPreferDirectPlay(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_PREFER_DIRECT_PLAY, enabled) }
    }
    
    fun getPreferDirectPlay(): Boolean {
        return sharedPreferences.getBoolean(KEY_PREFER_DIRECT_PLAY, true)
    }

    fun setPreferLocalPreviews(prefer: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_PREFER_LOCAL_PREVIEWS, prefer) }
    }

    fun getPreferLocalPreviews(): Boolean {
        return sharedPreferences.getBoolean(KEY_PREFER_LOCAL_PREVIEWS, false)
    }
    
    // ========== Utility Methods ==========
    
    fun isConfigured(): Boolean {
        return !getServerUrl().isNullOrBlank() && !getApiKey().isNullOrBlank()
    }
    
    fun isLoggedIn(): Boolean {
        return isConfigured() && !getUserId().isNullOrBlank()
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
        
        // UI keys
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC_TRANSLATION = "dynamic_translation"
        
        // Export keys
        private const val KEY_EXPORT_FORMAT = "export_format"
        private const val KEY_PRETTY_PRINT_JSON = "pretty_print_json"
        
        // Media library keys
        private const val KEY_ITEMS_PER_PAGE = "items_per_page"
        private const val KEY_HIDDEN_LIBRARY_IDS = "hidden_library_ids"
        
        // Video player keys
        private const val KEY_PREFER_DIRECT_PLAY = "prefer_direct_play"
        private const val KEY_PREFER_LOCAL_PREVIEWS = "prefer_local_previews"
    }
}
