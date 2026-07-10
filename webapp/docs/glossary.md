# LIPAS Glossary

Compact vocabulary for reading code, issues, and domain documentation. Source
maps and schemas remain authoritative for enumerated values.

| Term | Meaning |
|---|---|
| LIPAS | Finnish national sports facility registry; also the surrounding application and data ecosystem |
| liikuntapaikka / sports site | Core registry entity: a sports or recreation facility represented by a versioned document |
| `lipas-id` | Permanent positive integer identifying a sports site across all of its revisions |
| revision `id` | UUID identifying one row/revision in `sports_site`; not the public facility identifier |
| `event-date` | Domain-effective timestamp used to order revisions; distinct from row `created-at` |
| facility status | `:status` inside a sports-site document, describing planning/operational lifecycle |
| document status / `doc-status` | DB revision publication state: `published` or `draft` |
| current revision | Newest non-draft revision selected by `sports_site_current` for a `lipas-id` |
| yearly history | Latest revision per facility/year from `sports_site_by_year`; not a complete revision log |
| type code | Numeric sports-facility classification key; drives allowed geometry, properties, labels, and integrations |
| main/sub-category | Higher levels grouping individual sports-site type codes |
| property type | Data definition describing a type-specific sports-site property and its schema/UI metadata |
| owner | Controlled value for who owns a facility, defined in `lipas.data.owners` |
| admin | Controlled value for the entity administering a facility, defined in `lipas.data.admins`; not the same as the user `:admin` role |
| LOI | Location of interest (`loi`), a separate versioned entity for other mapped destinations |
| PTV | Palvelutietovaranto, Finland's national public-service information repository |
| UTP / Luontoon.fi | External outdoor/recreation content domain attached to selected LIPAS entities; older code and docs may use UTP terminology |
| ITRS | Classification/editing domain governed by the `:itrs/edit` privilege |
| search index | `sports_sites_current`, the current enriched Elasticsearch projection used for search and many reads |
| legacy index | `legacy_sports_sites_current`, transformed for legacy API behavior and restricted to selected facility statuses |
| enrichment | Deterministic transformation that resolves reference data and adds search/geospatial metadata before indexing |
| coherence | Agreement between the authoritative DB revision and its expected derived Elasticsearch projection |
| role assignment | Stored map such as `{:role :city-manager :city-code #{...}}` containing a role plus contextual constraints |
| privilege | Namespaced capability such as `:site/create-edit`; authorization should normally ask for privileges, not role names |
| role context | Values such as city, type, `lipas-id`, activity, or organization against which a role assignment is matched |
| `::roles/any` | Sentinel used to ask whether permission exists for any value of a context dimension |
| Re-frame app-db | Client-side immutable application state; events change it and subscriptions derive reads from it |
| Integrant system | Runtime graph of stateful components such as DB, search client, app, server, emailer, and PTV client |
| job registry | Data-driven definitions for background job behavior, including triggers, priority, retry, debounce, and concurrency policy |
| dead letter | Permanently failed job moved out of the live queue for inspection/acknowledgement |

## Similar names that are not interchangeable

| Names | Difference |
|---|---|
| `save-sports-site!` vs `upsert-sports-site!` | Complete DB + integration path versus DB revision operation |
| `get-sports-site` vs `get-sports-site2` | Current authoritative DB view versus current Elasticsearch projection |
| facility `:status` vs DB `sports_site.status` | Domain lifecycle versus draft/published revision visibility |
| `sports_site_current` vs `sports_sites_current` | PostgreSQL view versus Elasticsearch index |
| `lipas-id` vs sports-site row `id` | Stable facility identifier versus revision UUID |
| history vs revision count | Existing history API is yearly snapshots; exact revision count requires the raw log |
| admin value vs admin role | Facility administrator classification versus globally privileged user role |

Add terms when they decode recurring project language. Keep implementation
details in [domain-map.md](domain-map.md) or focused subsystem documentation.
