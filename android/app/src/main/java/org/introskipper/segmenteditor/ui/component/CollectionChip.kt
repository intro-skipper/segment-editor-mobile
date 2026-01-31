package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.introskipper.segmenteditor.ui.viewmodel.JellyfinCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionChip(
    collection: JellyfinCollection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(collection.name) },
        modifier = modifier.fillMaxWidth()
    )
}
