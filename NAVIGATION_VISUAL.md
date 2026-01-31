# Navigation Structure - Visual Comparison

## Before (Original Implementation)

```
┌─────────────────────────────────────┐
│         HomeScreen                   │
│  (Media Library Selection)           │
│                                      │
│  [TV Shows] [Movies] [Music]        │
│                                      │
│  ┌──────┐ ┌──────┐ ┌──────┐        │
│  │Series│ │Movie │ │Album │        │
│  │ Card │ │ Card │ │ Card │        │
│  └──────┘ └──────┘ └──────┘        │
└─────────────────────────────────────┘
           ↓ (click series)
┌─────────────────────────────────────┐
│       SeriesScreen (Old)             │
│  ┌─────────────────────────────┐   │
│  │   Series Header             │   │
│  │   [Poster] Title • Year     │   │
│  └─────────────────────────────┘   │
│                                      │
│  ┌─────────────────────────────┐   │
│  │  Season 1                    │   │  ← Header
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Ep 1 [Thumb] Title          │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Ep 2 [Thumb] Title          │   │
│  └─────────────────────────────┘   │
│                                      │
│  ┌─────────────────────────────┐   │
│  │  Season 2                    │   │  ← Header
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Ep 1 [Thumb] Title          │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Ep 2 [Thumb] Title          │   │
│  └─────────────────────────────┘   │
│  ... (all seasons in one scroll)    │
└─────────────────────────────────────┘
           ↓ (click episode)
┌─────────────────────────────────────┐
│         PlayerScreen                 │
│  ┌─────────────────────────────┐   │
│  │                              │   │
│  │      Video Player            │   │
│  │                              │   │
│  └─────────────────────────────┘   │
│  Timeline: [====|====|====]         │
│  Segments: Intro, Outro             │
└─────────────────────────────────────┘
```

## After (New Implementation)

```
┌─────────────────────────────────────┐
│         HomeScreen                   │
│  (Media Library Selection)           │
│                                      │
│  [TV Shows] [Movies] [Music]        │
│                                      │
│  ┌──────┐ ┌──────┐ ┌──────┐        │
│  │Series│ │Movie │ │Album │        │
│  │ Card │ │ Card │ │ Card │        │
│  └──────┘ └──────┘ └──────┘        │
└─────────────────────────────────────┘
           ↓ (click series)
┌─────────────────────────────────────┐
│       SeriesScreen (New)             │
│  ┌─────────────────────────────┐   │
│  │   Series Header             │   │
│  │   [Poster] Title • Year     │   │
│  └─────────────────────────────┘   │
│                                      │
│  ┌─────────────────────────────┐   │
│  │  [Season 1] [Season 2] ...  │   │  ← TABS!
│  │      ▼                       │   │
│  └─────────────────────────────┘   │
│                                      │
│  Episodes for selected season:       │
│  ┌─────────────────────────────┐   │
│  │ S1E1 [Thumb] Episode Title  │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ S1E2 [Thumb] Episode Title  │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ S1E3 [Thumb] Episode Title  │   │
│  └─────────────────────────────┘   │
│  ... (only selected season shown)   │
└─────────────────────────────────────┘
           ↓ (click episode)
┌─────────────────────────────────────┐
│         PlayerScreen                 │
│  ┌─────────────────────────────┐   │
│  │                              │   │
│  │      Video Player            │   │
│  │                              │   │
│  └─────────────────────────────┘   │
│  Timeline: [====|====|====]         │
│  Segments: Intro, Outro             │
│                                      │
│  ✅ Segments auto-load on mount     │
└─────────────────────────────────────┘
```

## Key Improvements

### 1. Season Navigation
**Before:** Long vertical scroll through all seasons
**After:** Tabs at top, tap to switch seasons

### 2. Episode Discovery
**Before:** Must scroll past all earlier seasons
**After:** Direct access to any season via tab

### 3. Visual Hierarchy
**Before:** Headers blend with content
**After:** Clear separation with Material3 tabs

### 4. Mobile UX
**Before:** Scroll-heavy navigation
**After:** Tap-based navigation (faster)

## Navigation Flow

```
Library Selection
    ↓
  [Select "TV Shows"]
    ↓
  Shows Grid
    ↓
  [Click Series]
    ↓
  Series Details with Tabs
    ↓ ←─────────┐
  Season 1 Tab  │  [Switch Tabs]
    ↓           │
  Episodes      │
    ↓ ←─────────┘
  [Click Episode]
    ↓
  Player
    ↓ (automatic)
  Segments Load
    ↓
  Edit Segments
```

## Segment Loading Flow

```
Episode Selected → Navigate to PlayerScreen
                         ↓
               PlayerViewModel.loadMediaItem(itemId)
                         ↓
            ┌────────────┴────────────┐
            ↓                         ↓
    Load Media Details        Extract Tracks
            ↓                         ↓
    MediaSources              Audio/Subtitle
    MediaStreams                   ↓
            ↓                    Display
            ↓
    loadSegments(itemId)
            ↓
    SegmentRepository.getSegmentsResult()
            ↓
    GET /MediaSegments/{itemId}
            ↓
    ┌───────┴────────┐
    ↓                ↓
 Success          Failure
    ↓                ↓
 Display         Log Warning
 Timeline        (Non-critical)
 & Cards
```

## Comparison Table

| Aspect | Before | After | Benefit |
|--------|--------|-------|---------|
| **Season Access** | Sequential scroll | Direct tab selection | Faster navigation |
| **Visual Clarity** | Headers in list | Prominent tabs | Better hierarchy |
| **Episode Count** | All visible | Per-season | Less clutter |
| **Scrolling** | Long scrolls | Short scrolls | Better UX |
| **Mobile-friendly** | Medium | High | Thumb-optimized |
| **Matches Web** | No | Yes | Consistent UX |
| **State Management** | Simple | Tabbed | More robust |

## Technical Architecture

```
┌─────────────────────────────────────┐
│           AppNavigation              │
│         (NavController)              │
└─────────────────────────────────────┘
                 │
    ┌────────────┼────────────┐
    ↓            ↓            ↓
┌────────┐  ┌──────────┐  ┌────────┐
│ Home   │  │ Series   │  │ Player │
│ Screen │  │ Screen   │  │ Screen │
└────────┘  └──────────┘  └────────┘
    │            │             │
    ↓            ↓             ↓
┌────────┐  ┌──────────┐  ┌────────┐
│ Home   │  │ Series   │  │ Player │
│ View   │  │ View     │  │ View   │
│ Model  │  │ Model    │  │ Model  │
└────────┘  └──────────┘  └────────┘
    │            │             │
    └────────────┼─────────────┘
                 ↓
         ┌──────────────┐
         │ Repositories │
         ├──────────────┤
         │ Media        │
         │ Segment      │
         │ Auth         │
         └──────────────┘
                 ↓
         ┌──────────────┐
         │ API Service  │
         │ (Jellyfin)   │
         └──────────────┘
```

## Summary

The new implementation provides:
- ✅ Cleaner visual hierarchy
- ✅ Faster season navigation
- ✅ Better mobile UX
- ✅ Matches web reference
- ✅ Automatic segment loading
- ✅ Robust error handling
