/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

/**
 * Utility object for converting between Jellyfin ticks and seconds.
 * Jellyfin uses 10,000,000 ticks per second (100-nanosecond intervals).
 */
object TimeUtils {
    /**
     * Number of ticks per second in Jellyfin
     */
    const val TICKS_PER_SECOND = 10_000_000L
    
    /**
     * Number of ticks per millisecond in Jellyfin
     */
    const val TICKS_PER_MILLISECOND = 10_000L
    
    /**
     * Converts ticks to seconds
     * @param ticks The number of ticks
     * @return The equivalent number of seconds
     */
    fun ticksToSeconds(ticks: Long): Double {
        return ticks / TICKS_PER_SECOND.toDouble()
    }
    
    /**
     * Converts seconds to ticks
     * @param seconds The number of seconds
     * @return The equivalent number of ticks
     */
    fun secondsToTicks(seconds: Double): Long {
        return (seconds * TICKS_PER_SECOND).toLong()
    }
    
    /**
     * Converts ticks to milliseconds
     * @param ticks The number of ticks
     * @return The equivalent number of milliseconds
     */
    fun ticksToMilliseconds(ticks: Long): Long {
        return ticks / TICKS_PER_MILLISECOND
    }
    
    /**
     * Converts milliseconds to ticks
     * @param milliseconds The number of milliseconds
     * @return The equivalent number of ticks
     */
    fun millisecondsToTicks(milliseconds: Long): Long {
        return milliseconds * TICKS_PER_MILLISECOND
    }
    
    /**
     * Formats ticks as a human-readable duration string (HH:MM:SS or MM:SS)
     * @param ticks The number of ticks
     * @return Formatted duration string
     */
    fun formatDuration(ticks: Long): String {
        val totalSeconds = ticksToSeconds(ticks).toLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    /**
     * Formats seconds as a human-readable duration string (HH:MM:SS or MM:SS)
     * @param seconds The number of seconds
     * @return Formatted duration string
     */
    fun formatDurationFromSeconds(seconds: Double): String {
        return formatDuration(secondsToTicks(seconds))
    }
    
    /**
     * Formats milliseconds as a human-readable duration string (HH:MM:SS or MM:SS)
     * @param milliseconds The number of milliseconds
     * @return Formatted duration string
     */
    fun formatMilliseconds(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
