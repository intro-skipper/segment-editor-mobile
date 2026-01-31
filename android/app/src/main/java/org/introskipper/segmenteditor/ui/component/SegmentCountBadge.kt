package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SegmentCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        Badge(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text(text = count.toString())
        }
    } else {
        Badge(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "0",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
