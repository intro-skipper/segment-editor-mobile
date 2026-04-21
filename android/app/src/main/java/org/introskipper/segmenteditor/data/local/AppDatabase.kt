/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.introskipper.segmenteditor.data.model.MetadataSubmission
import org.introskipper.segmenteditor.data.model.Submission

@Database(entities = [Submission::class, MetadataSubmission::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun submissionDao(): SubmissionDao
    abstract fun metadataSubmissionDao(): MetadataSubmissionDao
}
