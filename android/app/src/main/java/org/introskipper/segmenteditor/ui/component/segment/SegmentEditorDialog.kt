package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
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
                    "Segment created successfully"
                } else {
                    "Segment updated successfully"
                },
                duration = SnackbarDuration.Short
            )
            onSaved()
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
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            if (state.mode == EditorMode.Create) {
                                "Create Segment"
                            } else {
                                "Edit Segment"
                            }
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (state.mode == EditorMode.Edit) {
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !state.isDeleting && !state.isSaving
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSaving && !state.isDeleting
                        ) {
                            Text("Cancel")
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
                                    if (state.mode == EditorMode.Create) "Create" else "Save"
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
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
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Segment?") },
            text = { 
                Text("Are you sure you want to delete this ${state.segmentType} segment? This action cannot be undone.") 
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
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
