# Audio and Subtitle Track Selection Fix - Testing Guide

## Summary
Fixed the issue where audio and subtitle changes were not reflected in the player. Previously, when selecting a different audio or subtitle track, the player would pause but not change to the new stream.

## Bug Details
**Root Cause**: The AndroidView factory in VideoPlayerWithPreview.kt was setting `player = exoPlayer` only once during PlayerView creation. When the stream URL changed (due to track selection), a new ExoPlayer instance was created, but the PlayerView was never updated to use the new player instance.

**Symptom**: Player would pause/stop when selecting a new track but would not actually switch to the new audio/subtitle stream.

## Fix Applied
Moved the `player = exoPlayer` assignment from the AndroidView `factory` block to an `update` block. The update block runs on every recomposition, ensuring the PlayerView always uses the current ExoPlayer instance.

**File Changed**: `android/app/src/main/java/org/introskipper/segmenteditor/ui/component/VideoPlayerWithPreview.kt`

```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            // Factory runs once - setup only
            this.useController = useController
            // ... other setup ...
        }
    },
    update = { playerView ->
        // Update runs on every recomposition
        // This ensures PlayerView uses the new ExoPlayer when tracks change
        playerView.player = exoPlayer
    }
)
```

## How Track Selection Works
1. User opens audio or subtitle selection dialog
2. Dialog shows all available tracks from Jellyfin MediaStreams metadata
3. User selects a track → ViewModel updates selectedAudioTrack or selectedSubtitleTrack
4. Stream URL is regenerated with new AudioStreamIndex or SubtitleStreamIndex parameter
5. VideoPlayerWithPreview detects streamUrl change and creates new ExoPlayer instance
6. **NEW**: AndroidView update block sets the new player on the PlayerView
7. Playback continues from saved position with new track

## Testing Checklist

### Prerequisites
- Media file with multiple audio tracks (e.g., movie with English, Spanish, French audio)
- Media file with subtitle tracks (e.g., movie with English, Spanish subtitles)
- Jellyfin server with transcoding enabled
- Debug APK installed: `SegmentEditor-bc5326b-debug.apk`

### Test Cases

#### 1. Audio Track Selection - Basic
**Steps**:
1. Open a media item with multiple audio tracks
2. Play the video for ~10 seconds
3. Tap the Audio button
4. Verify all audio tracks are listed
5. Select a different audio track
6. Tap "Done"

**Expected Result**:
- ✅ Player briefly buffers/loads
- ✅ Playback continues from approximately the same position
- ✅ Audio is now in the selected language
- ✅ Video continues playing (does not pause indefinitely)

**Bug Behavior (if not fixed)**:
- ❌ Player stops/pauses and doesn't resume
- ❌ Audio doesn't change

#### 2. Subtitle Track Selection - Basic
**Steps**:
1. Open a media item with subtitle tracks
2. Play the video for ~10 seconds
3. Tap the Subtitles button
4. Select a subtitle track (not "Disabled")
5. Tap "Done"

**Expected Result**:
- ✅ Player briefly buffers/loads
- ✅ Playback continues from approximately the same position
- ✅ Subtitles appear on screen in the selected language
- ✅ Video continues playing

**Bug Behavior (if not fixed)**:
- ❌ Player stops and doesn't resume
- ❌ Subtitles don't appear

#### 3. Disable Subtitles
**Steps**:
1. With subtitles enabled, tap the Subtitles button
2. Select "Disabled"
3. Tap "Done"

**Expected Result**:
- ✅ Player reloads stream without subtitles
- ✅ Subtitles disappear
- ✅ Playback continues

#### 4. Multiple Track Changes
**Steps**:
1. Play video for 10 seconds
2. Change audio track → Verify change works
3. Wait 10 seconds
4. Change subtitle track → Verify change works
5. Wait 10 seconds
6. Change audio again → Verify change works
7. Disable subtitles → Verify change works

**Expected Result**:
- ✅ Each change successfully updates the stream
- ✅ Player continues playback after each change
- ✅ Position is preserved across changes

#### 5. Position Preservation
**Steps**:
1. Play video to 30 seconds
2. Note the exact timestamp
3. Change audio or subtitle track
4. Observe the timestamp after reload

**Expected Result**:
- ✅ Timestamp after reload is within 1-2 seconds of original position
- ✅ No jump back to beginning

#### 6. Playback State Preservation - Playing
**Steps**:
1. Play video (ensure it's actively playing, not paused)
2. While playing, change audio track
3. Observe player behavior

**Expected Result**:
- ✅ Brief pause for buffering
- ✅ Playback automatically resumes (playWhenReady = true)
- ✅ User doesn't need to press play again

#### 7. Playback State Preservation - Paused
**Steps**:
1. Play video for 10 seconds
2. Pause the video
3. Change subtitle track
4. Observe player behavior

**Expected Result**:
- ✅ Player remains paused after track change
- ✅ Position is preserved
- ✅ User can press play to resume

#### 8. Track Change Near Start
**Steps**:
1. Play video for just 0.5 seconds (very beginning)
2. Change audio track

**Expected Result**:
- ✅ Player changes track
- ✅ May jump to beginning (< 1 second threshold)
- ✅ Still works correctly

#### 9. Track Change Near End
**Steps**:
1. Seek to last 10 seconds of video
2. Change subtitle track

**Expected Result**:
- ✅ Player changes track
- ✅ Position preserved near end
- ✅ Video can play to completion

#### 10. Rapid Track Changes
**Steps**:
1. Play video for 10 seconds
2. Quickly change audio track 3 times in succession
3. Observe final state

**Expected Result**:
- ✅ Player eventually settles on the last selected track
- ✅ No crashes or frozen states
- ✅ Playback works correctly

### Performance Checks

#### Stream Reload Time
**Expected**: 1-3 seconds depending on network and transcoding
**Acceptable**: Brief loading indicator visible, then playback resumes

#### Memory Usage
**Check**: No memory leaks when changing tracks multiple times
**Method**: Monitor app memory in Android Studio Profiler or via `adb shell dumpsys meminfo`

#### Resource Cleanup
**Check**: Old ExoPlayer instances are properly released
**Method**: Look for "Creating new player with URL:" logs to confirm cleanup

## Log Messages to Watch For

### Success Indicators
```
D/VideoPlayerWithPreview: Creating new player with URL: ...AudioStreamIndex=1...
D/VideoPlayerWithPreview: Creating new player with URL: ...SubtitleStreamIndex=2...
```

### Player Lifecycle
```
D/PlayerViewModel: Audio track: index=1, title=English, default=true
D/PlayerViewModel: Subtitle track: index=2, title=Spanish, default=false
```

## Known Limitations
- Stream reload takes 1-3 seconds (network + transcoding time)
- Position may be off by ~1 second after reload
- Requires network connectivity for each track change
- Jellyfin server must support HLS transcoding with AudioStreamIndex/SubtitleStreamIndex parameters

## Debugging Failed Tests

### If audio/subtitle doesn't change:
1. Check logs for "Creating new player with URL:"
2. Verify URL contains correct AudioStreamIndex or SubtitleStreamIndex parameter
3. Check Jellyfin server transcoding logs
4. Verify media file actually has multiple tracks

### If player freezes after track change:
1. Check if AndroidView update block is being called
2. Verify new ExoPlayer instance is created
3. Check for exceptions in logcat
4. Verify PlayerView.player is set in update block (not factory)

### If position doesn't restore:
1. Check logs for position values: "restoring position: XXXXX"
2. Verify lastKnownPosition is being saved in DisposableEffect cleanup
3. Check if position > MIN_POSITION_TO_RESTORE_MS (1000ms)

## Test Environment
- **Build**: SegmentEditor-bc5326b-debug.apk
- **Android Version**: 8.0+ (API 26+)
- **Jellyfin Version**: 10.10+ with HLS transcoding
- **Test Media**: Movies/TV shows with multiple audio/subtitle tracks

## Regression Testing
After verifying the fix works, also verify these existing features still work:
- ✅ Video playback starts correctly
- ✅ Seeking works
- ✅ Playback speed control works
- ✅ Segment timeline displays correctly
- ✅ Segment creation/editing works
- ✅ Fullscreen mode works
- ✅ Scrub preview works

## Success Criteria
✅ All 10 test cases pass
✅ No crashes or ANRs
✅ No memory leaks detected
✅ Position preserved within 1-2 seconds
✅ Playback state (playing/paused) preserved
✅ Track changes reflected immediately after buffering
✅ No regressions in existing features

---

**Fix Committed**: February 3, 2026
**Files Changed**: VideoPlayerWithPreview.kt
**Lines Changed**: 4 lines (moved player assignment to update block)
