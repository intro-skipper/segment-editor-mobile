package org.introskipper.segmenteditor

import org.introskipper.segmenteditor.ui.validation.SegmentValidator
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for segment validation logic.
 * 
 * These tests verify that segment validation works correctly with the fixed duration
 * calculations where duration is stored in milliseconds.
 */
class SegmentValidationTest {
    
    @Test
    fun validate_segmentWithinDuration_isValid() {
        // Video duration: 60 seconds (60000 milliseconds)
        val durationMs = 60000L
        val durationSeconds = durationMs / 1000.0
        
        // Segment: 10s to 20s
        val startTime = 10.0
        val endTime = 20.0
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertTrue("Segment within duration should be valid", result.isValid)
        assertNull("Should not have error message", result.errorMessage)
    }
    
    @Test
    fun validate_endTimeExceedsDuration_isInvalid() {
        // Video duration: 60 seconds
        val durationMs = 60000L
        val durationSeconds = durationMs / 1000.0
        
        // Segment: 50s to 70s (end exceeds duration)
        val startTime = 50.0
        val endTime = 70.0
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertFalse("Segment exceeding duration should be invalid", result.isValid)
        assertEquals(
            "Should have correct error message",
            "End time exceeds video duration",
            result.errorMessage
        )
    }
    
    @Test
    fun validate_startTimeExceedsDuration_isInvalid() {
        // Video duration: 60 seconds
        val durationMs = 60000L
        val durationSeconds = durationMs / 1000.0
        
        // Segment: 70s to 80s (both exceed duration)
        val startTime = 70.0
        val endTime = 80.0
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertFalse("Segment with start time exceeding duration should be invalid", result.isValid)
        assertNotNull("Should have error message", result.errorMessage)
    }
    
    @Test
    fun validate_negativeStartTime_isInvalid() {
        val durationSeconds = 60.0
        val startTime = -5.0
        val endTime = 10.0
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertFalse("Negative start time should be invalid", result.isValid)
        assertEquals(
            "Should have correct error message",
            "Start time cannot be negative",
            result.errorMessage
        )
    }
    
    @Test
    fun validate_startAfterEnd_isInvalid() {
        val durationSeconds = 60.0
        val startTime = 20.0
        val endTime = 10.0
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertFalse("Start time after end time should be invalid", result.isValid)
        assertEquals(
            "Should have correct error message",
            "Start time must be before end time",
            result.errorMessage
        )
    }
    
    @Test
    fun validate_segmentAtVideoDurationBoundary_isValid() {
        // Video duration: 60 seconds
        val durationMs = 60000L
        val durationSeconds = durationMs / 1000.0
        
        // Segment: 50s to exactly 60s (at boundary)
        val startTime = 50.0
        val endTime = 60.0
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertTrue("Segment ending at duration boundary should be valid", result.isValid)
        assertNull("Should not have error message", result.errorMessage)
    }
    
    @Test
    fun validate_veryShortSegment_isValid() {
        val durationSeconds = 60.0
        val startTime = 10.0
        val endTime = 10.1 // 100ms segment
        
        val result = SegmentValidator.validate(startTime, endTime, durationSeconds)
        
        assertTrue("Very short segment should be valid", result.isValid)
        assertNull("Should not have error message", result.errorMessage)
    }
}
