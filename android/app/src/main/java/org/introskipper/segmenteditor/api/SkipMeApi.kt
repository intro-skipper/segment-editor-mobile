/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.api

import org.introskipper.segmenteditor.data.model.SkipMeBackfillRequest
import org.introskipper.segmenteditor.data.model.SkipMeBackfillResponse
import org.introskipper.segmenteditor.data.model.SkipMeCollectionSubmitResponse
import org.introskipper.segmenteditor.data.model.SkipMeSubmitRequest
import org.introskipper.segmenteditor.data.model.SkipMeSubmitResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SkipMeApi {

    @POST("v1/submit")
    suspend fun submitSegment(
        @Body request: SkipMeSubmitRequest
    ): Response<SkipMeSubmitResponse>

    @POST("v1/submit/collection")
    suspend fun submitCollection(
        @Body requests: List<SkipMeSubmitRequest>
    ): Response<SkipMeCollectionSubmitResponse>

    @POST("v1/backfill")
    suspend fun backfill(
        @Body requests: List<SkipMeBackfillRequest>
    ): Response<SkipMeBackfillResponse>
}
