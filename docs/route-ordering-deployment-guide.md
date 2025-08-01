# Route Ordering Feature - Deployment Guide

## Overview

This guide covers the deployment of the route ordering feature for LIPAS sports sites.

## Database Changes

### Schema Updates
The feature adds new optional fields to route documents:
- `segments`: Array of ordered segment references
- `ordering-method`: How the order was determined ("manual" or "algorithmic")

**No database migrations required** - these are additive changes to the JSON document structure.

## API Changes

### New Endpoint
- **POST** `/api/actions/suggest-route-order`
- Request: `{:lipas-id int, :activity-type keyword, :fids [string]}`
- Response: `{:segments [{:fid string, :direction string, :order int}], :confidence keyword, :warnings [string]}`

## Feature Flags

No feature flags implemented - the feature is available when deployed. Consider adding flags for:
- Gradual rollout by organization
- Activity type restrictions
- A/B testing

## Deployment Steps

1. **Deploy backend changes**:
   - New route namespace
   - Updated handler with new endpoint
   - Enhanced save logic

2. **Deploy frontend changes**:
   - New route editor component
   - Map visualization updates
   - Updated activities views

3. **Verify**:
   - Test algorithmic ordering endpoint
   - Verify map visualization
   - Test save functionality
   - Check backwards compatibility

## Rollback Plan

If issues arise:
1. Frontend can be rolled back independently
2. Backend maintains backwards compatibility
3. Routes with ordering can still be read by old versions

## Monitoring

### Key Metrics
- Algorithm performance (target: <100ms)
- Endpoint usage and errors
- Save success rates
- User engagement with ordering feature

### Logging
The following are logged:
- Route ordering requests with timing
- Validation failures
- Algorithm warnings
- Save operations

## Testing Checklist

Before deployment:
- [ ] Run all automated tests
- [ ] Test with routes of various sizes (5, 20, 50+ segments)
- [ ] Verify backwards compatibility
- [ ] Test on different activity types
- [ ] Check map visualization on different zoom levels
- [ ] Test drag-and-drop on mobile devices

## Known Limitations

1. **Performance**: Routes with >100 segments may be slower
2. **Mobile**: Drag-and-drop is desktop-optimized
3. **Offline**: Requires connection for algorithmic suggestions

## Support

### Common Issues
1. **Segments not connecting**: Check geometry data quality
2. **Slow ordering**: Consider route size limits
3. **Map not updating**: Refresh may be needed

### Debug Tools
- Browser console logs segment operations
- Backend logs include request details
- Check Elasticsearch for data integrity

## Future Enhancements

Consider for future releases:
1. Batch ordering for multiple routes
2. Route templates/presets
3. Mobile-optimized interface
4. Offline algorithm support
5. GPX export with ordered waypoints