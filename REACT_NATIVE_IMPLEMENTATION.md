# React Native Implementation Guide

This document describes the complete React Native implementation for the Segment Editor Mobile app.

## Features Implemented

### ✅ Navigation (React Navigation)
- Stack navigation with 5 screens
- Native screen transitions
- Type-safe navigation with TypeScript

### ✅ Home/Connection Screen
- Server URL and API key configuration
- Secure credential storage (EncryptedStorage)
- Connection testing
- Navigation to media library

### ✅ Media Library Screen
- Browse movies and episodes from Jellyfin
- Pull-to-refresh functionality
- Item metadata display (name, type, duration)
- Navigation to segment list

### ✅ Video Player Screen
- Video playback using react-native-video
- Play/Pause controls
- Seek forward/backward (±5s, ±10s)
- Playback speed control (0.5x to 2.0x)
- Live timestamp display
- Copy timestamp to clipboard (formatted time)
- Copy ticks to clipboard (Jellyfin format)
- Progress bar visualization
- Navigate to segments

### ✅ Segment List Screen
- View all segments for a media item
- Color-coded segment types (Intro, Outro, Recap, Preview, Credits)
- Edit existing segments
- Delete segments with confirmation
- Create new segments
- Launch video player
- Pull-to-refresh

### ✅ Segment Editor Screen
- Create new segments or edit existing ones
- Segment type selection (with visual distinction)
- Start/End time inputs (MM:SS or HH:MM:SS format)
- Real-time ticks conversion display
- Timeline visualization with colored segment
- Validation (start < end, valid times)
- Type cannot be changed when editing (per Jellyfin API)

### ✅ Utilities
- Time formatting functions
- Ticks/seconds conversion
- Clipboard integration
- Type-safe media and navigation types

## Architecture

### Screen Flow
```
Home Screen
    ↓ (Browse Media Library)
Media Library Screen
    ↓ (Select Item)
Segment List Screen
    ├─→ (Play Video) → Video Player Screen
    └─→ (Create/Edit) → Segment Editor Screen
```

### Key Technologies
- **React Native 0.83** - Latest stable version
- **React Navigation 7** - Native stack navigator
- **TypeScript** - Full type safety
- **react-native-video 6** - Video playback
- **react-native-encrypted-storage** - Secure storage
- **axios** - HTTP client for Jellyfin API

## File Structure

```
src/
├── App.tsx                         # Main app with navigation
├── screens/                        # Screen components
│   ├── HomeScreen.tsx             # Connection settings
│   ├── MediaLibraryScreen.tsx     # Browse media
│   ├── VideoPlayerScreen.tsx      # Video playback
│   ├── SegmentListScreen.tsx      # List segments
│   ├── SegmentEditorScreen.tsx    # Create/edit segments
│   └── index.ts                   # Screen exports
├── services/                       # Business logic
│   └── JellyfinApiService.ts      # Jellyfin API client
├── types/                          # TypeScript types
│   ├── navigation.ts              # Navigation params
│   └── media.ts                   # Media item types
├── utils/                          # Utility functions
│   └── timeUtils.ts               # Time/clipboard utils
└── styles/                         # Style constants
    └── Colors.ts                  # Color palette
```

## API Integration

### JellyfinApiService Methods
- `initialize()` - Load saved credentials
- `saveCredentials(url, key)` - Save credentials securely
- `testConnection()` - Test server connectivity
- `getMediaItems()` - Fetch media library items
- `getMediaItem(id)` - Get single item details
- `getSegments(itemId)` - Get all segments for item
- `createSegment(segment)` - Create new segment
- `updateSegment(itemId, type, segment)` - Update segment
- `deleteSegment(itemId, type)` - Delete segment
- `getVideoUrl(itemId)` - Get video stream URL

## UI/UX Features

### Dark Mode Support
All screens adapt to system dark mode preference with:
- Appropriate colors for light/dark themes
- High contrast text
- Theme-aware status bar

### Accessibility
- Semantic component usage
- Screen reader support
- Keyboard-friendly navigation
- Loading states with indicators
- Error feedback with alerts

### User Feedback
- Loading spinners for async operations
- Success/error alerts
- Confirmation dialogs for destructive actions
- Copy-to-clipboard notifications
- Pull-to-refresh indicators

## Testing

### Manual Testing Steps

1. **Connection Setup**
   ```
   - Enter Jellyfin server URL
   - Enter API key
   - Save credentials
   - Test connection (should show success)
   - Tap "Browse Media Library"
   ```

2. **Media Browsing**
   ```
   - See list of movies/episodes
   - Pull down to refresh
   - Tap an item to view segments
   ```

3. **Segment Management**
   ```
   - View existing segments (or empty state)
   - Tap "Create Segment" to add new
   - Select type (Intro, Outro, etc.)
   - Enter start/end times
   - See timeline visualization
   - Save segment
   - Edit segment to modify times
   - Delete segment with confirmation
   ```

4. **Video Player**
   ```
   - Tap "Play Video" from segment list
   - Video should load and play
   - Test play/pause
   - Test seek forward/back
   - Test speed control
   - Tap "Copy Time" at any moment
   - Tap "Copy Ticks" for Jellyfin ticks
   - Check timestamp updates in real-time
   ```

## Known Limitations

1. **Video Player**
   - Direct play only (no HLS fallback yet)
   - No subtitle support (can be added with react-native-video)
   - No scrubbing via seek bar (buttons only)

2. **Segment Editor**
   - Manual time entry only (no video player integration for picking times)
   - No visual timeline with existing segments overlay

3. **Media Library**
   - Flat list (no folders/hierarchy)
   - Limited to Movies and Episodes
   - No search functionality
   - No filtering

4. **General**
   - Android only (iOS not tested/configured)
   - English only (no i18n)
   - No offline support
   - No image caching

## Future Enhancements

### High Priority
1. Integrate video player with segment editor (jump to segment times)
2. Add scrubber to video player for precise seeking
3. Add search to media library
4. Add image thumbnails for media items
5. Add segment visualization on video timeline

### Medium Priority
6. iOS support
7. HLS video streaming support
8. Subtitle/caption support
9. Keyboard shortcuts (for Android TV)
10. Batch segment operations

### Low Priority
11. Internationalization (i18n)
12. Offline segment editing
13. Export/import segments
14. Advanced filtering and sorting
15. Dark/light theme toggle (currently auto)

## Performance Considerations

- Uses React Navigation's native stack for best performance
- FlatList for efficient rendering of large media lists
- Memoized callbacks to prevent unnecessary re-renders
- Encrypted storage for security without sacrificing speed
- Optimistic UI updates where possible

## Comparison with Desktop/Web Version

| Feature | Web (Original) | Mobile (This) | Notes |
|---------|---------------|---------------|-------|
| Video Player | ✅ Full | ✅ Core | Mobile has basic controls, web has advanced |
| Segment CRUD | ✅ | ✅ | Full parity |
| Timeline Viz | ✅ Interactive | ✅ Static | Mobile shows simple preview, web has draggable sliders |
| Media Browse | ✅ Advanced | ✅ Basic | Mobile has flat list, web has more features |
| Keyboard Shortcuts | ✅ | ❌ | Not applicable on mobile |
| Copy Timestamp | ✅ | ✅ | Full parity |
| Dark Mode | ✅ | ✅ | Full parity |
| Offline Mode | ❌ | ❌ | Neither support offline |

## Troubleshooting

### Build Issues
```bash
# Clear Metro cache
npm start -- --reset-cache

# Clean Android build
cd android && ./gradlew clean && cd ..

# Reinstall dependencies
rm -rf node_modules && npm install
```

### Runtime Issues
- **Video not playing**: Check that the Jellyfin server URL is correct and accessible
- **Credentials not saving**: Check Android permissions
- **Navigation not working**: Ensure all screens are registered in App.tsx

## Contributing

When adding new features:
1. Follow existing TypeScript patterns
2. Use React Navigation for new screens
3. Keep dark mode support
4. Add loading/error states
5. Test on physical Android device
6. Update this documentation

## License

See main repository LICENSE file.
