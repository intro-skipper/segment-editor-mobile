/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.introskipper.segmenteditor.data.model.Submission

@Dao
interface SubmissionDao {
    @Insert
    suspend fun insert(submission: Submission)

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM submissions 
            WHERE segmentType = :segmentType 
            AND durationMs = :durationMs
            AND ABS(startMs - :startMs) <= 1000
            AND ABS(endMs - :endMs) <= 1000
            AND (
                (tvdbId IS NOT NULL AND tvdbId = :tvdbId) OR
                (imdbId IS NOT NULL AND imdbId = :imdbId) OR
                (tmdbId IS NOT NULL AND tmdbId = :tmdbId AND season = :season AND episode = :episode) OR
                (imdbSeriesId IS NOT NULL AND imdbSeriesId = :imdbSeriesId AND season = :season AND episode = :episode) OR
                (aniListId IS NOT NULL AND aniListId = :aniListId AND episode = :episode)
            )
        )
    """)
    suspend fun isDuplicate(
        segmentType: String,
        durationMs: Long,
        startMs: Long,
        endMs: Long,
        tvdbId: Int? = null,
        imdbId: String? = null,
        tmdbId: Int? = null,
        imdbSeriesId: String? = null,
        aniListId: Int? = null,
        season: Int? = null,
        episode: Int? = null
    ): Boolean
}
