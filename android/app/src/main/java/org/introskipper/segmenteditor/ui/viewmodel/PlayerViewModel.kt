package org.introskipper.segmenteditor.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.introskipper.segmenteditor.data.model.MediaStream
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.preview.TrickplayPreviewLoader
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.PlayerEvent
import org.introskipper.segmenteditor.ui.state.PlayerUiState
import org.introskipper.segmenteditor.ui.state.TrackInfo
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val httpClient: OkHttpClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // Get the preferDirectPlay setting (when true, use direct play instead of HLS)
    fun shouldUseDirectPlay(): Boolean {
        return securePreferences.getPreferDirectPlay()
    }
    
    private val _events = MutableStateFlow<PlayerEvent?>(null)
    val events: StateFlow<PlayerEvent?> = _events.asStateFlow()
    
    fun loadMediaItem(itemId: String) {
        viewModelScope.launch {
            // Clear old state when loading new item to prevent state pollution
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    error = null,
                    segments = emptyList(),
                    currentPosition = 0L,
                    isPlaying = false,
                    // Clear track information when loading new media item
                    audioTracks = emptyList(),
                    subtitleTracks = emptyList(),
                    selectedAudioTrack = null,
                    selectedSubtitleTrack = null
                )
            }
            
            val userId = securePreferences.getUserId() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "User ID not found") }
                return@launch
            }
            
            try {
                // Load media item with detailed fields
                val mediaResult = mediaRepository.getItemResult(
                    userId = userId,
                    itemId = itemId,
                    fields = listOf("MediaSources", "MediaStreams", "Path", "Container")
                )
                
                mediaResult.fold(
                    onSuccess = { mediaItem ->
                        _uiState.update { 
                            it.copy(
                                mediaItem = mediaItem,
                                duration = mediaItem.runTimeTicks?.div(10_000) ?: 0L,
                                isLoading = false
                            )
                        }
                        
                        // Extract all available tracks from Jellyfin metadata
                        extractTracksFromMediaStreams(mediaItem.mediaStreams)
                        
                        // Load segments
                        loadSegments(itemId)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load media item", error)
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to load media: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading media item", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }
    
    private fun loadSegments(itemId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading segments for item: $itemId")
                val result = segmentRepository.getSegmentsResult(itemId)
                result.fold(
                    onSuccess = { segments ->
                        Log.d(TAG, "Successfully loaded ${segments.size} segments")
                        segments.forEachIndexed { index, segment ->
                            Log.d(TAG, "Segment $index: type=${segment.type}, start=${segment.getStartSeconds()}s, end=${segment.getEndSeconds()}s")
                        }
                        _uiState.update { it.copy(segments = segments) }
                        _events.value = PlayerEvent.SegmentLoaded(segments)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Failed to load segments (non-critical): ${error.message}", error)
                        // Segments are optional, don't show error to user
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception loading segments (non-critical): ${e.message}", e)
            }
        }
    }
    
    private fun buildTrackTitle(type: String, language: String?, codec: String?): String {
        return buildString {
            append(type)
            if (language != null) append(" - $language")
            if (codec != null) append(" ($codec)")
        }
    }
    
    private fun extractTracksFromMediaStreams(mediaStreams: List<MediaStream>?) {
        if (mediaStreams == null) {
            Log.d(TAG, "No media streams available")
            return
        }
        
        val audioTracks = mediaStreams
            .filter { it.type == "Audio" }
            .mapIndexed { relativeIndex, stream ->
                TrackInfo(
                    index = stream.index,  // Use Jellyfin stream index
                    relativeIndex = relativeIndex,  // Sequential 0-based index within audio tracks
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: buildTrackTitle("Audio", stream.language, stream.codec),
                    codec = stream.codec,
                    isDefault = stream.isDefault,
                    source = org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                )
            }
        
        val subtitleTracks = mediaStreams
            .filter { it.type == "Subtitle" }
            .mapIndexed { relativeIndex, stream ->
                TrackInfo(
                    index = stream.index,  // Use Jellyfin stream index
                    relativeIndex = relativeIndex,  // Sequential 0-based index within subtitle tracks
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: buildTrackTitle("Subtitle", stream.language, stream.codec),
                    codec = stream.codec,
                    isDefault = stream.isDefault,
                    source = org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                )
            }
        
        // Find the default track relativeIndex or use the first track if tracks exist
        // Use relativeIndex for consistency across both HLS and Direct Play modes
        val defaultAudioIndex = if (audioTracks.isEmpty()) {
            null
        } else {
            audioTracks.firstOrNull { it.isDefault }?.relativeIndex ?: audioTracks.firstOrNull()?.relativeIndex
        }
        val defaultSubtitleIndex = subtitleTracks.firstOrNull { it.isDefault }?.relativeIndex
        
        Log.d(TAG, "Extracted from Jellyfin: ${audioTracks.size} audio tracks, ${subtitleTracks.size} subtitle tracks")
        audioTracks.forEach { track ->
            Log.d(TAG, "Audio track: index=${track.index}, relativeIndex=${track.relativeIndex}, title=${track.displayTitle}, default=${track.isDefault}, source=${track.source}")
        }
        subtitleTracks.forEach { track ->
            Log.d(TAG, "Subtitle track: index=${track.index}, relativeIndex=${track.relativeIndex}, title=${track.displayTitle}, default=${track.isDefault}, source=${track.source}")
        }
        
        _uiState.update { 
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                selectedAudioTrack = defaultAudioIndex,
                selectedSubtitleTrack = defaultSubtitleIndex
            )
        }
    }
    
    fun updateTracksFromPlayer(tracks: androidx.media3.common.Tracks) {
        // For direct play: tracks are embedded in the media file, extract from ExoPlayer
        // For HLS: tracks come from Jellyfin MediaStreams API, this is just for fallback
        val useDirectPlay = shouldUseDirectPlay()
        
        Log.d(TAG, "ExoPlayer onTracksChanged called, useDirectPlay=$useDirectPlay")
        
        // Extract audio tracks from ExoPlayer with proper indexing
        val exoAudioTracks = mutableListOf<TrackInfo>()
        var audioRelativeIndex = 0
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language
                    // Use label from format (set by media container metadata)
                    val label = format.label ?: "Audio ${audioRelativeIndex + 1}"
                    exoAudioTracks.add(TrackInfo(
                        index = audioRelativeIndex,  // Use relativeIndex as index for direct play
                        relativeIndex = audioRelativeIndex,
                        language = language,
                        displayTitle = label,
                        codec = format.sampleMimeType,
                        isDefault = group.isTrackSelected(trackIndex),
                        source = org.introskipper.segmenteditor.ui.state.TrackSource.EXOPLAYER
                    ))
                    Log.d(TAG, "ExoPlayer audio track: groupIndex=$groupIndex, trackIndex=$trackIndex, relativeIndex=$audioRelativeIndex, language=$language, label=$label, selected=${group.isTrackSelected(trackIndex)}")
                    audioRelativeIndex++
                }
            }
        }
        
        // Extract subtitle tracks from ExoPlayer with proper indexing
        val exoSubtitleTracks = mutableListOf<TrackInfo>()
        var subtitleRelativeIndex = 0
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language
                    val label = format.label ?: "Subtitle ${subtitleRelativeIndex + 1}"
                    exoSubtitleTracks.add(TrackInfo(
                        index = subtitleRelativeIndex,  // Use relativeIndex as index for direct play
                        relativeIndex = subtitleRelativeIndex,
                        language = language,
                        displayTitle = label,
                        codec = format.sampleMimeType,
                        isDefault = group.isTrackSelected(trackIndex),
                        source = org.introskipper.segmenteditor.ui.state.TrackSource.EXOPLAYER
                    ))
                    Log.d(TAG, "ExoPlayer subtitle track: groupIndex=$groupIndex, trackIndex=$trackIndex, relativeIndex=$subtitleRelativeIndex, language=$language, label=$label, selected=${group.isTrackSelected(trackIndex)}")
                    subtitleRelativeIndex++
                }
            }
        }
        
        Log.d(TAG, "ExoPlayer tracks extracted: ${exoAudioTracks.size} audio, ${exoSubtitleTracks.size} subtitles")
        
        // For direct play: use ExoPlayer tracks (they're embedded in media)
        // For HLS: keep Jellyfin tracks (they come from API), only use ExoPlayer as fallback
        if (useDirectPlay) {
            // Direct play: tracks are in the media file, use what ExoPlayer found
            val defaultAudioIndex = exoAudioTracks.firstOrNull { it.isDefault }?.relativeIndex
            val defaultSubtitleIndex = exoSubtitleTracks.firstOrNull { it.isDefault }?.relativeIndex
            
            Log.d(TAG, "Direct play mode: using ExoPlayer tracks, defaultAudio=$defaultAudioIndex, defaultSubtitle=$defaultSubtitleIndex")
            
            _uiState.update { state ->
                state.copy(
                    audioTracks = exoAudioTracks,
                    subtitleTracks = exoSubtitleTracks,
                    selectedAudioTrack = defaultAudioIndex,
                    selectedSubtitleTrack = defaultSubtitleIndex
                )
            }
        } else {
            // HLS mode: keep existing Jellyfin tracks, only use ExoPlayer tracks if none exist OR if existing tracks are from ExoPlayer
            Log.d(TAG, "HLS mode: keeping Jellyfin tracks, ExoPlayer tracks as fallback")
            _uiState.update { state ->
                // Check if existing tracks are from Jellyfin or ExoPlayer
                val hasJellyfinTracks = state.audioTracks.isNotEmpty() && 
                    state.audioTracks.first().source == org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                
                // Only keep existing tracks if they're from Jellyfin
                // If they're from ExoPlayer (e.g., after switching from Direct Play), replace them with new ExoPlayer tracks from HLS
                val shouldKeepExistingTracks = state.audioTracks.isNotEmpty() && hasJellyfinTracks
                
                state.copy(
                    audioTracks = if (shouldKeepExistingTracks) state.audioTracks else exoAudioTracks,
                    subtitleTracks = if (shouldKeepExistingTracks) state.subtitleTracks else exoSubtitleTracks,
                    // When replacing tracks, reset selections to defaults if current selection is out of bounds
                    selectedAudioTrack = if (shouldKeepExistingTracks) {
                        state.selectedAudioTrack
                    } else {
                        exoAudioTracks.firstOrNull { it.isDefault }?.relativeIndex 
                            ?: exoAudioTracks.firstOrNull()?.relativeIndex
                    },
                    selectedSubtitleTrack = if (shouldKeepExistingTracks) {
                        state.selectedSubtitleTrack
                    } else {
                        exoSubtitleTracks.firstOrNull { it.isDefault }?.relativeIndex
                    }
                )
            }
        }
    }
    
    /**
     * Build the stream URL with track-specific parameters.
     * For HLS: includes AudioStreamIndex and SubtitleStreamIndex parameters if set
     * For Direct Play: no track parameters needed
     */
    fun getStreamUrl(useHls: Boolean = true): String? {
        val mediaItem = _uiState.value.mediaItem ?: return null
        val serverUrl = securePreferences.getServerUrl() ?: return null
        val apiKey = securePreferences.getApiKey() ?: return null
        
        return if (useHls) {
            // HLS streaming - build URL with track parameters upfront
            buildString {
                append("$serverUrl/Videos/${mediaItem.id}/master.m3u8")
                append("?MediaSourceId=${mediaItem.id}")
                append("&VideoCodec=h264,hevc")
                append("&AudioCodec=aac,mp3,ac3,eac3")
                append("&api_key=$apiKey")
                append("&TranscodingMaxAudioChannels=2")
                append("&RequireAvc=false")
                append("&Tag=${mediaItem.imageTags?.get("Primary") ?: ""}")
                append("&SegmentContainer=ts")
                append("&MinSegments=1")
                append("&BreakOnNonKeyFrames=true")
                
                // Add track parameters directly to URL (not via ResolvingDataSource)
                // selectedAudioTrack/selectedSubtitleTrack are relativeIndex values,
                // but HLS needs the Jellyfin MediaStream index, so look up the track
                val audioRelativeIndex = _uiState.value.selectedAudioTrack
                if (audioRelativeIndex != null) {
                    val audioTrack = _uiState.value.audioTracks.firstOrNull { it.relativeIndex == audioRelativeIndex }
                    if (audioTrack != null) {
                        append("&AudioStreamIndex=${audioTrack.index}")
                    }
                }
                
                val subtitleRelativeIndex = _uiState.value.selectedSubtitleTrack
                if (subtitleRelativeIndex != null) {
                    val subtitleTrack = _uiState.value.subtitleTracks.firstOrNull { it.relativeIndex == subtitleRelativeIndex }
                    if (subtitleTrack != null) {
                        append("&SubtitleStreamIndex=${subtitleTrack.index}")
                    }
                }
            }
        } else {
            // Direct play fallback
            "$serverUrl/Videos/${mediaItem.id}/stream?Static=true&api_key=$apiKey&Container=mkv"
        }
    }
    
    fun updatePlaybackState(isPlaying: Boolean, currentPosition: Long, bufferedPosition: Long) {
        _uiState.update {
            it.copy(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                bufferedPosition = bufferedPosition
            )
        }
    }
    
    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }
    
    fun selectAudioTrack(trackIndex: Int?) {
        _uiState.update { it.copy(selectedAudioTrack = trackIndex) }
    }
    
    fun selectSubtitleTrack(trackIndex: Int?) {
        _uiState.update { it.copy(selectedSubtitleTrack = trackIndex) }
    }
    
    fun showTrackSelection(show: Boolean) {
        _uiState.update { it.copy(showTrackSelection = show) }
    }
    
    fun showSpeedSelection(show: Boolean) {
        _uiState.update { it.copy(showSpeedSelection = show) }
    }
    
    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }
    
    fun captureStartTime() {
        _uiState.update { 
            it.copy(capturedStartTime = it.currentPosition)
        }
    }
    
    fun captureEndTime() {
        _uiState.update { 
            it.copy(capturedEndTime = it.currentPosition)
        }
    }
    
    fun clearCapturedTimes() {
        _uiState.update { 
            it.copy(capturedStartTime = null, capturedEndTime = null)
        }
    }
    
    /**
     * Deletes a segment
     */
    fun deleteSegment(segment: Segment) {
        viewModelScope.launch {
            try {
                val segmentId = segment.id
                if (segmentId == null) {
                    Log.w(TAG, "Cannot delete segment: missing ID")
                    return@launch
                }
                
                Log.d(TAG, "Deleting segment: ${segment.type}")
                
                val result = segmentRepository.deleteSegmentResult(
                    segmentId = segmentId,
                    itemId = segment.itemId,
                    segmentType = segment.type
                )
                
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Segment deleted successfully")
                        // Refresh segments to get the latest state
                        refreshSegments()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to delete segment", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting segment", e)
            }
        }
    }
    
    fun refreshSegments() {
        val itemId = _uiState.value.mediaItem?.id ?: return
        loadSegments(itemId)
    }
    
    fun clearEvent() {
        _events.value = null
    }

    fun createPreviewLoader(itemId: String): PreviewLoader? {
        val serverUrl = securePreferences.getServerUrl()
        val apiKey = securePreferences.getApiKey()
        val userId = securePreferences.getUserId()

        if (serverUrl != null && apiKey != null && userId != null) {
            try {
                return TrickplayPreviewLoader(serverUrl, apiKey, userId, itemId, httpClient)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create TrickplayPreviewLoader", e)
            }
        } else {
            Log.d(TAG, "Server URL, API key, or User ID not available, cannot create preview loader")
        }
        return null
    }
    
    companion object {
        private const val TAG = "PlayerViewModel"
    }
}
