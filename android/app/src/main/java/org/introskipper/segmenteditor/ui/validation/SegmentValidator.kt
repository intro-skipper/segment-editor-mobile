package org.introskipper.segmenteditor.ui.validation

import android.content.Context
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.data.model.Segment

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object SegmentValidator {
    
    /**
     * Validates a segment's time boundaries
     * @param startTime Start time in seconds
     * @param endTime End time in seconds
     * @param duration Total video duration in seconds
     * @param context Android Context for string resources
     * @return ValidationResult with error message if invalid
     */
    fun validate(
        startTime: Double,
        endTime: Double,
        duration: Double,
        context: Context
    ): ValidationResult {
        // Check for negative times
        if (startTime < 0) {
            return ValidationResult(false, context.getString(R.string.validation_error_start_negative))
        }
        
        if (endTime < 0) {
            return ValidationResult(false, context.getString(R.string.validation_error_end_negative))
        }
        
        // Check start < end
        if (startTime >= endTime) {
            return ValidationResult(false, context.getString(R.string.validation_error_start_after_end))
        }
        
        // Check within video duration
        if (endTime > duration) {
            return ValidationResult(false, context.getString(R.string.validation_error_end_exceeds_duration))
        }
        
        if (startTime > duration) {
            return ValidationResult(false, context.getString(R.string.validation_error_start_exceeds_duration))
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Checks if a new segment overlaps with existing segments
     * @param startTime Start time in seconds
     * @param endTime End time in seconds
     * @param existingSegments List of existing segments to check against
     * @param excludeSegmentType Segment type to exclude from overlap check (for editing)
     * @param context Android Context for string resources
     * @return ValidationResult with warning if overlaps exist
     */
    fun checkOverlaps(
        startTime: Double,
        endTime: Double,
        existingSegments: List<Segment>,
        excludeSegmentType: String? = null,
        context: Context
    ): ValidationResult {
        val overlaps = existingSegments
            .filter { it.type != excludeSegmentType }
            .filter { segment ->
                val segStart = segment.getStartSeconds()
                val segEnd = segment.getEndSeconds()
                
                // Check if ranges overlap
                !(endTime <= segStart || startTime >= segEnd)
            }
        
        if (overlaps.isNotEmpty()) {
            val types = overlaps.joinToString(", ") { it.type }
            return ValidationResult(
                false, 
                context.getString(R.string.validation_error_overlaps, types)
            )
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Parses time string in format HH:MM:SS or MM:SS to seconds
     * @param timeString Time string to parse
     * @return Seconds or null if invalid
     */
    fun parseTimeString(timeString: String): Double? {
        val parts = timeString.split(":")
        
        return try {
            when (parts.size) {
                2 -> {
                    // MM:SS format
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    (minutes * 60 + seconds).toDouble()
                }
                3 -> {
                    // HH:MM:SS format
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    (hours * 3600 + minutes * 60 + seconds).toDouble()
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * Formats seconds to time string (HH:MM:SS or MM:SS)
     * @param seconds Seconds to format
     * @return Formatted time string
     */
    fun formatTimeString(seconds: Double): String {
        val totalSecs = seconds.toLong()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
}
