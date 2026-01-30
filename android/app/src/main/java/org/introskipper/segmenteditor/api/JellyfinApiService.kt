package org.introskipper.segmenteditor.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.introskipper.segmenteditor.model.Segment
import org.introskipper.segmenteditor.model.SegmentCreateRequest
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
            level = HttpLoggingInterceptor.Level.BODY
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
    
    suspend fun testConnection(): Response<Map<String, Any>> {
        return api.getSystemInfo(apiKey)
    }
}
