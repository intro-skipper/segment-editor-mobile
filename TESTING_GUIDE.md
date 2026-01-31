# Android Testing Guide

This guide explains how to test the newly implemented Jellyfin integration features for Android.

## Prerequisites

1. Android device or emulator with API 30+ (Android 11+)
2. Jellyfin server (10.10+) with Intro Skipper plugin
3. Jellyfin API key (generate in server dashboard)
4. Android Studio or `adb` for installation

## Building the App

```bash
cd android
./gradlew assembleDebug
```

The APK will be generated at:
```
android/app/build/outputs/apk/debug/SegmentEditor-<commit>-debug.apk
```

## Installation

```bash
# Install via adb
adb install app/build/outputs/apk/debug/SegmentEditor-*-debug.apk

# Or open Android Studio and click Run
```

## Test Plan

### 1. Initial Setup Test
**Goal**: Verify credential storage and retrieval

**Steps**:
1. Open the app
2. In the WebView console (chrome://inspect), run:
   ```javascript
   window.JellyfinBridge.saveCredentials(
     "https://your-jellyfin-server.com",
     "your-api-key-here"
   );
   ```
3. Restart the app
4. Run:
   ```javascript
   console.log(window.JellyfinBridge.getServerUrl());
   console.log(window.JellyfinBridge.getApiKey());
   ```

**Expected**: Credentials persist across app restarts

### 2. Connection Test
**Goal**: Verify Jellyfin server connectivity

**Steps**:
1. In WebView console, run:
   ```javascript
   window.testCallback = function(result) {
     console.log("Connection result:", result);
   };
   window.JellyfinBridge.testConnection("testCallback");
   ```

**Expected**: 
- Success: `{success: true, data: {...server info...}}`
- Failure: `{success: false, error: "..."}` with error message

### 3. Segment Operations Test
**Goal**: Verify CRUD operations for segments

#### 3.1 Get Segments
```javascript
window.getCallback = function(result) {
  console.log("Segments:", result);
};
window.JellyfinBridge.getSegments("YOUR_ITEM_ID", "getCallback");
```

**Expected**: Array of existing segments or empty array

#### 3.2 Create Segment
```javascript
window.createCallback = function(result) {
  console.log("Created:", result);
};

const newSegment = {
  ItemId: "YOUR_ITEM_ID",
  Type: "Intro",
  StartTicks: 100000000,  // 10 seconds
  EndTicks: 900000000     // 90 seconds
};

window.JellyfinBridge.createSegment(
  JSON.stringify(newSegment),
  "createCallback"
);
```

**Expected**: `{success: true, data: {...created segment...}}`

#### 3.3 Update Segment
```javascript
window.updateCallback = function(result) {
  console.log("Updated:", result);
};

const updatedSegment = {
  ItemId: "YOUR_ITEM_ID",
  Type: "Intro",
  StartTicks: 120000000,  // 12 seconds
  EndTicks: 950000000     // 95 seconds
};

window.JellyfinBridge.updateSegment(
  "YOUR_ITEM_ID",
  "Intro",
  JSON.stringify(updatedSegment),
  "updateCallback"
);
```

**Expected**: `{success: true, data: {...updated segment...}}`

#### 3.4 Delete Segment
```javascript
window.deleteCallback = function(result) {
  console.log("Deleted:", result);
};

window.JellyfinBridge.deleteSegment(
  "YOUR_ITEM_ID",
  "Intro",
  "deleteCallback"
);
```

**Expected**: `{success: true}`

### 4. Video Player Test
**Goal**: Verify native video playback and timestamp copying

**Steps**:
1. Get a video stream URL from Jellyfin:
   ```
   https://your-jellyfin-server.com/Videos/ITEM_ID/stream?api_key=YOUR_KEY
   ```
2. Open player from WebView:
   ```javascript
   window.JellyfinBridge.openVideoPlayer(
     "https://your-jellyfin-server.com/Videos/ITEM_ID/stream?api_key=YOUR_KEY",
     "ITEM_ID"
   );
   ```
3. Test in player activity:
   - ✓ Video loads and plays
   - ✓ Timestamp updates in real-time
   - ✓ "Copy Timestamp" button works
   - ✓ "Copy Seconds" button works
   - ✓ "+10s" / "-10s" seek buttons work
   - ✓ Play/Pause toggle works
   - ✓ Toast appears when copying to clipboard
   - ✓ Landscape orientation works

**Expected**: 
- Video plays smoothly
- All controls respond correctly
- Timestamps copied to clipboard
- Toast confirmation appears

### 5. Clipboard Test
**Goal**: Verify clipboard integration

**Steps**:
1. From WebView console:
   ```javascript
   window.JellyfinBridge.copyToClipboard("Test text 12:34:56");
   ```
2. Long-press in any text field and paste

**Expected**: "Test text 12:34:56" is pasted

### 6. Error Handling Test
**Goal**: Verify proper error handling

#### 6.1 Invalid URL
```javascript
window.JellyfinBridge.openVideoPlayer("", "test");
```
**Expected**: Error logged, no crash

#### 6.2 Invalid Item ID
```javascript
window.testCallback = function(result) {
  console.log("Result:", result);
};
window.JellyfinBridge.getSegments("INVALID_ID", "testCallback");
```
**Expected**: `{success: false, error: "..."}` with HTTP error code

#### 6.3 Network Failure
1. Turn off network
2. Try any API operation
**Expected**: `{success: false, error: "..."}` with connection error

### 7. Security Test
**Goal**: Verify secure credential storage

**Steps**:
1. Save credentials
2. Check Android logcat for logs:
   ```bash
   adb logcat | grep -i "api\|key\|token"
   ```

**Expected** (in debug build):
- HTTP requests logged
- API key visible in logs (debug only)

**Expected** (in release build):
- Minimal logging
- No sensitive data in logs

### 8. Persistence Test
**Goal**: Verify data persists across app lifecycle

**Steps**:
1. Save credentials
2. Create a segment
3. Force-stop app (Settings > Apps > Force Stop)
4. Reopen app
5. Check credentials and segments

**Expected**: All data persists

## Debugging

### Enable WebView Debugging
1. Connect Android device via USB
2. Enable USB debugging on device
3. Open Chrome and navigate to: `chrome://inspect`
4. Find the WebView and click "inspect"
5. Use Console tab to run JavaScript commands

### View Native Logs
```bash
# All logs
adb logcat

# Filter for app
adb logcat | grep SegmentEditor

# Filter for Jellyfin bridge
adb logcat | grep JellyfinBridge

# Filter for network requests
adb logcat | grep okhttp
```

### Common Issues

**Issue**: "Bridge not available"
- **Solution**: Ensure app has loaded completely and WebView is initialized

**Issue**: "Connection failed"
- **Solution**: Check server URL, API key, and network connectivity

**Issue**: "Invalid callback ID"
- **Solution**: Use only alphanumeric characters and underscores in callback names

**Issue**: Video won't play
- **Solution**: Verify video URL is valid and accessible from device

## Performance Testing

### Memory
```bash
adb shell dumpsys meminfo org.introskipper.segmenteditor
```

### Battery
Monitor battery usage in Settings > Battery after extended use

### Network
Monitor network usage with Android Studio Profiler or:
```bash
adb shell dumpsys netstats
```

## Test Checklist

### Functionality
- [ ] Credentials save and persist
- [ ] Server connection works
- [ ] Get segments returns data
- [ ] Create segment works
- [ ] Update segment works
- [ ] Delete segment works
- [ ] Video player opens
- [ ] Video playback works
- [ ] Timestamp display updates
- [ ] Copy timestamp works
- [ ] Copy seconds works
- [ ] Seek controls work
- [ ] Play/Pause works
- [ ] Clipboard integration works

### Security
- [ ] Credentials encrypted (or logged if fallback)
- [ ] No sensitive data in release logs
- [ ] API key not exposed unnecessarily
- [ ] Callback IDs validated

### Performance
- [ ] No ANR (App Not Responding)
- [ ] Video playback smooth
- [ ] UI responsive
- [ ] Memory usage reasonable
- [ ] Battery drain acceptable

### Error Handling
- [ ] Invalid URLs handled
- [ ] Network errors handled
- [ ] Invalid data handled
- [ ] App doesn't crash
- [ ] Error messages are clear

## Automated Testing (Future)

Unit tests can be added for:
- `SecurePreferences`: Credential storage/retrieval
- `JellyfinApiService`: API request/response handling
- `Segment` models: Tick/second conversion
- `JellyfinBridge`: Response formatting

UI tests can be added for:
- VideoPlayerActivity: Control interactions
- Clipboard operations
- Error dialogs

## Reporting Issues

When reporting issues, include:
1. Android version
2. Device model
3. Jellyfin server version
4. Logcat output
5. Steps to reproduce
6. Expected vs actual behavior

## Success Criteria

The implementation is successful if:
- ✓ All functionality tests pass
- ✓ No security issues found
- ✓ Performance is acceptable
- ✓ Error handling works correctly
- ✓ Documentation is clear and complete

## Next Steps After Testing

1. Fix any issues found during testing
2. Add unit and UI tests
3. Optimize performance if needed
4. Update documentation based on findings
5. Prepare for release (signing, ProGuard, etc.)
6. Create release notes
7. Submit to app stores (if applicable)

---

**Status**: Ready for testing  
**Last Updated**: 2026-01-30  
**Version**: Initial implementation
