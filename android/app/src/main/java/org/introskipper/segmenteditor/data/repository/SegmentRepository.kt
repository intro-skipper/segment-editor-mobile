/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.repository

import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.SegmentCreateRequest
import org.introskipper.segmenteditor.data.model.SegmentType
import retrofit2.Response

/**
 * Repository for managing segment operations.
 * Wraps the JellyfinApiService segment-related calls.
 */
class SegmentRepository(private val apiService: JellyfinApiService) {
    
    /**
     * Retrieves all segments for a media item
     * @param itemId The media item ID
     * @return Response containing list of segments
     */
    suspend fun getSegments(itemId: String): Response<List<Segment>> {
        val response = apiService.getSegments(itemId)
        return if (response.isSuccessful && response.body() != null) {
            Response.success(response.body()!!.items)
        } else {
            // Create an error response with the same code and error body
            val errorBody = response.errorBody()
            if (errorBody != null) {
                Response.error(response.code(), errorBody)
            } else {
                // If no error body, return empty list as success
                Response.success(emptyList())
            }
        }
    }
    
    /**
     * Creates a new segment
     * @param itemId The media item ID
     * @param segment The segment to create
     * @param providerId The provider ID (default: "IntroSkipper")
     * @return Response containing the created segment
     */
    suspend fun createSegment(itemId: String, segment: SegmentCreateRequest, providerId: String = "IntroSkipper"): Response<Segment> {
        return apiService.createSegment(itemId, segment, providerId)
    }
    
    /**
     * Deletes a segment
     * @param segmentId The segment ID to delete
     * @param itemId The media item ID
     * @param segmentType The segment type to delete
     * @return Response indicating success or failure
     */
    suspend fun deleteSegment(segmentId: String, itemId: String, segmentType: String): Response<Unit> {
        return apiService.deleteSegment(segmentId, itemId, segmentType)
    }
    
    /**
     * Gets segments as a Result, wrapping exceptions
     */
    suspend fun getSegmentsResult(itemId: String): Result<List<Segment>> {
        return try {
            val response = getSegments(itemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch segments: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Creates a segment and returns a Result
     */
    suspend fun createSegmentResult(itemId: String, segment: SegmentCreateRequest, providerId: String = "IntroSkipper"): Result<Segment> {
        return try {
            val response = createSegment(itemId, segment, providerId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Successfully created and got segment data back
                    Result.success(body)
                } else {
                    // Request succeeded but no body returned
                    // This is acceptable - return a segment based on what we sent
                    // The caller should call refreshSegments() to get actual server data including the ID
                    Result.success(Segment(
                        id = null, // Will be assigned by server, retrieved via refresh
                        itemId = segment.itemId,
                        type = SegmentType.apiValueToString(segment.type),
                        startTicks = segment.startTicks,
                        endTicks = segment.endTicks
                    ))
                }
            } else {
                Result.failure(Exception("Failed to create segment: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Deletes a segment and returns a Result
     */
    suspend fun deleteSegmentResult(segmentId: String, itemId: String, segmentType: String): Result<Unit> {
        return try {
            val response = deleteSegment(segmentId, itemId, segmentType)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete segment: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
