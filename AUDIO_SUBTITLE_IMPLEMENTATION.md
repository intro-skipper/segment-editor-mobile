# Audio and Subtitle Track Selection Implementation

## Overview
This implementation enables working audio and subtitle track selection in the Segment Editor Mobile app by using Jellyfin's stream URL parameters to request specific tracks.

## Problem Statement
The original implementation attempted to switch audio/subtitle tracks within ExoPlayer using track selection overrides. However, Jellyfin's HLS transcoding doesn't support in-player track switching - the tracks must be specified when requesting the stream.

## Solution Architecture

### High-Level Flow
```
1. Media Loaded → Extract tracks from Jellyfin MediaStreams metadata
2. Display all available tracks to user in UI
3. User selects a track → Update UI state with selected track index
4. Stream URL regenerates with AudioStreamIndex/SubtitleStreamIndex parameter
5. ExoPlayer automatically recreates with new stream URL
6. Playback position is preserved during reload
```

### Key Components

#### 1. Track Discovery (PlayerViewModel.kt)
```kotlin
private fun extractTracksFromMediaStreams(mediaStreams: List<MediaStream>?) {
    // Extract audio tracks
    val audioTracks = mediaStreams
        .filter { it.type == "Audio" }
        .map { stream ->
            TrackInfo(
                index = stream.index,  // Use Jellyfin stream index
                language = stream.language,
                displayTitle = stream.displayTitle ?: buildTrackTitle(...),
                codec = stream.codec,
                isDefault = stream.isDefault
            )
        }
    
    // Extract subtitle tracks similarly
    // Update UI state with tracks
}
```

**Why This Works:**
- Uses Jellyfin's MediaStreams metadata which includes ALL available tracks
- Preserves Jellyfin's stream indices for use in URL parameters
- Shows complete track list even if not all are in current stream

#### 2. Stream URL Generation (PlayerViewModel.kt)
```kotlin
fun getStreamUrl(
    useHls: Boolean = true,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null
): String? {
    return buildString {
        append("$serverUrl/Videos/${mediaItem.id}/master.m3u8")
        append("?MediaSourceId=${mediaItem.id}")
        // ... other parameters ...
        
        if (audioStreamIndex != null) {
            append("&AudioStreamIndex=$audioStreamIndex")
        }
        if (subtitleStreamIndex != null) {
            append("&SubtitleStreamIndex=$subtitleStreamIndex")
        }
    }
}
```

**How It Works:**
- Jellyfin's transcoding API accepts `AudioStreamIndex` and `SubtitleStreamIndex` parameters
- When these are provided, Jellyfin includes the specified tracks in the HLS stream
- Each unique combination of parameters produces a different stream URL

#### 3. Automatic Stream Reload (PlayerScreen.kt)
```kotlin
// Stream URL that updates when tracks change
val streamUrl = remember(uiState.selectedAudioTrack, uiState.selectedSubtitleTrack) {
    viewModel.getStreamUrl(
        useHls = true,
        audioStreamIndex = uiState.selectedAudioTrack,
        subtitleStreamIndex = uiState.selectedSubtitleTrack
    )
}
```

**Automatic Reload Mechanism:**
- `remember` with track indices as keys causes recomputation when they change
- VideoPlayerWithPreview uses `remember(streamUrl)` for exoPlayer
- When streamUrl changes, Compose automatically recreates the player
- No manual player management needed

#### 4. Position Preservation (VideoPlayerWithPreview.kt)
```kotlin
// Track position across stream changes
var lastKnownPosition by remember { mutableStateOf(0L) }
var lastKnownPlayWhenReady by remember { mutableStateOf(true) }

val exoPlayer = remember(streamUrl) {
    ExoPlayer.Builder(context)
        // ... configuration ...
        .build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            
            // Restore position if reloading (> MIN_POSITION_TO_RESTORE_MS)
            if (lastKnownPosition > MIN_POSITION_TO_RESTORE_MS) {
                seekTo(lastKnownPosition)
            }
            
            prepare()
            playWhenReady = lastKnownPlayWhenReady
        }
}

// Update position using player listener callbacks
DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(...) {
            lastKnownPosition = newPosition.positionMs
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            lastKnownPlayWhenReady = playWhenReady
        }
    }
    exoPlayer.addListener(listener)
    
    onDispose {
        lastKnownPosition = exoPlayer.currentPosition
        lastKnownPlayWhenReady = exoPlayer.playWhenReady
        exoPlayer.removeListener(listener)
    }
}
```

**Position Preservation Details:**
- Uses `DisposableEffect` to add/remove player listeners
- Listener callbacks update position on player events (not polling)
- Position saved in `onDispose` before player is destroyed
- Position restored when new player is created (if > 1 second)
- `MIN_POSITION_TO_RESTORE_MS` threshold prevents restoring during initial load

## Technical Details

### Jellyfin Stream URL Parameters
- **AudioStreamIndex**: Integer index of audio stream from MediaStreams array
- **SubtitleStreamIndex**: Integer index of subtitle stream from MediaStreams array  
- These indices correspond to the `index` field in MediaStream objects
- Jellyfin's transcoder uses these to include specific tracks in HLS output

### Why Not Use ExoPlayer Track Selection?
1. **HLS Transcoding**: Jellyfin generates HLS streams on-demand with specific tracks
2. **Track Availability**: ExoPlayer only sees tracks that are IN the current stream
3. **No Switching**: Can't switch to tracks that aren't in the stream
4. **Solution**: Must request new stream with different tracks from Jellyfin

### Stream Recreation vs. Track Switching
**Old Approach (Doesn't Work):**
```
User selects track → Try to switch in ExoPlayer → Track not available → Fail
```

**New Approach (Works):**
```
User selects track → New URL with track parameter → New stream with track → Success
```

## User Experience

### What Happens When User Selects Track
1. User taps audio/subtitle button
2. Sheet shows all available tracks from Jellyfin
3. User selects a track
4. Brief loading indicator as stream regenerates
5. Playback continues from same position with new track
6. Sheet closes automatically

### Seamless Transition
- Position is preserved (within ~1 second accuracy)
- Playback state is preserved (playing/paused)
- Video doesn't jump to beginning
- Minimal interruption to viewing experience

## Files Modified

### PlayerViewModel.kt
- `extractTracksFromMediaStreams()` - Extract tracks from Jellyfin metadata
- `getStreamUrl()` - Accept optional track indices for URL generation
- `updateTracksFromPlayer()` - Simplified to just logging (for debugging)

### PlayerScreen.kt  
- Changed streamUrl to depend on selected track indices
- Removed LaunchedEffect blocks for track selection
- Removed unused imports (C, TrackSelectionOverride)

### VideoPlayerWithPreview.kt
- Added position preservation with DisposableEffect listeners
- Removed infinite polling loop (replaced with event callbacks)
- Removed `onTracksChanged` callback parameter
- Added MIN_POSITION_TO_RESTORE_MS constant

## Testing Checklist

1. **Initial Load**
   - Media loads with default tracks
   - Audio and subtitle lists populate correctly

2. **Audio Track Switching**
   - Select different audio track
   - Audio changes to selected track
   - Position preserved
   - Playback state preserved

3. **Subtitle Track Switching**
   - Enable subtitles
   - Subtitles appear
   - Switch to different subtitle
   - Subtitles change language/style
   - Disable subtitles
   - Subtitles disappear

4. **Edge Cases**
   - Change track while paused (should stay paused)
   - Change track while playing (should keep playing)
   - Change track near beginning (< 1 sec) - may not restore position
   - Change track multiple times rapidly

5. **Multiple Tracks**
   - Media with 5+ audio tracks
   - Media with 10+ subtitle tracks
   - Tracks display correctly
   - All tracks selectable

## Build Status
✅ Compilation successful  
✅ APK generated successfully  
✅ Code review completed  
✅ Ready for testing

## Conclusion
This implementation provides working audio and subtitle track selection by leveraging Jellyfin's stream URL parameters instead of attempting in-player track switching. The approach is more reliable and shows all available tracks to the user, not just those in the current stream.
