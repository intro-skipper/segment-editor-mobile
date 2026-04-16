/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import com.google.gson.annotations.SerializedName

data class UpdateUserItemDataDto(
    @SerializedName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    @SerializedName("PlayedPercentage")
    val playedPercentage: Double? = null,
    @SerializedName("Played")
    val played: Boolean? = null
)
