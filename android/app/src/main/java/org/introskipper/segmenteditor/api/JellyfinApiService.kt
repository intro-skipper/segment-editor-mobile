package org.introskipper.segmenteditor.api

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.introskipper.segmenteditor.BuildConfig
import org.introskipper.segmenteditor.data.model.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class JellyfinApiService(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val api: JellyfinApi
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(JellyfinApi::class.java)
    }
    
    // ========== Segment Operations ==========
    
    suspend fun getSegments(itemId: String): Response<List<Segment>> {
        return api.getSegments(itemId, apiKey)
    }
    
    suspend fun createSegment(segment: SegmentCreateRequest): Response<Segment> {
        return api.createSegment(segment, apiKey)
    }
    
    suspend fun updateSegment(
        itemId: String,
        segmentType: String,
        segment: SegmentCreateRequest
    ): Response<Segment> {
        return api.updateSegment(itemId, segmentType, segment, apiKey)
    }
    
    suspend fun deleteSegment(itemId: String, segmentType: String): Response<Unit> {
        return api.deleteSegment(itemId, segmentType, apiKey)
    }
    
    // ========== Authentication Operations ==========
    
    suspend fun authenticate(username: String, password: String, deviceId: String, deviceName: String, appVersion: String): Response<AuthenticationResult> {
        val authHeader = buildAuthHeader(deviceId, deviceName, appVersion)
        val request = AuthenticationRequest(username, password)
        return api.authenticate(request, authHeader)
    }
    
    suspend fun getSystemInfo(): Response<ServerInfo> {
        return api.getSystemInfo(apiKey)
    }
    
    suspend fun getPublicSystemInfo(): Response<PublicSystemInfo> {
        return api.getPublicSystemInfo()
    }
    
    suspend fun getUserById(userId: String): Response<User> {
        return api.getUserById(userId, apiKey)
    }
    
    /**
     * Validates the API key by attempting to fetch system info
     */
    suspend fun validateApiKey(): Boolean {
        return try {
            val response = getSystemInfo()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    // ========== Media Discovery Operations ==========
    
    suspend fun getItems(
        userId: String? = null,
        parentId: String? = null,
        includeItemTypes: List<String>? = null,
        recursive: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
        searchTerm: String? = null,
        fields: List<String>? = null,
        filters: List<String>? = null
    ): Response<ItemsResponse> {
        return api.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = includeItemTypes?.joinToString(","),
            recursive = recursive,
            sortBy = sortBy,
            sortOrder = sortOrder,
            startIndex = startIndex,
            limit = limit,
            searchTerm = searchTerm,
            fields = fields?.joinToString(","),
            filters = filters?.joinToString(","),
            apiKey = apiKey
        )
    }
    
    suspend fun getItem(userId: String, itemId: String, fields: List<String>? = null): Response<MediaItem> {
        return api.getItem(userId, itemId, fields?.joinToString(","), apiKey)
    }
    
    suspend fun getEpisodes(
        seriesId: String,
        userId: String,
        seasonId: String? = null,
        fields: List<String>? = null,
        startIndex: Int? = null,
        limit: Int? = null
    ): Response<ItemsResponse> {
        return api.getEpisodes(seriesId, userId, seasonId, fields?.joinToString(","), startIndex, limit, apiKey)
    }
    
    suspend fun getSeasons(seriesId: String, userId: String, fields: List<String>? = null): Response<ItemsResponse> {
        return api.getSeasons(seriesId, userId, fields?.joinToString(","), apiKey)
    }
    
    suspend fun getLibraries(userId: String): Response<ItemsResponse> {
        return api.getLibraries(userId, apiKey)
    }
    
    // ========== Image URL Builders ==========
    
    /**
     * Gets the primary image URL for an item
     */
    fun getPrimaryImageUrl(itemId: String, imageTag: String, maxWidth: Int? = null, maxHeight: Int? = null): String {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Items")
            .appendPath(itemId)
            .appendPath("Images")
            .appendPath("Primary")
            .apply {
                appendQueryParameter("tag", imageTag)
                maxWidth?.let { appendQueryParameter("maxWidth", it.toString()) }
                maxHeight?.let { appendQueryParameter("maxHeight", it.toString()) }
            }
            .build()
        return uri.toString()
    }
    
    /**
     * Gets the backdrop image URL for an item
     */
    fun getBackdropUrl(itemId: String, backdropIndex: Int = 0, maxWidth: Int? = null, maxHeight: Int? = null): String {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Items")
            .appendPath(itemId)
            .appendPath("Images")
            .appendPath("Backdrop")
            .appendPath(backdropIndex.toString())
            .apply {
                maxWidth?.let { appendQueryParameter("maxWidth", it.toString()) }
                maxHeight?.let { appendQueryParameter("maxHeight", it.toString()) }
            }
            .build()
        return uri.toString()
    }
    
    /**
     * Gets the user avatar image URL
     */
    fun getUserImageUrl(userId: String, imageTag: String, maxWidth: Int? = null, maxHeight: Int? = null): String {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Users")
            .appendPath(userId)
            .appendPath("Images")
            .appendPath("Primary")
            .apply {
                appendQueryParameter("tag", imageTag)
                maxWidth?.let { appendQueryParameter("maxWidth", it.toString()) }
                maxHeight?.let { appendQueryParameter("maxHeight", it.toString()) }
            }
            .build()
        return uri.toString()
    }
    
    // ========== Streaming URL Builders ==========
    
    /**
     * Gets the direct play URL for a media item
     */
    fun getDirectPlayUrl(itemId: String, mediaSourceId: String? = null, container: String? = null): String {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Videos")
            .appendPath(itemId)
            .appendPath("stream")
            .apply {
                appendQueryParameter("Static", "true")
                appendQueryParameter("api_key", apiKey)
                mediaSourceId?.let { appendQueryParameter("MediaSourceId", it) }
                container?.let { appendQueryParameter("Container", it) }
            }
            .build()
        return uri.toString()
    }
    
    /**
     * Gets the HLS playlist URL for a media item
     */
    fun getHlsPlaylistUrl(
        itemId: String,
        mediaSourceId: String? = null,
        deviceId: String = "segment-editor-android",
        maxStreamingBitrate: Int = 140000000
    ): String {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Videos")
            .appendPath(itemId)
            .appendPath("master.m3u8")
            .apply {
                appendQueryParameter("api_key", apiKey)
                appendQueryParameter("DeviceId", deviceId)
                appendQueryParameter("MaxStreamingBitrate", maxStreamingBitrate.toString())
                mediaSourceId?.let { appendQueryParameter("MediaSourceId", it) }
            }
            .build()
        return uri.toString()
    }
    
    /**
     * Gets the direct stream URL for a media item
     */
    fun getDirectStreamUrl(itemId: String, mediaSourceId: String? = null, container: String? = null): String {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Videos")
            .appendPath(itemId)
            .appendPath("stream")
            .apply {
                appendQueryParameter("Static", "false")
                appendQueryParameter("api_key", apiKey)
                mediaSourceId?.let { appendQueryParameter("MediaSourceId", it) }
                container?.let { appendQueryParameter("Container", it) }
            }
            .build()
        return uri.toString()
    }
    
    // ========== Connection Testing ==========
    
    suspend fun testConnection(): Response<ServerInfo> {
        return getSystemInfo()
    }
    
    // ========== Helper Methods ==========
    
    private fun buildAuthHeader(deviceId: String, deviceName: String, appVersion: String): String {
        return "MediaBrowser Client=\"Segment Editor Android\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$appVersion\""
    }
    
    companion object {
        // Common field sets for queries
        val BASIC_FIELDS = listOf("PrimaryImageAspectRatio", "ImageTags")
        val DETAIL_FIELDS = listOf(
            "Overview", "PrimaryImageAspectRatio", "ImageTags", "MediaSources",
            "MediaStreams", "Path", "Container", "RunTimeTicks"
        )
        val EPISODE_FIELDS = listOf(
            "Overview", "PrimaryImageAspectRatio", "ImageTags", "MediaSources",
            "SeriesName", "SeasonName", "IndexNumber", "ParentIndexNumber"
        )
    }
}
