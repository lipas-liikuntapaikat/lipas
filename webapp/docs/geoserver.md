# LIPAS Legacy WFS (Geoserver) Documentation

## Overview

The LIPAS Legacy WFS (Web Feature Service) provides backward compatibility with the original WFS service implemented in 2012. This system maintains the exact layer structure, naming conventions, and data formats required by existing client applications and integrations.

### Purpose

The primary motivation for this implementation is to facilitate the retirement of the legacy server and database infrastructure while maintaining service continuity for dependent systems. This compatibility layer ensures that existing WFS clients can continue to operate without modification.

**Important**: This compatibility layer is maintained for legacy support purposes only. New implementations should follow contemporary API and naming standards.

## Architecture

### High-Level Design

The WFS system consists of several key components:

1. **Data Transformation Layer** (`lipas.wfs.core`): Converts LIPAS sports site data to legacy WFS format
2. **Field Mappings** (`lipas.wfs.mappings`): Handles property name translation and data type conversion
3. **Materialized Views**: PostgreSQL views that provide optimized data access for Geoserver
4. **Geoserver Integration**: REST API integration for layer management and publishing

### Data Flow

```
LIPAS Sports Sites (PostgreSQL)
          ↓
Data Transformation (lipas.wfs.core/->wfs-rows)
          ↓
Legacy Field Mapping (lipas.wfs.mappings)
          ↓
WFS Master Table (wfs.master)
          ↓
Materialized Views (per type-code)
          ↓
Geoserver WFS Layers
```

## Data Refresh Process

The `refresh-all!` function performs a complete data refresh in two stages:

### Stage 1: Master Table Refresh

The `refresh-wfs-master-table!` function:

1. **Truncates** the `wfs.master` table
2. **Iterates** through all sports site type codes
3. **Fetches** all active sites from the database for each type
4. **Transforms** each site using `->wfs-rows` (explodes multi-geometries into separate rows)
5. **Applies** legacy field mappings via `legacy-field->resolve-fn`
6. **Transforms** geometries from EPSG:4326 to EPSG:3067 coordinate system
7. **Inserts** processed data into `wfs.master` table

### Stage 2: Materialized Views Refresh

The `refresh-legacy-mat-views!` function:

1. **Queries** filtered data from `wfs.master` for specific type codes
2. **Applies** geometry casting (2D vs 3D variants for LineString types)
3. **Orders** fields using legacy field ordering for backwards compatibility
4. **Refreshes** all materialized views

## Field Mapping System

### Property Resolution

The system uses a two-tier mapping approach:

1. **Legacy Handle Mapping**: Maps old property handles to current property names
2. **Field Resolution**: Creates resolver functions for each legacy field

```clojure
;; Example mapping flow:
:lighting-info → :valaistuksen_lisätieto → (comp :lighting-info :properties :site)
```

### Common Fields

All WFS layers include these common fields:

- `id`: LIPAS site identifier
- `nimi_fi`, `nimi_se`, `nimi_en`: Site names in Finnish, Swedish, English
- `tyyppikoodi`: Type code
- `tyyppi_nimi_fi/se/en`: Type names in multiple languages
- `katuosoite`, `postinumero`, `postitoimipaikka`: Address information
- `kuntanumero`, `kunta_nimi_fi`: Municipality information
- `omistaja`, `yllapitaja`: Owner and administrator
- `the_geom`: Geometry in EPSG:3067

### Special Fields

- `reitti_id`: Auto-generated identifier for LineString features (1-based index)
- `alue_id`: Auto-generated identifier for Polygon features (1-based index)
- `kulkusuunta`: Travel direction for LineString features

## Type-Specific Configurations

### Field Mappings

Each type code has specific field mappings defined in `type-code->legacy-fields`:

```clojure
;; Example for Pulkkamäki (1190)
1190 #{:koulun_liikuntapaikka
       :valaistuksen_lisätieto
       :yleiso_wc
       :saa_julkaista_harrastuspassissa
       :valaistus}
```

### View Names

View names follow strict patterns based on geometry type:

- **Point types**: Single view `["lipas_{type-code}_{name}"]`
- **LineString types**: Dual views `["lipas_{type-code}_{name}", "lipas_{type-code}_{name}_3d"]`
- **Polygon types**: Single view `["lipas_{type-code}_{name}"]`

### Newly Added Type Codes (2024)

The following 9 type codes were added in 2024:

| Type Code | Name | Finnish Name | Geometry | View Name |
|-----------|------|--------------|----------|-----------|
| 1190 | Sledding hill | Pulkkamäki | Point | lipas_1190_pulkkamaki |
| 1650 | Golf course | Golfkenttä | Polygon | lipas_1650_golfkentta |
| 2225 | Indoor playground/activity park | Sisäleikki-/sisäaktiviteettipuisto | Point | lipas_2225_sisaleikki_sisaaktiviteettipuisto |
| 2620 | Billiard hall | Biljardisali | Point | lipas_2620_biljardisali |
| 3250 | Water sports center | Vesiurheilukeskus | Point | lipas_3250_vesiurheilukeskus |
| 4406 | Multi-use trail | Monikäyttöreitti | LineString | lipas_4406_monikayttoreitti, lipas_4406_monikayttoreitti_3d |
| 4407 | Roller ski track | Rullahiihtorata | LineString | lipas_4407_rullahiihtorata, lipas_4407_rullahiihtorata_3d |
| 4441 | Dog sledding route | Koiravaljakkoreitti | LineString | lipas_4441_koiravaljakkoreitti, lipas_4441_koiravaljakkoreitti_3d |
| 6150 | Oval track | Ovaalirata | Point | lipas_6150_ovaalirata |

## Materialized View Creation

Views are created dynamically using:

- **Type-specific fields** from `type-code->legacy-fields`
- **Common fields** (id, name, contact info, location, etc.)
- **Geometry handling** with proper EPSG:3067 projection
- **Data type casting** (text, integer, boolean, timestamp)

### Geometry Types

The system supports three geometry types with proper EPSG:3067 projection:

- **Point**: `geometry(Point,3067)` / `geometry(PointZ,3067)`
- **LineString**: `geometry(LineString,3067)` / `geometry(LineStringZ,3067)`
- **Polygon**: `geometry(Polygon,3067)` / `geometry(PolygonZ,3067)`

## Geoserver Integration

### Layer Publishing

Layer publishing involves:

1. **Publishing featuretype** from PostGIS materialized view
2. **Setting appropriate style** based on geometry type
3. **Configuring spatial bounds** for Finland (EPSG:3067)
4. **Managing layer groups** for category-based organization

### REST API Configuration

The system uses Geoserver's REST API for:

- Layer creation and deletion
- Style management
- Layer group organization
- Workspace configuration

### Layer Groups

Layers are organized into hierarchical groups:

- **Main category groups**: Based on type main categories (1000, 2000, etc.)
- **Sub-category groups**: Based on type sub-categories
- **All sites group**: Contains all published layers

## Deployment Process

### Manual Deployment Steps

Execute the following sequence via REPL:

```clojure
;; 1. Refresh master table
(refresh-wfs-master-table! db)

;; 2. Create new materialized views
(create-legacy-mat-views! db)

;; 3. Publish new Geoserver layers
;; For each new view, use publish-layer function
(publish-layer "lipas_1190_pulkkamaki" "lipas_1190_pulkkamaki" "Point")
;; ... repeat for all new views

;; 4. Update layer groups (if needed)
(rebuild-all-legacy-layer-groups)
```

### Verification

After deployment, verify:

1. All new materialized views exist in PostgreSQL
2. All new layers are published in Geoserver
3. Layer groups are properly organized
4. WFS GetCapabilities shows new layers
5. Sample WFS requests return expected data

## Backwards Compatibility

### Legacy Field Names

The system maintains exact legacy field names, including:

- Finnish characters (ä, ö) in field names
- Underscores instead of hyphens
- Specific abbreviations (e.g., `lkm` for count, `m2` for square meters)

### Data Type Compatibility

- **Enums**: Converted to Finnish labels
- **Boolean**: Mapped to legacy boolean representation
- **Numbers**: Proper integer/float typing
- **Dates**: Helsinki timezone timestamps

### Coordinate System

All geometries are transformed to EPSG:3067 (Finnish national coordinate system) as required by legacy clients.

## Performance Considerations

### Materialized Views

- Views are materialized for performance
- Refresh required after data changes
- Indexed on common query fields

### Geometry Processing

- Efficient multi-geometry to single-geometry conversion
- Proper spatial indexing
- EPSG transformation optimized

## Maintenance

### Adding New Type Codes

To add support for new type codes:

1. **Define legacy field mappings** in `type-code->legacy-fields`
2. **Define view names** in `type-code->view-names`
3. **Verify property mappings** in `legacy-field->resolve-fn`
4. **Test WFS row generation** with sample data
5. **Execute deployment process**

### Troubleshooting

Common issues:

- **Missing resolvers**: Check `legacy-field->resolve-fn` for missing property mappings
- **Geometry errors**: Verify EPSG:3067 transformation
- **Field type mismatches**: Check data type casting in view creation
- **Geoserver errors**: Verify REST API connectivity and authentication

## Security Considerations

- WFS layers are read-only
- Access controlled through Geoserver security
- No sensitive data exposed in legacy format
- Audit trail maintained in master table

## Related Files

- `src/clj/lipas/wfs/core.clj`: Core WFS functionality
- `src/clj/lipas/wfs/mappings.clj`: Field mappings and view definitions
- `test/clj/lipas/wfs/core_test.clj`: Test suite with examples
- `src/clj/lipas/integration/old_lipas/sports_site.clj`: Legacy property mappings

## API Reference

### Key Functions

- `refresh-all! [db]`: Complete data refresh
- `->wfs-rows [sports-site]`: Convert site to WFS format
- `publish-layer [feature-name publish-name geom-type]`: Publish to Geoserver
- `create-legacy-mat-views! [db]`: Create materialized views
- `rebuild-all-legacy-layer-groups []`: Rebuild layer groups

### Configuration

- Geoserver connection: `geoserver-config`
- Field ordering: `field-order`
- Layer groups: `main-category-layer-groups`, `sub-category-layer-groups`

This documentation provides a comprehensive overview of the LIPAS Legacy WFS system, its architecture, data processing, and maintenance procedures. The system successfully maintains backward compatibility while leveraging modern infrastructure and practices.