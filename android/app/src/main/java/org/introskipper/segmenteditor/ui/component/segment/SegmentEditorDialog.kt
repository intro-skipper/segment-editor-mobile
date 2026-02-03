package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.state.EditorMode
import org.introskipper.segmenteditor.ui.viewmodel.SegmentEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditorDialog(
    itemId: String,
    duration: Double,
    existingSegments: List<Segment>,
    initialStartTime: Double? = null,
    initialEndTime: Double? = null,
    editSegment: Segment? = null,
    currentPosition: Double? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SegmentEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Initialize view model
    LaunchedEffect(Unit) {
        if (editSegment != null) {
            viewModel.initializeEdit(
                segment = editSegment,
                duration = duration,
                existingSegments = existingSegments
            )
        } else {
            viewModel.initializeCreate(
                itemId = itemId,
                duration = duration,
                startTime = initialStartTime,
                endTime = initialEndTime,
                existingSegments = existingSegments
            )
        }
    }
    
    // Handle save success
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar(
                message = if (state.mode == EditorMode.Create) {
                    context.getString(R.string.segment_created)
                } else {
                    context.getString(R.string.segment_updated)
                },
                duration = SnackbarDuration.Short
            )
            onSaved()
            // Dismiss will be called separately to allow the snackbar to show briefly
        }
    }
    
    // Auto-dismiss after success
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            kotlinx.coroutines.delay(500) // Brief delay to show snackbar
            onDismiss()
        }
    }
    
    // Handle errors
    LaunchedEffect(state.saveError) {
        state.saveError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with title and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.mode == EditorMode.Create) {
                        stringResource(R.string.segment_create_title)
                    } else {
                        stringResource(R.string.segment_edit_title)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                
                Row {
                    if (state.mode == EditorMode.Edit) {
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !state.isDeleting && !state.isSaving
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Segment type selector
                SegmentTypeSelector(
                    selectedType = state.segmentType,
                    onTypeSelected = { viewModel.setSegmentType(it) }
                )
                
                // Timeline scrubber
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Timeline",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        TimelineScrubber(
                            duration = state.duration,
                            startTime = state.startTime,
                            endTime = state.endTime,
                            onStartTimeChanged = { viewModel.setStartTime(it) },
                            onEndTimeChanged = { viewModel.setEndTime(it) },
                            currentPosition = currentPosition
                        )
                    }
                }
                
                // Time inputs
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Time Range",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        TimeInputField(
                            label = "Start Time",
                            timeInSeconds = state.startTime,
                            onTimeChanged = { viewModel.setStartTime(it) },
                            isError = state.validationError != null
                        )
                        
                        TimeInputField(
                            label = "End Time",
                            timeInSeconds = state.endTime,
                            onTimeChanged = { viewModel.setEndTime(it) },
                            isError = state.validationError != null
                        )
                    }
                }
                
                // Validation error message
                if (state.validationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = state.validationError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Loading indicator
                if (state.isDeleting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Deleting segment...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Action buttons
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving && !state.isDeleting
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Button(
                        onClick = { viewModel.saveSegment() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving && 
                                 !state.isDeleting && 
                                 state.validationError == null
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (state.mode == EditorMode.Create) stringResource(R.string.create) else stringResource(R.string.save)
                            )
                        }
                    }
                }
                
                // Spacer for gesture area
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.segment_delete_title)) },
            text = { 
                Text(stringResource(R.string.segment_delete_message, state.segmentType)) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteSegment()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
