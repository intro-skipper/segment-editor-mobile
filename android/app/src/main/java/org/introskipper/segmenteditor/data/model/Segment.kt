/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import com.google.gson.annotations.SerializedName

data class Segment(
    @SerializedName("Id")
    val id: String? = null,
    
    @SerializedName("ItemId")
    val itemId: String,
    
    @SerializedName("Type")
    val type: String,
    
    @SerializedName("StartTicks")
    val startTicks: Long,
    
    @SerializedName("EndTicks")
    val endTicks: Long,

    // SegmentProviderId identifies which provider (e.g. "SkipMe.db", "IntroSkipper") created the
    // segment.  Jellyfin stores this as MediaSegment.SegmentProviderId in its database and is
    // expected to surface it in MediaSegmentDto once the upstream API exposes the field.
    @SerializedName("SegmentProviderId")
    val segmentProviderId: String? = null
) {
    // Convert ticks to seconds (Jellyfin uses 10,000,000 ticks per second)
    fun getStartSeconds(): Double = startTicks / 10_000_000.0
    fun getEndSeconds(): Double = endTicks / 10_000_000.0
    
    companion object {
        const val SKIPME_PROVIDER_ID = "SkipMe.db"

        // Convert seconds to ticks
        fun secondsToTicks(seconds: Double): Long = (seconds * 10_000_000).toLong()
    }
}

/**
 * Returns a list with all SkipMe.db-provided segments removed.
 * Used when the user has enabled "Filter SkipMe.db Segments" in settings.
 */
fun List<Segment>.filterSkipMe(): List<Segment> = filter { it.segmentProviderId != Segment.SKIPME_PROVIDER_ID }

data class SegmentCreateRequest(
    @SerializedName("ItemId")
    val itemId: String,
    
    @SerializedName("Type")
    val type: Int,  // API expects integer enum value
    
    @SerializedName("StartTicks")
    val startTicks: Long,
    
    @SerializedName("EndTicks")
    val endTicks: Long
)

data class SegmentResponse(
    @SerializedName("Items")
    val items: List<Segment>
)
