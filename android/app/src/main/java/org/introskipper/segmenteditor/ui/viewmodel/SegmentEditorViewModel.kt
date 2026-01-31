package org.introskipper.segmenteditor.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.model.SegmentCreateRequest
import org.introskipper.segmenteditor.data.model.TimeUtils
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.ui.state.EditorMode
import org.introskipper.segmenteditor.ui.state.SegmentEditorState
import org.introskipper.segmenteditor.ui.validation.SegmentValidator
import javax.inject.Inject

@HiltViewModel
class SegmentEditorViewModel @Inject constructor(
    private val segmentRepository: SegmentRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(SegmentEditorState())
    val state: StateFlow<SegmentEditorState> = _state.asStateFlow()
    
    private var existingSegments: List<Segment> = emptyList()
    
    /**
     * Initialize for creating a new segment
     */
    fun initializeCreate(
        itemId: String,
        duration: Double,
        startTime: Double? = null,
        endTime: Double? = null,
        existingSegments: List<Segment> = emptyList()
    ) {
        this.existingSegments = existingSegments
        _state.update {
            SegmentEditorState(
                mode = EditorMode.Create,
                itemId = itemId,
                duration = duration,
                startTime = startTime ?: 0.0,
                endTime = endTime ?: duration
            )
        }
        validateCurrentState()
    }
    
    /**
     * Initialize for editing an existing segment
     */
    fun initializeEdit(
        segment: Segment,
        duration: Double,
        existingSegments: List<Segment> = emptyList()
    ) {
        this.existingSegments = existingSegments
        _state.update {
            SegmentEditorState(
                mode = EditorMode.Edit,
                itemId = segment.itemId,
                segmentType = segment.type,
                startTime = segment.getStartSeconds(),
                endTime = segment.getEndSeconds(),
                duration = duration,
                originalSegment = segment
            )
        }
        validateCurrentState()
    }
    
    /**
     * Updates the segment type
     */
    fun setSegmentType(type: String) {
        _state.update { it.copy(segmentType = type) }
        validateCurrentState()
    }
    
    /**
     * Updates the start time
     */
    fun setStartTime(seconds: Double) {
        _state.update { it.copy(startTime = seconds) }
        validateCurrentState()
    }
    
    /**
     * Updates the end time
     */
    fun setEndTime(seconds: Double) {
        _state.update { it.copy(endTime = seconds) }
        validateCurrentState()
    }
    
    /**
     * Updates start time from a time string (HH:MM:SS or MM:SS)
     */
    fun setStartTimeFromString(timeString: String) {
        val seconds = SegmentValidator.parseTimeString(timeString)
        if (seconds != null) {
            setStartTime(seconds)
        } else {
            _state.update { it.copy(validationError = "Invalid time format") }
        }
    }
    
    /**
     * Updates end time from a time string (HH:MM:SS or MM:SS)
     */
    fun setEndTimeFromString(timeString: String) {
        val seconds = SegmentValidator.parseTimeString(timeString)
        if (seconds != null) {
            setEndTime(seconds)
        } else {
            _state.update { it.copy(validationError = "Invalid time format") }
        }
    }
    
    /**
     * Validates the current state
     */
    private fun validateCurrentState() {
        val current = _state.value
        
        // Validate basic time constraints
        val basicValidation = SegmentValidator.validate(
            current.startTime,
            current.endTime,
            current.duration
        )
        
        if (!basicValidation.isValid) {
            _state.update { it.copy(validationError = basicValidation.errorMessage) }
            return
        }
        
        // Check for overlaps (exclude current segment when editing)
        val excludeType = if (current.mode == EditorMode.Edit) {
            current.originalSegment?.type
        } else {
            null
        }
        
        val overlapValidation = SegmentValidator.checkOverlaps(
            current.startTime,
            current.endTime,
            existingSegments,
            excludeType
        )
        
        if (!overlapValidation.isValid) {
            _state.update { it.copy(validationError = overlapValidation.errorMessage) }
            return
        }
        
        // All validations passed
        _state.update { it.copy(validationError = null) }
    }
    
    /**
     * Saves the segment (create or update)
     */
    fun saveSegment() {
        // Re-validate before saving
        validateCurrentState()
        
        // Check validation after updating state
        if (_state.value.validationError != null) {
            return
        }
        
        val current = _state.value
        
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null, saveSuccess = false) }
            
            try {
                val segmentRequest = SegmentCreateRequest(
                    itemId = current.itemId,
                    type = current.segmentType,
                    startTicks = TimeUtils.secondsToTicks(current.startTime),
                    endTicks = TimeUtils.secondsToTicks(current.endTime)
                )
                
                val result = when (current.mode) {
                    EditorMode.Create -> {
                        segmentRepository.createSegmentResult(segmentRequest)
                    }
                    EditorMode.Edit -> {
                        segmentRepository.updateSegmentResult(
                            itemId = current.itemId,
                            segmentType = current.originalSegment?.type ?: current.segmentType,
                            segment = segmentRequest
                        )
                    }
                }
                
                result.fold(
                    onSuccess = { segment ->
                        Log.d(TAG, "Segment saved successfully: ${segment.type}")
                        _state.update { 
                            it.copy(
                                isSaving = false,
                                saveSuccess = true,
                                saveError = null
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to save segment", error)
                        _state.update {
                            it.copy(
                                isSaving = false,
                                saveError = "Failed to save: ${error.message}",
                                saveSuccess = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception saving segment", e)
                _state.update {
                    it.copy(
                        isSaving = false,
                        saveError = "Error: ${e.message}",
                        saveSuccess = false
                    )
                }
            }
        }
    }
    
    /**
     * Deletes the segment (only in edit mode)
     */
    fun deleteSegment() {
        val current = _state.value
        
        if (current.mode != EditorMode.Edit || current.originalSegment == null) {
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, saveError = null) }
            
            try {
                val result = segmentRepository.deleteSegmentResult(
                    itemId = current.itemId,
                    segmentType = current.originalSegment.type
                )
                
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Segment deleted successfully")
                        _state.update { 
                            it.copy(
                                isDeleting = false,
                                saveSuccess = true,
                                saveError = null
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to delete segment", error)
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                saveError = "Failed to delete: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting segment", e)
                _state.update {
                    it.copy(
                        isDeleting = false,
                        saveError = "Error: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Clears any error messages
     */
    fun clearError() {
        _state.update { it.copy(saveError = null) }
    }
    
    companion object {
        private const val TAG = "SegmentEditorViewModel"
    }
}
