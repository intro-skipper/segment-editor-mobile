package org.introskipper.segmenteditor.api

import org.introskipper.segmenteditor.model.Segment
import org.introskipper.segmenteditor.model.SegmentCreateRequest
import retrofit2.Response
import retrofit2.http.*

interface JellyfinApi {
    
    @GET("MediaSegments/{itemId}")
    suspend fun getSegments(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") apiKey: String
    ): Response<List<Segment>>
    
    @POST("MediaSegments")
    suspend fun createSegment(
        @Body segment: SegmentCreateRequest,
        @Header("X-Emby-Token") apiKey: String
    ): Response<Segment>
    
    @PUT("MediaSegments/{itemId}/{segmentType}")
    suspend fun updateSegment(
        @Path("itemId") itemId: String,
        @Path("segmentType") segmentType: String,
        @Body segment: SegmentCreateRequest,
        @Header("X-Emby-Token") apiKey: String
    ): Response<Segment>
    
    @DELETE("MediaSegments/{itemId}/{segmentType}")
    suspend fun deleteSegment(
        @Path("itemId") itemId: String,
        @Path("segmentType") segmentType: String,
        @Header("X-Emby-Token") apiKey: String
    ): Response<Unit>
    
    @GET("System/Info")
    suspend fun getSystemInfo(
        @Header("X-Emby-Token") apiKey: String
    ): Response<Map<String, Any>>
}
