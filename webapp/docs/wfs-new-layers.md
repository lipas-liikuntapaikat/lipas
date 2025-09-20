# Adding new type-codes to legacy WFS (Geoserver)

Goal: add support for these new type codes

;; 1190 Pulkkamäki
;; 1650 Golfkenttä (alue)
;; 2225 Sisäleikki- / sisäaktiviteettipuisto
;; 2620 Biljardisali
;; 3250 Vesiurheilukeskus
;; 4406 Monikäyttöreitti
;; 4407 Rullahiihtorata
;; 4441 Koiravaljakkoreitti
;; 6150 Ovaalirata

Extra goal: have hiqh-quality documentation of the legacy WFS functionality, including background, purpose and high-level impl description. This documentation should be created as a last step and written to webapp/docs/geoserver.md.

## Current implementation

- WFS layers are maintained as materialized views in Postgresql/Postgis
- Geoserver publishes layers from the materialized views
- lipas.wf.score namespace contains admin utilities for recreating the materialized views programmatically
- lipas.wfs.core namespace contains admin utilities for publishing the layers programmatically using Geoserver REST api
- implementation is data-driven

Data refresh process:

The `refresh-all!` function performs a complete data refresh in two stages:

1. **refresh-wfs-master-table!**: Truncates and repopulates the `wfs.master` table
   - Iterates through all sports site type codes
   - For each type, fetches all active sites from the database
   - Transforms each site using `->wfs-rows` (explodes multi-geometries into separate rows)
   - Applies legacy field mappings via `legacy-field->resolve-fn`
   - Transforms geometries from EPSG:4326 to EPSG:3067 coordinate system
   - Inserts processed data into `wfs.master` table

2. **refresh-legacy-mat-views!**: Refreshes all materialized views
   - Each view queries filtered data from `wfs.master` for specific type codes
   - Applies geometry casting (2D vs 3D variants for LineString types)
   - Orders fields using legacy field ordering for backwards compatibility

### Namespaces

- lipas.wfs.core
- lipas.wfs.mappings

### Tests

- lipas.wfs.core-test

See the (comment) block at the bottom of the test-file for examples how to run individual tests.

## Problem

Geoserver WFS layers are legacy. They've been maintained "as is" for over a decade. Backwards compatibility is extremely important. Design choices of previous iterations of LIPAS-systems are visible in Finnish prop names etc. Mappings from legacy-props to current props exist in mappings.clj.

## Plan

### Overview

The implementation requires adding support for 9 new type codes to the legacy WFS system. All type codes already exist in `lipas.data.types-new` but need WFS mappings defined.

**New type codes to add:**
- **1190** Pulkkamäki (Point)
- **1650** Golfkenttä (Polygon) 
- **2225** Sisäleikki-/sisäaktiviteettipuisto (Point)
- **2620** Biljardisali (Point)
- **3250** Vesiurheilukeskus (Point)
- **4406** Monikäyttöreitti (LineString)
- **4407** Rullahiihtorata (LineString)
- **4441** Koiravaljakkoreitti (LineString)
- **6150** Ovaalirata (Point)

### Implementation Steps

#### Step 1: Define Legacy Field Mappings
Add entries to `type-code->legacy-fields` map in `lipas.wfs.mappings` namespace for each new type code. This involves:

1. **Analyze existing properties** for each type code using `lipas.data.types-new`
2. **Map properties to legacy field names** using existing patterns from similar types
3. **Determine geometry-specific fields** (reitti_id for LineString, alue_id for Polygon)
4. **Include common fields** (all types get common-fields automatically)

#### Step 2: Define View Names
Add entries to `type-code->view-names` map in `lipas.wfs.mappings` namespace:

1. **Point types**: Single view name `["lipas_{type-code}_{name}"]`
2. **LineString types**: Dual views `["lipas_{type-code}_{name}", "lipas_{type-code}_{name}_3d"]`  
3. **Polygon types**: Single view name `["lipas_{type-code}_{name}"]`

#### Step 3: Property Mapping Setup
Update property resolution in `legacy-field->resolve-fn` map if needed:

1. **Check existing property mappings** in `legacy-handle->prop`
2. **Add missing property mappings** for new properties not yet supported
3. **Verify enum/enum-coll handling** for properties with predefined values

#### Step 4: Testing & Verification
Use REPL helpers to test the implementation:

1. **Test WFS row generation** with sample sites of each new type
2. **Verify field mappings** produce correct legacy field names and values
3. **Check geometry handling** for each geometry type
4. **Validate view generation** produces correct SQL

#### Step 5: Manual Deployment
Execute the following sequence manually via REPL:

1. **Refresh master table**: `(refresh-wfs-master-table! db)`
2. **Create new materialized views**: `(create-legacy-mat-views! db)` 
3. **Publish new Geoserver layers**: Use `publish-layer` for each new view
4. **Update layer groups**: Re-run `rebuild-all-legacy-layer-groups` if needed

### Technical Details

#### Data Refresh Process Analysis
The `refresh-all!` function works as follows:

1. **Truncates `wfs.master` table** and repopulates from live sports sites data
2. **Transforms each sports site** using `->wfs-rows` function
3. **Applies field mappings** via `legacy-field->resolve-fn` lookup
4. **Refreshes all materialized views** based on the updated master table
5. **Maintains geometry transformations** (4326 → 3067 coordinate system)

#### Materialized View Creation
Views are created dynamically using:
- **Type-specific fields** from `type-code->legacy-fields`
- **Common fields** (id, name, contact info, location, etc.)
- **Geometry handling** with proper EPSG:3067 projection
- **Data type casting** (text, integer, boolean, timestamp)

#### Geoserver Integration  
Layer publishing involves:
- **Publishing featuretype** from PostGIS materialized view
- **Setting appropriate style** based on geometry type
- **Configuring spatial bounds** for Finland (EPSG:3067)
- **Managing layer groups** for category-based organization

### Risk Mitigation
- **Backwards compatibility preserved** - no changes to existing layers
- **Data-driven approach** - mappings defined in code, not hardcoded
- **Manual execution** - controlled deployment without automation risks
- **Rollback capability** - can drop new views if issues arise

### Success Criteria
1. All 9 new type codes have functioning WFS layers
2. Legacy field names and values match expected format
3. Geometry types correctly handled (Point/LineString/Polygon)
4. Geoserver layers published and accessible
5. No impact on existing WFS functionality
