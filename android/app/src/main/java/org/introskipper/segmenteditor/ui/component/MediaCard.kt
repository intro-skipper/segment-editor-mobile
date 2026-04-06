/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.ui.theme.DynamicColorsOptions
import org.introskipper.segmenteditor.ui.theme.SegmentEditorTheme
import org.introskipper.segmenteditor.ui.util.getDominantColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    item: JellyfinMediaItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSubmitting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dominantColor by remember(item.imageUrl) { mutableStateOf<Int?>(null) }

    LaunchedEffect(item.imageUrl) {
        item.imageUrl?.let { url ->
            dominantColor = getDominantColor(context, url)
        }
    }

    SegmentEditorTheme(
        dynamicColorsOptions = DynamicColorsOptions(seedColor = dominantColor)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = if (isSubmitting) ({}) else onClick,
                    onLongClick = if (isSubmitting) null else onLongClick,
                    enabled = !isSubmitting
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dominantColor != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Box {
                Column {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        contentScale = ContentScale.Crop,
                        alpha = if (isSubmitting) 0.5f else 1.0f
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isSubmitting) 0.5f else 1.0f)
                        )

                        item.productionYear?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isSubmitting) 0.4f else 0.7f)
                            )
                        }
                    }
                }

                if (isSubmitting) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }
    }
}
