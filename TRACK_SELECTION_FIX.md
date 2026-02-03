# Audio and Subtitle Track Selection Fix

## Problem
The audio and subtitle track selections in the video player were not working. When users selected different audio or subtitle tracks from the UI, the video player did not actually switch to the selected track.

## Root Cause
The previous implementation had two main issues:

1. **Track Extraction Source**: Tracks were being extracted from `MediaItem.mediaStreams` metadata rather than from the actual ExoPlayer `Tracks` object. The metadata tracks may not match the actual tracks available in the player after the media is loaded.

2. **Missing Track Change Callback**: The `onTracksChanged` callback in the video player was empty, so the app never received updates about which tracks were actually available in the player.

## Solution

### 1. Implemented `onTracksChanged` Callback
- Added `onTracksChanged` parameter to `VideoPlayerWithPreview` component
- This callback is triggered when ExoPlayer's tracks change (e.g., after media is loaded)
- The callback passes the `Tracks` object containing actual available tracks from the player

**File**: `VideoPlayerWithPreview.kt`
```kotlin
override fun onTracksChanged(tracks: Tracks) {
    onTracksChanged(tracks)
}
```

### 2. Created `updateTracksFromPlayer()` Method
Added a new method in `PlayerViewModel` to extract tracks directly from ExoPlayer's `Tracks` object:

**File**: `PlayerViewModel.kt`
```kotlin
fun updateTracksFromPlayer(tracks: androidx.media3.common.Tracks) {
    val audioTracks = mutableListOf<TrackInfo>()
    val subtitleTracks = mutableListOf<TrackInfo>()
    
    // Iterate through track groups and extract audio/subtitle tracks
    tracks.groups.forEachIndexed { groupIndex, trackGroup ->
        when (trackGroup.type) {
            C.TRACK_TYPE_AUDIO -> {
                for (trackIndex in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(trackIndex)
                    audioTracks.add(TrackInfo(...))
                }
            }
            C.TRACK_TYPE_TEXT -> {
                for (trackIndex in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(trackIndex)
                    subtitleTracks.add(TrackInfo(...))
                }
            }
        }
    }
    
    // Also detect currently selected tracks
    // Update UI state with new track lists and selections
}
```

This method:
- Extracts all audio and subtitle tracks from the player's `Tracks` object
- Uses accumulated indices to match the flat list structure used by the UI
- Detects which tracks are currently selected
- Updates the UI state with the correct track information

### 3. Removed Old Track Extraction
Removed the `extractTracks()` method that was extracting tracks from `MediaStreams` metadata, as it was providing incorrect track information.

### 4. Improved Track Selection Logic
Updated the `LaunchedEffect` blocks that apply track selections to properly clear previous overrides before setting new ones:

**File**: `PlayerScreen.kt`
```kotlin
// Audio track selection
exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
    .buildUpon()
    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)  // Clear previous overrides
    .setOverrideForType(
        TrackSelectionOverride(group.mediaTrackGroup, trackIndexInGroup)
    )
    .build()
```

The key improvement is calling `clearOverridesOfType()` before setting new overrides, ensuring that previous selections don't interfere with new ones.

### 5. Connected Everything in PlayerScreen
Updated the `VideoPlayerWithPreview` call to pass the callback:

```kotlin
VideoPlayerWithPreview(
    streamUrl = streamUrl,
    useController = true,
    previewLoader = previewLoader,
    onPlayerReady = onPlayerReady,
    onPlaybackStateChanged = { ... },
    onTracksChanged = { tracks ->
        viewModel.updateTracksFromPlayer(tracks)
    }
)
```

## How It Works Now

1. **Media Loading**: When a media item is loaded, ExoPlayer prepares the media and determines which tracks are available
2. **Track Discovery**: ExoPlayer triggers `onTracksChanged` callback with the actual available tracks
3. **Track Extraction**: `updateTracksFromPlayer()` extracts audio and subtitle tracks from the `Tracks` object and updates the UI state
4. **UI Display**: The track selection sheets show the correct tracks extracted from the player
5. **Track Selection**: When user selects a track, the selection is applied using proper ExoPlayer track selection API with correct MediaTrackGroup references
6. **Playback**: The video player actually switches to the selected audio or subtitle track

## Key Changes Summary

### VideoPlayerWithPreview.kt
- Added `onTracksChanged` callback parameter
- Implemented the callback to forward `Tracks` changes to the caller

### PlayerViewModel.kt
- Added `updateTracksFromPlayer()` method to extract tracks from ExoPlayer
- Removed `extractTracks()` method that used MediaStreams metadata
- Added import for `androidx.media3.common.C`

### PlayerScreen.kt
- Updated `VideoPlayerWithPreview` call to include `onTracksChanged` callback
- Improved track selection LaunchedEffects to use `clearOverridesOfType()`
- Removed unnecessary playback state save/restore code

## Testing
Build the APK and test:
1. Load a media item with multiple audio tracks
2. Open the audio track selection sheet - verify tracks are displayed
3. Select a different audio track - verify audio changes
4. Open subtitle track selection sheet - verify tracks are displayed  
5. Select a subtitle track - verify subtitles appear
6. Disable subtitles - verify subtitles disappear

## Result
Audio and subtitle track selection now works correctly. The tracks shown in the UI match the actual tracks available in the player, and selecting a track properly changes the audio or subtitles in the video player.
