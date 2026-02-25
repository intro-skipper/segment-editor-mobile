/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.component.CollectionChip
import org.introskipper.segmenteditor.ui.component.JellyfinCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionFilterSheet(
    collections: List<JellyfinCollection>,
    selectedCollections: Set<String>,
    onToggleCollection: (String) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.filter_collections),
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = {
                    onClearFilter()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.filter_clear_all))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (collections.isEmpty()) {
                Text(
                    text = stringResource(R.string.filter_no_collections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(collections) { collection ->
                        CollectionChip(
                            collection = collection,
                            isSelected = selectedCollections.contains(collection.id),
                            onClick = { onToggleCollection(collection.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}
