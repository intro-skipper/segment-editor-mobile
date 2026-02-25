/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.introskipper.segmenteditor.BuildConfig
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.utils.TranslationService
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideSecurePreferences(
        @ApplicationContext context: Context
    ): SecurePreferences {
        return SecurePreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideJellyfinApiService(
        securePreferences: SecurePreferences
    ): JellyfinApiService {
        return JellyfinApiService(securePreferences)
    }
    
    @Provides
    @Singleton
    fun provideTranslationService(
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences
    ): TranslationService {
        return TranslationService(context, securePreferences)
    }
    
    @Provides
    @Singleton
    fun provideMediaRepository(
        apiService: JellyfinApiService
    ): MediaRepository {
        return MediaRepository(apiService)
    }
    
    @Provides
    @Singleton
    fun provideSegmentRepository(
        apiService: JellyfinApiService
    ): SegmentRepository {
        return SegmentRepository(apiService)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: JellyfinApiService
    ): AuthRepository {
        return AuthRepository(apiService)
    }
}
