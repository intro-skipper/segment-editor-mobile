package org.introskipper.segmenteditor.data.repository

import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.SegmentCreateRequest
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
        return apiService.getSegments(itemId)
    }
    
    /**
     * Creates a new segment
     * @param segment The segment to create
     * @return Response containing the created segment
     */
    suspend fun createSegment(segment: SegmentCreateRequest): Response<Segment> {
        return apiService.createSegment(segment)
    }
    
    /**
     * Updates an existing segment
     * @param itemId The media item ID
     * @param segmentType The segment type
     * @param segment The updated segment data
     * @return Response containing the updated segment
     */
    suspend fun updateSegment(
        itemId: String,
        segmentType: String,
        segment: SegmentCreateRequest
    ): Response<Segment> {
        return apiService.updateSegment(itemId, segmentType, segment)
    }
    
    /**
     * Deletes a segment
     * @param itemId The media item ID
     * @param segmentType The segment type to delete
     * @return Response indicating success or failure
     */
    suspend fun deleteSegment(itemId: String, segmentType: String): Response<Unit> {
        return apiService.deleteSegment(itemId, segmentType)
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
    suspend fun createSegmentResult(segment: SegmentCreateRequest): Result<Segment> {
        return try {
            val response = createSegment(segment)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create segment: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Updates a segment and returns a Result
     */
    suspend fun updateSegmentResult(
        itemId: String,
        segmentType: String,
        segment: SegmentCreateRequest
    ): Result<Segment> {
        return try {
            val response = updateSegment(itemId, segmentType, segment)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update segment: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Deletes a segment and returns a Result
     */
    suspend fun deleteSegmentResult(itemId: String, segmentType: String): Result<Unit> {
        return try {
            val response = deleteSegment(itemId, segmentType)
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
