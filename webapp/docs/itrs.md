# ITRS (International Trail Rating System) Implementation

## Overview

LIPAS supports the International Trail Rating System (ITRS) for classifying cycling route difficulty. ITRS provides standardized, internationally recognized difficulty ratings for mountain biking trails.

**Status:** ✅ Complete and working  
**Implementation Date:** October 2025

## What is ITRS?

ITRS is a standardized trail difficulty classification system that provides consistent ratings across different regions and countries. It uses four dimensions to describe trail characteristics:

### Route-Level Classification

Applied to entire routes in the route properties:

- **Endurance (1-5):** Physical fitness requirement
  - 1 = Very short (< 30 min)
  - 2 = Short (30-60 min)
  - 3 = Medium (1-2 hours)
  - 4 = Long (2-4 hours)
  - 5 = Very long (> 4 hours)

- **Wilderness (1-4):** Remoteness from services
  - 1 = Urban proximity
  - 2 = Rural
  - 3 = Remote
  - 4 = Very remote

### Segment-Level Classification

Applied to individual route segments on the map:

- **Technical (0-5):** Technical difficulty
  - 0 = No technical challenges (smooth surface)
  - 1 = Very easy
  - 2 = Easy
  - 3 = Intermediate
  - 4 = Difficult
  - 5 = Extremely difficult

- **Exposure (1-4):** Fall risk and danger
  - 1 = Low exposure (little fall risk)
  - 2 = Moderate exposure
  - 3 = High exposure
  - 4 = Extreme exposure (life-threatening)

**More information:** https://itrs.bike/

## How to Use ITRS in LIPAS

### Setting Route-Level ITRS Properties

1. Edit a cycling route (type 4411 or 4412)
2. In the route properties (Ulkoilutietopalvelu tab), find:
   - **ITRS Kesto** dropdown (Endurance 1-5)
   - **ITRS Erämaa** dropdown (Wilderness 1-4)
3. Select appropriate values
4. Save the route

These fields appear alongside the existing "Haastavuus" field - you can use ITRS, the old system, or both.

### Setting Segment-Level ITRS Ratings

1. Edit the route geometry (enter map editing mode)
2. Open the "More tools" menu (three dots icon)
3. Select **"ITRS-luokitus"** from the menu
4. Click on any route segment on the map
5. A popup appears with two dropdowns:
   - **ITRS Teknisyys** (Technical 0-5)
   - **ITRS Altistuminen** (Exposure 1-4)
6. Select values for the segment
7. The segment label updates on the map: "ITRS: T3 / E2"
8. Save the route when done

## Relationship with Existing Difficulty Fields

ITRS works **alongside** the existing difficulty classification system:

**Existing fields (still supported):**
- Route-level: "Haastavuus" dropdown (1-5 difficulty)
- Route-level: "Haastavuus" text field (free-form description)
- Segment-level: "Reittiosan vaativuus" (1a-5 difficulty per segment)

**New ITRS fields:**
- Route-level: ITRS Kesto (1-5) and ITRS Erämaa (1-4)
- Segment-level: ITRS Teknisyys (0-5) and ITRS Altistuminen (1-4)

**You can:**
- Use only the old system
- Use only ITRS
- Use both systems together

No existing data has been changed or migrated. Routes without ITRS values continue to work exactly as before.

## Technical Implementation

### Data Storage

**Route properties:**
```json
{
  "routes": [{
    "route-name": "Example Trail",
    "itrs-endurance": "3",
    "itrs-wilderness": "2",
    "geometries": {
      "type": "FeatureCollection",
      "features": [...]
    }
  }]
}
```

**Segment (geometry feature) properties:**
```json
{
  "type": "Feature",
  "geometry": {
    "type": "LineString",
    "coordinates": [[...]]
  },
  "properties": {
    "itrs-technical": "3",
    "itrs-exposure": "2"
  }
}
```

### Files Modified

1. **Data definitions:** `src/cljc/lipas/data/activities.cljc`
   - Added `itrs-technical-options` (0-5, with labels and descriptions)
   - Added `itrs-exposure-options` (1-4, with labels and descriptions)
   - Added `itrs-endurance-options` (1-5)
   - Added `itrs-wilderness-options` (1-4)
   - Updated cycling route schema with ITRS fields

2. **Segment schema:** `src/cljc/lipas/schema/sports_sites/location.cljc`
   - Added `:itrs-technical` and `:itrs-exposure` to line-string feature properties

3. **Map editing:** `src/cljs/lipas/ui/map/editing.cljs`
   - Added `set-itrs-segment-edit-mode` function
   - Handles ITRS segment selection and popup positioning

4. **UI component:** `src/cljs/lipas/ui/map/views.cljs`
   - Added `itrs-segment` UIx component with dual dropdowns
   - Added ITRS tool to map tools menu
   - Added case clauses for tooltip and icon

5. **Event handlers:** `src/cljs/lipas/ui/map/events.cljs`
   - Added `::set-itrs-technical` event
   - Added `::set-itrs-exposure` event

6. **Map styling:** `src/cljs/lipas/ui/map/styles.cljs`
   - Added `itrs-segment-style-fn` for displaying "ITRS: TX / EX" labels

7. **Translations:** `src/cljc/lipas/i18n/{fi,se,en}/map.edn`
   - Finnish, Swedish, and English labels for all ITRS fields

### Implementation Pattern

The ITRS implementation follows the existing `route-part-difficulty` pattern:
- Segment-level editing on the map
- Popup-based value selection
- Real-time label updates
- Property storage in geometry features

## Known Limitations

1. **No conditional visibility:** The ITRS segment tool appears for all cycling routes, not just those using ITRS classification

2. **No automatic migration:** Existing routes with "Reittiosan vaativuus" values are not automatically converted to ITRS

3. **No validation:** The system doesn't validate logical consistency (e.g., warning if Technical is T5 but Exposure is E1)

4. **Map label language:** Segment labels on the map show "ITRS: TX / EX" format regardless of UI language

## Future Enhancement Ideas

- Conditional menu display (only show ITRS tool for routes using ITRS)
- Migration helper to convert legacy difficulty values to ITRS
- Validation warnings for inconsistent ratings
- Summary statistics showing route ITRS range (e.g., "T2-T4, E1-E2")
- Visual difficulty heatmap on route overview

## Testing Checklist

- [x] ITRS Kesto and Erämaa dropdowns appear in route properties
- [x] Dropdown values render correctly
- [x] ITRS-luokitus menu item appears for cycling routes
- [x] Map segment editing works (click segment → popup appears)
- [x] Technical and Exposure dropdowns function correctly
- [x] Segment labels update in real-time
- [x] Values persist after save and reload
- [x] Old difficulty system still works unchanged
- [x] Multi-language support (Finnish, Swedish, English)

## References

- **ITRS Official:** https://itrs.bike/
- **Implementation Plan:** `/docs/itrs-implementation-plan.md` (technical details and testing report)

---

**Last Updated:** October 2025  
**Maintained by:** LIPAS Development Team
