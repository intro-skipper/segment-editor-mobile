package org.introskipper.segmenteditor.ui.component.segment

import kotlinx.serialization.Serializable

@Serializable
data class Intervals(
    val startTimeMs: Long,
    val endTimeMS: Long?
)

@Serializable
data class Events (
    val startTimeMs: Long,
    val eventType: String?,
    val intervals: List<Intervals>
)

@Serializable
data class TimeStamo (
    val Id: String,
    val ItemId: String,
    val Type: String,
    val StartTicks: Long,
    val EndTicks: Long?
)