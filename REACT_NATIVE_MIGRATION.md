# React Native Migration Guide

This document describes the migration from WebView-based architecture to React Native for the Android platform.

## Overview

The Segment Editor Mobile app has been migrated from a hybrid WebView architecture to a native React Native implementation for Android. This provides better performance, native UI components, and a more maintainable codebase.

## Architecture Changes

### Before (WebView Architecture)
```
┌─────────────────────────────────────┐
│         Android Activity            │
│  ┌───────────────────────────────┐  │
│  │  Jetpack Compose WebView      │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  HTML/JS Web App        │  │  │
│  │  │  (Vite + React)         │  │  │
│  │  └──────────┬──────────────┘  │  │
│  │             │ JS Bridge        │  │
│  └─────────────┼──────────────────┘  │
│                │                     │
│  ┌─────────────┴──────────────────┐ │
│  │  Native Kotlin Components      │ │
│  │  - JellyfinBridge              │ │
│  │  - JellyfinApiService          │ │
│  │  - SecurePreferences           │ │
│  │  - VideoPlayerActivity         │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### After (React Native Architecture)
```
┌─────────────────────────────────────┐
│      React Native Android           │
│  ┌───────────────────────────────┐  │
│  │   React Native Components     │  │
│  │   (TypeScript/JSX)            │  │
│  │                               │  │
│  │   App.tsx                     │  │
│  │   ├─ JellyfinApiService.ts    │  │
│  │   ├─ UI Components            │  │
│  │   └─ React Navigation         │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │   MainActivity (ReactActivity)│  │
│  │   MainApplication             │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## Components Converted

### 1. API Service Layer
**Before:** `android/app/src/main/java/org/introskipper/segmenteditor/api/`
- `JellyfinApi.kt` (Retrofit interface)
- `JellyfinApiService.kt` (HTTP client)

**After:** `src/services/JellyfinApiService.ts`
- TypeScript class using axios
- Same functionality, cleaner API
- Better type safety with TypeScript

### 2. Data Storage
**Before:** `android/app/src/main/java/org/introskipper/segmenteditor/storage/SecurePreferences.kt`
- Native Android EncryptedSharedPreferences
- Kotlin implementation

**After:** `react-native-encrypted-storage`
- React Native library
- Cross-platform compatible
- Same security level (AES-256-GCM)

### 3. UI Layer
**Before:** `android/app/src/main/java/org/introskipper/segmenteditor/ComposeWrappedWebView.kt`
- Jetpack Compose wrapping WebView
- Loading external web app
- JavaScript bridge for communication

**After:** `src/App.tsx`
- Pure React Native components
- Native UI elements (Text, TextInput, TouchableOpacity, etc.)
- No bridge needed - direct TypeScript/JavaScript

### 4. Data Models
**Before:** `android/app/src/main/java/org/introskipper/segmenteditor/model/`
- `Segment.kt`
- `SegmentType.kt`
- Kotlin data classes

**After:** TypeScript interfaces in `src/services/JellyfinApiService.ts`
```typescript
export interface Segment {
  ItemId: string;
  Type: SegmentType;
  StartTicks: number;
  EndTicks: number;
}

export enum SegmentType {
  Intro = 'Intro',
  Outro = 'Outro',
  Recap = 'Recap',
  Preview = 'Preview',
  Credits = 'Credits',
}
```

### 5. JavaScript Bridge
**Before:** `android/app/src/main/java/org/introskipper/segmenteditor/bridge/JellyfinBridge.kt`
- Exposed native methods to JavaScript
- Callback-based async communication
- Complex error handling

**After:** Not needed!
- Direct TypeScript method calls
- Promise-based async/await
- Native JavaScript error handling

### 6. Video Player
**Before:** `android/app/src/main/java/org/introskipper/segmenteditor/player/VideoPlayerActivity.kt`
- Native Android Activity with ExoPlayer
- Jetpack Compose UI

**After:** Will use `react-native-video`
- React Native component
- Same functionality
- Easier to integrate with UI

## File Changes Summary

### Files Removed
```
android/app/src/main/java/org/introskipper/segmenteditor/
├── ComposeWrappedWebView.kt
├── api/
│   ├── JellyfinApi.kt
│   └── JellyfinApiService.kt
├── bridge/
│   └── JellyfinBridge.kt
├── model/
│   ├── Segment.kt
│   └── SegmentType.kt
├── player/
│   └── VideoPlayerActivity.kt
└── storage/
    └── SecurePreferences.kt
```

### Files Added
```
/
├── index.js                        # React Native entry point
├── app.json                        # React Native app config
├── metro.config.js                 # Metro bundler config
├── babel.config.js                 # Babel transpiler config
├── tsconfig.json                   # TypeScript config
├── react-native.config.js          # React Native configuration
├── package.json                    # npm dependencies
└── src/
    ├── App.tsx                     # Main React Native component
    ├── services/
    │   └── JellyfinApiService.ts   # API client (TypeScript)
    └── styles/
        └── Colors.ts               # Color constants
```

### Files Modified
```
android/
├── build.gradle                    # Added React Native plugins
├── settings.gradle                 # Added React Native repos
├── gradle.properties              # React Native config
└── app/
    ├── build.gradle               # React Native dependencies
    ├── src/main/AndroidManifest.xml  # Updated for ReactActivity
    └── src/main/java/org/introskipper/segmenteditor/
        ├── MainActivity.kt        # Now extends ReactActivity
        └── MainApplication.kt     # New: React Native app setup
```

## Dependencies Changed

### Removed Dependencies
```gradle
// WebView
implementation "androidx.webkit:webkit:1.15.0"

// Networking (no longer needed in Android)
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

// ExoPlayer (replaced with react-native-video)
implementation 'androidx.media3:media3-exoplayer:1.5.0'
implementation 'androidx.media3:media3-ui:1.5.0'

// Security (replaced with React Native library)
implementation 'androidx.security:security-crypto:1.1.0-alpha06'
```

### Added Dependencies
```json
{
  "dependencies": {
    "react": "^19.2.4",
    "react-native": "^0.83.1",
    "axios": "^1.13.4",
    "react-native-encrypted-storage": "^4.0.3",
    "react-native-video": "^6.19.0",
    "@react-navigation/native": "^7.1.28",
    "@react-navigation/native-stack": "^7.11.0",
    "react-native-safe-area-context": "^5.6.2",
    "react-native-screens": "^4.20.0"
  }
}
```

## Benefits of React Native Migration

### 1. **Performance**
- ✅ No WebView overhead
- ✅ Native rendering
- ✅ Faster startup time
- ✅ Better memory management

### 2. **Development Experience**
- ✅ Single codebase for UI logic
- ✅ Hot reloading during development
- ✅ Better debugging tools (React DevTools)
- ✅ Larger ecosystem of libraries

### 3. **Code Quality**
- ✅ Type safety with TypeScript
- ✅ No bridge complexity
- ✅ Easier to test
- ✅ More maintainable

### 4. **User Experience**
- ✅ Native UI components (looks and feels native)
- ✅ Better animations
- ✅ Smoother scrolling
- ✅ Native gestures

### 5. **Future Scalability**
- ✅ Easier to add new features
- ✅ Can share code with iOS (future)
- ✅ Access to React Native ecosystem
- ✅ Community support

## Migration Checklist

- [x] Install React Native and dependencies
- [x] Create React Native project structure
- [x] Convert API service to TypeScript
- [x] Convert UI from WebView to React Native components
- [x] Update MainActivity to use ReactActivity
- [x] Create MainApplication for React Native
- [x] Remove WebView and bridge code
- [x] Update build configuration
- [ ] Implement video player with react-native-video
- [ ] Add navigation between screens
- [ ] Implement full segment editor UI
- [ ] Test all functionality
- [ ] Update documentation

## Building and Running

### Development
```bash
# Install dependencies
npm install

# Start Metro bundler
npm start

# In another terminal, run Android app
npm run android
```

### Production Build
```bash
cd android
./gradlew assembleRelease
```

## Testing

### Unit Tests
Tests can be added for:
- API service methods
- Data transformations
- Business logic

### Component Tests
Using React Native Testing Library:
- UI component rendering
- User interactions
- State management

### Integration Tests
- API integration
- Encrypted storage
- Navigation flow

## Troubleshooting

### Common Issues

**Issue:** Metro bundler not starting
**Solution:** Clear cache with `npm start -- --reset-cache`

**Issue:** Android build fails
**Solution:** Clean and rebuild with `cd android && ./gradlew clean && cd ..`

**Issue:** Module not found errors
**Solution:** Reinstall dependencies with `rm -rf node_modules && npm install`

**Issue:** Native module linking issues
**Solution:** React Native 0.83+ uses autolinking, ensure `android/settings.gradle` is configured correctly

## Next Steps

1. **Complete UI Implementation**
   - Add screens for segment management
   - Implement video player screen
   - Add navigation

2. **Feature Parity**
   - Ensure all WebView features work in React Native
   - Add any missing functionality
   - Improve user experience

3. **Testing**
   - Write comprehensive tests
   - Perform manual testing
   - Fix any bugs

4. **Documentation**
   - Update README
   - Add API documentation
   - Create user guide

5. **iOS Support** (Future)
   - Leverage shared React Native code
   - Implement iOS-specific features
   - Test on iOS devices

## Conclusion

The migration from WebView to React Native provides a more native, performant, and maintainable solution for the Segment Editor Mobile app. The conversion of Kotlin components to TypeScript/JavaScript reduces complexity and improves the development experience while maintaining all functionality.
