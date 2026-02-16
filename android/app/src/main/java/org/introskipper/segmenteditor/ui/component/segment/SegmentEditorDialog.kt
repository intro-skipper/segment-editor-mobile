package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getString
import androidx.hilt.navigation.compose.hiltViewModel
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.ui.state.EditorMode
import org.introskipper.segmenteditor.ui.theme.getSegmentColor
import org.introskipper.segmenteditor.ui.viewmodel.SegmentEditorViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditorDialog(
    itemId: String,
    duration: Double,
    existingSegments: List<Segment>,
    initialStartTime: Double? = null,
    initialEndTime: Double? = null,
    editSegment: Segment? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SegmentEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // Store original values for restoration on dismiss
    val originalStartTime = remember { editSegment?.getStartSeconds() ?: initialStartTime }
    val originalEndTime = remember { editSegment?.getEndSeconds() ?: initialEndTime }
    val originalSegmentType = remember { editSegment?.type }
    
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
            val message = when {
                state.isDeleting -> getString(context, R.string.segment_deleted)
                state.mode == EditorMode.Create -> getString(context, R.string.segment_created)
                else -> getString(context, R.string.segment_updated)
            }
            snackbarHostState.showSnackbar(
                message = message,
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
    
    // Function to handle dismiss - restores original values
    val handleDismiss = {
        // Restore original values when dismissing without saving
        if (!state.saveSuccess) {
            if (originalStartTime != null) {
                viewModel.setStartTime(originalStartTime)
            }
            if (originalEndTime != null) {
                viewModel.setEndTime(originalEndTime)
            }
            if (originalSegmentType != null) {
                viewModel.setSegmentType(originalSegmentType)
            }
        }
        onDismiss()
    }
    
    ModalBottomSheet(
        onDismissRequest = handleDismiss
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
                
                // Only show delete button in edit mode
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
                
                // Timeline slider
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Timeline",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // Slider track with draggable handles
                        SegmentEditorSlider(
                            duration = state.duration,
                            startTime = state.startTime,
                            endTime = state.endTime,
                            segmentType = state.segmentType,
                            onStartTimeChanged = { viewModel.setStartTime(it) },
                            onEndTimeChanged = { viewModel.setEndTime(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Editable time inputs below timeline
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TimeInputField(
                                label = "Start Time",
                                timeInSeconds = state.startTime,
                                onTimeChanged = { viewModel.setStartTime(it) },
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                isError = state.validationError != null,
                                modifier = Modifier.weight(1f)
                            )
                            
                            TimeInputField(
                                label = "End Time",
                                timeInSeconds = state.endTime,
                                onTimeChanged = { viewModel.setEndTime(it) },
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                isError = state.validationError != null,
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                        onClick = handleDismiss,
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
                
                // Bottom spacing for the sheet's swipe-to-dismiss gesture area
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

/**
 * Interactive slider component for segment editing with draggable handles
 */
@Composable
private fun SegmentEditorSlider(
    duration: Double,
    startTime: Double,
    endTime: Double,
    segmentType: String,
    onStartTimeChanged: (Double) -> Unit,
    onEndTimeChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackHeight = 40.dp
    val handleWidth = 14.dp
    val segmentColor = getSegmentColor(segmentType)
    val minGap = 0.1 // Minimum gap between start and end in seconds
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTimeString(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Slider track
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            
            val startPercent = if (duration > 0) (startTime / duration).toFloat() else 0f
            val endPercent = if (duration > 0) (endTime / duration).toFloat() else 1f
            
            // Segment range - drawn using Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw segment range
                val startX = startPercent * width
                val endX = endPercent * width
                val segmentWidth = kotlin.math.max(endX - startX, 2f)
                
                drawRect(
                    color = segmentColor.copy(alpha = 0.7f),
                    topLeft = Offset(startX, 0f),
                    size = Size(segmentWidth, height)
                )
            }
            
            // Start handle
            Box(
                modifier = Modifier
                    .offset(x = ((maxWidth * startPercent) - (handleWidth / 2)).coerceAtLeast(0.dp))
                    .width(handleWidth)
                    .fillMaxHeight()
                    .background(segmentColor)
                    .pointerInput(duration, startTime, endTime) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dragDelta = (dragAmount.x / maxWidthPx) * duration
                                val newStart = (startTime + dragDelta).coerceIn(0.0, endTime - minGap)
                                onStartTimeChanged(newStart)
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.8f))
                        .align(Alignment.Center)
                )
            }
            
            // End handle
            Box(
                modifier = Modifier
                    .offset(x = ((maxWidth * endPercent) - (handleWidth / 2)).coerceAtLeast(0.dp))
                    .width(handleWidth)
                    .fillMaxHeight()
                    .background(segmentColor)
                    .pointerInput(duration, startTime, endTime) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dragDelta = (dragAmount.x / maxWidthPx) * duration
                                val newEnd = (endTime + dragDelta).coerceIn(startTime + minGap, duration)
                                onEndTimeChanged(newEnd)
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.8f))
                        .align(Alignment.Center)
                )
            }
        }
    }
}

private fun formatTimeString(seconds: Double): String {
    val totalSeconds = seconds.toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
