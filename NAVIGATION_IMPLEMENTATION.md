# Navigation Implementation Summary

## Overview
This document summarizes the implementation of the corrected navigation flow and pagination improvements.

## Changes Made

### 1. Segment Loading (Already Implemented ✅)
**No changes required** - The app already loads existing media segments from the Jellyfin server when loading an episode into the editor:

- **PlayerViewModel.loadMediaItem()** (line 34-87): Loads media item and calls `loadSegments()`
- **PlayerViewModel.loadSegments()** (line 89-107): Fetches segments from Jellyfin server
- **SegmentRepository.getSegmentsResult()**: Wraps API call and handles errors
- **JellyfinApi.getSegments()**: Calls `GET /MediaSegments/{itemId}` endpoint
- **Segments are stored** in PlayerUiState and displayed in the UI
- **Segments are refreshed** after edits via `refreshSegments()`

This matches the pattern from the segment-editor template repository.

### 2. Navigation Flow: Library → Show → Season → Episode

#### Created Files:
1. **LibraryScreen.kt**: New screen to display available libraries
   - Shows libraries in a grid layout
   - Displays library name and collection type
   - Handles navigation to library-specific content

2. **LibraryViewModel.kt**: ViewModel for library screen
   - Fetches libraries from JellyfinRepository
   - Manages loading/error states
   - Provides refresh functionality

#### Modified Files:

1. **Screen.kt**: Added `Library` screen route
   ```kotlin
   object Library : Screen("library")
   ```

2. **AppNavigation.kt**: Updated navigation flow
   - `Screen.Main.route` now shows LibraryScreen (entry point)
   - Added `Screen.Library.route` for library selection
   - Modified `Screen.Home.route` to accept `libraryId` parameter: `home/{libraryId}`
   - Library → Home (shows) → Series (seasons/episodes) → Player

3. **HomeScreen.kt**: Modified to show content for a specific library
   - Added `libraryId` parameter (required)
   - Added `onNavigateBack` callback for back navigation
   - Removed collection filter UI (no longer needed)
   - Added "Show All Items" pagination option
   - Updated title to "TV Shows"
   - Added back button in TopAppBar

4. **HomeViewModel.kt**: Updated to work with specific library
   - Added `setLibraryId()` method to set current library
   - Added `showAllItems` StateFlow for pagination control
   - Added `toggleShowAllItems()` method
   - Removed collection filtering logic
   - Updated `loadMediaItems()` to:
     - Filter by specific libraryId
     - Support showing all items (no pagination)
     - Only show Series type items (TV shows)
   - Updated `HomeUiState.Success` to include `totalItems` count

### 3. Pagination Improvements

Added option to display all items without pagination:

- **Toggle Button**: "Show all items" / "Show with pagination"
- **All Items Mode**: 
  - Loads all items in one request (no limit)
  - Displays total count: "Showing all X items"
  - Single page view
- **Paginated Mode**:
  - Default behavior (20 items per page)
  - Previous/Next page navigation
  - Shows page numbers
- **Automatic Reset**: Pagination resets when switching modes or changing library

## Navigation Flow

### Before:
```
HomeScreen (all media items with optional filtering)
  → Series/Album/Artist/Player screens
```

### After:
```
LibraryScreen (select library)
  → HomeScreen (TV shows in library)
    → SeriesScreen (seasons and episodes)
      → PlayerScreen (video player with segment editor)
```

## Technical Details

### Library Selection
- Calls `JellyfinRepository.getLibraries()` which fetches user libraries
- Returns `List<MediaItem>` with library metadata
- Each library has: `id`, `name`, `collectionType`

### Home Screen (Shows in Library)
- Filters items by `parentId` (libraryId)
- Only shows `includeItemTypes = ["Series"]` (TV shows)
- Supports search within library
- Supports pagination or "show all" mode

### Segment Loading
- Segments are automatically loaded in PlayerViewModel when an episode is opened
- No manual trigger needed - happens in `loadMediaItem()` lifecycle
- Segments are stored in UI state: `uiState.segments`
- Refreshed after segment edits via `refreshSegments()`

## Testing Recommendations

1. **Navigation Flow**:
   - Start app → should show LibraryScreen
   - Select a library → should show TV shows in that library
   - Select a TV show → should show SeriesScreen with seasons/episodes
   - Select an episode → should open PlayerScreen

2. **Pagination**:
   - Verify page navigation works (Previous/Next)
   - Click "Show all items" → should load all items
   - Verify item count displays correctly
   - Switch back to pagination → should work correctly

3. **Segment Loading**:
   - Open an episode in PlayerScreen
   - Verify existing segments load automatically
   - Segments should display in the UI
   - Edit a segment → verify segments refresh

## API Endpoints Used

- `GET /Users/{userId}/Views` - Get libraries
- `GET /Items?parentId={libraryId}&includeItemTypes=Series` - Get TV shows in library
- `GET /MediaSegments/{itemId}` - Get segments for episode (already implemented)

## Future Enhancements

1. Support other library types (Movies, Music, etc.)
2. Add library icon/image display
3. Implement infinite scroll as alternative to "show all"
4. Cache library list for offline access
5. Add library type filtering (Show TV libraries only)
