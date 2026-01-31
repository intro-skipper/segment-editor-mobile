package org.introskipper.segmenteditor.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.storage.SecurePreferences
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
    fun provideJellyfinApiService(
        securePreferences: SecurePreferences
    ): JellyfinApiService {
        val serverUrl = securePreferences.getServerUrl() ?: ""
        val apiKey = securePreferences.getApiKey() ?: ""
        return JellyfinApiService.create(serverUrl, apiKey)
    }
}
