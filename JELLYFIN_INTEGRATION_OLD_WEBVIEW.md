# Jellyfin Integration for Android

This document describes the Android native implementation of Jellyfin integration, segment editor, and media player features.

## Architecture

The Android app uses a hybrid architecture:
- **Native UI Layer**: Jetpack Compose for Material Design 3 UI
- **WebView Layer**: Embeds the segment-editor web application
- **JavaScript Bridge**: Enables communication between web app and native Android code
- **Native Services**: Jellyfin API integration, secure storage, and video playback

## Components

### 1. Jellyfin API Integration (`api/`)

#### `JellyfinApi.kt`
Retrofit interface defining the Jellyfin MediaSegments API endpoints:
- `GET /MediaSegments/{itemId}` - Get all segments for an item
- `POST /MediaSegments` - Create a new segment
- `PUT /MediaSegments/{itemId}/{segmentType}` - Update a segment
- `DELETE /MediaSegments/{itemId}/{segmentType}` - Delete a segment
- `GET /System/Info` - Test server connection

#### `JellyfinApiService.kt`
Service class that manages:
- Retrofit instance with OkHttp client
- HTTP logging for debugging
- Connection timeouts (30s)
- API key authentication via `X-Emby-Token` header

### 2. Data Models (`model/`)

#### `SegmentType.kt`
Enum defining supported segment types:
- Intro
- Outro
- Recap
- Preview
- Credits

#### `Segment.kt`
Data classes for segments:
- `Segment` - Represents a media segment with tick-based timing
- `SegmentCreateRequest` - Request model for creating/updating segments
- Helper methods to convert between ticks and seconds (10,000,000 ticks = 1 second)

### 3. Secure Storage (`storage/`)

#### `SecurePreferences.kt`
Manages secure storage of Jellyfin credentials using:
- EncryptedSharedPreferences with AES256_GCM encryption
- Stores server URL and API key securely
- Fallback to regular SharedPreferences if encryption fails
- Methods: `saveServerUrl()`, `getServerUrl()`, `saveApiKey()`, `getApiKey()`, `isConfigured()`, `clear()`

### 4. JavaScript Bridge (`bridge/`)

#### `JellyfinBridge.kt`
Provides JavaScript interface for web app communication:

**Configuration Methods:**
- `getServerUrl()` - Retrieve saved server URL
- `getApiKey()` - Retrieve saved API key
- `saveCredentials(serverUrl, apiKey)` - Save credentials securely
- `testConnection(callbackId)` - Test Jellyfin server connection

**Segment Operations:**
- `getSegments(itemId, callbackId)` - Fetch all segments for an item
- `createSegment(segmentJson, callbackId)` - Create a new segment
- `updateSegment(itemId, segmentType, segmentJson, callbackId)` - Update existing segment
- `deleteSegment(itemId, segmentType, callbackId)` - Delete a segment

**Utility Methods:**
- `copyToClipboard(text)` - Copy text to system clipboard
- `openVideoPlayer(videoUrl, itemId)` - Launch native video player

All async methods use coroutines and notify the WebView via JavaScript callbacks.

### 5. Video Player (`player/`)

#### `VideoPlayerActivity.kt`
Native video player using ExoPlayer with features:

**Playback Controls:**
- Play/Pause
- Seek backward (-10s)
- Seek forward (+10s)
- Full ExoPlayer controls in player view

**Timestamp Features:**
- Real-time timestamp display (HH:MM:SS or MM:SS)
- Copy formatted timestamp button
- Copy raw seconds button
- Automatic clipboard feedback via Toast

**UI Components:**
- PlayerView with built-in controls
- Material Design 3 bottom sheet with timestamp tools
- Responsive layout supporting portrait and landscape

### 6. WebView Integration (`ComposeWrappedWebView.kt`)

Enhanced WebView with:
- JavaScript enabled for web app functionality
- JavaScript bridge injection: `window.JellyfinBridge`
- Fullscreen video support via WebChromeClient
- Automatic landscape orientation for fullscreen video
- Asset loading from `assets/dist/` directory
- Mixed content support for Jellyfin servers
- DOM storage and media playback enabled

## Usage from Web App

### Initialize Bridge Connection
```javascript
if (window.JellyfinBridge) {
  // Bridge is available
  const serverUrl = window.JellyfinBridge.getServerUrl();
  const apiKey = window.JellyfinBridge.getApiKey();
}
```

### Save Credentials
```javascript
window.JellyfinBridge.saveCredentials(
  "https://jellyfin.example.com",
  "your-api-key-here"
);
```

### Test Connection
```javascript
window.testConnectionCallback = function(result) {
  const data = JSON.parse(result);
  if (data.success) {
    console.log("Connected:", data.data);
  } else {
    console.error("Error:", data.error);
  }
};
window.JellyfinBridge.testConnection("testConnectionCallback");
```

### Get Segments
```javascript
window.segmentsCallback = function(result) {
  const data = JSON.parse(result);
  if (data.success) {
    const segments = data.data;
    // Process segments
  }
};
window.JellyfinBridge.getSegments("item-id", "segmentsCallback");
```

### Create Segment
```javascript
const segment = {
  ItemId: "item-id",
  Type: "Intro",
  StartTicks: 100000000,  // 10 seconds
  EndTicks: 900000000     // 90 seconds
};
window.createCallback = function(result) {
  const data = JSON.parse(result);
  if (data.success) {
    console.log("Created:", data.data);
  }
};
window.JellyfinBridge.createSegment(
  JSON.stringify(segment),
  "createCallback"
);
```

### Open Video Player
```javascript
window.JellyfinBridge.openVideoPlayer(
  "https://jellyfin.example.com/Videos/item-id/stream?api_key=key",
  "item-id"
);
```

### Copy to Clipboard
```javascript
window.JellyfinBridge.copyToClipboard("00:01:23");
```

## Dependencies Added

```gradle
// Networking for Jellyfin API
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
implementation 'com.google.code.gson:gson:2.10.1'

// ExoPlayer for video playback
implementation 'androidx.media3:media3-exoplayer:1.5.0'
implementation 'androidx.media3:media3-ui:1.5.0'
implementation 'androidx.media3:media3-common:1.5.0'

// Security for encrypted preferences
implementation 'androidx.security:security-crypto:1.1.0-alpha06'

// ViewModel and LiveData
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0'
implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.10.0'
```

## AndroidManifest Updates

Added VideoPlayerActivity:
```xml
<activity
    android:name=".player.VideoPlayerActivity"
    android:exported="false"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
    android:theme="@style/Theme.ReactInMobile" />
```

## Security Considerations

1. **Encrypted Storage**: Credentials stored using EncryptedSharedPreferences with AES256_GCM
2. **HTTPS Support**: Cleartext traffic allowed for local Jellyfin servers (configurable)
3. **API Key Authentication**: Using Jellyfin's standard X-Emby-Token header
4. **Sandboxed WebView**: File access disabled, only assets loaded from app

## Future Enhancements

1. **Settings UI**: Native settings screen for server configuration
2. **Authentication UI**: Login flow for username/password authentication
3. **Error Handling**: Better UI feedback for network errors
4. **Offline Support**: Cache segments locally
5. **Timeline Integration**: Visual segment timeline in video player
6. **Segment Markers**: Show segment boundaries during playback
7. **Auto-Skip**: Automatically skip intro segments during playback

## Testing

### Manual Testing Steps

1. **Credentials Storage**:
   - Open app in WebView
   - Call `JellyfinBridge.saveCredentials()`
   - Verify credentials persist across app restarts

2. **API Connection**:
   - Test connection to Jellyfin server
   - Verify API key authentication works
   - Check network error handling

3. **Segment Operations**:
   - Create a new segment
   - Update an existing segment
   - Delete a segment
   - Fetch all segments for an item

4. **Video Player**:
   - Launch video player with a media URL
   - Test playback controls
   - Copy timestamps to clipboard
   - Verify landscape mode for fullscreen

### Build Commands

```bash
# Debug build
cd android
./gradlew assembleDebug

# Release build (requires keystore)
./gradlew assembleRelease

# Install on device
./gradlew installDebug
```

## Troubleshooting

### Common Issues

1. **Build Errors**: Ensure Android Gradle Plugin and Kotlin versions are compatible
2. **Network Errors**: Check Jellyfin server URL and API key
3. **Encryption Errors**: May fall back to unencrypted storage on some devices
4. **Video Playback**: Ensure video URL is accessible and format is supported by ExoPlayer

### Debugging

Enable HTTP logging to see API requests:
```kotlin
// Already enabled in JellyfinApiService.kt
loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
```

Check WebView console from Chrome:
1. Enable USB debugging on Android device
2. Open `chrome://inspect` in Chrome
3. Find the WebView and click "inspect"
4. View console logs and network requests
