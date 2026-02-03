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
import org.introskipper.segmenteditor.data.model.SegmentCreateRequest
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
                    isPlaying = false
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
            .map { stream ->
                TrackInfo(
                    index = stream.index,  // Use Jellyfin stream index
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: buildTrackTitle("Audio", stream.language, stream.codec),
                    codec = stream.codec,
                    isDefault = stream.isDefault,
                    source = org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                )
            }
        
        val subtitleTracks = mediaStreams
            .filter { it.type == "Subtitle" }
            .map { stream ->
                TrackInfo(
                    index = stream.index,  // Use Jellyfin stream index
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: buildTrackTitle("Subtitle", stream.language, stream.codec),
                    codec = stream.codec,
                    isDefault = stream.isDefault,
                    source = org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                )
            }
        
        // Find the default track index or use the first track if tracks exist
        val defaultAudioIndex = if (audioTracks.isEmpty()) {
            null
        } else {
            audioTracks.firstOrNull { it.isDefault }?.index ?: audioTracks.firstOrNull()?.index
        }
        val defaultSubtitleIndex = subtitleTracks.firstOrNull { it.isDefault }?.index
        
        Log.d(TAG, "Extracted from Jellyfin: ${audioTracks.size} audio tracks, ${subtitleTracks.size} subtitle tracks")
        audioTracks.forEach { track ->
            Log.d(TAG, "Audio track: index=${track.index}, title=${track.displayTitle}, default=${track.isDefault}, source=${track.source}")
        }
        subtitleTracks.forEach { track ->
            Log.d(TAG, "Subtitle track: index=${track.index}, title=${track.displayTitle}, default=${track.isDefault}, source=${track.source}")
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
        // Extract actual available tracks from ExoPlayer player
        // Merge with Jellyfin tracks to cover all possible audio and subtitle types
        val exoAudioCount = tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }
        val exoSubtitleCount = tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
        
        Log.d(TAG, "ExoPlayer tracks available: $exoAudioCount audio groups, $exoSubtitleCount subtitle groups")
        
        // Extract audio tracks from ExoPlayer
        val exoAudioTracks = mutableListOf<TrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language
                    val label = format.label ?: "Audio ${exoAudioTracks.size + 1}"
                    exoAudioTracks.add(TrackInfo(
                        index = trackIndex,
                        language = language,
                        displayTitle = label,
                        codec = format.sampleMimeType,
                        isDefault = false,
                        source = org.introskipper.segmenteditor.ui.state.TrackSource.EXOPLAYER
                    ))
                    Log.d(TAG, "ExoPlayer audio track: index=$trackIndex, language=$language, label=$label")
                }
            }
        }
        
        // Extract subtitle tracks from ExoPlayer
        val exoSubtitleTracks = mutableListOf<TrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language
                    val label = format.label ?: "Subtitle ${exoSubtitleTracks.size + 1}"
                    exoSubtitleTracks.add(TrackInfo(
                        index = trackIndex,
                        language = language,
                        displayTitle = label,
                        codec = format.sampleMimeType,
                        isDefault = false,
                        source = org.introskipper.segmenteditor.ui.state.TrackSource.EXOPLAYER
                    ))
                    Log.d(TAG, "ExoPlayer subtitle track: index=$trackIndex, language=$language, label=$label")
                }
            }
        }
        
        // Log track counts from both sources
        val jellyfinAudioCount = _uiState.value.audioTracks.size
        val jellyfinSubtitleCount = _uiState.value.subtitleTracks.size
        Log.d(TAG, "Jellyfin tracks: $jellyfinAudioCount audio, $jellyfinSubtitleCount subtitles")
        Log.d(TAG, "ExoPlayer tracks: ${exoAudioTracks.size} audio, ${exoSubtitleTracks.size} subtitles")
        
        // Update UI state with merged tracks
        _uiState.update { state ->
            state.copy(
                audioTracks = _uiState.value.audioTracks.ifEmpty { exoAudioTracks },
                subtitleTracks = _uiState.value.subtitleTracks.ifEmpty { exoSubtitleTracks }
            )
        }
    }
    
    fun getStreamUrl(useHls: Boolean = true, audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null): String? {
        val mediaItem = _uiState.value.mediaItem ?: return null
        val serverUrl = securePreferences.getServerUrl() ?: return null
        val apiKey = securePreferences.getApiKey() ?: return null
        
        return if (useHls) {
            // HLS streaming (preferred)
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
                append("&MinSegments=2")
                append("&BreakOnNonKeyFrames=true")
                
                // Add audio stream index if specified
                if (audioStreamIndex != null) {
                    append("&AudioStreamIndex=$audioStreamIndex")
                }
                
                // Add subtitle stream index if specified
                if (subtitleStreamIndex != null) {
                    append("&SubtitleStreamIndex=$subtitleStreamIndex")
                    // For HLS, subtitles need to be encoded into the stream
                    // append("&SubtitleMethod=Encode")
                }
            }
        } else {
            // Direct play fallback
            "$serverUrl/Videos/${mediaItem.id}/stream?Static=true&api_key=$apiKey"
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
     * Updates an existing segment by deleting the old one and creating a new one
     * This matches the pattern used in SegmentEditorViewModel
     */
    fun updateSegment(oldSegment: Segment, newSegment: Segment) {
        viewModelScope.launch {
            try {
                // Validate segments match
                if (oldSegment.itemId != newSegment.itemId || oldSegment.type != newSegment.type) {
                    Log.e(TAG, "Segment validation failed: itemId or type mismatch")
                    _events.value = PlayerEvent.Error("Invalid segment update: type or item mismatch")
                    return@launch
                }
                
                Log.d(TAG, "Updating segment: ${oldSegment.type}")
                
                // Delete the old segment
                val segmentId = oldSegment.id
                if (segmentId == null) {
                    Log.w(TAG, "Cannot update segment: missing ID")
                    _events.value = PlayerEvent.Error("Cannot update segment: missing ID")
                    return@launch
                }
                
                val deleteResult = segmentRepository.deleteSegmentResult(
                    segmentId = segmentId,
                    itemId = oldSegment.itemId,
                    segmentType = oldSegment.type
                )
                
                if (deleteResult.isFailure) {
                    Log.e(TAG, "Failed to delete old segment", deleteResult.exceptionOrNull())
                    _events.value = PlayerEvent.Error("Failed to update segment")
                    return@launch
                }
                
                // Create the new segment
                val segmentRequest = SegmentCreateRequest(
                    itemId = newSegment.itemId,
                    type = newSegment.type,
                    startTicks = newSegment.startTicks,
                    endTicks = newSegment.endTicks
                )
                
                val createResult = segmentRepository.createSegmentResult(
                    itemId = newSegment.itemId,
                    segment = segmentRequest
                )
                
                createResult.fold(
                    onSuccess = {
                        Log.d(TAG, "Segment updated successfully")
                        // Refresh segments to get the latest state
                        refreshSegments()
                        _events.value = PlayerEvent.SegmentUpdated
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to create updated segment", error)
                        _events.value = PlayerEvent.Error("Failed to save changes: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception updating segment", e)
                _events.value = PlayerEvent.Error("Error updating segment: ${e.message}")
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
