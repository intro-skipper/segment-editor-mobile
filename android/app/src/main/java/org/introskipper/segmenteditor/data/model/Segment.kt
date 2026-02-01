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
    val endTicks: Long
) {
    // Convert ticks to seconds (Jellyfin uses 10,000,000 ticks per second)
    fun getStartSeconds(): Double = startTicks / 10_000_000.0
    fun getEndSeconds(): Double = endTicks / 10_000_000.0
    
    companion object {
        // Convert seconds to ticks
        fun secondsToTicks(seconds: Double): Long = (seconds * 10_000_000).toLong()
    }
}

data class SegmentCreateRequest(
    @SerializedName("ItemId")
    val itemId: String,
    
    @SerializedName("Type")
    val type: String,
    
    @SerializedName("StartTicks")
    val startTicks: Long,
    
    @SerializedName("EndTicks")
    val endTicks: Long
)

data class SegmentResponse(
    @SerializedName("Items")
    val items: List<Segment>
)
