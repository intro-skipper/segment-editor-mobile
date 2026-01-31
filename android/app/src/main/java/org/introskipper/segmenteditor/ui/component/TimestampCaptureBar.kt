package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.data.model.TimeUtils

@Composable
fun TimestampCaptureBar(
    capturedStartTime: Long?,
    capturedEndTime: Long?,
    onCaptureStart: () -> Unit,
    onCaptureEnd: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Segment Timestamps",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (capturedStartTime != null || capturedEndTime != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear timestamps")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start Time
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onCaptureStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = capturedStartTime?.let { 
                                TimeUtils.formatMilliseconds(it)
                            } ?: "Capture"
                        )
                    }
                }
                
                // End Time
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "End",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onCaptureEnd,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = capturedEndTime?.let { 
                                TimeUtils.formatMilliseconds(it)
                            } ?: "Capture"
                        )
                    }
                }
            }
            
            if (capturedStartTime != null && capturedEndTime != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Duration: ${TimeUtils.formatMilliseconds(capturedEndTime - capturedStartTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
