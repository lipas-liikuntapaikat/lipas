# Route Ordering Feature - User Guide

## Overview

The route ordering feature allows you to define the exact order and direction in which segments should be traversed in a route. This is particularly useful for:
- Creating guided routes with a specific start and end
- Defining circular routes with a recommended direction
- Creating complex routes that use some segments multiple times

## How to Use

### Creating a New Ordered Route

1. **Add a new route**: Click "Add sub-route" in the activities section
2. **Select segments**: Click on map segments to add them to your route
3. **Switch to ordering mode**: Click "Order segments" to arrange them
4. **Arrange segments**:
   - Drag segments up/down to change order
   - Click arrow buttons to reverse direction
   - Click copy button to use a segment multiple times
   - Click delete to remove segments
5. **Save**: Click save when satisfied with the order

### Ordering an Existing Route

1. **Select a route** from the route list
2. **Click "Order segments"** button
3. **Use algorithmic suggestion** (optional):
   - Click "Load Real Route" to get AI-suggested ordering
   - Review and adjust as needed
4. **Manually adjust**:
   - Drag to reorder
   - Toggle directions
   - Add/remove segments
5. **Save changes**

## Visual Indicators

### On the Map
- **Numbers**: Show the order (1, 2, 3...)
- **Arrows**: Show traversal direction
- **Red segments**: Indicate segments used multiple times

### In the List
- **Order numbers**: Clear sequence indicators
- **Direction arrows**: Forward (→) or Backward (←)
- **Drag handles**: For reordering

## Features

### Algorithmic Ordering
The system can suggest an optimal order based on segment connectivity:
- **High confidence**: Well-connected route
- **Medium confidence**: Some disconnected segments
- **Low confidence**: Many issues found

### Manual Override
You always have full control to:
- Reorder segments by dragging
- Change direction of individual segments
- Use segments multiple times
- Create any route pattern you need

## Tips

1. **Start with algorithmic suggestion**: Let the system propose an initial order
2. **Check the map**: Visual numbers help verify the route makes sense
3. **Test the route**: Imagine following it to ensure it's logical
4. **Use repetition wisely**: Some routes may need to traverse a segment twice

## Backwards Compatibility

Routes created with ordering are fully compatible with older systems:
- The `fids` field is automatically maintained
- Older clients will see all segments (unordered)
- No data is lost when switching between versions

## Activity-Specific Behavior

Different activity types may have different defaults:
- **Cycling routes**: Often benefit from algorithmic ordering
- **Hiking trails**: May have specific waypoint requirements
- **Ski tracks**: Direction matters for difficulty ratings

## Troubleshooting

### "Disconnected segments" warning
Some segments don't connect to others. The algorithm places these at the end.

### "Multiple segments reversed" warning
Many segments need to be traversed backwards. Consider if the route direction is correct.

### Segments not showing on map
Ensure you're in the correct editing mode and segments are properly selected.

## Technical Details

- Performance: Ordering algorithm typically completes in <100ms
- Supports routes with 100+ segments
- All changes are saved to the database
- Full audit trail maintained