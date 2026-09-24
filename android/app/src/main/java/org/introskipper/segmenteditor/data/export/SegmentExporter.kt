/*
 * Copyright (c) 2026 Intro-Skipper Devs <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.export

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.ExportFormat
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A normalized segment record shared by all export entry points. */
@Serializable
data class SegmentExportItem(
    @SerialName("segment_type") val segmentType: String,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("start_sec") val startSec: Double,
    @SerialName("end_sec") val endSec: Double,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("media_name") val mediaName: String? = null,
    @SerialName("series_name") val seriesName: String? = null,
    @SerialName("duration_sec") val durationSec: Double? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_series_id") val imdbSeriesId: String? = null,
    @SerialName("tvdb_series_id") val tvdbSeriesId: Int? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("ani_list_id") val aniListId: Int? = null
) {
    companion object {
        fun from(
            segment: Segment,
            mediaItem: MediaItem,
            seriesItem: MediaItem? = null,
            seriesTmdbId: Int? = seriesItem?.getTmdbId(),
            seriesImdbId: String? = seriesItem?.getImdbId(),
            seriesTvdbId: Int? = seriesItem?.getTvdbId(),
            seriesAniListId: Int? = seriesItem?.getAniListId()
        ): SegmentExportItem = SegmentExportItem(
            segmentType = segment.type,
            season = mediaItem.parentIndexNumber,
            episode = mediaItem.indexNumber,
            startSec = segment.getStartSeconds(),
            endSec = segment.getEndSeconds(),
            mediaId = mediaItem.id,
            mediaName = mediaItem.name,
            seriesName = mediaItem.seriesName ?: seriesItem?.name,
            durationSec = mediaItem.getRuntimeSeconds(),
            tmdbId = seriesTmdbId ?: if (seriesItem == null) mediaItem.getTmdbId() else null,
            imdbSeriesId = seriesImdbId,
            tvdbSeriesId = seriesTvdbId,
            tvdbId = mediaItem.getTvdbId(),
            imdbId = mediaItem.getImdbId(),
            aniListId = seriesAniListId
        )
    }
}

data class SegmentExportFile(
    val uri: Uri,
    val mimeType: String,
    val fileName: String
)

@Singleton
class SegmentExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    suspend fun export(items: List<SegmentExportItem>, title: String): SegmentExportFile {
        require(items.isNotEmpty()) { "At least one segment is required" }

        val format = securePreferences.getExportFormat()
        val extension = format.name.lowercase(Locale.ROOT)
        val content = when (format) {
            ExportFormat.JSON -> formatJson(items, securePreferences.getPrettyPrintJson())
            ExportFormat.CSV -> formatCsv(items)
            ExportFormat.XML -> formatXml(items)
        }

        val safeTitle = title
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "segments" }
        val fileName = "${safeTitle}_segments.$extension"
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDirectory, fileName)
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        return SegmentExportFile(uri, format.mimeType(), fileName)
    }

    private fun formatJson(items: List<SegmentExportItem>, prettyPrint: Boolean): String {
        val json = Json {
            this.prettyPrint = prettyPrint
            encodeDefaults = false
            explicitNulls = false
        }
        return json.encodeToString(mapOf("items" to items))
    }

    private fun formatCsv(items: List<SegmentExportItem>): String {
        val columns = listOf(
            "segment_type", "season", "episode", "start_sec", "end_sec", "media_id",
            "media_name", "series_name", "duration_sec", "tmdb_id", "imdb_series_id",
            "tvdb_series_id", "tvdb_id", "imdb_id", "ani_list_id"
        )
        return buildString {
            appendLine(columns.joinToString(","))
            items.forEach { item ->
                appendLine(listOf(
                    item.segmentType, item.season, item.episode, item.startSec, item.endSec,
                    item.mediaId, item.mediaName, item.seriesName, item.durationSec, item.tmdbId,
                    item.imdbSeriesId, item.tvdbSeriesId, item.tvdbId, item.imdbId, item.aniListId
                ).joinToString(",") { csvValue(it?.toString().orEmpty()) })
            }
        }
    }

    private fun formatXml(items: List<SegmentExportItem>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<segments>")
        items.forEach { item ->
            append("  <segment")
            xmlAttribute("segment_type", item.segmentType)
            xmlAttribute("season", item.season)
            xmlAttribute("episode", item.episode)
            xmlAttribute("start_sec", item.startSec)
            xmlAttribute("end_sec", item.endSec)
            xmlAttribute("media_id", item.mediaId)
            xmlAttribute("media_name", item.mediaName)
            xmlAttribute("series_name", item.seriesName)
            xmlAttribute("duration_sec", item.durationSec)
            xmlAttribute("tmdb_id", item.tmdbId)
            xmlAttribute("imdb_series_id", item.imdbSeriesId)
            xmlAttribute("tvdb_series_id", item.tvdbSeriesId)
            xmlAttribute("tvdb_id", item.tvdbId)
            xmlAttribute("imdb_id", item.imdbId)
            xmlAttribute("ani_list_id", item.aniListId)
            appendLine(" />")
        }
        appendLine("</segments>")
    }

    private fun StringBuilder.xmlAttribute(name: String, value: Any?) {
        if (value != null) append(" $name=\"${xmlEscape(value.toString())}\"")
    }

    private fun csvValue(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun ExportFormat.mimeType(): String = when (this) {
        ExportFormat.JSON -> "application/json"
        ExportFormat.CSV -> "text/csv"
        ExportFormat.XML -> "application/xml"
    }
}

fun Context.shareExport(file: SegmentExportFile) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, file.uri)
        clipData = ClipData.newRawUri(file.fileName, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, file.fileName)
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}
