/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.introskipper.segmenteditor.BuildConfig
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.api.SkipMeApiService
import org.introskipper.segmenteditor.data.local.AppDatabase
import org.introskipper.segmenteditor.data.local.MetadataSubmissionDao
import org.introskipper.segmenteditor.data.local.SubmissionDao
import org.introskipper.segmenteditor.data.repository.AnimeIdsRepository
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.data.repository.TvMazeRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.utils.TranslationService
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SkipMeClient

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
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @SkipMeClient
    fun provideSkipMeOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "SkipMe.db")
                .build()
            chain.proceed(request)
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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

    @Provides
    @Singleton
    fun provideSkipMeApiService(
        @SkipMeClient httpClient: OkHttpClient
    ): SkipMeApiService {
        return SkipMeApiService(BuildConfig.SKIPME_BASE_URL, httpClient)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "segment_editor.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSubmissionDao(database: AppDatabase): SubmissionDao {
        return database.submissionDao()
    }

    @Provides
    fun provideMetadataSubmissionDao(database: AppDatabase): MetadataSubmissionDao {
        return database.metadataSubmissionDao()
    }

    @Provides
    @Singleton
    fun provideAnimeIdsRepository(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        securePreferences: SecurePreferences
    ): AnimeIdsRepository {
        return AnimeIdsRepository(context, httpClient, securePreferences)
    }

    @Provides
    @Singleton
    fun provideTvMazeRepository(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient
    ): TvMazeRepository {
        return TvMazeRepository(context, httpClient)
    }
}
