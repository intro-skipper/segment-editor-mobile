/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.introskipper.segmenteditor.data.model.MetadataSubmission

@Dao
interface MetadataSubmissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(submission: MetadataSubmission)

    @Query("SELECT * FROM metadata_submissions WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun getSubmission(seriesId: String, seasonNumber: Int, episodeNumber: Int): MetadataSubmission?
}
