package org.introskipper.segmenteditor.utils

import android.content.Context
import android.util.Log
import androidx.core.app.LocaleManagerCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.introskipper.segmenteditor.storage.SecurePreferences
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that handles dynamic translation using ML Kit.
 * Downloads models as needed and provides translation functionality.
 */
@Singleton
class TranslationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    private var translator: Translator? = null
    private var currentTargetLanguage: String? = null

    /**
     * Translates the given text to the app's selected language.
     * Returns the original text if translation fails or if the target language is English.
     */
    suspend fun translate(text: String?): String {
        if (text.isNullOrBlank()) return ""
        
        val targetLangCode = getSupportedTargetLanguage()
        // Skip translation if target is English or not supported
        if (targetLangCode == null || targetLangCode == TranslateLanguage.ENGLISH) {
            return text
        }

        try {
            val translator = getTranslator(targetLangCode)
            return translator.translate(text).await()
        } catch (e: Exception) {
            Log.e("TranslationService", "Translation failed for: $text", e)
            return text
        }
    }

    private suspend fun getTranslator(targetLanguage: String): Translator {
        if (translator != null && currentTargetLanguage == targetLanguage) {
            return translator!!
        }

        // Close old translator if it exists
        translator?.close()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLanguage)
            .build()
        
        val newTranslator = Translation.getClient(options)
        
        // Download model if necessary
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        
        newTranslator.downloadModelIfNeeded(conditions).await()
        
        translator = newTranslator
        currentTargetLanguage = targetLanguage
        return newTranslator
    }

    /**
     * Maps the app's language (from Android Settings) to ML Kit supported TranslateLanguage.
     * Returns null if the language is not supported or is the source language (English).
     */
    private fun getSupportedTargetLanguage(): String? {
        val appLanguage = LocaleManagerCompat.getApplicationLocales(context).get(0)?.language 
            ?: Locale.getDefault().language

        return when (appLanguage) {
            "de" -> TranslateLanguage.GERMAN
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "th" -> TranslateLanguage.THAI
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ru" -> TranslateLanguage.RUSSIAN
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "zh" -> TranslateLanguage.CHINESE
            else -> null // Default to original text
        }
    }
    
    fun close() {
        translator?.close()
        translator = null
    }
}
