# WebView to React Native Migration

This document explains the migration from a WebView-based approach to native React Native.

## What Changed

### Before
The app used to:
1. Clone the `segment-editor` web project from GitHub during build
2. Build it as a static website using Vite
3. Load it in an Android WebView component
4. Display the web interface through the WebView

### After  
The app now uses:
1. React Native for the mobile UI
2. Native React Native components instead of web views
3. React Navigation for screen navigation
4. Direct integration between Kotlin/Compose and React Native

## Technical Changes

### Removed
- `ComposeWrappedWebView.kt` - Old WebView wrapper
- All web build tasks from gradle (buildWeb, copyDistToAssets, etc.)
- Dependency on `androidx.webkit:webkit`
- Git clone tasks for the web repository
- Node gradle plugin dependency

### Added
- `MainApplication.kt` - React Native application initialization
- `ComposeWrappedReactNative.kt` - Bridge between Compose and React Native
- React Native dependencies (`react-native`, `react-navigation`, etc.)
- React Native configuration files (`babel.config.js`, `metro.config.js`)
- Native screens in `src/screens/` directory
- `index.js` - React Native entry point

### Modified
- `MainActivity.kt` - Now extends `ReactActivity` instead of `ComponentActivity`
- `AndroidManifest.xml` - Uses `MainApplication` as the application class
- `build.gradle` files - Updated for React Native integration
- `settings.gradle` - Added React Native maven repositories

## Project Structure

```
segment-editor-mobile/
├── android/                    # Android native code
│   └── app/
│       └── src/main/java/org/introskipper/segmenteditor/
│           ├── MainActivity.kt              # Main Activity (extends ReactActivity)
│           ├── MainApplication.kt           # React Native initialization
│           └── ComposeWrappedReactNative.kt # Compose-RN bridge
├── src/                        # React Native source code
│   └── screens/
│       ├── HomeScreen.tsx     # Landing screen
│       └── PlayerScreen.tsx   # Player screen
├── index.js                   # React Native entry point
├── package.json              # Dependencies
├── babel.config.js           # Babel configuration
├── metro.config.js           # Metro bundler configuration
└── tsconfig.json             # TypeScript configuration
```

## Benefits

1. **Better Performance** - Native components are faster than WebView
2. **Native Feel** - Uses platform-native UI components
3. **Offline First** - No need to load web assets
4. **Simpler Build** - No need to clone and build web project
5. **Better Integration** - Direct access to native Android APIs

## Development

### Running the App
```bash
# Install dependencies
npm install

# Run on Android
npm run android

# Or build manually
cd android
./gradlew assembleDebug
```

### Adding New Screens
1. Create a new screen component in `src/screens/`
2. Add the screen to the navigation stack in `index.js`

### Debugging
React Native provides excellent debugging tools:
- React Native Debugger
- Chrome DevTools integration
- Hot reload support

## Future Improvements

The current implementation provides a basic structure. Future enhancements could include:
1. Port more functionality from the web app
2. Add media player integration
3. Implement segment editing features
4. Add Jellyfin API integration
5. Implement data persistence
6. Add more screens and navigation
