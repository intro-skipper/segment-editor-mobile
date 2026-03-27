/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.api

import okhttp3.OkHttpClient
import org.introskipper.segmenteditor.data.model.SkipMeCollectionSubmitResponse
import org.introskipper.segmenteditor.data.model.SkipMeMergeResponse
import org.introskipper.segmenteditor.data.model.SkipMeSubmitRequest
import org.introskipper.segmenteditor.data.model.SkipMeSubmitResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SkipMeApiService(baseUrl: String, httpClient: OkHttpClient) {

    private val api: SkipMeApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SkipMeApi::class.java)

    suspend fun submitSegment(request: SkipMeSubmitRequest): Response<SkipMeSubmitResponse> {
        return api.submitSegment(request)
    }

    suspend fun submitCollection(requests: List<SkipMeSubmitRequest>): Response<SkipMeCollectionSubmitResponse> {
        return api.submitCollection(requests)
    }

    suspend fun mergeRecords(): Response<SkipMeMergeResponse> {
        return api.mergeRecords()
    }
}
