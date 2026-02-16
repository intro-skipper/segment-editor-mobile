package org.introskipper.segmenteditor.api

import android.net.Uri
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.introskipper.segmenteditor.BuildConfig
import org.introskipper.segmenteditor.data.model.AuthenticationRequest
import org.introskipper.segmenteditor.data.model.AuthenticationResult
import org.introskipper.segmenteditor.data.model.ItemsResponse
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.PublicSystemInfo
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.SegmentCreateRequest
import org.introskipper.segmenteditor.data.model.SegmentResponse
import org.introskipper.segmenteditor.data.model.ServerInfo
import org.introskipper.segmenteditor.data.model.User
import org.introskipper.segmenteditor.storage.SecurePreferences
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class JellyfinApiService {
    private var api: JellyfinApi? = null
    private var securePreferences: SecurePreferences? = null
    private var currentBaseUrl: String? = null
    
    constructor(baseUrl: String, apiKey: String) {
        initializeApi(baseUrl)
    }
    
    constructor(securePreferences: SecurePreferences) {
        this.securePreferences = securePreferences
        val serverUrl = securePreferences.getServerUrl()
        if (!serverUrl.isNullOrBlank()) {
            initializeApi(serverUrl)
        }
    }
    
    private fun initializeApi(baseUrl: String) {
        currentBaseUrl = baseUrl
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
    
    fun updateBaseUrl(baseUrl: String) {
        if (currentBaseUrl != baseUrl) {
            initializeApi(baseUrl)
        }
    }
    
    private fun getApiKey(): String {
        return securePreferences?.getApiKey() ?: ""
    }
    
    private fun ensureInitialized() {
        if (api == null) {
            val serverUrl = securePreferences?.getServerUrl()
            if (!serverUrl.isNullOrBlank()) {
                initializeApi(serverUrl)
            } else {
                throw IllegalStateException("API not initialized. Server URL is not configured.")
            }
        }
    }
    
    // ========== Segment Operations ==========
    
    suspend fun getSegments(itemId: String): Response<SegmentResponse> {
        ensureInitialized()
        return api!!.getSegments(itemId, getApiKey())
    }
    
    suspend fun createSegment(itemId: String, segment: SegmentCreateRequest, providerId: String = "IntroSkipper"): Response<Segment> {
        ensureInitialized()
        return api!!.createSegment(itemId, providerId, segment, getApiKey())
    }
    
    suspend fun deleteSegment(segmentId: String, itemId: String, segmentType: String): Response<Unit> {
        ensureInitialized()
        return api!!.deleteSegment(segmentId, itemId, segmentType, getApiKey())
    }
    
    // ========== Authentication Operations ==========
    
    suspend fun authenticate(username: String, password: String, deviceId: String, deviceName: String, appVersion: String): Response<AuthenticationResult> {
        ensureInitialized()
        val authHeader = buildAuthHeader(deviceId, deviceName, appVersion)
        val request = AuthenticationRequest(username, password)
        return api!!.authenticate(request, authHeader)
    }
    
    suspend fun getSystemInfo(): Response<ServerInfo> {
        ensureInitialized()
        return api!!.getSystemInfo(getApiKey())
    }
    
    suspend fun getPublicSystemInfo(): Response<PublicSystemInfo> {
        ensureInitialized()
        return api!!.getPublicSystemInfo()
    }
    
    suspend fun getUsers(): Response<List<User>> {
        ensureInitialized()
        return api!!.getUsers(getApiKey())
    }
    
    suspend fun getUserById(userId: String): Response<User> {
        ensureInitialized()
        return api!!.getUserById(userId, getApiKey())
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
        ensureInitialized()
        return api!!.getItems(
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
            apiKey = getApiKey()
        )
    }
    
    suspend fun getItem(userId: String, itemId: String, fields: List<String>? = null): Response<MediaItem> {
        ensureInitialized()
        return api!!.getItem(userId, itemId, fields?.joinToString(","), getApiKey())
    }
    
    suspend fun getEpisodes(
        seriesId: String,
        userId: String,
        seasonId: String? = null,
        fields: List<String>? = null,
        startIndex: Int? = null,
        limit: Int? = null
    ): Response<ItemsResponse> {
        ensureInitialized()
        return api!!.getEpisodes(seriesId, userId, seasonId, fields?.joinToString(","), startIndex, limit, getApiKey())
    }
    
    suspend fun getSeasons(seriesId: String, userId: String, fields: List<String>? = null): Response<ItemsResponse> {
        ensureInitialized()
        return api!!.getSeasons(seriesId, userId, fields?.joinToString(","), getApiKey())
    }
    
    suspend fun getLibraries(userId: String): Response<ItemsResponse> {
        ensureInitialized()
        return api!!.getLibraries(userId, getApiKey())
    }
    
    // ========== Image URL Builders ==========
    
    /**
     * Gets the primary image URL for an item
     */
    fun getPrimaryImageUrl(itemId: String, imageTag: String, maxWidth: Int? = null, maxHeight: Int? = null): String {
        val baseUrl = currentBaseUrl ?: throw IllegalStateException("Base URL not set")
        val uri = baseUrl.toUri()
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
        val baseUrl = currentBaseUrl ?: throw IllegalStateException("Base URL not set")
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
        val baseUrl = currentBaseUrl ?: throw IllegalStateException("Base URL not set")
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
        val baseUrl = currentBaseUrl ?: throw IllegalStateException("Base URL not set")
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Videos")
            .appendPath(itemId)
            .appendPath("stream")
            .apply {
                appendQueryParameter("Static", "true")
                appendQueryParameter("api_key", getApiKey())
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
        val baseUrl = currentBaseUrl ?: throw IllegalStateException("Base URL not set")
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Videos")
            .appendPath(itemId)
            .appendPath("master.m3u8")
            .apply {
                appendQueryParameter("api_key", getApiKey())
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
        val baseUrl = currentBaseUrl ?: throw IllegalStateException("Base URL not set")
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("Videos")
            .appendPath(itemId)
            .appendPath("stream")
            .apply {
                appendQueryParameter("Static", "false")
                appendQueryParameter("api_key", getApiKey())
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
            "MediaStreams", "Path", "Container", "RunTimeTicks", "Trickplay"
        )
        val EPISODE_FIELDS = listOf(
            "Overview", "PrimaryImageAspectRatio", "ImageTags", "MediaSources",
            "SeriesName", "SeasonName", "IndexNumber", "ParentIndexNumber"
        )
    }
}
