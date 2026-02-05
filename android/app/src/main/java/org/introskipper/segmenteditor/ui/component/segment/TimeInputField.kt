package org.introskipper.segmenteditor.ui.component.segment

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.validation.SegmentValidator

@Composable
fun TimeInputField(
    modifier: Modifier = Modifier,
    label: String,
    timeInSeconds: Double,
    onTimeChanged: (Double) -> Unit,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false
) {
    var textValue by remember(timeInSeconds) {
        mutableStateOf(SegmentValidator.formatTimeString(timeInSeconds))
    }
    var localError by remember { mutableStateOf(false) }
    
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
        placeholder = { Text(stringResource(R.string.time_format_hint)) },
        isError = isError || localError,
        supportingText = {
            if (localError) {
                Text(stringResource(R.string.time_format_error))
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}
