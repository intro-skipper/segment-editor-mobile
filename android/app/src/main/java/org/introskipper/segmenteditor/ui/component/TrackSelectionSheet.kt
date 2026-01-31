package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.ui.state.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionSheet(
    title: String,
    tracks: List<TrackInfo>,
    selectedTrackIndex: Int?,
    onTrackSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
    allowDisable: Boolean = false,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (allowDisable) {
                TrackItem(
                    displayTitle = "Disabled",
                    isSelected = selectedTrackIndex == null,
                    onClick = { onTrackSelected(null) }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            if (tracks.isEmpty()) {
                Text(
                    text = "No tracks available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                tracks.forEach { track ->
                    TrackItem(
                        displayTitle = track.displayTitle,
                        isSelected = track.index == selectedTrackIndex,
                        onClick = { onTrackSelected(track.index) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackItem(
    displayTitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        
        if (isSelected) {
            RadioButton(
                selected = true,
                onClick = null
            )
        }
    }
}
