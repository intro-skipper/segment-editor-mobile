package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.ui.component.CollectionChip
import org.introskipper.segmenteditor.ui.viewmodel.JellyfinCollection

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
                    text = "Filter Collections",
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = {
                    onClearFilter()
                    onDismiss()
                }) {
                    Text("Clear All")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (collections.isEmpty()) {
                Text(
                    text = "No collections available",
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
                Text("Apply")
            }
        }
    }
}
