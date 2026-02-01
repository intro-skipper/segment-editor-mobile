package org.introskipper.segmenteditor.ui.component

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
