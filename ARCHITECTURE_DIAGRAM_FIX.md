# Audio/Subtitle Track Selection - Architecture Diagram

## Before Fix (Broken Behavior)

```
User Action: Select Different Audio Track
         ↓
PlayerViewModel.selectAudioTrack(newIndex)
         ↓
UI State Updated: selectedAudioTrack = newIndex
         ↓
streamUrl recomputed: master.m3u8?AudioStreamIndex=newIndex
         ↓
VideoPlayerWithPreview recomposes
         ↓
remember(streamUrl) detects change
         ↓
[DisposableEffect cleanup] Old player released
         ↓
[remember block executes] New ExoPlayer created with new URL
         ↓
❌ BUG: PlayerView still references OLD (released) player
         ↓
Result: Black screen / frozen player (old player is released)
```

## After Fix (Working Behavior)

```
User Action: Select Different Audio Track
         ↓
PlayerViewModel.selectAudioTrack(newIndex)
         ↓
UI State Updated: selectedAudioTrack = newIndex
         ↓
streamUrl recomputed: master.m3u8?AudioStreamIndex=newIndex
         ↓
VideoPlayerWithPreview recomposes
         ↓
remember(streamUrl) detects change
         ↓
[DisposableEffect cleanup] 
  - Save current position
  - Release old player
         ↓
[remember block executes]
  - Create new ExoPlayer with new URL
  - Restore saved position
         ↓
✅ FIX: AndroidView.update runs
  - playerView.player = exoPlayer (new instance)
         ↓
[DisposableEffect setup]
  - Add listeners to new player
  - Call onPlayerReady
         ↓
Result: PlayerView displays new player with selected audio track
```

## Code Comparison

### Before (Broken)
```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            player = exoPlayer  // ❌ Set once, never updated
            // ... setup ...
        }
    }
)
```

**Problem**: `player = exoPlayer` runs only once in factory. When `exoPlayer` changes to a new instance, PlayerView keeps showing the old released player.

### After (Fixed)
```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            // Factory: one-time setup only
            this.useController = useController
            // ... setup ...
        }
    },
    update = { playerView ->
        // ✅ Update runs on every recomposition
        playerView.player = exoPlayer
    }
)
```

**Solution**: `player = exoPlayer` in update block runs whenever `exoPlayer` changes. PlayerView always shows the current player instance.

## Jetpack Compose Lifecycle

### remember(key) Behavior
```
streamUrl = "...AudioStreamIndex=0"  (initial)
         ↓
exoPlayer = remember(streamUrl) { create player A }
         ↓
AndroidView.factory { create PlayerView }
AndroidView.update { playerView.player = player A }  ✅
         ↓
         
User selects different track
         ↓
streamUrl = "...AudioStreamIndex=1"  (changed)
         ↓
remember(streamUrl) detects change:
  - DisposableEffect cleanup runs (release player A)
  - New instance created: player B
         ↓
AndroidView recomposes:
  - factory: NOT called (view already exists)
  - update: CALLED with player B  ✅
         ↓
PlayerView now shows player B with new audio track
```

## Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     PlayerViewModel                          │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ audioTracks: List<TrackInfo>                           │ │
│  │ selectedAudioTrack: Int?                               │ │
│  │                                                          │ │
│  │ fun selectAudioTrack(index: Int?) {                    │ │
│  │   _uiState.update { it.copy(selectedAudioTrack = index)} │
│  │ }                                                        │ │
│  │                                                          │ │
│  │ fun getStreamUrl(audioStreamIndex: Int?): String {     │ │
│  │   return ".../master.m3u8?AudioStreamIndex=$index"     │ │
│  │ }                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                      PlayerScreen                            │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ val streamUrl = remember(                              │ │
│  │   uiState.selectedAudioTrack,                         │ │
│  │   uiState.selectedSubtitleTrack                       │ │
│  │ ) {                                                     │ │
│  │   viewModel.getStreamUrl(                             │ │
│  │     audioStreamIndex = uiState.selectedAudioTrack     │ │
│  │   )                                                     │ │
│  │ }                                                       │ │
│  │                                                          │ │
│  │ VideoPlayerWithPreview(                                │ │
│  │   streamUrl = streamUrl  // Passed as prop            │ │
│  │ )                                                       │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                 VideoPlayerWithPreview                       │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ val exoPlayer = remember(streamUrl) {                  │ │
│  │   ExoPlayer.Builder(context)                           │ │
│  │     .build().apply {                                   │ │
│  │       setMediaItem(MediaItem.fromUri(streamUrl))       │ │
│  │       prepare()                                        │ │
│  │     }                                                   │ │
│  │ }                                                       │ │
│  │                                                          │ │
│  │ AndroidView(                                           │ │
│  │   factory = { PlayerView(it) },                       │ │
│  │   update = { playerView ->                            │ │
│  │     playerView.player = exoPlayer  // ✅ THE FIX      │ │
│  │   }                                                     │ │
│  │ )                                                       │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Key Insight

**AndroidView** is Jetpack Compose's bridge to Android View system:
- `factory` = Constructor (runs once)
- `update` = Setter for mutable properties (runs on recomposition)

For properties that change over time (like which player to display), always use `update`, not `factory`.

## Testing Flow

```
1. Install APK
2. Open media with multiple audio tracks
3. Play for 10 seconds
4. Tap Audio button
5. Select different language
6. Tap Done
         ↓
Expected Behavior:
  ✅ Player briefly buffers (1-2 sec)
  ✅ Playback continues from ~same position
  ✅ Audio is in new language
  ✅ Player doesn't freeze

Bug Behavior (if not fixed):
  ❌ Player stops
  ❌ Black screen or frozen frame
  ❌ Audio doesn't change
  ❌ User must restart playback manually
```

## Impact Analysis

**Minimal Change**: Only 7 lines modified in 1 file
- Removed 1 line from factory
- Added 4 lines for update block
- Added 2 lines of explanatory comments

**Zero Breaking Changes**: 
- Existing functionality unchanged
- No API changes
- No behavior changes for other features

**High Impact**:
- Fixes critical bug preventing track selection
- Enables multi-language support
- Enables accessibility (subtitle selection)
- No workarounds needed

---

**This fix demonstrates the importance of understanding Jetpack Compose lifecycle and the difference between one-time initialization (factory) and reactive updates (update).**
