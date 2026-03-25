/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.api

import okhttp3.OkHttpClient
import org.introskipper.segmenteditor.data.model.SkipMeSubmitRequest
import org.introskipper.segmenteditor.data.model.SkipMeSubmitResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SkipMeApiService(httpClient: OkHttpClient) {

    private val api: SkipMeApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SkipMeApi::class.java)

    suspend fun submitSegment(request: SkipMeSubmitRequest): Response<SkipMeSubmitResponse> {
        return api.submitSegment(request)
    }

    companion object {
        const val BASE_URL = "https://skipme.deadlymediocre.workers.dev/"
    }
}
