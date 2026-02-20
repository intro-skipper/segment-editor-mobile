package org.introskipper.segmenteditor.utils

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * Utility to provide translated strings in non-composable contexts where a Context is available.
 */
object TranslationUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TranslationServiceEntryPoint {
        fun translationService(): TranslationService
    }

    /**
     * Gets a translated string for the given resource ID.
     * Note: This uses runBlocking and should be called from background threads or non-critical paths.
     */
    fun getTranslatedString(context: Context, @StringRes resId: Int): String {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TranslationServiceEntryPoint::class.java
        )
        val service = entryPoint.translationService()
        
        return runBlocking {
            service.getString(resId)
        }
    }

    /**
     * Gets a translated string for the given resource ID with format arguments.
     * Note: This uses runBlocking and should be called from background threads or non-critical paths.
     */
    fun getTranslatedString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TranslationServiceEntryPoint::class.java
        )
        val service = entryPoint.translationService()
        
        return runBlocking {
            service.getString(resId, *formatArgs)
        }
    }
}

/**
 * Extension function for Context to easily get translated strings.
 */
fun Context.getTranslatedString(@StringRes resId: Int): String {
    return TranslationUtils.getTranslatedString(this, resId)
}

fun Context.getTranslatedString(@StringRes resId: Int, vararg formatArgs: Any): String {
    return TranslationUtils.getTranslatedString(this, resId, *formatArgs)
}
