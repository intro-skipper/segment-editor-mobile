/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem

@Composable
fun MediaGrid(
    items: List<JellyfinMediaItem>,
    onItemClick: (JellyfinMediaItem) -> Unit,
    onItemLongClick: ((JellyfinMediaItem) -> Unit)? = null,
    submittingItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val scrollbarSettings = ScrollbarSettings.Default.copy(
        thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        thumbSelectedColor = MaterialTheme.colorScheme.primary
    )
    
    LazyVerticalGridScrollbar(
        state = gridState,
        settings = scrollbarSettings,
        indicatorContent = { index: Int, isThumbSelected: Boolean ->
            if (isThumbSelected) {
                val item = items.getOrNull(index)
                val firstLetter = item?.name?.take(1)?.uppercase() ?: ""
                if (firstLetter.isNotEmpty()) {
                    ScrollbarIndicator(firstLetter)
                }
            }
        },
        modifier = modifier
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = onItemLongClick?.let { { it(item) } },
                    isSubmitting = item.id == submittingItemId
                )
            }
        }
    }
}

@Composable
private fun ScrollbarIndicator(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.headlineSmall
    )
}
