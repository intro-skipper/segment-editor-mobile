package org.introskipper.segmenteditor.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.introskipper.segmenteditor.ui.theme.LocalTranslationService

@Composable
fun translatedString(@StringRes resId: Int): String {
    val translationService = LocalTranslationService.current
    val defaultText = stringResource(resId)
    
    if (translationService == null) return defaultText
    
    val isEnabled by translationService.isDynamicTranslationEnabled.collectAsState()
    
    if (!isEnabled) return defaultText
    
    // Use the current locale as a key for produceState to react to locale changes
    val config = LocalConfiguration.current
    val locale = config.locales[0]
    
    val translatedText by produceState(initialValue = defaultText, resId, isEnabled, locale) {
        value = translationService.getString(resId)
    }
    
    return translatedText
}

@Composable
fun translatedString(@StringRes resId: Int, vararg formatArgs: Any): String {
    val translationService = LocalTranslationService.current
    val defaultText = stringResource(resId, *formatArgs)
    
    if (translationService == null) return defaultText
    
    val isEnabled by translationService.isDynamicTranslationEnabled.collectAsState()
    
    if (!isEnabled) return defaultText
    
    val config = LocalConfiguration.current
    val locale = config.locales[0]
    
    val translatedText by produceState(initialValue = defaultText, resId, isEnabled, locale, formatArgs) {
        value = translationService.getString(resId, *formatArgs)
    }
    
    return translatedText
}

@Composable
fun translatedText(text: String?): String {
    val translationService = LocalTranslationService.current
    if (text.isNullOrBlank() || translationService == null) return text ?: ""
    
    val isEnabled by translationService.isDynamicTranslationEnabled.collectAsState()
    if (!isEnabled) return text
    
    val config = LocalConfiguration.current
    val locale = config.locales[0]
    
    val translatedValue by produceState(initialValue = text, text, isEnabled, locale) {
        value = translationService.translate(text)
    }
    
    return translatedValue
}
