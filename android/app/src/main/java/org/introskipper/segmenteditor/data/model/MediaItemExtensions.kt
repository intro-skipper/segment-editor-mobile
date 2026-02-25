/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

/**
 * Extension properties for MediaItem to provide Jellyfin-specific data.
 */

/**
 * Constructs the image URL for this media item.
 */
fun MediaItem.getImageUrl(serverUrl: String, imageType: String = "Primary", maxWidth: Int = 600): String? {
    val imageTag = when (imageType) {
        "Primary" -> getPrimaryImageTag()
        "Backdrop" -> backdropImageTags?.firstOrNull()
        else -> null
    } ?: return null
    
    return "$serverUrl/Items/$id/Images/$imageType?maxWidth=$maxWidth&tag=$imageTag&quality=90"
}

/**
 * Gets the official rating (e.g., "PG-13", "TV-MA")
 */
val MediaItem.officialRating: String?
    get() = null // This would come from additional fields in the API

/**
 * Adapter type for Jellyfin media items with URL context
 */
data class JellyfinMediaItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val productionYear: Int?,
    val runTimeTicks: Long?,
    val officialRating: String?,
    val type: String?
)

/**
 * Converts MediaItem to JellyfinMediaItem with server URL context
 */
fun MediaItem.toJellyfinMediaItem(serverUrl: String): JellyfinMediaItem {
    return JellyfinMediaItem(
        id = id,
        name = name ?: "Unknown",
        imageUrl = getImageUrl(serverUrl),
        productionYear = productionYear,
        runTimeTicks = runTimeTicks,
        officialRating = null, // Would need to be fetched from additional fields
        type = type
    )
}
