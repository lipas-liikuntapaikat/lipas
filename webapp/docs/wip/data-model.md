# LIPAS Data Model

> **Status**: Draft needed

## Topics to Cover

- [ ] Sports sites (liikuntapaikat) - core entity
- [ ] Type codes and categorization hierarchy
- [ ] Properties system (type-specific fields)
- [ ] Locations of Interest (LOI)
- [ ] Routes (trails, tracks)
- [ ] Organizations and ownership
- [ ] Cities and regions
- [ ] Geometry types

## Key Files

- `src/cljc/lipas/data/` - Static data definitions
- `src/cljc/lipas/schema/` - Malli schemas

## Entity Relationships

```
Sports Site
├── Type (from type-codes hierarchy)
├── Properties (type-specific)
├── Location (geometry)
├── Owner (organization)
├── Operator (organization)
└── City
```

## Type Code Hierarchy

```
Main Category (e.g., 1000 = Ball sports)
└── Sub Category (e.g., 1100 = Football)
    └── Type (e.g., 1110 = Football field)
```

## Notes

<!-- Add notes here as you explore -->
