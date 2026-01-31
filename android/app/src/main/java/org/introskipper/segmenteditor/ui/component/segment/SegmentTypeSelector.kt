package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.data.model.SegmentType

@Composable
fun SegmentTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Segment Type",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SegmentType.entries) { type ->
                SegmentTypeChip(
                    type = type,
                    isSelected = selectedType.equals(type.value, ignoreCase = true),
                    onClick = { onTypeSelected(type.value) }
                )
            }
        }
    }
}

@Composable
private fun SegmentTypeChip(
    type: SegmentType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (type) {
        SegmentType.INTRO -> Icons.Default.PlayArrow
        SegmentType.OUTRO -> Icons.Default.Stop
        SegmentType.RECAP -> Icons.Default.Replay
        SegmentType.PREVIEW -> Icons.Default.Preview
        SegmentType.CREDITS -> Icons.Default.Movie
    }
    
    val colors = if (isSelected) {
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = when (type) {
                SegmentType.INTRO -> MaterialTheme.colorScheme.primaryContainer
                SegmentType.OUTRO -> MaterialTheme.colorScheme.secondaryContainer
                SegmentType.RECAP -> MaterialTheme.colorScheme.tertiaryContainer
                SegmentType.PREVIEW -> MaterialTheme.colorScheme.surfaceVariant
                SegmentType.CREDITS -> MaterialTheme.colorScheme.errorContainer
            }
        )
    } else {
        FilterChipDefaults.filterChipColors()
    }
    
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(type.value) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = colors
    )
}
