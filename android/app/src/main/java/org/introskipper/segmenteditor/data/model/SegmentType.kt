/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

enum class SegmentType(val value: String, val apiValue: Int) {
    INTRO("Intro", 5),
    OUTRO("Outro", 4),
    RECAP("Recap", 3),
    PREVIEW("Preview", 2),
    COMMERCIAL("Commercial", 1),
    UNKNOWN("Unknown", 0);

    companion object {
        fun fromString(value: String): SegmentType? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
        
        fun fromApiValue(apiValue: Int): SegmentType? {
            return entries.find { it.apiValue == apiValue }
        }
        
        /**
         * Convert string type name to API integer value
         */
        fun stringToApiValue(value: String): Int {
            return fromString(value)?.apiValue ?: 0
        }
        
        /**
         * Convert API integer value to string type name
         */
        fun apiValueToString(apiValue: Int): String {
            return fromApiValue(apiValue)?.value ?: "Unknown"
        }
    }
}
