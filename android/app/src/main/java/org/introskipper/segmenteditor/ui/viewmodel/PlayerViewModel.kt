package org.introskipper.segmenteditor.ui.viewmodel

import android.util.Log
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import org.introskipper.segmenteditor.framecapture.PreviewFrames.loadPreviewFrame
import org.introskipper.segmenteditor.framecapture.PreviewFrames.onPreviewsRequested
import org.introskipper.segmenteditor.framecapture.PreviewFrames.onReleasePreviews
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.introskipper.segmenteditor.data.model.MediaItem
import org.introskipper.segmenteditor.data.model.MediaStream
import org.introskipper.segmenteditor.data.model.Segment
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.preview.PreviewLoader
import org.introskipper.segmenteditor.ui.preview.TrickplayPreviewLoader
import org.introskipper.segmenteditor.ui.state.PlayerEvent
import org.introskipper.segmenteditor.ui.state.PlayerUiState
import org.introskipper.segmenteditor.ui.state.TrackInfo
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences,
    private val httpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Tracks an in-flight batch save so it can be cancelled before starting a new one
    private var saveJob: Job? = null

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
                    selectedSubtitleTrack = null,
                    nextItemId = null
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
                    fields = listOf("MediaSources", "MediaStreams", "Path", "Container", "SeriesId", "SeasonId", "IndexNumber")
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

                        // Find next episode for auto-play
                        findNextEpisode(mediaItem)
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

    private fun findNextEpisode(mediaItem: MediaItem) {
        // Only look for next episode if it's an episode and has season/series info
        if (mediaItem.type != "Episode" || mediaItem.seriesId == null || mediaItem.seasonId == null) {
            return
        }

        viewModelScope.launch {
            try {
                val userId = securePreferences.getUserId() ?: return@launch
                
                // Fetch episodes for the same season
                val response = mediaRepository.getEpisodes(
                    seriesId = mediaItem.seriesId,
                    userId = userId,
                    seasonId = mediaItem.seasonId,
                    fields = listOf("IndexNumber")
                )

                if (response.isSuccessful && response.body() != null) {
                    val episodes = response.body()!!.items.sortedBy { it.indexNumber }
                    val currentIndex = episodes.indexOfFirst { it.id == mediaItem.id }
                    
                    if (currentIndex != -1 && currentIndex < episodes.size - 1) {
                        val nextEpisode = episodes[currentIndex + 1]
                        Log.d(TAG, "Found next episode: ${nextEpisode.name} (ID: ${nextEpisode.id})")
                        _uiState.update { it.copy(nextItemId = nextEpisode.id) }
                    } else {
                        Log.d(TAG, "No more episodes in this season.")
                        _uiState.update { it.copy(nextItemId = null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to find next episode", e)
            }
        }
    }

    private fun loadSegments(itemId: String) {
        viewModelScope.launch {
            try {
                val result = segmentRepository.getSegmentsResult(itemId)
                result.fold(
                    onSuccess = { segments ->
                        Log.d(TAG, "Successfully loaded ${segments.size} segments for $itemId")
                        segments.forEachIndexed { index, segment ->
                            Log.d(TAG, "Segment $index: type=${segment.type}, start=${segment.getStartSeconds()}s, end=${segment.getEndSeconds()}s")
                        }
                        _uiState.update { it.copy(segments = segments) }
                        _events.value = PlayerEvent.SegmentLoaded(segments)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Failed to load segments for $itemId (non-critical): ${error.message}", error)
                        // Segments are optional, don't show error to user
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception loading segments for $itemId (non-critical): ${e.message}", e)
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
        
        audioTracks.forEach { track ->
            Log.d(TAG, " Jellyfin Audio track: index=${track.index}, relativeIndex=${track.relativeIndex}, title=${track.displayTitle}, default=${track.isDefault}, source=${track.source}")
        }
        subtitleTracks.forEach { track ->
            Log.d(TAG, " Jellyfin Subtitle track: index=${track.index}, relativeIndex=${track.relativeIndex}, title=${track.displayTitle}, default=${track.isDefault}, source=${track.source}")
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

    fun updateTracksFromPlayer(tracks: androidx.media3.common.Tracks, useDirectPlay: Boolean) {
        // For direct play: tracks are embedded in the media file, extract from ExoPlayer
        // For HLS: tracks come from Jellyfin MediaStreams API, this is just for fallback

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
            // HLS mode: keep existing Jellyfin tracks, only use ExoPlayer tracks if none exist
            // DO NOT update track selections in HLS mode to prevent reload loops:
            // Track selection changes trigger streamUrl recalculation (tracks are remember keys in PlayerScreen)
            // which causes the player to reload repeatedly
            Log.d(TAG, "HLS mode: managing tracks for HLS playback")
            _uiState.update { state ->
                // Check if we have tracks and what their source is
                val currentTrackSource = state.audioTracks.firstOrNull()?.source
                val hasExoPlayerTracks = currentTrackSource == org.introskipper.segmenteditor.ui.state.TrackSource.EXOPLAYER
                val hasJellyfinTracks = currentTrackSource == org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                val hasNoTracks = state.audioTracks.isEmpty()

                Log.d(TAG, "HLS mode: current track state - hasNoTracks=$hasNoTracks, hasExoPlayerTracks=$hasExoPlayerTracks, hasJellyfinTracks=$hasJellyfinTracks, currentSelections=(audio=${state.selectedAudioTrack}, subtitle=${state.selectedSubtitleTrack})")

                when {
                    // Case 1: Switching from Direct Play to HLS - restore Jellyfin tracks
                    hasExoPlayerTracks -> {
                        val mediaStreams = state.mediaItem?.mediaStreams

                        if (mediaStreams != null) {
                            val jellyfinAudioTracks = mediaStreams
                                .filter { it.type == "Audio" }
                                .mapIndexed { relativeIndex, stream ->
                                    TrackInfo(
                                        index = stream.index,
                                        relativeIndex = relativeIndex,
                                        language = stream.language,
                                        displayTitle = stream.displayTitle ?: buildTrackTitle("Audio", stream.language, stream.codec),
                                        codec = stream.codec,
                                        isDefault = stream.isDefault,
                                        source = org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                                    )
                                }

                            val jellyfinSubtitleTracks = mediaStreams
                                .filter { it.type == "Subtitle" }
                                .mapIndexed { relativeIndex, stream ->
                                    TrackInfo(
                                        index = stream.index,
                                        relativeIndex = relativeIndex,
                                        language = stream.language,
                                        displayTitle = stream.displayTitle ?: buildTrackTitle("Subtitle", stream.language, stream.codec),
                                        codec = stream.codec,
                                        isDefault = stream.isDefault,
                                        source = org.introskipper.segmenteditor.ui.state.TrackSource.JELLYFIN
                                    )
                                }

                            // Preserve current selections (same relativeIndex should work for both sources)
                            // Audio: current selection (if valid) → default track → first track → null
                            // Subtitle: current selection (if valid) → null (subtitles are optional)
                            val preservedAudioSelection = state.selectedAudioTrack?.let { current ->
                                // Keep current if within bounds, otherwise try fallbacks
                                if (current < jellyfinAudioTracks.size) {
                                    current
                                } else {
                                    // Current selection invalid, use default or first track
                                    jellyfinAudioTracks.firstOrNull { it.isDefault }?.relativeIndex
                                        ?: jellyfinAudioTracks.firstOrNull()?.relativeIndex
                                }
                            } ?: jellyfinAudioTracks.firstOrNull { it.isDefault }?.relativeIndex
                                ?: jellyfinAudioTracks.firstOrNull()?.relativeIndex

                            val preservedSubtitleSelection = state.selectedSubtitleTrack?.let { current ->
                                // Keep current if within bounds, otherwise clear (subtitles are optional)
                                if (current < jellyfinSubtitleTracks.size) current else null
                            }

                            Log.d(TAG, "HLS mode: restored ${jellyfinAudioTracks.size} Jellyfin tracks, selections (audio=$preservedAudioSelection, subtitle=$preservedSubtitleSelection)")

                            state.copy(
                                audioTracks = jellyfinAudioTracks,
                                subtitleTracks = jellyfinSubtitleTracks,
                                selectedAudioTrack = preservedAudioSelection,
                                selectedSubtitleTrack = preservedSubtitleSelection
                            )
                        } else {
                            // Fallback to ExoPlayer tracks if Jellyfin metadata not available
                            Log.w(TAG, "HLS mode: no mediaStreams, using ExoPlayer tracks")
                            state.copy(
                                audioTracks = exoAudioTracks,
                                subtitleTracks = exoSubtitleTracks,
                                selectedAudioTrack = state.selectedAudioTrack ?: exoAudioTracks.firstOrNull()?.relativeIndex,
                                selectedSubtitleTrack = state.selectedSubtitleTrack
                            )
                        }
                    }

                    // Case 2: Initial HLS load with no tracks - use ExoPlayer tracks
                    hasNoTracks -> {
                        Log.d(TAG, "HLS mode: initial load, using ExoPlayer tracks")
                        state.copy(
                            audioTracks = exoAudioTracks,
                            subtitleTracks = exoSubtitleTracks,
                            selectedAudioTrack = exoAudioTracks.firstOrNull { it.isDefault }?.relativeIndex
                                ?: exoAudioTracks.firstOrNull()?.relativeIndex,
                            selectedSubtitleTrack = exoSubtitleTracks.firstOrNull { it.isDefault }?.relativeIndex
                        )
                    }

                    // Case 3: Already have Jellyfin tracks - keep them stable
                    hasJellyfinTracks -> {
                        Log.d(TAG, "HLS mode: keeping existing Jellyfin tracks and selections stable")
                        state
                    }

                    // Default: keep existing state
                    else -> {
                        Log.d(TAG, "HLS mode: keeping existing state (unknown track source)")
                        state
                    }
                }
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

        // https://developer.android.com/media/platform/supported-formats
        return if (useHls) {
            // HLS streaming - build URL with track parameters upfront
            buildString {
                append("$serverUrl/Videos/${mediaItem.id}/master.m3u8")
                append("?MediaSourceId=${mediaItem.id}")
                append("&VideoCodec=h264,hevc,h265,av1")
                append("&AudioCodec=aac,mp3,opus,flac,ac3,eac3")
                append("&api_key=$apiKey")
                append("&TranscodingMaxAudioChannels=2")
                append("&RequireAvc=false")
                append("&Tag=${mediaItem.imageTags?.get("Primary") ?: ""}")
                append("&SegmentContainer=mkv,mp4,ts")
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
                    } else {
                        Log.w(TAG, "Failed to find audio track with relativeIndex $audioRelativeIndex")
                    }
                }

                val subtitleRelativeIndex = _uiState.value.selectedSubtitleTrack
                if (subtitleRelativeIndex != null) {
                    val subtitleTrack = _uiState.value.subtitleTracks.firstOrNull { it.relativeIndex == subtitleRelativeIndex }
                    if (subtitleTrack != null) {
                        append("&SubtitleStreamIndex=${subtitleTrack.index}")
                    } else {
                        Log.w(TAG, "Failed to find subtitle track with relativeIndex $subtitleRelativeIndex")
                    }
                }
            }
        } else {
            // Direct play fallback
            "$serverUrl/Videos/${mediaItem.id}/stream?Static=true&api_key=$apiKey&Container=mp4,mkv"
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

    fun handlePlaybackEnded() {
        val nextId = _uiState.value.nextItemId
        val autoPlayEnabled = securePreferences.getAutoPlayNextEpisode()
        
        Log.d(TAG, "Playback ended. nextItemId=$nextId, autoPlayEnabled=$autoPlayEnabled")
        
        if (autoPlayEnabled && nextId != null) {
            _events.value = PlayerEvent.NavigateToPlayer(nextId)
        } else {
            _events.value = PlayerEvent.PlaybackEnded
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

    fun setUserLandscape() {
        _uiState.update { it.copy(isUserLandscape = true) }
    }

    fun setUserPortrait() {
        _uiState.update { it.copy(isUserLandscape = false) }
    }


    fun nextContentScale() {
        _uiState.update {
            when (it.playerContentScale) {
                ContentScale.FillBounds -> it.copy(playerContentScale = ContentScale.Crop)
                ContentScale.Crop -> it.copy(playerContentScale = ContentScale.Fit)
                else -> it.copy(playerContentScale = ContentScale.FillBounds)
            }
        }
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

                val result = segmentRepository.deleteSegmentResult(
                    segmentId = segmentId,
                    itemId = segment.itemId,
                    segmentType = segment.type
                )

                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Deleted ${segment.type} segment successfully")
                        // Refresh segments to get the latest state
                        refreshSegments()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to delete  ${segment.type} segment", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting  ${segment.type} segment", e)
            }
        }
    }

    /**
     * Helper function to save a single segment (delete-then-create pattern)
     * Returns the saved segment or null if save failed
     */
    private suspend fun saveSegmentInternal(segment: Segment): Segment? {
        return try {
            val segmentRequest = org.introskipper.segmenteditor.data.model.SegmentCreateRequest(
                itemId = segment.itemId,
                type = org.introskipper.segmenteditor.data.model.SegmentType.stringToApiValue(segment.type),
                startTicks = segment.startTicks,
                endTicks = segment.endTicks
            )
            
            // If segment has an ID, delete it first then create new
            if (segment.id != null) {
                val deleteResult = segmentRepository.deleteSegmentResult(
                    segmentId = segment.id,
                    itemId = segment.itemId,
                    segmentType = segment.type
                )
                
                if (deleteResult.isFailure) {
                    Log.e(TAG, "Failed to delete segment ${segment.type} during update", deleteResult.exceptionOrNull())
                    return null
                }
            }
            
            // Create the segment
            val result = segmentRepository.createSegmentResult(
                itemId = segment.itemId,
                segment = segmentRequest
            )
            
            result.getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving segment ${segment.type}", e)
            null
        }
    }

    /**
     * Saves a segment (creates new or updates existing)
     */
    fun saveSegment(segment: Segment, onComplete: (Result<Segment>) -> Unit) {
        viewModelScope.launch {
            val savedSegment = saveSegmentInternal(segment)
            if (savedSegment != null) {
                onComplete(Result.success(savedSegment))
            } else {
                onComplete(Result.failure(Exception("Failed to save segment")))
            }
        }
    }

    /**
     * Saves all segments using a delete-all-then-create-all batch pattern with parallel
     * operations. Any previously in-flight save is cancelled before starting a new one.
     */
    fun saveAllSegments(
        segments: List<Segment>,
        existingSegments: List<Segment>,
        onComplete: (Result<List<Segment>>) -> Unit
    ) {
        // Cancel any previous in-flight save to prevent concurrent operations
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _uiState.update { it.copy(isBatchSaving = true) }
            try {
                // Step 1: Delete all existing server-side segments in parallel.
                // Track failed IDs so we can skip recreating them in step 2 and avoid
                // leaving both old and new copies on the server (duplicates).
                val deleteResults = coroutineScope {
                    existingSegments
                        .filter { it.id != null }
                        .map { segment ->
                            async {
                                val result = segmentRepository.deleteSegmentResult(
                                    segmentId = segment.id!!,
                                    itemId = segment.itemId,
                                    segmentType = segment.type
                                )
                                result.onFailure { e ->
                                    Log.w(TAG, "Failed to delete segment ${segment.type} (ID: ${segment.id}) during batch save", e)
                                }
                                segment.id!! to result
                            }
                        }
                        .awaitAll()
                }

                // Collect IDs of segments whose delete failed; skipping their create
                // prevents leaving both old and new copies on the server.
                val failedDeleteIds = deleteResults
                    .filter { (_, result) -> result.isFailure }
                    .map { (id, _) -> id }
                    .toSet()

                // Bail out early if this job was cancelled while deletes were running
                ensureActive()

                // Step 2: Create new segments in parallel.
                // Skip any segment whose previous server copy could not be deleted.
                // New segments (id == null) are always included since null is never in failedDeleteIds.
                val segmentsToCreate = segments.filter { it.id !in failedDeleteIds }
                val createResults = coroutineScope {
                    segmentsToCreate.map { segment ->
                        async {
                            val segmentRequest = org.introskipper.segmenteditor.data.model.SegmentCreateRequest(
                                itemId = segment.itemId,
                                type = org.introskipper.segmenteditor.data.model.SegmentType.stringToApiValue(segment.type),
                                startTicks = segment.startTicks,
                                endTicks = segment.endTicks
                            )
                            segmentRepository.createSegmentResult(segment.itemId, segmentRequest).getOrNull()
                        }
                    }.awaitAll()
                }

                val saved = createResults.filterNotNull()
                val failedCount = segmentsToCreate.size - saved.size
                if (failedCount > 0) {
                    Log.w(TAG, "Batch save completed with $failedCount failures out of ${segmentsToCreate.size} segments")
                }
                onComplete(Result.success(saved))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled (e.g. a newer save started); propagate so coroutine is torn down
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception in batch save", e)
                onComplete(Result.failure(e))
            } finally {
                // Always clear the saving flag, even on cancellation, so the UI is never stuck
                _uiState.update { it.copy(isBatchSaving = false) }
            }
        }
    }

    fun refreshSegments(itemId: String? = null) {
        val mediaItemId = itemId ?: _uiState.value.mediaItem?.id ?: return
        loadSegments(mediaItemId)
    }

    fun clearEvent() {
        _events.value = null
    }

    fun createPreviewLoader(itemId: String, streamUrl: String?): PreviewLoader? {
        // Local generation preferred: skip trickplay
        if (securePreferences.getPreferLocalPreviews()) {
            return object : PreviewLoader {
                override suspend fun loadPreview(positionMs: Long): android.graphics.Bitmap? =
                    loadPreviewFrame(positionMs)
                override fun getPreviewInterval(): Long = 1000L
                override fun release() = onReleasePreviews()
            }
        }

        val serverUrl = securePreferences.getServerUrl()
        val apiKey = securePreferences.getApiKey()
        val userId = securePreferences.getUserId()

        if (serverUrl != null && apiKey != null && userId != null) {
            try {
                return TrickplayPreviewLoader(serverUrl, apiKey, userId, itemId, httpClient, streamUrl != null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create TrickplayPreviewLoader", e)
            }
        }

        // No Jellyfin credentials: local frame extraction only
        return object : PreviewLoader {
            override suspend fun loadPreview(positionMs: Long): android.graphics.Bitmap? =
                loadPreviewFrame(positionMs)
            override fun getPreviewInterval(): Long = 1000L
            override fun release() = onReleasePreviews()
        }
    }

    // Exposes onPreviewsRequested (a PreviewFrames extension on PlayerViewModel) to the UI layer
    fun setupFallbackPreviews(streamUrl: String) {
        onPreviewsRequested(streamUrl)
    }

    companion object {
        private const val TAG = "PlayerViewModel"
    }
}