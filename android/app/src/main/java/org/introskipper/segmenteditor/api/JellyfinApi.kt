package org.introskipper.segmenteditor.api

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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {
    
    // ========== Segment Endpoints ==========
    
    @GET("MediaSegments/{itemId}")
    suspend fun getSegments(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") apiKey: String
    ): Response<SegmentResponse>
    
    @POST("MediaSegmentsApi/{itemId}")
    suspend fun createSegment(
        @Path("itemId") itemId: String,
        @Query("providerId") providerId: String,
        @Body segment: SegmentCreateRequest,
        @Header("X-Emby-Token") apiKey: String
    ): Response<Segment>
    
    @DELETE("MediaSegmentsApi/{segmentId}")
    suspend fun deleteSegment(
        @Path("segmentId") segmentId: String,
        @Query("itemId") itemId: String,
        @Query("type") segmentType: String,
        @Header("X-Emby-Token") apiKey: String
    ): Response<Unit>
    
    // ========== Authentication Endpoints ==========
    
    @POST("Users/AuthenticateByName")
    suspend fun authenticate(
        @Body request: AuthenticationRequest,
        @Header("X-Emby-Authorization") authHeader: String
    ): Response<AuthenticationResult>
    
    @GET("System/Info")
    suspend fun getSystemInfo(
        @Header("X-Emby-Token") apiKey: String
    ): Response<ServerInfo>
    
    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<PublicSystemInfo>
    
    @GET("Users")
    suspend fun getUsers(
        @Header("X-Emby-Token") apiKey: String
    ): Response<List<User>>
    
    @GET("Users/{userId}")
    suspend fun getUserById(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") apiKey: String
    ): Response<User>
    
    // ========== Media Discovery Endpoints ==========
    
    @GET("Items")
    suspend fun getItems(
        @Query("userId") userId: String? = null,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("fields") fields: String? = null,
        @Query("filters") filters: String? = null,
        @Header("X-Emby-Token") apiKey: String
    ): Response<ItemsResponse>
    
    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("fields") fields: String? = null,
        @Header("X-Emby-Token") apiKey: String
    ): Response<MediaItem>
    
    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("fields") fields: String? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("limit") limit: Int? = null,
        @Header("X-Emby-Token") apiKey: String
    ): Response<ItemsResponse>
    
    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("fields") fields: String? = null,
        @Header("X-Emby-Token") apiKey: String
    ): Response<ItemsResponse>
    
    @GET("Users/{userId}/Views")
    suspend fun getLibraries(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") apiKey: String
    ): Response<ItemsResponse>
}
