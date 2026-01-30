# Archived Documentation Notice

The following files contain documentation for the **previous WebView-based architecture** and are kept for reference only:

- `JELLYFIN_INTEGRATION_OLD_WEBVIEW.md` - Old Jellyfin integration guide for WebView
- `IMPLEMENTATION_SUMMARY_OLD_WEBVIEW.md` - Old implementation details
- `TESTING_GUIDE_OLD_WEBVIEW.md` - Old testing procedures
- `COMPLETION_SUMMARY_OLD_WEBVIEW.md` - Old completion summary

## Current Documentation

For the current **React Native architecture**, please refer to:

- **[README.md](./README.md)** - Main documentation and getting started guide
- **[REACT_NATIVE_MIGRATION.md](./REACT_NATIVE_MIGRATION.md)** - Detailed migration guide explaining the architectural changes

## What Changed

The app was migrated from:
- **WebView-based hybrid app** (Jetpack Compose WebView loading HTML/JS)
- **Native Kotlin bridge** for API communication

To:
- **Pure React Native** implementation
- **TypeScript/JavaScript** for all business logic
- **Native rendering** for better performance

This migration provides:
- ✅ Better performance (no WebView overhead)
- ✅ Native UI components
- ✅ Improved development experience
- ✅ Type safety with TypeScript
- ✅ Easier maintenance

## Timeline

- **Before Jan 30, 2026**: WebView architecture
- **Jan 30, 2026**: Migrated to React Native
