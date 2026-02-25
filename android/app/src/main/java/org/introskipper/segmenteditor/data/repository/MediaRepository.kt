/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.repository

import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.model.ItemsResponse
import org.introskipper.segmenteditor.data.model.MediaItem
import retrofit2.Response

/**
 * Repository for managing media discovery operations.
 * Wraps the JellyfinApiService media-related calls.
 */
class MediaRepository(
    private val apiService: JellyfinApiService
) {
    
    /**
     * Gets media items with various filters
     */
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
        return apiService.getItems(
            userId, parentId, includeItemTypes, recursive, sortBy, sortOrder,
            startIndex, limit, searchTerm, fields, filters
        )
    }
    
    /**
     * Gets a specific media item by ID
     */
    suspend fun getItem(userId: String, itemId: String, fields: List<String>? = null): Response<MediaItem> {
        return apiService.getItem(userId, itemId, fields)
    }
    
    /**
     * Gets episodes for a TV series
     */
    suspend fun getEpisodes(
        seriesId: String,
        userId: String,
        seasonId: String? = null,
        fields: List<String>? = null,
        startIndex: Int? = null,
        limit: Int? = null
    ): Response<ItemsResponse> {
        return apiService.getEpisodes(seriesId, userId, seasonId, fields, startIndex, limit)
    }
    
    /**
     * Gets seasons for a TV series
     */
    suspend fun getSeasons(seriesId: String, userId: String, fields: List<String>? = null): Response<ItemsResponse> {
        return apiService.getSeasons(seriesId, userId, fields)
    }
    
    /**
     * Gets user libraries (collections)
     */
    suspend fun getLibraries(userId: String): Response<ItemsResponse> {
        return apiService.getLibraries(userId)
    }
    
    /**
     * Gets movies from a library
     */
    suspend fun getMovies(
        userId: String,
        parentId: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending"
    ): Response<ItemsResponse> {
        return getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf("Movie"),
            recursive = true,
            sortBy = sortBy,
            sortOrder = sortOrder,
            startIndex = startIndex,
            limit = limit,
            fields = JellyfinApiService.DETAIL_FIELDS
        )
    }
    
    /**
     * Gets TV series from a library
     */
    suspend fun getSeries(
        userId: String,
        parentId: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending"
    ): Response<ItemsResponse> {
        return getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf("Series"),
            recursive = true,
            sortBy = sortBy,
            sortOrder = sortOrder,
            startIndex = startIndex,
            limit = limit,
            fields = JellyfinApiService.DETAIL_FIELDS
        )
    }
    
    /**
     * Gets albums from a library
     */
    suspend fun getAlbums(
        userId: String,
        parentId: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending"
    ): Response<ItemsResponse> {
        return getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf("MusicAlbum"),
            recursive = true,
            sortBy = sortBy,
            sortOrder = sortOrder,
            startIndex = startIndex,
            limit = limit,
            fields = JellyfinApiService.DETAIL_FIELDS
        )
    }
    
    /**
     * Searches for media items
     */
    suspend fun searchItems(
        userId: String,
        searchTerm: String,
        includeItemTypes: List<String>? = null,
        limit: Int = 20
    ): Response<ItemsResponse> {
        return getItems(
            userId = userId,
            searchTerm = searchTerm,
            includeItemTypes = includeItemTypes,
            recursive = true,
            limit = limit,
            fields = JellyfinApiService.BASIC_FIELDS
        )
    }
    
    /**
     * Gets continue watching items
     */
    suspend fun getContinueWatching(userId: String, limit: Int = 20): Response<ItemsResponse> {
        return getItems(
            userId = userId,
            filters = listOf("IsResumable"),
            recursive = true,
            limit = limit,
            sortBy = "DatePlayed",
            sortOrder = "Descending",
            fields = JellyfinApiService.DETAIL_FIELDS
        )
    }
    
    /**
     * Gets next up episodes for TV shows
     */
    suspend fun getNextUp(userId: String, limit: Int = 20): Response<ItemsResponse> {
        return getItems(
            userId = userId,
            includeItemTypes = listOf("Episode"),
            filters = listOf("IsNotFolder"),
            recursive = true,
            limit = limit,
            sortBy = "DatePlayed",
            sortOrder = "Descending",
            fields = JellyfinApiService.EPISODE_FIELDS
        )
    }
    
    /**
     * Gets item as a Result, wrapping exceptions
     */
    suspend fun getItemResult(userId: String, itemId: String, fields: List<String>? = null): Result<MediaItem> {
        return try {
            val response = getItem(userId, itemId, fields)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch item: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets items as a Result, wrapping exceptions
     */
    suspend fun getItemsResult(
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
    ): Result<ItemsResponse> {
        return try {
            val response = getItems(
                userId, parentId, includeItemTypes, recursive, sortBy, sortOrder,
                startIndex, limit, searchTerm, fields, filters
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch items: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
