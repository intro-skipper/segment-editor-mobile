package org.introskipper.segmenteditor.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
        // Fallback to regular SharedPreferences if encryption fails
        context.getSharedPreferences("jellyfin_prefs", Context.MODE_PRIVATE)
    }
    
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
    
    fun isConfigured(): Boolean {
        return !getServerUrl().isNullOrBlank() && !getApiKey().isNullOrBlank()
    }
    
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
    
    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
    }
}
