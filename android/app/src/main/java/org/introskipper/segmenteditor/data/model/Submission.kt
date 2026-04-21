/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "submissions")
data class Submission(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbSeriesId: Int? = null,
    val imdbSeriesId: String? = null,
    val tvdbSeasonId: Int? = null,
    val tvdbId: Int? = null,
    val aniListId: Int? = null,
    val segmentType: String,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val submittedAt: Long = System.currentTimeMillis()
)
