package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.ui.validation.SegmentValidator

@Composable
fun TimeInputField(
    label: String,
    timeInSeconds: Double,
    onTimeChanged: (Double) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    var textValue by remember(timeInSeconds) {
        mutableStateOf(SegmentValidator.formatTimeString(timeInSeconds))
    }
    var localError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            textValue = newValue
            // Try to parse and update
            val parsed = SegmentValidator.parseTimeString(newValue)
            if (parsed != null) {
                localError = false
                onTimeChanged(parsed)
            } else if (newValue.isNotEmpty()) {
                localError = true
            }
        },
        label = { Text(label) },
        placeholder = { Text("MM:SS or HH:MM:SS") },
        isError = isError || localError,
        supportingText = {
            if (localError) {
                Text("Invalid format. Use MM:SS or HH:MM:SS")
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}
