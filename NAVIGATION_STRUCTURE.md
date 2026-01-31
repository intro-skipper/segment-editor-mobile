# Navigation Structure Documentation

## Overview

This document describes the media library navigation structure and segment loading behavior in the Jellyfin Segment Editor mobile app.

## Navigation Hierarchy

The app follows a structured navigation pattern: **Library → Show → Season → Episodes → Player**

```
AppNavigation (NavHost)
├── Main Flow
│   ├── HomeScreen (Library Selection)
│   │   ├── Filter by collection
│   │   ├── Search by name
│   │   └── Paginated media grid
│   │
│   ├── Type-based Navigation
│   │   ├── Series → SeriesScreen
│   │   ├── MusicAlbum → AlbumScreen
│   │   ├── MusicArtist → ArtistScreen
│   │   └── Movies/Episodes → PlayerScreen
│   │
│   └── SeriesScreen (Show Details)
│       ├── Series header with metadata
│       ├── Season tabs (for multi-season series)
│       │   ├── Tab for each season
│       │   └── Episode list for selected season
│       └── Direct episode list (for single-season series)
│
└── PlayerScreen (Episode/Movie Playback)
    ├── Video player with HLS streaming
    ├── Segment timeline visualization
    ├── Segment editor (create/edit/delete)
    └── Track selection (audio/subtitle)
```

## Navigation Routes

### Defined Routes (Screen.kt)

- `main` / `home` - Media library browser (HomeScreen)
- `series/{seriesId}` - Series details with seasons (SeriesScreen)
- `player/{itemId}` - Video player with segment editor (PlayerScreen)
- `album/{albumId}` - Music album details (AlbumScreen)
- `artist/{artistId}` - Music artist details (ArtistScreen)
- `settings` - Application settings (SettingsScreen)

### Smart Navigation (HomeScreen)

The HomeScreen implements type-aware routing:

```kotlin
when (item.type) {
    "Series" → navigate("series/${item.id}")
    "MusicAlbum" → navigate("album/${item.id}")
    "MusicArtist" → navigate("artist/${item.id}")
    else → navigate("player/${item.id}") // Movies, Episodes, Audio
}
```

## Series Navigation Flow

### 1. Library Selection (HomeScreen)

Users start by:
1. Selecting a collection (e.g., "TV Shows", "Movies")
2. Optionally searching/filtering items
3. Browsing paginated media grid

### 2. Series Details (SeriesScreen)

When clicking a Series, the app navigates to SeriesScreen which displays:

**For Multi-Season Series:**
- Series header with poster, backdrop, metadata
- ScrollableTabRow with tabs for each season
- Episode list for the selected season

**For Single-Season Series:**
- Series header with poster, backdrop, metadata
- Simple season header
- Episode list directly below

**Features:**
- Pull-to-refresh support
- Season sorting (specials at end)
- Episode cards with thumbnails
- Segment count indicators

### 3. Episode Selection

Clicking an episode navigates to: `player/{episodeId}`

## Segment Loading

### Automatic Loading

Segments are **automatically loaded** when the PlayerScreen opens:

```kotlin
// PlayerViewModel.loadMediaItem()
fun loadMediaItem(itemId: String) {
    // 1. Load media item with MediaSources, MediaStreams
    // 2. Extract audio/subtitle tracks
    // 3. Load segments via loadSegments(itemId)
}
```

### Load Sequence

1. **PlayerScreen** calls `viewModel.loadMediaItem(itemId)` on composition
2. **PlayerViewModel** fetches media item details from Jellyfin API
3. **PlayerViewModel** automatically calls `loadSegments(itemId)`
4. **SegmentRepository** queries: `GET /MediaSegments/{itemId}`
5. Segments are stored in UI state and displayed

### Non-Blocking Behavior

Segment loading is designed to be **non-critical**:
- Failures are logged as warnings, not errors
- Player remains functional even if segments fail to load
- Empty segment list is valid (no segments created yet)

### Segment API Endpoints

```kotlin
GET    /MediaSegments/{itemId}           // Get all segments
POST   /MediaSegments                    // Create new segment
PUT    /MediaSegments/{itemId}/{type}    // Update segment
DELETE /MediaSegments/{itemId}/{type}    // Delete segment
```

## Comparison to Web Reference

The mobile app follows the same pattern as the web reference (`segment-editor`):

| Aspect | Web (segment-editor) | Mobile (segment-editor-mobile) |
|--------|---------------------|-------------------------------|
| Library | FilterView (index) | HomeScreen |
| Series | SeriesView with tabs | SeriesScreen with ScrollableTabRow |
| Episodes | Grouped by season | Grouped by season with tabs |
| Player | PlayerEditor | PlayerScreen with editor dialog |
| Segments | Auto-load with `fetchSegments` param | Auto-load on player mount |

**Key Difference:**
- Web uses URL search param `?fetchSegments=true` for explicit control
- Mobile always loads segments (simpler mobile UX pattern)

## UI Components

### SeriesScreen Components

- **MediaHeader**: Series poster, backdrop, title, metadata
- **ScrollableTabRow**: Season navigation (multi-season only)
- **Tab**: Individual season selector
- **LazyColumn**: Scrollable episode list
- **EpisodeCard**: Episode thumbnail, title, number, duration, segment count

### PlayerScreen Components

- **VideoView**: HLS video player with ExoPlayer
- **SegmentTimeline**: Visual timeline of segments
- **SegmentCard**: Individual segment with edit/delete actions
- **SegmentEditorDialog**: Create/edit segment modal
- **TrackSelector**: Audio/subtitle track picker

## State Management

### SeriesViewModel

```kotlin
sealed class SeriesUiState {
    object Loading
    data class Success(
        series: MediaItem,
        episodesBySeason: Map<Int, List<EpisodeWithSegments>>
    )
    data class Error(message: String)
}
```

### PlayerViewModel

```kotlin
data class PlayerUiState(
    isLoading: Boolean,
    mediaItem: MediaItem?,
    segments: List<Segment>,
    duration: Long,
    currentPosition: Long,
    isPlaying: Boolean,
    // ... tracks, speed, etc.
)
```

## Testing the Flow

To verify the complete navigation:

1. **Launch app** → HomeScreen displays
2. **Select collection** (e.g., "TV Shows")
3. **Click a Series** → SeriesScreen loads with seasons
4. **Switch season tabs** → Episode list updates
5. **Click an episode** → PlayerScreen loads
6. **Wait for load** → Segments auto-load and display
7. **Open editor** → Create/edit segments

## Troubleshooting

### Segments not loading?

Check:
1. Jellyfin server has Intro Skipper plugin installed
2. API key has proper permissions
3. Network logs show successful `GET /MediaSegments/{itemId}` call
4. Server returns valid JSON (empty array `[]` is valid)

### Episodes not showing?

Check:
1. Series has episodes in Jellyfin library
2. Metadata refresh completed on server
3. User has access to the series/episodes
4. SeriesViewModel logs show successful episode fetch

## Future Enhancements

Potential improvements:
- Add segment count to episode cards
- Preload segments for upcoming episodes
- Batch segment operations
- Segment templates for quick creation
- Episode skip/next navigation in player
