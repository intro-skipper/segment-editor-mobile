package org.introskipper.segmenteditor.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeState @Inject constructor() {
    var globalSeedColor by mutableStateOf<Int?>(null)
}
