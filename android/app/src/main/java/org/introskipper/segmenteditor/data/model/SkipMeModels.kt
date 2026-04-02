/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /v1/submit to the SkipMe.db API.
 * At least one of [tmdbId], [tvdbId], or [aniListId] is required.
 */
data class SkipMeSubmitRequest(
    @SerializedName("tmdb_id")
    val tmdbId: Int? = null,

    @SerializedName("tvdb_series_id")
    val tvdbSeriesId: Int? = null,

    @SerializedName("tvdb_season_id")
    val tvdbSeasonId: Int? = null,

    @SerializedName("tvdb_id")
    val tvdbId: Int? = null,

    @SerializedName("anilist_id")
    val aniListId: Int? = null,

    @SerializedName("segment")
    val segment: String,

    @SerializedName("season")
    val season: Int? = null,

    @SerializedName("episode")
    val episode: Int? = null,

    @SerializedName("duration_ms")
    val durationMs: Long,

    @SerializedName("start_ms")
    val startMs: Long,

    @SerializedName("end_ms")
    val endMs: Long
)

/**
 * Response body for a successful POST /v1/submit (HTTP 201).
 */
data class SkipMeSubmitResponse(
    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("submission")
    val submission: SkipMeSubmission?
)

data class SkipMeSubmission(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String
)

/**
 * Response body for POST /v1/submit/collection.
 */
data class SkipMeCollectionSubmitResponse(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("submitted")
    val submitted: Int = 0
)

/**
 * Response body for POST /v1/admin/merge-records.
 */
data class SkipMeMergeResponse(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("merged")
    val merged: Int = 0
)
