package org.introskipper.segmenteditor.ui.state

import org.introskipper.segmenteditor.data.model.Segment

data class SegmentEditorState(
    val mode: EditorMode = EditorMode.Create,
    val itemId: String = "",
    val segmentType: String = "Intro",
    val startTime: Double = 0.0,
    val endTime: Double = 0.0,
    val duration: Double = 0.0,
    val originalSegment: Segment? = null,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false
)

enum class EditorMode {
    Create,
    Edit
}
