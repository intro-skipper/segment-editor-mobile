package org.introskipper.segmenteditor.ui.state

import androidx.compose.ui.layout.ContentScale
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.Segment

data class PlayerUiState(
    val isLoading: Boolean = true,
    val mediaItem: MediaItem? = null,
    val segments: List<Segment> = emptyList(),
    val error: String? = null,
    
    // Playback state
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    
    // Track selection
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: Int? = null,
    val selectedSubtitleTrack: Int? = null,
    
    // Playback settings
    val playbackSpeed: Float = 1.0f,
    
    // UI state
    val showTrackSelection: Boolean = false,
    val showSpeedSelection: Boolean = false,
    val isFullscreen: Boolean = false,
    val isUserLandscape: Boolean = false,
    val playerContentScale: ContentScale = ContentScale.FillBounds,
    
    // Segment editing (timestamp capture)
    val capturedStartTime: Long? = null,
    val capturedEndTime: Long? = null,

    // Auto Play
    val nextItemId: String? = null
)

data class TrackInfo(
    val index: Int,              // Jellyfin MediaStream index (for HLS mode)
    val relativeIndex: Int = 0,  // 0-based position within tracks of same type (for Direct Play)
    val language: String?,
    val displayTitle: String,
    val codec: String?,
    val isDefault: Boolean = false,
    val source: TrackSource = TrackSource.JELLYFIN
)

enum class TrackSource {
    JELLYFIN,     // Track from Jellyfin MediaStreams metadata
    EXOPLAYER,    // Track discovered by ExoPlayer in the stream
    MERGED        // Track present in both sources
}

sealed class PlayerEvent {
    data class Error(val message: String) : PlayerEvent()
    data class SegmentLoaded(val segments: List<Segment>) : PlayerEvent()
    object PlaybackEnded : PlayerEvent()
    data class NavigateToPlayer(val itemId: String) : PlayerEvent()
}