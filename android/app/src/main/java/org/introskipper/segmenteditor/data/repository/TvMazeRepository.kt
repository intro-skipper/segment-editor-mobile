/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvMazeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    data class TvMazeShow(val tvdbId: Int?, val imdbId: String?)

    private data class CacheEntry(
        @SerializedName("show") val show: TvMazeShow?,
        @SerializedName("timestamp") val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }

    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "tvmaze_cache.json")
    private val cache = mutableMapOf<String, CacheEntry>()
    private var cacheLoaded = false

    /**
     * Looks up a show by its TVDB series ID and returns the associated IMDB ID if available.
     * Results are cached on disk for [CACHE_TTL_MS] to avoid redundant network calls.
     */
    suspend fun lookupByTvdbId(tvdbId: Int): TvMazeShow? =
        withContext(Dispatchers.IO) {
            lookupShow("tvdb", tvdbId.toString(), "https://api.tvmaze.com/lookup/shows?thetvdb=$tvdbId")
        }

    /**
     * Looks up a show by its IMDB series ID and returns the associated TVDB ID if available.
     * Results are cached on disk for [CACHE_TTL_MS] to avoid redundant network calls.
     */
    suspend fun lookupByImdbId(imdbId: String): TvMazeShow? =
        withContext(Dispatchers.IO) {
            lookupShow("imdb", imdbId, "https://api.tvmaze.com/lookup/shows?imdb=$imdbId")
        }

    private fun lookupShow(provider: String, id: String, url: String): TvMazeShow? {
        val cacheKey = "$provider:$id"
        loadCacheIfNeeded()

        val cached = cache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return cached.show
        }

        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // Not found — cache null so we don't retry on every submission
                    updateCache(cacheKey, null)
                    return@use null
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "TVMaze lookup failed for $provider:$id: HTTP ${response.code}")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                val json = gson.fromJson(body, Map::class.java)
                val externals = json["externals"] as? Map<*, *>
                val show = TvMazeShow(
                    tvdbId = (externals?.get("thetvdb") as? Double)?.toInt(),
                    imdbId = externals?.get("imdb") as? String
                )
                updateCache(cacheKey, show)
                Log.d(TAG, "TVMaze resolved $provider:$id → tvdb=${show.tvdbId}, imdb=${show.imdbId}")
                show
            }
        } catch (e: Exception) {
            Log.e(TAG, "TVMaze lookup error for $provider:$id", e)
            null
        }
    }

    private fun loadCacheIfNeeded() {
        if (cacheLoaded) return
        cacheLoaded = true
        try {
            if (cacheFile.exists()) {
                val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
                val loaded: Map<String, CacheEntry> = gson.fromJson(cacheFile.readText(), type)
                cache.putAll(loaded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TVMaze cache", e)
        }
    }

    private fun updateCache(key: String, show: TvMazeShow?) {
        cache[key] = CacheEntry(show, System.currentTimeMillis())
        try {
            cacheFile.writeText(gson.toJson(cache))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save TVMaze cache", e)
        }
    }

    companion object {
        private const val TAG = "TvMazeRepository"
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }
}
