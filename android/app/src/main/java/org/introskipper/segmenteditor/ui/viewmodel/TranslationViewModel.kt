package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.introskipper.segmenteditor.utils.TranslationService
import javax.inject.Inject

@HiltViewModel
class TranslationViewModel @Inject constructor(
    val translationService: TranslationService
) : ViewModel()
