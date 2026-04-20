/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /v1/submit to the SkipMe.db API.
 * At least one of [tmdbId], [tvdbId], [imdbSeriesId] (for TV), [imdbId] (for movies), or [aniListId] is required.
 */
data class SkipMeSubmitRequest(
    @SerializedName("tmdb_id")
    val tmdbId: Int? = null,

    @SerializedName("imdb_series_id")
    val imdbSeriesId: String? = null,

    @SerializedName("imdb_id")
    val imdbId: String? = null,

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
 * Per-episode item for POST /v1/submit/season.
 */
data class SkipMeSeasonItem(
    @SerializedName("tvdb_id")
    val tvdbId: Int? = null,

    @SerializedName("imdb_id")
    val imdbId: String? = null,

    @SerializedName("episode")
    val episode: Int?,

    @SerializedName("segment")
    val segment: String,

    @SerializedName("duration_ms")
    val durationMs: Long,

    @SerializedName("start_ms")
    val startMs: Long,

    @SerializedName("end_ms")
    val endMs: Long
)

/**
 * Request body for POST /v1/submit/season.
 * Groups all episode timestamps for one season into a single request.
 * At least one of [tmdbId], [tvdbSeriesId], [imdbSeriesId], or [aniListId] is required.
 */
data class SkipMeSeasonSubmitRequest(
    @SerializedName("tvdb_series_id")
    val tvdbSeriesId: Int? = null,

    @SerializedName("tvdb_season_id")
    val tvdbSeasonId: Int? = null,

    @SerializedName("tmdb_id")
    val tmdbId: Int? = null,

    @SerializedName("imdb_series_id")
    val imdbSeriesId: String? = null,

    @SerializedName("anilist_id")
    val aniListId: Int? = null,

    @SerializedName("season")
    val season: Int?,

    @SerializedName("items")
    val items: List<SkipMeSeasonItem>
)

/**
 * Response body for POST /v1/submit/season.
 */
data class SkipMeSeasonSubmitResponse(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("submitted")
    val submitted: Int = 0
)

/**
 * Request item for POST /v1/backfill.
 * Fills in missing identifier fields on existing accepted submissions.
 * At least one valid matching strategy must be present per item.
 */
data class SkipMeBackfillRequest(
    @SerializedName("tvdb_id")
    val tvdbId: Int? = null,

    @SerializedName("tmdb_id")
    val tmdbId: Int? = null,

    @SerializedName("imdb_id")
    val imdbId: String? = null,

    @SerializedName("tvdb_season_id")
    val tvdbSeasonId: Int? = null,

    @SerializedName("tvdb_series_id")
    val tvdbSeriesId: Int? = null,

    @SerializedName("anilist_id")
    val aniListId: Int? = null,

    @SerializedName("season")
    val season: Int? = null,

    @SerializedName("episode")
    val episode: Int? = null
)

/**
 * Response body for POST /v1/backfill.
 */
data class SkipMeBackfillResponse(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("updated")
    val updated: Int = 0
)
