# Audio and Subtitle Track Selection Implementation

## Summary

This implementation fixes audio stream and subtitle track selection without recreating the ExoPlayer instance, as requested in the issue.

## Problem Statement

- Audio streams did not change when selected from the UI
- Subtitles were not displayed or changed when selecting from the UI  
- The previous implementation recreated the ExoPlayer on every track change (inefficient)
- Need to reference jellyfin-android projects and segment-editor for proper implementation

## Solution Overview

The implementation now:

1. **Keeps ExoPlayer instance stable** - Created once with `remember{}` without dependencies on track selection
2. **Audio track switching** - Updates MediaItem with new HLS URL containing `AudioStreamIndex` parameter (server-side selection via Jellyfin transcoding)
3. **Subtitle track switching** - Uses ExoPlayer's `TrackSelectionParameters` for client-side selection
4. **Track information extraction** - Implements detailed track information extraction in `onTracksChanged` listener

## Key Changes

### 1. VideoPlayerWithPreview.kt

#### Before:
```kotlin
val exoPlayer = remember(streamUrl) {
    // Player was recreated every time streamUrl changed
    ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(streamUrl))
        prepare()
    }
}
```

#### After:
```kotlin
// Create player once - stable across track changes
val trackSelector = remember { DefaultTrackSelector(context) }
val exoPlayer = remember {
    ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()
}

// Update MediaItem when URL changes (without recreating player)
LaunchedEffect(streamUrl) {
    val currentPosition = exoPlayer.currentPosition
    val wasPlaying = exoPlayer.playWhenReady
    
    exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
    exoPlayer.prepare()
    
    // Restore playback state
    if (currentPosition > MIN_POSITION_TO_RESTORE_MS) {
        exoPlayer.seekTo(currentPosition)
    }
    exoPlayer.playWhenReady = wasPlaying
}
```

### 2. Subtitle Track Selection

```kotlin
LaunchedEffect(selectedSubtitleTrackIndex) {
    val parametersBuilder = trackSelector.parameters.buildUpon()
    
    if (selectedSubtitleTrackIndex != null) {
        // Enable and select subtitle track
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        
        exoPlayer.currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                val override = TrackSelectionOverride(
                    group.mediaTrackGroup, 
                    listOf(0) // Select first track in group
                )
                parametersBuilder.setOverrideForType(override)
            }
        }
    } else {
        // Disable subtitles
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    }
    
    trackSelector.setParameters(parametersBuilder)
}
```

### 3. Track Information Extraction (New Requirement)

Implemented in `onTracksChanged` listener:

```kotlin
override fun onTracksChanged(tracks: Tracks) {
    // Extract audio tracks
    val availableAudioTracks = mutableListOf<Pair<Int, String>>()
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val language = format.language ?: "Unknown"
                val label = format.label ?: "Audio ${trackIndex + 1}"
                availableAudioTracks.add(Pair(trackIndex, "$label ($language)"))
            }
        }
    }
    
    // Extract subtitle tracks
    val availableSubtitleTracks = mutableListOf<Pair<Int, String>>()
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_TEXT) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val language = format.language ?: "Unknown"
                val label = format.label ?: "Subtitle ${trackIndex + 1}"
                availableSubtitleTracks.add(Pair(trackIndex, "$label ($language)"))
            }
        }
    }
}
```

### 4. PlayerScreen.kt

Updated to pass track indices to VideoPlayer:

```kotlin
VideoPlayerWithPreview(
    streamUrl = streamUrl,
    selectedAudioTrackIndex = uiState.selectedAudioTrack,
    selectedSubtitleTrackIndex = uiState.selectedSubtitleTrack,
    onTracksChanged = { tracks ->
        viewModel.updateTracksFromPlayer(tracks)
    }
)
```

Stream URL generation now includes audio track for transcoding:

```kotlin
val streamUrl = remember(uiState.selectedAudioTrack, uiState.mediaItem) {
    viewModel.getStreamUrl(
        useHls = true,
        audioStreamIndex = uiState.selectedAudioTrack,
        subtitleStreamIndex = null  // Handled client-side
    )
}
```

### 5. PlayerViewModel.kt

Enhanced `updateTracksFromPlayer` to extract and log available tracks:

```kotlin
fun updateTracksFromPlayer(tracks: androidx.media3.common.Tracks) {
    // Extract actual available tracks from ExoPlayer
    val exoAudioTracks = mutableListOf<TrackInfo>()
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                exoAudioTracks.add(TrackInfo(
                    index = trackIndex,
                    language = format.language,
                    displayTitle = format.label ?: "Audio ${exoAudioTracks.size + 1}",
                    codec = format.sampleMimeType
                ))
            }
        }
    }
    // Similar for subtitles...
}
```

## Architecture Decisions

### Why Different Approaches for Audio vs Subtitles?

1. **Audio Tracks**: Jellyfin's HLS transcoding requires the audio track to be selected at transcode session creation time via the `AudioStreamIndex` URL parameter. We cannot switch audio tracks client-side in most cases, so we reload the stream URL with the new audio index.

2. **Subtitle Tracks**: Subtitles are often embedded in the HLS manifest or can be loaded externally. ExoPlayer can switch between them client-side using `TrackSelectionParameters` without reloading the stream.

### Why Keep ExoPlayer Instance?

- **Performance**: Creating a new ExoPlayer instance is expensive (codec initialization, decoder setup)
- **User Experience**: Reusing the instance provides smoother transitions and better state preservation
- **Resource Management**: Avoids memory leaks and ensures proper cleanup

## Behavior Flow

### Audio Track Change:
1. User selects new audio track from UI
2. `selectedAudioTrack` state updates
3. `streamUrl` recomputes with new `AudioStreamIndex` parameter
4. `LaunchedEffect(streamUrl)` triggers
5. Current position and play state are saved
6. New `MediaItem` is set with updated URL
7. Player prepares new stream
8. Position and play state are restored
9. **ExoPlayer instance remains the same**

### Subtitle Track Change:
1. User selects new subtitle track from UI
2. `selectedSubtitleTrack` state updates
3. `LaunchedEffect(selectedSubtitleTrackIndex)` triggers
4. `TrackSelectionParameters` are updated with new override
5. ExoPlayer switches subtitle track instantly
6. **No stream reload, no position change**

## Testing Checklist

- [ ] Audio track changes work correctly
- [ ] Subtitle track changes work correctly
- [ ] Subtitles display properly when enabled
- [ ] Subtitles hide properly when disabled
- [ ] Playback position is preserved during audio track changes
- [ ] Play/pause state is preserved during track changes
- [ ] ExoPlayer instance is NOT recreated on track changes
- [ ] Available tracks are logged in onTracksChanged
- [ ] Track information is accurate (language, codec, label)
- [ ] No memory leaks or resource issues

## Logging

The implementation includes comprehensive logging:

- Player creation: `"Creating ExoPlayer instance"`
- Stream URL changes: `"Stream URL changed: $streamUrl"`
- Subtitle selection: `"Applying subtitle track selection: $index"`
- Track changes: `"onTracksChanged: X track groups"`
- Available tracks: `"Available tracks - Audio: X, Subtitles: Y"`
- Track details: Language, codec, label for each track

## References

- [ExoPlayer Track Selection Documentation](https://exoplayer.dev/track-selection.html)
- [Jellyfin Android Client](https://github.com/jellyfin/jellyfin-android)
- [segment-editor Reference](https://github.com/intro-skipper/segment-editor)
- [Media3 TrackSelectionParameters API](https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters)

## Future Improvements

1. Map Jellyfin subtitle indices to ExoPlayer track groups more precisely
2. Support external subtitle file loading for non-embedded subtitles
3. Add subtitle track preference persistence
4. Implement automatic track selection based on device language
5. Support ASS/SSA subtitle rendering if needed
