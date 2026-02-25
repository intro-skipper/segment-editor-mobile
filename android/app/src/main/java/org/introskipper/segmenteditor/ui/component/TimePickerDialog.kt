/*
  * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
  * SPDX-License-Identifier: GPL-3.0-only
  */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.introskipper.segmenteditor.R

@Composable
fun TimePickerDialog(
    initialTimeSeconds: Double,
    onTimeSelected: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val totalSeconds = initialTimeSeconds.toLong()
    val initialHours = (totalSeconds / 3600).toInt()
    val initialMinutes = ((totalSeconds % 3600) / 60).toInt()
    val initialSeconds = (totalSeconds % 60).toInt()

    var hours by remember { mutableStateOf(initialHours.toString()) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    var seconds by remember { mutableStateOf(initialSeconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_picker_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimePartInput(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() }.take(3) },
                    label = stringResource(R.string.time_picker_hours)
                )
                Text(":", style = MaterialTheme.typography.headlineMedium)
                TimePartInput(
                    value = minutes,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }.take(2)
                        if (filtered.isEmpty() || filtered.toInt() < 60) {
                            minutes = filtered
                        }
                    },
                    label = stringResource(R.string.time_picker_minutes)
                )
                Text(":", style = MaterialTheme.typography.headlineMedium)
                TimePartInput(
                    value = seconds,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }.take(2)
                        if (filtered.isEmpty() || filtered.toInt() < 60) {
                            seconds = filtered
                        }
                    },
                    label = stringResource(R.string.time_picker_seconds)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hours.toDoubleOrNull() ?: 0.0
                    val m = minutes.toDoubleOrNull() ?: 0.0
                    val s = seconds.toDoubleOrNull() ?: 0.0
                    onTimeSelected(h * 3600 + m * 60 + s)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TimePartInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(64.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}