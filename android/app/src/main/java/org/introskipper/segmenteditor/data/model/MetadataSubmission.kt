/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import androidx.room.Entity

@Entity(
    tableName = "metadata_submissions",
    primaryKeys = ["seriesId", "seasonNumber", "episodeNumber"]
)
data class MetadataSubmission(
    val seriesId: String, // Jellyfin Item ID (Series ID for episodes, Movie ID for movies)
    val seasonNumber: Int,
    val episodeNumber: Int,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val tvdbSeriesId: Int? = null,
    val tvdbSeasonId: Int? = null,
    val imdbSeriesId: String? = null,
    val aniListId: Int? = null,
    val submittedAt: Long = System.currentTimeMillis()
)
