package org.introskipper.segmenteditor.ui.component.settings

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SwitchSettingItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
