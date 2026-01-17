# Jellyfin Segment Editor

Manage Jellyfin Media Segment positions the simple way. This tool is in early stages of development.

- Create/Edit/Delete all kind of Segments (Intro, Outro, ...)
- Player to copy timestamps while you watch

## Requirements

- Jellyfin 10.10
- [Intro Skipper](https://github.com/intro-skipper/intro-skipper) for Jellyfin 10.10.2+
- [MediaSegments API](https://github.com/intro-skipper/jellyfin-plugin-ms-api) for Jellyfin 10.10
- Jellyfin Server API Key (created by you)

## Installation

- Download for your platform from [Releases](https://github.com/intro-skipper/segment-editor/releases/latest)

## Related projects

- Jellyfin Plugin: [.EDL Creator](https://github.com/intro-skipper/jellyfin-plugin-edl)
- Jellyfin Plugin: [Chapter Creator](https://github.com/intro-skipper/jellyfin-plugin-ms-chapter)
- Jellyfin Plugin: [MediaSegments API](https://github.com/intro-skipper/jellyfin-plugin-ms-api)

## Prerequisites

- Xcode 14.3 or higher for iOS

## Running it on iOS

1. Run `cd web` and run `npm install` to install the dependencies
2. Open up `ios/Host.xcproj` in Xcode
3. Run it in a simulator or on a physical device

## Running on Android

1. Run `cd web` and run `npm install` to install the dependencies
2. Open the `android` directory in Android Studio
3. Build and run the app in an emulator or on a physical device

*Template provided by https://github.com/getditto/react-in-mobile*
