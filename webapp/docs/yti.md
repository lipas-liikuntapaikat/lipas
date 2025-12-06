# LIPAS Integration with Yhteentoimivuusalusta (YTI)

This document describes the LIPAS data model publication to Finland's national [Yhteentoimivuusalusta](https://dvv.fi/en/interoperability-platform) (Interoperability Platform).

## Overview

The YTI platform, maintained by DVV (Digital and Population Data Services Agency), is Finland's national infrastructure for semantic interoperability. Publishing LIPAS here makes it the **first sports/recreation facility data model** in Finland's national registry.

### Platform Components

| Tool | Finnish Name | Purpose | LIPAS Usage |
|------|--------------|---------|-------------|
| [Koodistot](https://koodistot.suomi.fi/) | Code Lists | Classifications and enumerations | Type codes, owners, admins |
| [Sanastot](https://sanastot.suomi.fi/) | Vocabularies | Terminology and concepts | Attribute terminology links |
| [Tietomallit](https://tietomallit.suomi.fi/) | Data Models | Data structure specifications | Full data model |

## Current Status

### Published Code Lists (DRAFT)

All code lists are in the `educ` (Education) registry as proof-of-concept. They should be moved to a Sports/Recreation registry for production use.

| Code List | URL | Codes | Status |
|-----------|-----|-------|--------|
| LIPAS Liikuntapaikkatyypit | [lipas-type-codes](https://koodistot.suomi.fi/codescheme;registryCode=educ;schemeCode=lipas-type-codes) | 177 | DRAFT |
| LIPAS Ylläpitäjätyypit | [lipas-admin-types](https://koodistot.suomi.fi/codescheme;registryCode=educ;schemeCode=lipas-admin-types) | 11 | DRAFT |
| LIPAS Omistajatyypit | [lipas-owner-types](https://koodistot.suomi.fi/codescheme;registryCode=educ;schemeCode=lipas-owner-types) | 9 | DRAFT |
| LIPAS Liikuntapaikan tilat | [lipas-statuses](https://koodistot.suomi.fi/codescheme;registryCode=educ;schemeCode=lipas-statuses) | 6 | DRAFT |
| LIPAS Pintamateriaalit | [lipas-surface-materials](https://koodistot.suomi.fi/codescheme;registryCode=educ;schemeCode=lipas-surface-materials) | 24 | DRAFT |

**Total: 227 codes across 5 code lists**

### Published Data Model (DRAFT)

**URL**: https://tietomallit.suomi.fi/model/lipasdev9

| Component | Count | Description |
|-----------|-------|-------------|
| Classes | 186 | 1 core (LiikuntaPaikka) + 8 main categories + 28 sub-categories + 149 types |
| Attributes | 195 | 21 core (incl. 6 embedded location attrs) + 174 type-specific |
| Property assignments | 1730 | Type-specific attributes → type classes |
| Code lists | 5 | statuses, types, owners, admins, municipalities |

### Pending

- [ ] Move code lists to correct registry (Sports/Recreation instead of Education)
- [ ] Change status from DRAFT to VALID (requires DVV approval)
- [ ] Get DVV approval for production prefix `lipas`

## Data Model Design

### Core Entity: LiikuntaPaikka (Sports Facility)

The central concept is **LiikuntaPaikka** (Sports Facility). The model uses a 4-level class hierarchy that mirrors the LIPAS type code structure:

```
Level 1: LiikuntaPaikka (base class)
│
Level 2: Main Categories (8)
├── 1000 Ulkokentät ja -alueet
├── 2000 Sisäliikuntapaikat
├── 3000 Vesiliikuntapaikat
├── 4000 Maastoliikuntapaikat
├── 5000 Veneily, ilmailu ja moottoriurheilu
├── 6000 Eläinurheilualueet
├── 7000 Huoltorakennukset
└── 0    Virkistyskohteet ja palvelut
│
Level 3: Sub Categories (28)
│   Examples: Palloilukentät, Uimahallit, Hiihtokeskukset
│
Level 4: Type Codes (149)
    Examples: Uimahalli (3110), Jalkapallostadion (1320)
```

### Type-Specific Properties

LIPAS has 174 type-specific properties from `lipas.data.prop-types`. Each type class has only its relevant properties:

- Swimming pools have `poolTemperatureC`, `poolTracksCount`
- Ice rinks have `iceRinksCount`, `iceReduction`
- Hiking trails have `routeLengthKm`, `surfaceMaterial`

### Location: Embedded Attributes

Location is embedded directly into LiikuntaPaikka (not a separate class):

| Attribute | Description |
|-----------|-------------|
| `locationAddress` | Street address |
| `locationPostalCode` | Postal code |
| `locationPostalOffice` | Post office name |
| `locationCityCode` | Municipality code (links to jhs/kunta) |
| `locationNeighborhood` | Neighborhood name |
| `locationGeometry` | GeoSPARQL geometry |

### External References

The model references:
- **isa2core** (EU Core Vocabularies) for Address, Location, ContactPoint classes
- **GeoSPARQL** for geometry types
- **Sanastot terminology** for attribute concepts (e.g., `name` → `eucore-fi/c91`)
- **Koodistot code lists** for classifications

## Implementation

### Module Structure

```
src/clj/lipas/integration/yti/
├── schema.clj            ; Malli schemas for validation
├── discovery.clj         ; API client for searching YTI
├── koodistot.clj         ; Excel export for code list imports
├── tietomallit.clj       ; API client for model creation
└── data/
    ├── concept-mappings.edn  ; Discovered mappings to external models
    └── model-spec.edn        ; Declarative model specification
```

### Usage

```clojure
(require '[lipas.integration.yti.tietomallit :as tietomallit])

;; Dry run - see what would be created
(def result (tietomallit/sync-model! :dry-run true))

;; Full sync with progress reporting
(def results (tietomallit/sync-model-with-progress!))

;; Partial sync (without 149 type classes, faster for testing)
(tietomallit/sync-model-with-progress! :include-types false)

;; Print summary
(tietomallit/print-sync-summary results)
```

### Code List Export

```clojure
(require '[lipas.integration.yti.koodistot :as koodistot])

;; Export type codes (hierarchical: main → sub → types)
(koodistot/export-to-registry-excel "/path/to/lipas-types.xlsx")

;; Export flat code lists
(koodistot/export-admin-types-excel "/path/to/admin-types.xlsx")
(koodistot/export-owner-types-excel "/path/to/owner-types.xlsx")
(koodistot/export-status-types-excel "/path/to/statuses.xlsx")
(koodistot/export-surface-materials-excel "/path/to/surfaces.xlsx")
```

### Discovery

```clojure
(require '[lipas.integration.yti.discovery :as discovery])

;; Search for related data models
(discovery/search-models "sijainti")

;; Search for terminology concepts
(discovery/search-concepts "nimi")

;; Get recommended terminology subjects for attributes
(discovery/get-recommended-subjects)
```

## API Reference

### Authentication

```
Authorization: Bearer ${YTI_API_KEY}
Content-Type: application/json
```

The API key is configured as environment variable `YTI_API_KEY`.

### Key Endpoints

| Endpoint | Purpose |
|----------|---------|
| `POST /v2/model/library` | Create new model |
| `POST /v2/class/library/{prefix}` | Add class to model |
| `POST /v2/resource/library/{prefix}/attribute` | Add attribute |
| `PUT /v2/class/library/{prefix}/{class}/properties` | Add property to class |
| `PUT /v2/class/library/{prefix}/{class}/codeList` | Link code list to attribute |
| `DELETE /v2/model/{prefix}` | Delete draft model |

### API Quirks

1. **Service category format**: Use identifier only (`"P27"`), not full URN
2. **Language completeness**: All labels must include fi, en, sv
3. **Swedish code**: Use `sv` (ISO 639-1), not `se`
4. **Class references**: Use full URI format: `https://iri.suomi.fi/model/{prefix}/{class}`
5. **XSD types**: Use full URIs, not prefixed notation. `xsd:gYear` not supported.
6. **Code lists**: Cannot be specified at model creation, add via update

### Error Reference

| Error | Cause | Solution |
|-------|-------|----------|
| `prefix-in-use` | Model exists | Use different prefix |
| `label-language-count-mismatch` | Missing language | Include fi, en, sv |
| `Namespace X: not in owl:imports` | Wrong reference format | Use full IRI |
| `library-not-supported` | codeLists in creation | Remove codeLists field |

## Concept Mappings

### Location & Address

| LIPAS Concept | External Source | Status |
|---------------|-----------------|--------|
| Municipality | `jhs/kunta` | VALID |
| Region | `jhs/maakunta` | VALID |
| Address | EU Core `isa2core:Address` | VALID |
| Location | EU Core `isa2core:Location` | VALID |
| Geometry | GeoSPARQL `geo:Geometry` | External |

### Contact Information

| LIPAS Concept | External Source | Status |
|---------------|-----------------|--------|
| Email | `isa2core:hasEmail` | VALID |
| Phone | `isa2core:hasTelephone` | VALID |
| Website | `isa2core:homepage` | VALID |

### LIPAS-Specific (No National Equivalent)

- Sports facility types
- Admin types (municipal department distinctions)
- Fields (nested facility structures)
- Type-specific properties

## Resources

### Documentation

- [YTI Developer Guide](https://kehittajille.suomi.fi/palvelut/yhteentoimivuus)
- [Tietomallit API Swagger](https://tietomallit.suomi.fi/datamodel-api/swagger-ui/index.html)
- [DVV Wiki](https://wiki.dvv.fi/display/YTIJD)

### Example Models

- [JaPy](https://tietomallit.suomi.fi/model/japy) - Pedestrian/Cycling data model
- [DigiOne](https://tietomallit.suomi.fi/model/digione) - Education data model

### Contact

- YTI Support: yhteentoimivuus@dvv.fi
- LIPAS Team: lipasinfo@jyu.fi

## LIPAS Source Data

| Data | Namespace |
|------|-----------|
| Type codes | `lipas.data.types` |
| Owners | `lipas.data.owners` |
| Admins | `lipas.data.admins` |
| Cities | `lipas.data.cities` |
| Properties | `lipas.data.prop-types` |
| Schema | `lipas.schema.sports-sites` |
