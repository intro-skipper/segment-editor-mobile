# Jellyfin Segment Editor

Manage Jellyfin Media Segment positions the simple way. This tool is in early stages of development.

- Create/Edit/Delete all kind of Segments (Intro, Outro, ...)
- Player to copy timestamps while you watch

## Requirements

- Jellyfin 10.10
- [Intro Skipper](https://github.com/intro-skipper/intro-skipper) for Jellyfin 10.10.2+
  - [MediaSegments API](https://github.com/intro-skipper/jellyfin-plugin-ms-api) for 10.10.0 / 10.10.1
- Jellyfin Server API Key (created by you)

## Installation

- Download for your platform from [Releases](https://github.com/intro-skipper/segment-editor/releases/latest)

## Related projects

- Jellyfin Plugin: [.EDL Creator](https://github.com/intro-skipper/jellyfin-plugin-edl)
- Jellyfin Plugin: [Chapter Creator](https://github.com/intro-skipper/jellyfin-plugin-ms-chapter)

## Architecture

This mobile app uses **React Native** for cross-platform development with native performance.

### Technology Stack
- **React Native 0.83** - Mobile framework
- **TypeScript** - Type-safe JavaScript
- **Axios** - HTTP client for Jellyfin API
- **React Native Encrypted Storage** - Secure credential storage
- **React Navigation** - Navigation framework

## Development Setup

### Prerequisites

- Node.js 20 (see `.nvmrc`)
- **For Android development:**
  - Android Studio
  - JDK 17
- **For iOS development:**
  - Xcode 14 or later
  - CocoaPods (`sudo gem install cocoapods`)
  - macOS (required for iOS development)

### Running on Android

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start Metro bundler:
   ```bash
   npm start
   ```

3. In another terminal, run the Android app:
   ```bash
   npm run android
   ```

   Or open the `android` directory in Android Studio and build/run from there.

### Running on iOS

1. Install dependencies:
   ```bash
   npm install
   ```

2. Install CocoaPods dependencies:
   ```bash
   cd ios
   pod install
   cd ..
   ```

3. Start Metro bundler:
   ```bash
   npm start
   ```

4. In another terminal, run the iOS app:
   ```bash
   npm run ios
   ```

   Or open `ios/SegmentEditor.xcworkspace` in Xcode and build/run from there.

   **Note:** iOS development requires macOS and Xcode.

### Building for Production

**Android:**
```bash
cd android
./gradlew assembleRelease
```

The APK will be in `android/app/build/outputs/apk/release/`

**iOS:**
1. Open `ios/SegmentEditor.xcworkspace` in Xcode
2. Select "Product" → "Archive"
3. Follow Xcode's instructions to distribute the app

## Migration from WebView

This app was recently migrated from a WebView-based architecture to pure React Native. See [REACT_NATIVE_MIGRATION.md](./REACT_NATIVE_MIGRATION.md) for details.

## Features

- ✅ Secure credential storage (Jellyfin server URL + API key)
- ✅ Connection testing to Jellyfin server
- ✅ TypeScript API client for Jellyfin MediaSegments API
- 🚧 Segment CRUD operations
- 🚧 Video player with timestamp copying
- 🚧 Segment editor UI

## Project Structure

```
segment-editor-mobile/
├── android/                 # Android native code
│   └── app/src/main/java/...
├── ios/                     # iOS native code
│   ├── SegmentEditor/       # iOS app files
│   ├── SegmentEditor.xcodeproj/  # Xcode project
│   └── Podfile              # CocoaPods dependencies
├── src/                     # React Native source code
│   ├── App.tsx             # Main app component
│   ├── services/           # API services
│   ├── screens/            # Screen components
│   ├── components/         # Reusable components
│   └── styles/             # Styles and themes
├── index.js                # React Native entry point
├── package.json            # npm dependencies
└── tsconfig.json           # TypeScript configuration
```

## Contributing

Contributions are welcome! Please ensure:
- Code follows TypeScript best practices
- Components are properly typed
- UI is tested on both Android and iOS platforms

## License

See [LICENSE](./LICENSE) file for details.

*Original template provided by https://github.com/getditto/react-in-mobile*
