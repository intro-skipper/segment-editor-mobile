package org.introskipper.segmenteditor.data.repository

import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.model.ItemsResponse
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.storage.SecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Jellyfin operations, handling both authentication and media access.
 * Wraps MediaRepository and provides convenience methods with automatic user ID.
 */
@Singleton
class JellyfinRepository @Inject constructor(
    private val apiService: JellyfinApiService,
    private val securePreferences: SecurePreferences
) {
    private val mediaRepository = MediaRepository(apiService)
    
    private fun getUserId(): String {
        return securePreferences.getUserId() ?: throw IllegalStateException("User not authenticated")
    }
    
    /**
     * Gets media items with optional filters
     */
    suspend fun getMediaItems(
        searchTerm: String? = null,
        parentIds: List<String>? = null,
        startIndex: Int = 0,
        limit: Int = 20,
        includeItemTypes: List<String> = listOf("Movie", "Series", "Episode")
    ): ItemsResponse {
        val userId = getUserId()
        
        // If multiple parent IDs, we need to make multiple requests and combine
        if (parentIds != null && parentIds.size > 1) {
            val allItems = mutableListOf<MediaItem>()
            var totalCount = 0
            
            for (parentId in parentIds) {
                val response = mediaRepository.getItems(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = includeItemTypes,
                    recursive = true,
                    searchTerm = searchTerm,
                    startIndex = startIndex,
                    limit = limit,
                    fields = JellyfinApiService.DETAIL_FIELDS
                )
                
                if (response.isSuccessful) {
                    response.body()?.let { itemsResponse ->
                        allItems.addAll(itemsResponse.items)
                        totalCount += itemsResponse.totalRecordCount
                    }
                }
            }
            
            return ItemsResponse(
                items = allItems,
                totalRecordCount = totalCount,
                startIndex = startIndex
            )
        } else {
            // Single or no parent ID
            val response = mediaRepository.getItems(
                userId = userId,
                parentId = parentIds?.firstOrNull(),
                includeItemTypes = includeItemTypes,
                recursive = true,
                searchTerm = searchTerm,
                startIndex = startIndex,
                limit = limit,
                fields = JellyfinApiService.DETAIL_FIELDS
            )
            
            if (response.isSuccessful) {
                return response.body() ?: ItemsResponse(emptyList(), 0, 0)
            } else {
                throw Exception("Failed to fetch items: ${response.code()} ${response.message()}")
            }
        }
    }
    
    /**
     * Gets user libraries (collections)
     */
    suspend fun getLibraries(): List<MediaItem> {
        val userId = getUserId()
        val response = mediaRepository.getLibraries(userId)
        
        if (response.isSuccessful) {
            return response.body()?.items ?: emptyList()
        } else {
            throw Exception("Failed to fetch libraries: ${response.code()} ${response.message()}")
        }
    }
    
    /**
     * Gets a specific media item by ID
     */
    suspend fun getMediaItem(itemId: String): MediaItem {
        val userId = getUserId()
        val response = mediaRepository.getItem(
            userId = userId,
            itemId = itemId,
            fields = JellyfinApiService.DETAIL_FIELDS
        )
        
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Item not found")
        } else {
            throw Exception("Failed to fetch item: ${response.code()} ${response.message()}")
        }
    }
    
    /**
     * Gets episodes for a TV series
     */
    suspend fun getEpisodes(
        seriesId: String,
        seasonId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100
    ): ItemsResponse {
        val userId = getUserId()
        val response = mediaRepository.getEpisodes(
            seriesId = seriesId,
            userId = userId,
            seasonId = seasonId,
            fields = JellyfinApiService.DETAIL_FIELDS,
            startIndex = startIndex,
            limit = limit
        )
        
        if (response.isSuccessful) {
            return response.body() ?: ItemsResponse(emptyList(), 0, 0)
        } else {
            throw Exception("Failed to fetch episodes: ${response.code()} ${response.message()}")
        }
    }
    
    /**
     * Gets seasons for a TV series
     */
    suspend fun getSeasons(seriesId: String): ItemsResponse {
        val userId = getUserId()
        val response = mediaRepository.getSeasons(
            seriesId = seriesId,
            userId = userId,
            fields = JellyfinApiService.DETAIL_FIELDS
        )
        
        if (response.isSuccessful) {
            return response.body() ?: ItemsResponse(emptyList(), 0, 0)
        } else {
            throw Exception("Failed to fetch seasons: ${response.code()} ${response.message()}")
        }
    }
}
