# Segment Slider Usage Guide

## For Users

### Viewing Segments
- Segments appear as colored cards below the video player
- Each segment shows:
  - Type badge (e.g., "Intro", "Credits") with color coding
  - Total duration
  - Start and end times
  - Interactive timeline slider

### Editing Segments

#### Adjusting Segment Times
1. **Drag the handles**: Touch and drag the start (left) or end (right) handle on the timeline
   - The handle will follow your finger
   - The segment duration updates in real-time
   - Invalid boundaries show an error message

2. **Use seek buttons**: Tap the play icon next to start/end times to jump to that point in the video

3. **Copy segment**: Tap the copy icon to copy segment information to clipboard

4. **Delete segment**: Tap the delete icon to remove the segment (confirmation via dialog)

### Visual Indicators

#### Segment Colors
- **Green**: Intro
- **Blue**: Credits
- **Red**: Commercial
- **Orange**: Recap
- **Purple**: Preview
- **Yellow**: Other types

#### Active Segment
- **Blue border**: Indicates the currently selected segment
- **Highlighted background**: Makes it easier to identify

#### Validation Errors
- **Red dot + message**: Appears when segment boundaries are invalid
  - "Start time must be before end time"
  - "End time exceeds video duration"
  - And other validation messages

## For Developers

### Basic Usage

```kotlin
SegmentSlider(
    segment = segment,
    index = 0,
    isActive = true,
    runtimeSeconds = 3600.0, // 1 hour video
    onUpdate = { updatedSegment ->
        // Handle segment update
        viewModel.updateSegment(updatedSegment)
    },
    onDelete = {
        // Handle segment deletion
        viewModel.deleteSegment(segment)
    },
    onSeekTo = { timeSeconds ->
        // Seek player to time
        player.seekTo((timeSeconds * 1000).toLong())
    },
    onSetActive = {
        // Update active segment
        activeSegmentIndex = index
    }
)
```

### Integration Example

```kotlin
@Composable
fun SegmentsList(
    segments: List<Segment>,
    activeIndex: Int,
    duration: Long,
    player: ExoPlayer?,
    onSegmentUpdate: (Segment) -> Unit,
    onSegmentDelete: (Segment) -> Unit,
    onActiveChange: (Int) -> Unit
) {
    LazyColumn {
        items(segments.size) { index ->
            SegmentSlider(
                segment = segments[index],
                index = index,
                isActive = index == activeIndex,
                runtimeSeconds = duration / 1000.0,
                onUpdate = onSegmentUpdate,
                onDelete = { onSegmentDelete(segments[index]) },
                onSeekTo = { time ->
                    player?.seekTo((time * 1000).toLong())
                },
                onSetActive = { onActiveChange(index) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
```

### Customization

#### Colors
Modify `getSegmentColor()` function to change segment colors:

```kotlin
private fun getSegmentColor(type: String): Color {
    return when (type.lowercase()) {
        "intro" -> Color(0xFF4CAF50)  // Your custom color
        // ... other types
    }
}
```

#### Validation Rules
Modify `SegmentValidator` to change validation logic:

```kotlin
// In SegmentValidator.kt
val minGap = 1.0 // Change minimum gap to 1 second
```

#### Handle Width
Adjust handle size in `SliderTrack`:

```kotlin
val handleWidth = 16.dp // Make handles wider
```

### State Management

The component manages local state for smooth dragging:

1. **During drag**: Local state updates immediately
2. **On drag end**: Callback fires with final values
3. **On prop change**: Local state syncs with new prop values

This prevents lag during dragging while ensuring data consistency.

### Performance Tips

1. **Lazy Loading**: Use `LazyColumn` for large segment lists
2. **Remember**: Wrap expensive calculations in `remember { }`
3. **Keys**: Provide stable keys when using with `items()`
4. **Recomposition**: Minimize state changes during dragging

### Accessibility

The component includes:
- Content descriptions for icons
- Semantic labels for time displays
- Touch target sizes following Material guidelines (48dp minimum)

### Troubleshooting

#### Handles not dragging smoothly
- Check if device is low on memory
- Reduce number of recompositions
- Profile with Layout Inspector

#### Validation not working
- Ensure `SegmentValidator` is imported
- Check if runtime seconds is correctly calculated
- Verify validation logic in `SegmentValidator.validate()`

#### Seek not working
- Verify player instance is not null
- Check time conversion (seconds to milliseconds)
- Ensure player is in correct state

#### Copy to clipboard fails
- Requires `ClipboardManager` - should work on all Android versions
- Check if permission is required on newer Android versions

### Testing

#### Unit Tests
```kotlin
@Test
fun testSegmentValidation() {
    val result = SegmentValidator.validate(
        startTime = 10.0,
        endTime = 20.0,
        duration = 100.0
    )
    assertTrue(result.isValid)
}
```

#### UI Tests
```kotlin
@Test
fun testDragHandle() {
    composeTestRule.onNode(hasTestTag("startHandle"))
        .performTouchInput {
            swipeRight(endX = 100f)
        }
    // Assert segment updated
}
```

## Keyboard Shortcuts (Future)

For tablet/device with keyboard support:
- `Arrow keys`: Adjust handle by 1 second
- `Shift + Arrow keys`: Adjust handle by 0.1 second
- `Space`: Play/pause
- `Enter`: Save changes
- `Delete`: Delete segment

## Known Limitations

1. **No inline save**: Changes trigger edit dialog instead of direct save
2. **No undo**: Cannot undo drag operations
3. **No multi-select**: Can only edit one segment at a time
4. **Desktop only keyboard**: No keyboard shortcuts on mobile devices

## Contributing

When modifying this component:

1. Maintain backward compatibility
2. Update this documentation
3. Add unit tests for new features
4. Test on multiple device sizes
5. Follow Material Design 3 guidelines
6. Keep accessibility in mind

## Support

For issues or questions:
1. Check existing GitHub issues
2. Review this documentation
3. Create a new issue with:
   - Device model and Android version
   - Steps to reproduce
   - Expected vs actual behavior
   - Screenshots if applicable
