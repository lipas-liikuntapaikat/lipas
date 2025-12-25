# LIPAS Data Model Explorer - Feature Plan

## Overview

The LIPAS Data Model Explorer is an interactive component designed to help users understand and navigate the complex relationships between sports facility types, properties, activities, and locations of interest (LOI) in the LIPAS system. This tool will make the data model accessible to both technical and non-technical users through intuitive navigation, search capabilities, and comprehensive export options.

## Key Objectives

1. Provide clear visualization of hierarchical type code system
2. Enable exploration of property definitions and their usage
3. Show relationships between types, properties, and activities
4. Support data export for documentation and analysis
5. Offer multilingual support (Finnish, Swedish, English)

## Core Components

### 1. Type Code Explorer

#### Features
- **Hierarchical Tree View**
  - Main categories (0: Recreational, 1000: Outdoor fields, 2000: Indoor facilities, 3000: Water sports, 4000: Outdoor routes)
  - Sub-categories under each main category
  - Individual type codes with metadata
  - Visual indicators for geometry types (point/line/polygon)

- **Property Integration**
  - Badge showing count of applicable properties
  - Expandable property list for each type
  - Direct links to property definitions

- **Smart Search**
  - Search across type names and descriptions
  - Auto-expand tree to show matches
  - Highlight matching text

#### Export Options
- Complete type hierarchy with all levels
- Types filtered by main/sub-category
- Types with their associated properties
- Custom selection of types

### 2. Property Browser

#### Features
- **Organized List View**
  - Group by data type (numeric, boolean, enum, text, etc.)
  - Sort by name, usage count, or data type
  - Filter by property characteristics

- **Detailed Property Information**
  - Name in all languages
  - Description and usage guidelines
  - Data type and constraints
  - Unit of measurement (if applicable)
  - Enum values (for enum types)

- **Usage Analysis**
  - List of all types using each property
  - Usage frequency statistics
  - Required vs optional indicator

#### Export Options
- All properties with full details
- Properties grouped by data type
- Property usage matrix (properties × types)
- Properties for specific type selections

### 3. Activity Explorer

#### Features
- **Category Navigation**
  - Outdoor recreation areas
  - Outdoor recreation facilities
  - Outdoor recreation routes
  - Cycling, paddling, fishing activities

- **Field Schema Browser**
  - View activity-specific fields
  - Understand data requirements
  - See field types and validation rules

- **Relationship Mapping**
  - Which types support which activities
  - Activity-specific properties

#### Export Options
- Activity definitions and schemas
- Activities by category
- Type-to-activity mapping

### 4. LOI (Location of Interest) Browser

#### Features
- **Two-Level Hierarchy**
  - Categories (e.g., structures, natural sites)
  - Types within categories
  - Status indicators

- **Property Mapping**
  - Common properties (name, description, images)
  - Type-specific properties
  - Geometry requirements

#### Export Options
- LOI type hierarchy
- LOI properties and schemas
- Complete LOI data model documentation

## User Interface Design

### Layout Structure

```
┌─────────────────────────────────────────────────────────────┐
│  Navigation Tabs:  [Types] [Properties] [Activities] [LOIs]  │
├─────────────────────────────────────────────────────────────┤
│  Search: [_______________] 🔍  Language: [FI|SE|EN]  Export  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┬─────────────────────────────────┐  │
│  │                     │                                   │  │
│  │   Tree/List View    │      Detail Panel                │  │
│  │                     │                                   │  │
│  │  ▼ Main Category    │   Selected Item Details         │  │
│  │    ▼ Sub Category   │                                   │  │
│  │      • Type 1234    │   Properties, descriptions,      │  │
│  │      • Type 1235    │   relationships, etc.            │  │
│  │                     │                                   │  │
│  └─────────────────────┴─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Interactive Features

1. **Navigation**
   - Click to select and view details
   - Breadcrumb trail for current location
   - Keyboard navigation support
   - Back/forward history

2. **Search Functionality**
   - Real-time filtering as you type
   - Search across names and descriptions
   - Advanced search with filters
   - Search result highlighting

3. **Cross-References**
   - Clickable links between related items
   - "Used by" and "Uses" relationships
   - Quick navigation between sections

## Excel Export Functionality

### Export Templates

#### 1. Type Hierarchy Export
```
Columns:
- Main Category Code
- Main Category Name (FI/SE/EN)
- Sub Category Code
- Sub Category Name (FI/SE/EN)
- Type Code
- Type Name (FI/SE/EN)
- Description (FI/SE/EN)
- Geometry Type
- Property Count
- Supports Activities
- Status
```

#### 2. Property Definitions Export
```
Columns:
- Property Key
- Property Name (FI/SE/EN)
- Description (FI/SE/EN)
- Data Type
- Unit
- Enum Values (if applicable)
- Used by Type Count
- Used by Types (comma-separated)
- Priority
- Required/Optional
```

#### 3. Type-Property Matrix Export
```
Rows: Type Codes
Columns: Property Keys
Values: ✓ (if property applies to type)
Additional columns: Type Name, Category, Geometry Type
```

#### 4. Complete Data Model Export
Multiple sheets:
- Types (full hierarchy)
- Properties (all definitions)
- Type-Property Mapping
- Activities
- LOIs
- Enum Value Lists

#### 5. Custom Selection Export
- User-selected types/properties
- Filtered by categories or characteristics
- Include/exclude specific fields

### Export Features

1. **Format Options**
   - Excel (.xlsx) with multiple sheets
   - CSV for simple lists
   - JSON for technical users

2. **Customization**
   - Select language for export
   - Choose fields to include
   - Apply current filters to export
   - Include/exclude inactive items

3. **Documentation Export**
   - Generate readable documentation
   - Include examples and descriptions
   - Create data dictionary format

## Technical Implementation

### Re-frame Architecture

#### Application State Structure
```clojure
{:data-model-explorer
 {:ui {:selected-tab :types
       :selected-item-id nil
       :search-query ""
       :expanded-nodes #{}
       :language :fi
       :filters {:show-inactive? false
                 :main-category nil
                 :data-type nil}
       :export-dialog {:open? false
                       :selected-template :type-hierarchy
                       :options {}}}
  
  :data {:types (types/all)
         :properties (prop-types/all)
         :activities (activities/all)
         :lois (loi/all)
         
         ;; Computed indices for performance
         :type-hierarchy (build-hierarchy types/all)
         :properties-by-type {}
         :types-using-property {}
         :search-index {}}}}
```

#### Key Events
```clojure
;; Navigation
::select-tab [tab]
::select-item [item-id]
::toggle-node [node-id]

;; Search and filter
::update-search [query]
::update-filter [filter-key value]
::clear-filters

;; Export
::open-export-dialog
::select-export-template [template]
::configure-export [options]
::execute-export
```

#### Key Subscriptions
```clojure
;; UI State
::current-tab
::selected-item
::search-results
::filtered-items

;; Data queries
::type-hierarchy
::properties-list
::item-details
::related-items

;; Export
::export-data
::export-preview
```

### Component Structure

```clojure
;; Main component
[data-model-explorer
 [explorer-header {:on-search update-search
                   :on-language-change set-language
                   :on-export open-export-dialog}]
 [explorer-tabs {:selected current-tab
                 :on-select select-tab}]
 [explorer-content
  [split-pane
   [navigation-panel {:items filtered-items
                      :selected selected-item
                      :on-select select-item}]
   [detail-panel {:item item-details
                  :on-navigate navigate-to-item}]]]
 [export-dialog {:open? export-dialog-open?
                 :on-close close-export-dialog
                 :on-export execute-export}]]
```

### Performance Considerations

1. **Data Indexing**
   - Pre-compute hierarchies on load
   - Build search indices
   - Cache computed relationships

2. **UI Optimization**
   - Virtual scrolling for large lists
   - Lazy loading of details
   - React.memo for static components

3. **Export Optimization**
   - Stream large exports
   - Progress indication
   - Background processing for large datasets

## Implementation Phases

### Phase 1: Core Navigation (Week 1-2)
- Basic UI layout and navigation
- Type hierarchy browser
- Property list view
- Basic detail panels

### Phase 2: Search and Filtering (Week 3)
- Search implementation
- Advanced filters
- Result highlighting
- Cross-reference navigation

### Phase 3: Export Functionality (Week 4-5)
- Excel export templates
- Custom export configuration
- Export preview
- Multi-format support

### Phase 4: Polish and Optimization (Week 6)
- Performance optimization
- UI/UX improvements
- Documentation
- Testing and bug fixes

## Success Metrics

1. **Usability**
   - Users can find type/property information within 3 clicks
   - Search returns relevant results
   - Export generates useful documentation

2. **Performance**
   - Initial load under 2 seconds
   - Search results appear within 100ms
   - Export generation under 5 seconds for full dataset

3. **Adoption**
   - Used by both technical and non-technical staff
   - Reduces support questions about data model
   - Becomes primary reference for data structure

## Future Enhancements

1. **Visualization**
   - Graphical relationship diagrams
   - Property usage heatmaps
   - Type hierarchy visualization

2. **Integration**
   - Direct links from main LIPAS application
   - API endpoint documentation
   - Code generation for developers

3. **Collaboration**
   - Comments on types/properties
   - Change history tracking
   - Proposal system for new types

## Dependencies

- Re-frame for state management
- Reagent for UI components
- cljs-ajax for data loading
- Excel export library (e.g., sheetjs wrapper)
- UI component library (existing LIPAS components)

## Conclusion

The LIPAS Data Model Explorer will significantly improve the accessibility and understanding of the LIPAS data structure. By providing intuitive navigation, comprehensive search, and flexible export options, it will serve as both a learning tool for new users and a reference tool for experienced users. The modular architecture ensures maintainability and allows for future enhancements as the data model evolves.
