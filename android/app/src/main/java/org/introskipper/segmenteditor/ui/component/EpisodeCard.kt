/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.state.EpisodeWithSegments

@Composable
fun EpisodeCard(
    episode: EpisodeWithSegments,
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Episode thumbnail
            episode.episode.getPrimaryImageTag()?.let { imageTag ->
                Card(
                    modifier = Modifier.size(80.dp, 60.dp)
                ) {
                    AsyncImage(
                        model = "$serverUrl/Items/${episode.episode.id}/Images/Primary?maxWidth=200&tag=$imageTag&quality=90",
                        contentDescription = episode.episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Episode info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Episode title
                Text(
                    text = episode.episode.name ?: translatedString(R.string.player_unknown),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Season, episode number, and duration
                val episodeLabel = buildString {
                    if (episode.episode.parentIndexNumber != null) {
                        append("S${episode.episode.parentIndexNumber}")
                    }
                    if (episode.episode.indexNumber != null) {
                        append("E${episode.episode.indexNumber}")
                    }
                }

                val durationLabel = episode.episode.getRuntimeSeconds()?.let { seconds ->
                    translatedString(R.string.episode_duration_minutes, (seconds / 60).toInt())
                }
                val metadata = listOfNotNull(episodeLabel.takeIf { it.isNotEmpty() }, durationLabel)
                    .joinToString(" · ")

                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SegmentTimeline(
                    segments = episode.segments,
                    runtimeSeconds = episode.episode.getRuntimeSeconds(),
                    isLoading = episode.isLoadingSegments || episode.segments == null
                )
            }
        }
    }
}
