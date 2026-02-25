/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.introskipper.segmenteditor.utils.TranslationService
import javax.inject.Inject

@HiltViewModel
class TranslationViewModel @Inject constructor(
    val translationService: TranslationService
) : ViewModel()
