package org.introskipper.segmenteditor.model

enum class SegmentType(val value: String) {
    INTRO("Intro"),
    OUTRO("Outro"),
    RECAP("Recap"),
    PREVIEW("Preview"),
    CREDITS("Credits");

    companion object {
        fun fromString(value: String): SegmentType? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}
