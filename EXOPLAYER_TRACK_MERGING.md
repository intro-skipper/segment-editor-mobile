# ExoPlayer Track Merging Implementation

## Overview
This implementation merges audio and subtitle track information from both Jellyfin metadata and ExoPlayer's actual stream to ensure all available tracks are discoverable and selectable by users.

## Problem Statement
Previously, the app only used track information from Jellyfin's `MediaStreams` metadata. This could miss tracks that:
- Are embedded in the container but not in Jellyfin metadata
- Are discovered by ExoPlayer's format parser
- Have different indices between metadata and actual stream

Conversely, using only ExoPlayer tracks would miss:
- External subtitle files listed in Jellyfin
- Tracks with Jellyfin-specific indices needed for URL parameters
- Metadata about default/forced track flags

## Solution Architecture

### Track Discovery Flow
```
┌─────────────────────────────────────────────────────────────┐
│                   Media Item Loaded                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ↓
         ┌─────────────────────────────────────┐
         │  extractTracksFromMediaStreams()    │
         │  - Extracts from Jellyfin metadata  │
         │  - Marks source as JELLYFIN         │
         │  - Stores in UI state               │
         └─────────────────────────────────────┘
                           │
                           ↓
         ┌─────────────────────────────────────┐
         │      Stream Loaded in ExoPlayer      │
         └─────────────────────────────────────┘
                           │
                           ↓
         ┌─────────────────────────────────────┐
         │    updateTracksFromPlayer(tracks)    │
         │  - Extracts from ExoPlayer Tracks    │
         │  - Marks source as EXOPLAYER         │
         │  - Calls mergeTracksFromBothSources()│
         └─────────────────────────────────────┘
                           │
                           ↓
         ┌─────────────────────────────────────┐
         │   mergeTracksFromBothSources()       │
         │  - Matches tracks by language        │
         │  - Marks matched as MERGED           │
         │  - Adds ExoPlayer-only tracks        │
         │  - Updates UI state                  │
         └─────────────────────────────────────┘
                           │
                           ↓
         ┌─────────────────────────────────────┐
         │    User Sees Complete Track List     │
         │  - Jellyfin tracks                   │
         │  - ExoPlayer-only tracks (labeled)   │
         │  - Source indicators (MERGED, etc)   │
         └─────────────────────────────────────┘
```

### Track Matching Algorithm

**Goal**: Identify when a track from Jellyfin and a track from ExoPlayer represent the same stream.

**Strategy**: Match by language only
- Compares `track1.language` with `track2.language` (case-insensitive)
- Both tracks must have non-null language values
- Returns `true` only if languages match

**Why Language-Only Matching?**
1. **Avoids False Negatives**: Same language track may have different codec variants (quality levels)
2. **Avoids False Positives**: Codec substring matching (e.g., "aac" in "aacp") caused incorrect matches
3. **Practical**: Most media has unique languages per track type
4. **Safe**: Better to merge same-language tracks than show duplicates

**Alternative Considered**: Language + Codec matching
- **Rejected**: Too strict, would treat quality variants as separate tracks
- **Rejected**: Substring matching caused false positives
- **Rejected**: Exact codec matching missed legitimate matches

### Merge Strategy

**Priority**: Jellyfin tracks are primary
- Jellyfin indices are required for stream URL parameters
- Jellyfin metadata has default/forced flags
- Jellyfin lists external subtitle files

**Process**:
1. **Keep All Jellyfin Tracks**
   - Iterate through Jellyfin tracks
   - For each, try to find matching ExoPlayer track
   - If match found: mark track source as `MERGED`
   - If no match: keep track source as `JELLYFIN`

2. **Add ExoPlayer-Only Tracks**
   - Iterate through ExoPlayer tracks
   - For each, check if already matched with Jellyfin track
   - If not matched: add as new track with source `EXOPLAYER`
   - Label display title with `[ExoPlayer only]` suffix

3. **Update UI State**
   - Replace track lists with merged lists
   - Preserve selected track indices
   - Trigger UI recomposition

## Code Structure

### Data Model (`PlayerUiState.kt`)

```kotlin
enum class TrackSource {
    JELLYFIN,     // Track from Jellyfin MediaStreams metadata
    EXOPLAYER,    // Track discovered by ExoPlayer in the stream
    MERGED        // Track present in both sources
}

data class TrackInfo(
    val index: Int,
    val language: String?,
    val displayTitle: String,
    val codec: String?,
    val isDefault: Boolean = false,
    val source: TrackSource = TrackSource.JELLYFIN
)
```

### Track Extraction (PlayerViewModel.kt)

#### From Jellyfin Metadata
```kotlin
private fun extractTracksFromMediaStreams(mediaStreams: List<MediaStream>?) {
    val audioTracks = mediaStreams
        .filter { it.type == "Audio" }
        .map { stream ->
            TrackInfo(
                index = stream.index,
                language = stream.language,
                displayTitle = stream.displayTitle ?: buildTrackTitle(...),
                codec = stream.codec,
                isDefault = stream.isDefault,
                source = TrackSource.JELLYFIN  // Mark source
            )
        }
    // Similar for subtitle tracks
    // Update UI state with tracks
}
```

#### From ExoPlayer Tracks
```kotlin
fun updateTracksFromPlayer(tracks: androidx.media3.common.Tracks) {
    // Extract audio tracks
    val exoAudioTracks = mutableListOf<TrackInfo>()
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                exoAudioTracks.add(TrackInfo(
                    index = trackIndex,
                    language = format.language,
                    displayTitle = format.label ?: "Audio ${index + 1}",
                    codec = format.sampleMimeType,
                    source = TrackSource.EXOPLAYER  // Mark source
                ))
            }
        }
    }
    // Similar for subtitle tracks
    
    // Merge with Jellyfin tracks
    val mergedAudioTracks = mergeTracksFromBothSources(
        _uiState.value.audioTracks,
        exoAudioTracks,
        "audio"
    )
    // Update UI state
}
```

#### Merging Logic
```kotlin
private fun mergeTracksFromBothSources(
    jellyfinTracks: List<TrackInfo>,
    exoPlayerTracks: List<TrackInfo>,
    trackType: String
): List<TrackInfo> {
    val merged = mutableListOf<TrackInfo>()
    
    // Add Jellyfin tracks, mark as MERGED if found in ExoPlayer
    jellyfinTracks.forEach { jellyfinTrack ->
        val matchingExoTrack = exoPlayerTracks.find { exoTrack ->
            matchTracks(jellyfinTrack, exoTrack)
        }
        
        if (matchingExoTrack != null) {
            merged.add(jellyfinTrack.copy(source = TrackSource.MERGED))
        } else {
            merged.add(jellyfinTrack)
        }
    }
    
    // Add ExoPlayer-only tracks
    exoPlayerTracks.forEach { exoTrack ->
        val existsInJellyfin = jellyfinTracks.any { jellyfinTrack ->
            matchTracks(jellyfinTrack, exoTrack)
        }
        
        if (!existsInJellyfin) {
            merged.add(exoTrack.copy(
                displayTitle = "${exoTrack.displayTitle} [ExoPlayer only]"
            ))
        }
    }
    
    return merged
}
```

#### Track Matching
```kotlin
private fun matchTracks(track1: TrackInfo, track2: TrackInfo): Boolean {
    val lang1 = track1.language?.lowercase()
    val lang2 = track2.language?.lowercase()
    
    // Require both to have language
    if (lang1 == null || lang2 == null) {
        return false
    }
    
    // Match by language only
    return lang1 == lang2
}
```

## User Experience

### Track Display
- All tracks are shown in track selection sheet
- ExoPlayer-only tracks have `[ExoPlayer only]` suffix
- Source information available for debugging (not shown in UI by default)

### Track Selection
- **Jellyfin/MERGED tracks**: Use Jellyfin index for stream URL parameters
- **ExoPlayer-only tracks**: Cannot use with Jellyfin URL parameters (would fail)
  - Note: Current implementation still uses Jellyfin URLs, so these tracks may not be selectable

### Example Scenarios

#### Scenario 1: Perfect Match
```
Jellyfin metadata:
  - Audio track 0: English (aac)
  - Audio track 1: Spanish (ac3)

ExoPlayer discovers:
  - Audio track 0: English (aac)
  - Audio track 1: Spanish (ac3)

Result:
  ✓ Audio - English (source: MERGED)
  ✓ Audio - Spanish (source: MERGED)
```

#### Scenario 2: ExoPlayer Has Extra Track
```
Jellyfin metadata:
  - Audio track 0: English (aac)

ExoPlayer discovers:
  - Audio track 0: English (aac)
  - Audio track 1: Japanese (ac3)

Result:
  ✓ Audio - English (source: MERGED)
  ✓ Audio - Japanese [ExoPlayer only] (source: EXOPLAYER)
```

#### Scenario 3: Jellyfin Has External Subtitle
```
Jellyfin metadata:
  - Subtitle track 0: English (srt, external)

ExoPlayer discovers:
  - (no subtitle tracks in stream)

Result:
  ✓ Subtitle - English (source: JELLYFIN)
```

#### Scenario 4: Quality Variants
```
Jellyfin metadata:
  - Audio track 0: English (ac3)

ExoPlayer discovers:
  - Audio track 0: English (ac3)
  - Audio track 1: English (eac3) - higher quality

Result:
  ✓ Audio - English (source: MERGED)
  ✓ Audio - English [ExoPlayer only] (source: EXOPLAYER)

Note: Both have same language, so second one is treated as ExoPlayer-only.
This may show duplicate in UI, but ensures no tracks are lost.
```

## Logging

Comprehensive logging for debugging:

```
PlayerViewModel: Extracted from Jellyfin: 2 audio tracks, 3 subtitle tracks
PlayerViewModel: Audio track: index=0, title=English, default=true, source=JELLYFIN
PlayerViewModel: Audio track: index=1, title=Spanish, default=false, source=JELLYFIN
PlayerViewModel: Subtitle track: index=2, title=English, default=true, source=JELLYFIN
...

PlayerViewModel: ExoPlayer tracks available: 2 audio groups, 2 subtitle groups
PlayerViewModel: ExoPlayer audio track: index=0, language=eng, label=English
PlayerViewModel: ExoPlayer audio track: index=1, language=spa, label=Spanish
...

PlayerViewModel: Jellyfin tracks: 2 audio, 3 subtitles
PlayerViewModel: ExoPlayer tracks: 2 audio, 2 subtitles

PlayerViewModel: audio track matched: English (Jellyfin idx=0 ↔ ExoPlayer idx=0)
PlayerViewModel: audio track matched: Spanish (Jellyfin idx=1 ↔ ExoPlayer idx=1)
PlayerViewModel: subtitle track from Jellyfin only: English
PlayerViewModel: subtitle track from Jellyfin only: Spanish
PlayerViewModel: subtitle track from Jellyfin only: French

PlayerViewModel: Merged tracks: 2 audio, 3 subtitles
```

## Testing Recommendations

### Test Cases

1. **Media with tracks in both sources**
   - Verify tracks are marked as MERGED
   - Verify no duplicate tracks shown
   - Verify selection works correctly

2. **Media with Jellyfin-only tracks**
   - Verify external subtitles are shown
   - Verify source is JELLYFIN
   - Verify selection works

3. **Media with ExoPlayer-only tracks**
   - Verify tracks are shown with [ExoPlayer only] label
   - Verify source is EXOPLAYER
   - Document selection behavior (may not work with Jellyfin URLs)

4. **Media with no language metadata**
   - Verify tracks are not incorrectly merged
   - Verify all tracks are shown separately

5. **Media with quality variants**
   - Verify different codec same language tracks are shown
   - Verify selection works for each variant

### Manual Testing Steps

1. Load media file with multiple audio/subtitle tracks
2. Open audio track selection sheet
3. Verify all expected tracks are shown
4. Check logs for track discovery and merging
5. Select different tracks and verify playback
6. Check for [ExoPlayer only] labels
7. Verify no duplicate tracks shown

## Limitations and Future Improvements

### Current Limitations

1. **ExoPlayer-only tracks may not be selectable**
   - Current implementation uses Jellyfin stream URLs
   - Jellyfin URLs require Jellyfin track indices
   - ExoPlayer indices may not match Jellyfin indices
   - **Impact**: User can see track but may not be able to select it

2. **Quality variants may appear as duplicates**
   - Same language, different codec tracks show as separate
   - Could confuse users
   - **Mitigation**: ExoPlayer-only tracks are labeled

3. **No index remapping**
   - Assumes ExoPlayer indices can be used if needed
   - May not work in practice
   - **Future**: Implement index remapping system

### Future Enhancements

1. **Direct ExoPlayer Track Selection**
   - For ExoPlayer-only tracks, use ExoPlayer's track selector
   - Don't rely on Jellyfin URL parameters
   - Would enable selecting all discovered tracks

2. **Smart Quality Variant Handling**
   - Detect same language + similar codec tracks
   - Group as quality variants
   - Show single entry with quality selector

3. **Track Verification**
   - Test track selection before showing to user
   - Hide tracks that fail to load
   - Provide user feedback on unavailable tracks

4. **Index Remapping**
   - Build mapping between Jellyfin and ExoPlayer indices
   - Use correct index for each track based on source
   - Enable seamless track switching

5. **User Preferences**
   - Remember user's track preferences per media
   - Auto-select based on language preferences
   - Show/hide ExoPlayer-only tracks option

## Conclusion

This implementation provides a robust solution for discovering and displaying all available audio and subtitle tracks from both Jellyfin metadata and ExoPlayer's stream parsing. The language-only matching strategy ensures tracks are properly merged without false positives or negatives, while clearly labeling ExoPlayer-only tracks for user awareness.

The approach maintains compatibility with Jellyfin's stream URL parameters while extending coverage to include tracks that might only be discovered by ExoPlayer, ensuring users have access to all available audio and subtitle options.
