/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a Jellyfin BaseItemDto - a media item from the Jellyfin server.
 * This includes movies, TV shows, episodes, audio tracks, and other media types.
 */
data class MediaItem(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("Type")
    val type: String? = null,
    
    @SerializedName("SeriesName")
    val seriesName: String? = null,
    
    @SerializedName("SeriesId")
    val seriesId: String? = null,
    
    @SerializedName("SeasonId")
    val seasonId: String? = null,
    
    @SerializedName("SeasonName")
    val seasonName: String? = null,
    
    @SerializedName("IndexNumber")
    val indexNumber: Int? = null,
    
    @SerializedName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    
    @SerializedName("Overview")
    val overview: String? = null,
    
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    
    @SerializedName("ProductionYear")
    val productionYear: Int? = null,
    
    @SerializedName("PremiereDate")
    val premiereDate: String? = null,
    
    @SerializedName("ImageTags")
    val imageTags: Map<String, String>? = null,
    
    @SerializedName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    
    @SerializedName("MediaSources")
    val mediaSources: List<MediaSource>? = null,
    
    @SerializedName("MediaStreams")
    val mediaStreams: List<MediaStream>? = null,
    
    @SerializedName("Path")
    val path: String? = null,
    
    @SerializedName("Container")
    val container: String? = null,
    
    @SerializedName("UserData")
    val userData: UserItemData? = null,
    
    @SerializedName("IsFolder")
    val isFolder: Boolean = false,
    
    @SerializedName("CollectionType")
    val collectionType: String? = null,
    
    @SerializedName("AlbumArtist")
    val albumArtist: String? = null,
    
    @SerializedName("Artists")
    val artists: List<String>? = null,
    
    @SerializedName("Album")
    val album: String? = null,
    
    @SerializedName("CanDelete")
    val canDelete: Boolean = false,
    
    @SerializedName("SupportsMediaControl")
    val supportsMediaControl: Boolean = false,
    
    @SerializedName("ChildCount")
    val childCount: Int? = null
) {
    /**
     * Gets the runtime in seconds
     */
    fun getRuntimeSeconds(): Double? = runTimeTicks?.let { it / 10_000_000.0 }
    
    /**
     * Gets the primary image tag
     */
    fun getPrimaryImageTag(): String? = imageTags?.get("Primary")
    
    /**
     * Checks if this item has a primary image
     */
    fun hasPrimaryImage(): Boolean = getPrimaryImageTag() != null
    
    /**
     * Checks if this item has backdrop images
     */
    fun hasBackdropImages(): Boolean = !backdropImageTags.isNullOrEmpty()
    
    /**
     * Gets display title with episode information if available
     */
    fun getDisplayTitle(): String {
        return when (type) {
            "Episode" -> {
                val episodeInfo = buildString {
                    if (parentIndexNumber != null) append("S$parentIndexNumber")
                    if (indexNumber != null) append("E$indexNumber")
                }
                if (episodeInfo.isNotEmpty() && name != null) {
                    "$episodeInfo - $name"
                } else {
                    name ?: "Unknown"
                }
            }
            else -> name ?: "Unknown"
        }
    }
}

/**
 * Represents a media source for playback
 */
data class MediaSource(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("Path")
    val path: String? = null,
    
    @SerializedName("Container")
    val container: String? = null,
    
    @SerializedName("Size")
    val size: Long? = null,
    
    @SerializedName("Bitrate")
    val bitrate: Int? = null,
    
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    
    @SerializedName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean = false,
    
    @SerializedName("SupportsDirectStream")
    val supportsDirectStream: Boolean = false,
    
    @SerializedName("SupportsTranscoding")
    val supportsTranscoding: Boolean = false,
    
    @SerializedName("IsRemote")
    val isRemote: Boolean = false,
    
    @SerializedName("Protocol")
    val protocol: String? = null,
    
    @SerializedName("MediaStreams")
    val mediaStreams: List<MediaStream>? = null
)

/**
 * Represents a media stream (video, audio, subtitle)
 */
data class MediaStream(
    @SerializedName("Index")
    val index: Int,
    
    @SerializedName("Type")
    val type: String,
    
    @SerializedName("Codec")
    val codec: String? = null,
    
    @SerializedName("CodecTag")
    val codecTag: String? = null,
    
    @SerializedName("Language")
    val language: String? = null,
    
    @SerializedName("DisplayLanguage")
    val displayLanguage: String? = null,
    
    @SerializedName("DisplayTitle")
    val displayTitle: String? = null,
    
    @SerializedName("IsDefault")
    val isDefault: Boolean = false,
    
    @SerializedName("IsForced")
    val isForced: Boolean = false,
    
    @SerializedName("Width")
    val width: Int? = null,
    
    @SerializedName("Height")
    val height: Int? = null,
    
    @SerializedName("AspectRatio")
    val aspectRatio: String? = null,
    
    @SerializedName("BitRate")
    val bitRate: Int? = null,
    
    @SerializedName("Channels")
    val channels: Int? = null,
    
    @SerializedName("ChannelLayout")
    val channelLayout: String? = null,
    
    @SerializedName("SampleRate")
    val sampleRate: Int? = null,
    
    @SerializedName("IsExternal")
    val isExternal: Boolean = false,
    
    @SerializedName("IsTextSubtitleStream")
    val isTextSubtitleStream: Boolean = false,
    
    @SerializedName("SupportsExternalStream")
    val supportsExternalStream: Boolean = false
)

/**
 * User-specific data for a media item
 */
data class UserItemData(
    @SerializedName("PlaybackPositionTicks")
    val playbackPositionTicks: Long = 0,
    
    @SerializedName("PlayCount")
    val playCount: Int = 0,
    
    @SerializedName("IsFavorite")
    val isFavorite: Boolean = false,
    
    @SerializedName("Played")
    val played: Boolean = false,
    
    @SerializedName("Likes")
    val likes: Boolean? = null,
    
    @SerializedName("LastPlayedDate")
    val lastPlayedDate: String? = null,
    
    @SerializedName("UnplayedItemCount")
    val unplayedItemCount: Int? = null
) {
    fun getPlaybackPositionSeconds(): Double = playbackPositionTicks / 10_000_000.0
}

/**
 * Response wrapper for paginated item queries
 */
data class ItemsResponse(
    @SerializedName("Items")
    val items: List<MediaItem>,
    
    @SerializedName("TotalRecordCount")
    val totalRecordCount: Int,
    
    @SerializedName("StartIndex")
    val startIndex: Int
)
