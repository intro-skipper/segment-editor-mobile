/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.state.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionSheet(
    modifier: Modifier = Modifier,
    title: String,
    tracks: List<TrackInfo>,
    selectedTrackIndex: Int?,
    onTrackSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
    allowDisable: Boolean = false,
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
                    displayTitle = translatedString(R.string.settings_preview_disabled),
                    isSelected = selectedTrackIndex == null,
                    onClick = { onTrackSelected(null) }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            if (tracks.isEmpty()) {
                Text(
                    text = translatedString(R.string.tracks_none_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                tracks.forEach { track ->
                    TrackItem(
                        displayTitle = track.displayTitle,
                        isSelected = track.relativeIndex == selectedTrackIndex,
                        onClick = { onTrackSelected(track.relativeIndex) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(translatedString(R.string.done))
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
