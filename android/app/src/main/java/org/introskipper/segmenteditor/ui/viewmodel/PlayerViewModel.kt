package org.introskipper.segmenteditor.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.introskipper.segmenteditor.data.model.MediaStream
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            
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
                        
                        // Extract audio and subtitle tracks
                        extractTracks(mediaItem.mediaStreams)
                        
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
    
    private fun extractTracks(mediaStreams: List<MediaStream>?) {
        if (mediaStreams == null) return
        
        val audioTracks = mediaStreams
            .filter { it.type == "Audio" }
            .mapIndexed { idx, stream ->
                TrackInfo(
                    index = idx,  // Use accumulated index (position in filtered list)
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: buildTrackTitle("Audio", stream.language, stream.codec),
                    codec = stream.codec,
                    isDefault = stream.isDefault
                )
            }
        
        val subtitleTracks = mediaStreams
            .filter { it.type == "Subtitle" }
            .mapIndexed { idx, stream ->
                TrackInfo(
                    index = idx,  // Use accumulated index (position in filtered list)
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: buildTrackTitle("Subtitle", stream.language, stream.codec),
                    codec = stream.codec,
                    isDefault = stream.isDefault
                )
            }
        
        // Find the default track index or use the first track (index 0)
        val defaultAudioIndex = audioTracks.indexOfFirst { it.isDefault }.takeIf { it >= 0 } ?: audioTracks.firstOrNull()?.index
        val defaultSubtitleIndex = subtitleTracks.indexOfFirst { it.isDefault }.takeIf { it >= 0 }
        
        _uiState.update { 
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                selectedAudioTrack = defaultAudioIndex,
                selectedSubtitleTrack = defaultSubtitleIndex
            )
        }
    }
    
    private fun buildTrackTitle(type: String, language: String?, codec: String?): String {
        return buildString {
            append(type)
            if (language != null) append(" - $language")
            if (codec != null) append(" ($codec)")
        }
    }
    
    fun getStreamUrl(useHls: Boolean = true): String? {
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
