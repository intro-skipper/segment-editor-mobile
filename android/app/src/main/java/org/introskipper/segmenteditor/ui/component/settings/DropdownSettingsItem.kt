package org.introskipper.segmenteditor.ui.component.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun <T> DropdownSettingsItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedOption }?.second ?: ""

    Box(modifier = modifier.fillMaxWidth()) {
        SettingItem(
            title = title,
            subtitle = subtitle ?: selectedLabel,
            trailingContent = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select $title"
                    )
                }
            }
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onOptionSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
