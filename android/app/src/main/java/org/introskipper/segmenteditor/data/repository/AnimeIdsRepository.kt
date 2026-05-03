/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.introskipper.segmenteditor.storage.SecurePreferences
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeIdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "anime_ids.json")
    
    private var cachedIds: List<Map<String, Any>>? = null

    suspend fun getAnimeIds(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        if (cachedIds != null) return@withContext cachedIds!!

        // Try to load from local cache first
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                cachedIds = gson.fromJson(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read cached anime IDs", e)
            }
        }

        // Check for updates if needed or if nothing was loaded
        refreshCacheIfNeeded()

        return@withContext cachedIds ?: emptyList()
    }

    private suspend fun refreshCacheIfNeeded() {
        try {
            val lastModified = securePreferences.getAnimeIdsLastModified()
            val requestBuilder = Request.Builder().url(ANIME_IDS_URL)
            
            if (lastModified != null && cacheFile.exists()) {
                requestBuilder.header("If-Modified-Since", lastModified)
            }

            val request = requestBuilder.build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 304) {
                    Log.d(TAG, "Anime IDs are up to date (304 Not Modified)")
                    return
                }

                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch anime IDs: ${response.code}")
                    return
                }

                val body = response.body?.string() ?: return
                val newLastModified = response.header("Last-Modified")

                cacheFile.writeText(body)
                if (newLastModified != null) {
                    securePreferences.saveAnimeIdsLastModified(newLastModified)
                }
                
                cachedIds = gson.fromJson(body, object : TypeToken<List<Map<String, Any>>>() {}.type)
                Log.d(TAG, "Successfully updated anime IDs cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing anime IDs cache", e)
        }
    }

    /**
     * Finds mapping IDs for a given provider series ID.
     * 
     * Note: The source data from Kometa-Team uses series-level IDs for TVDB and IMDB.
     *
     * @param providerName The name of the provider (e.g., "tvdb", "tmdb", "anilist")
     * @param providerId The series ID from the provider
     * @return A map of all associated IDs, or null if not found
     */
    suspend fun findIds(providerName: String, providerId: Any): Map<String, Any>? {
        val ids = getAnimeIds()
        val key = when (providerName.lowercase()) {
            "tvdb" -> "thetvdb_id"
            "tmdb" -> "themoviedb_id"
            "anilist" -> "anilist_id"
            "mal", "myanimelist" -> "myanimelist_id"
            "imdb" -> "imdb_id"
            else -> providerName
        }

        return ids.find { 
            val value = it[key]
            if (value is Double && providerId is Number) {
                value.toInt() == providerId.toInt()
            } else {
                value?.toString() == providerId.toString()
            }
        }
    }

    companion object {
        private const val TAG = "AnimeIdsRepository"
        private const val ANIME_IDS_URL = "https://raw.githubusercontent.com/Kometa-Team/Anime-IDs/master/anime_ids.json"
    }
}
