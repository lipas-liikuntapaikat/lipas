# PTV Integration - Detailed Technical Documentation

This document provides detailed technical information about the PTV (Palvelutietovaranto) integration implementation, including sequence diagrams, example payloads, and code references.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Use Case 1: Initial Sync / Getting Started Wizard](#use-case-1-initial-sync--getting-started-wizard)
3. [Use Case 2: Sports Site Data Updates](#use-case-2-sports-site-data-updates)
4. [Use Case 3: Data Archival](#use-case-3-data-archival)
5. [Use Case 4: PTV Audit](#use-case-4-ptv-audit)
6. [Data Transformation Functions](#data-transformation-functions)
7. [API Endpoints Reference](#api-endpoints-reference)
8. [Example Payloads](#example-payloads)

---

## Architecture Overview

### Component Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Frontend (CLJS)                                │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │  PTV Wizard     │  │  Sports Site    │  │  PTV Audit Dashboard        │  │
│  │  (Initial Sync) │  │  Editor         │  │                             │  │
│  └────────┬────────┘  └────────┬────────┘  └──────────────┬──────────────┘  │
└───────────┼────────────────────┼─────────────────────────┼──────────────────┘
            │                    │                         │
            ▼                    ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Backend API (handler.clj)                          │
│  /actions/save-ptv-service-location  │  /actions/save-ptv-audit            │
│  /actions/fetch-ptv-services         │  /actions/generate-ptv-descriptions │
└────────────────────────────┬────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Core Business Logic (core.clj)                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │ upsert-ptv-     │  │ sync-ptv!       │  │ save-ptv-audit              │  │
│  │ service-        │  │ (auto-trigger   │  │                             │  │
│  │ location!       │  │ on save)        │  │                             │  │
│  └────────┬────────┘  └────────┬────────┘  └──────────────┬──────────────┘  │
└───────────┼────────────────────┼─────────────────────────┼──────────────────┘
            │                    │                         │
            ▼                    ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PTV Integration Layer (integration.clj)                 │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌──────────────────────┐ │
│  │ create-service-     │  │ update-service-     │  │ get-org-services     │ │
│  │ location            │  │ location            │  │ get-org-service-     │ │
│  │ create-service      │  │ update-service      │  │ channels             │ │
│  └─────────┬───────────┘  └─────────┬───────────┘  └──────────┬───────────┘ │
└────────────┼────────────────────────┼───────────────────────┼───────────────┘
             │                        │                       │
             ▼                        ▼                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        External PTV API (suomi.fi)                          │
│  POST /v11/Service/token           │  GET  /v11/Organization/{id}           │
│  POST /v11/Service                 │  GET  /v11/Service/list/organization   │
│  PUT  /v11/Service/SourceId/{id}   │  GET  /v11/ServiceChannel/organization │
│  POST /v11/ServiceChannel/ServiceLocation                                   │
│  PUT  /v11/ServiceChannel/ServiceLocation/{id}                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `src/cljc/lipas/data/ptv.cljc` | Data transformation functions (`->ptv-service`, `->ptv-service-location`) |
| `src/clj/lipas/backend/ptv/integration.clj` | HTTP client for PTV API communication |
| `src/clj/lipas/backend/ptv/core.clj` | Business logic coordination |
| `src/clj/lipas/backend/ptv/handler.clj` | REST API endpoint definitions |
| `src/clj/lipas/backend/ptv/ai.clj` | AI-powered description generation |
| `src/cljc/lipas/schema/sports_sites/ptv.cljc` | Schema definitions for PTV metadata |
| `src/cljc/lipas/data/types_new.cljc` | Type definitions with PTV ontology mappings |

---

## Use Case 1: Initial Sync / Getting Started Wizard

The Getting Started Wizard guides administrators through enabling PTV integration for an organization's sports facilities.

### Sequence Diagram

```
┌───────┐          ┌─────────┐         ┌────────────┐        ┌──────────┐       ┌─────────┐
│ Admin │          │Frontend │         │ Backend    │        │ PTV API  │       │   DB    │
│ User  │          │   UI    │         │            │        │          │       │         │
└───┬───┘          └────┬────┘         └─────┬──────┘        └────┬─────┘       └────┬────┘
    │                   │                    │                    │                  │
    │ 1. Open Wizard    │                    │                    │                  │
    │ ─────────────────>│                    │                    │                  │
    │                   │                    │                    │                  │
    │                   │ 2. fetch-ptv-services                   │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 3. GET /v11/Service/list/organization │
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 4. Services list   │                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │ 5. Existing services                    │                  │
    │                   │ <──────────────────│                    │                  │
    │                   │                    │                    │                  │
    │                   │ 6. fetch-ptv-service-channels           │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 7. GET /v11/ServiceChannel/organization/{id}
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 8. Channels list   │                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │ 9. Existing channels                    │                  │
    │                   │ <──────────────────│                    │                  │
    │                   │                    │                    │                  │
    │                   │ 10. get-ptv-integration-candidates      │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 11. Query ES for eligible sites       │
    │                   │                    │ ─────────────────────────────────────>│
    │                   │                    │                    │                  │
    │                   │                    │ 12. Eligible sites │                  │
    │                   │                    │ <─────────────────────────────────────│
    │                   │                    │                    │                  │
    │                   │ 13. Candidate sites                     │                  │
    │                   │ <──────────────────│                    │                  │
    │                   │                    │                    │                  │
    │ 14. Select sites  │                    │                    │                  │
    │ & Generate        │                    │                    │                  │
    │ descriptions      │                    │                    │                  │
    │ ─────────────────>│                    │                    │                  │
    │                   │                    │                    │                  │
    │                   │ 15. generate-ptv-descriptions           │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 16. OpenAI API call│                  │
    │                   │                    │ ═══════════════════╪═══>              │
    │                   │                    │                    │                  │
    │                   │                    │ 17. AI response    │                  │
    │                   │                    │ <═══════════════════╪═══               │
    │                   │                    │                    │                  │
    │                   │ 18. Generated summary/description       │                  │
    │                   │ <──────────────────│                    │                  │
    │                   │                    │                    │                  │
    │ 19. Review &      │                    │                    │                  │
    │ Approve           │                    │                    │                  │
    │ ─────────────────>│                    │                    │                  │
    │                   │                    │                    │                  │
    │                   │ 20. save-ptv-service (if missing)       │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 21. POST /v11/Service                 │
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 22. Service created│                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │ 23. save-ptv-service-location           │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 24. POST /v11/ServiceChannel/ServiceLocation
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 25. Channel created│                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 26. Update sports site with PTV meta  │
    │                   │                    │ ─────────────────────────────────────>│
    │                   │                    │                    │                  │
    │                   │ 27. Success        │                    │                  │
    │                   │ <──────────────────│                    │                  │
    │                   │                    │                    │                  │
    │ 28. Done          │                    │                    │                  │
    │ <─────────────────│                    │                    │                  │
    │                   │                    │                    │                  │
```

### Key Functions Called

1. **`get-ptv-integration-candidates`** (`core.clj:17`)
   - Queries Elasticsearch for eligible sports sites
   - Filters by city codes, owners, and excludes certain type codes (7000, 207)

2. **`resolve-missing-services`** (`ptv.cljc:483`)
   - Compares selected sites with existing PTV services
   - Returns list of sub-categories that need new PTV Services

3. **`upsert-ptv-service!`** (`core.clj:69`)
   - Creates or updates a PTV Service using source ID
   - Transforms data using `->ptv-service`

4. **`upsert-ptv-service-location!`** (`core.clj:181`)
   - Creates or updates a PTV ServiceChannel (service location)
   - Transforms data using `->ptv-service-location`
   - Stores PTV metadata back to sports site

---

## Use Case 2: Sports Site Data Updates

When a sports site is saved with PTV integration enabled, the system automatically syncs changes to PTV.

### Sequence Diagram

```
┌───────┐          ┌─────────┐         ┌────────────┐        ┌──────────┐       ┌─────────┐
│ User  │          │Frontend │         │ Backend    │        │ PTV API  │       │DB/Search│
│       │          │   UI    │         │            │        │          │       │         │
└───┬───┘          └────┬────┘         └─────┬──────┘        └────┬─────┘       └────┬────┘
    │                   │                    │                    │                  │
    │ 1. Edit & Save    │                    │                    │                  │
    │    Sports Site    │                    │                    │                  │
    │ ─────────────────>│                    │                    │                  │
    │                   │                    │                    │                  │
    │                   │ 2. POST /api/sports-sites/{id}          │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 3. save-sports-site!                  │
    │                   │                    │   (upsert-sports-site!)               │
    │                   │                    │ ─────────────────────────────────────>│
    │                   │                    │                    │                  │
    │                   │                    │ 4. Check: sync-enabled?               │
    │                   │                    │          ptv-candidate?               │
    │                   │                    │          is-sent-to-ptv?              │
    │                   │                    │                    │                  │
    │                   │                    │─────────┐          │                  │
    │                   │                    │ 5. If   │          │                  │
    │                   │                    │ sync    │          │                  │
    │                   │                    │ needed  │          │                  │
    │                   │                    │<────────┘          │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 6. sync-ptv!       │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 7. Check type-code changed?           │
    │                   │                    │    (compare with previous-type-code)  │
    │                   │                    │                    │                  │
    │                   │                    │─────────┐          │                  │
    │                   │                    │ 8. If   │          │                  │
    │                   │                    │ type    │          │                  │
    │                   │                    │ changed │          │                  │
    │                   │                    │<────────┘          │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 9. GET /v11/Service/list/organization │
    │                   │                    │    (fetch current services)           │
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 10. Services       │                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 11. Update service-ids mapping        │
    │                   │                    │                    │                  │
    │                   │                    │ 12. ->ptv-service-location            │
    │                   │                    │     (transform data)                  │
    │                   │                    │                    │                  │
    │                   │                    │ 13. PUT /v11/ServiceChannel/ServiceLocation/{id}
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 14. Updated        │                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 15. Update service connections if needed
    │                   │                    │     PUT /v11/Connection/serviceId/{id}│
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 16. Connections updated               │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 17. Save updated :ptv meta to DB      │
    │                   │                    │ ─────────────────────────────────────>│
    │                   │                    │                    │                  │
    │                   │                    │ 18. Index to Elasticsearch            │
    │                   │                    │ ─────────────────────────────────────>│
    │                   │                    │                    │                  │
    │                   │ 19. Updated site data                   │                  │
    │                   │ <──────────────────│                    │                  │
    │                   │                    │                    │                  │
    │ 20. Success       │                    │                    │                  │
    │ <─────────────────│                    │                    │                  │
```

### Sync Trigger Conditions (`core.clj:584-589`)

The PTV sync is triggered when ALL of these conditions are true:
- `draft?` is false (not a draft save)
- `(:sync-enabled (:ptv resp))` OR `(:delete-existing (:ptv resp))` is true
- Site is either a PTV candidate OR was previously sent to PTV

```clojure
(if (and (not draft?)
         (or (:sync-enabled (:ptv resp))
             (:delete-existing (:ptv resp)))
         (or (ptv-data/ptv-candidate? resp)
             (ptv-data/is-sent-to-ptv? resp)))
  (sync-ptv! ...)
  resp)
```

### PTV Candidate Check (`ptv.cljc:631-640`)

```clojure
(defn ptv-candidate? [site]
  (let [{:keys [status owner]} site
        type-code (-> site :type :type-code)]
    (boolean (and (not (contains? #{"incorrect-data" "out-of-service-permanently"} status))
                  (#{"city" "city-main-owner"} owner)
                  (not (#{7000 207} type-code))))))
```

---

## Use Case 3: Data Archival

When a sports site is marked inactive or no longer eligible for PTV integration, the system archives it in PTV.

### Sequence Diagram

```
┌───────┐          ┌─────────┐         ┌────────────┐        ┌──────────┐       ┌─────────┐
│ User  │          │Frontend │         │ Backend    │        │ PTV API  │       │DB/Search│
│       │          │   UI    │         │            │        │          │       │         │
└───┬───┘          └────┬────┘         └─────┬──────┘        └────┬─────┘       └────┬────┘
    │                   │                    │                    │                  │
    │ 1. Change status  │                    │                    │                  │
    │ to "out-of-       │                    │                    │                  │
    │ service-          │                    │                    │                  │
    │ permanently"      │                    │                    │                  │
    │ ─────────────────>│                    │                    │                  │
    │                   │                    │                    │                  │
    │                   │ 2. Save sports site│                    │                  │
    │                   │ ──────────────────>│                    │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 3. sync-ptv!       │                  │
    │                   │                    │                    │                  │
    │                   │                    │ 4. Check:          │                  │
    │                   │                    │    previous-sent? = true              │
    │                   │                    │    candidate-now? = false             │
    │                   │                    │    to-archive? = true                 │
    │                   │                    │                    │                  │
    │                   │                    │ 5. ->ptv-service-location             │
    │                   │                    │    with publishingStatus="Deleted"    │
    │                   │                    │                    │                  │
    │                   │                    │ 6. PUT /v11/ServiceChannel/ServiceLocation/{id}
    │                   │                    │    {publishingStatus: "Deleted", ...} │
    │                   │                    │ ──────────────────>│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 7. Archived        │                  │
    │                   │                    │ <──────────────────│                  │
    │                   │                    │                    │                  │
    │                   │                    │ 8. Update DB:      │                  │
    │                   │                    │    - Clear source-id                  │
    │                   │                    │    - Clear service-channel-ids        │
    │                   │                    │    - Store publishing-status          │
    │                   │                    │ ─────────────────────────────────────>│
    │                   │                    │                    │                  │
    │                   │ 9. Success         │                    │                  │
    │                   │ <──────────────────│                    │                  │
```

### Archive Logic (`core.clj:237-242`)

```clojure
;; Determine if archival is needed
(let [to-archive? (and previous-sent?
                       (or (not candidate-now?)
                           (:delete-existing ptv)))]
  ...)
```

### PTV Status Mapping (`ptv.cljc:358-360`)

```clojure
:publishingStatus (case status
                    ("incorrect-data" "out-of-service-permanently") "Deleted"
                    "Published")
```

---

## Use Case 4: PTV Audit

Auditors can review and provide feedback on PTV data quality.

### Sequence Diagram

```
┌─────────┐       ┌─────────┐         ┌────────────┐        ┌─────────┐       ┌───────────┐
│ Auditor │       │Frontend │         │ Backend    │        │DB/Search│       │  Emailer  │
│         │       │   UI    │         │            │        │         │       │           │
└────┬────┘       └────┬────┘         └─────┬──────┘        └────┬────┘       └─────┬─────┘
     │                 │                    │                    │                  │
     │ 1. View audit   │                    │                    │                  │
     │    dashboard    │                    │                    │                  │
     │ ───────────────>│                    │                    │                  │
     │                 │                    │                    │                  │
     │                 │ 2. fetch-ptv-services/channels          │                  │
     │                 │ ──────────────────>│                    │                  │
     │                 │                    │                    │                  │
     │                 │ 3. get-ptv-integration-candidates       │                  │
     │                 │ ──────────────────>│                    │                  │
     │                 │                    │                    │                  │
     │                 │                    │ 4. Query sites     │                  │
     │                 │                    │ ──────────────────>│                  │
     │                 │                    │                    │                  │
     │                 │                    │ 5. Sites with :ptv │                  │
     │                 │                    │ <──────────────────│                  │
     │                 │                    │                    │                  │
     │                 │ 6. Sites list with audit status         │                  │
     │                 │ <──────────────────│                    │                  │
     │                 │                    │                    │                  │
     │ 7. Review site  │                    │                    │                  │
     │    details      │                    │                    │                  │
     │ ───────────────>│                    │                    │                  │
     │                 │                    │                    │                  │
     │ 8. Submit audit │                    │                    │                  │
     │    (approve or  │                    │                    │                  │
     │    request      │                    │                    │                  │
     │    changes)     │                    │                    │                  │
     │ ───────────────>│                    │                    │                  │
     │                 │                    │                    │                  │
     │                 │ 9. save-ptv-audit  │                    │                  │
     │                 │ ──────────────────>│                    │                  │
     │                 │                    │                    │                  │
     │                 │                    │ 10. Add timestamp & auditor-id        │
     │                 │                    │                    │                  │
     │                 │                    │ 11. Update site    │                  │
     │                 │                    │ ──────────────────>│                  │
     │                 │                    │                    │                  │
     │                 │                    │ 12. Index to ES    │                  │
     │                 │                    │ ──────────────────>│                  │
     │                 │                    │                    │                  │
     │                 │ 13. Audit saved    │                    │                  │
     │                 │ <──────────────────│                    │                  │
     │                 │                    │                    │                  │
     │ 14. Complete    │                    │                    │                  │
     │     audit       │                    │                    │                  │
     │ ───────────────>│                    │                    │                  │
     │                 │                    │                    │                  │
     │                 │ 15. send-audit-notification             │                  │
     │                 │ ──────────────────>│                    │                  │
     │                 │                    │                    │                  │
     │                 │                    │ 16. Get PTV managers                  │
     │                 │                    │ ──────────────────>│                  │
     │                 │                    │                    │                  │
     │                 │                    │ 17. Send emails    │                  │
     │                 │                    │ ─────────────────────────────────────>│
     │                 │                    │                    │                  │
     │                 │ 18. Notifications sent                  │                  │
     │                 │ <──────────────────│                    │                  │
```

### Audit Status Determination (`ptv.cljc:539-566`)

```clojure
(defn determine-audit-status [site]
  (let [audit-data (get-in site [:ptv :audit])
        summary-status (get-in audit-data [:summary :status])
        desc-status (get-in audit-data [:description :status])]
    (cond
      (and summary-status desc-status)
      (cond
        (and (= "approved" summary-status) (= "approved" desc-status))
        :approved
        (or (= "changes-requested" summary-status) (= "changes-requested" desc-status))
        :changes-requested
        :else :partial)
      (or summary-status desc-status) :partial
      :else :none)))
```

---

## Data Transformation Functions

### `->ptv-service` (`ptv.cljc:127-229`)

Transforms Lipas type categorization data into PTV Service format.

**Input:**
```clojure
{:org-id "3d1759a2-e47a-4947-9a31-cab1c1e2512b"
 :city-codes [889]
 :source-id "lipas-3d1759a2-e47a-4947-9a31-cab1c1e2512b-2500"
 :sub-category-id 2500
 :languages ["fi"]
 :summary {:fi "Jäähallit tarjoavat..."}
 :description {:fi "Jäähallit ovat sisätiloissa..."}}
```

**Output (PTV Service):**
```json
{
  "sourceId": "lipas-3d1759a2-e47a-4947-9a31-cab1c1e2512b-2500",
  "ontologyTerms": [
    "http://www.yso.fi/onto/koko/p10416",
    "http://www.yso.fi/onto/koko/p11376"
  ],
  "serviceClasses": [
    "http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.1"
  ],
  "type": "Service",
  "fundingType": "PubliclyFunded",
  "serviceNames": [
    {
      "type": "Name",
      "language": "fi",
      "value": "Jäähallit"
    }
  ],
  "targetGroups": [
    "http://uri.suomi.fi/codelist/ptv/ptvkohderyhmat/code/KR1"
  ],
  "areaType": "LimitedType",
  "areas": [
    {
      "type": "Municipality",
      "areaCodes": [889]
    }
  ],
  "languages": ["fi"],
  "serviceDescriptions": [
    {
      "type": "Summary",
      "language": "fi",
      "value": "Jäähallit tarjoavat..."
    },
    {
      "type": "Description",
      "language": "fi",
      "value": "Jäähallit ovat sisätiloissa..."
    }
  ],
  "serviceProducers": [
    {
      "provisionType": "SelfProducedServices",
      "organizations": ["3d1759a2-e47a-4947-9a31-cab1c1e2512b"]
    }
  ],
  "publishingStatus": "Published",
  "mainResponsibleOrganization": "3d1759a2-e47a-4947-9a31-cab1c1e2512b"
}
```

### `->ptv-service-location` (`ptv.cljc:272-382`)

Transforms Lipas sports site data into PTV ServiceChannel format.

**Input (Sports Site):**
```clojure
{:lipas-id 89913
 :name "Utajärven jäähalli"
 :status "active"
 :email "palaute@utajarvi.fi"
 :phone-number "+358858755700"
 :www "https://www.utajarvi.fi"
 :location {:city {:city-code 889}
            :address "Laitilantie 5"
            :postal-code "91600"}
 :search-meta {:location {:wgs84-point [26.413 64.763]}}
 :ptv {:org-id "7b83257d-06ad-4e3b-985d-16a5c9d3fced"
       :source-id "lipas-7b83257d-06ad-4e3b-985d-16a5c9d3fced-89913-2024-01-01"
       :languages ["fi"]
       :summary {:fi "Utajärven jäähalli..."}
       :description {:fi "Utajärven jäähalli on..."}
       :service-ids ["abc-123"]}}
```

**Output (PTV ServiceLocation):**
```json
{
  "organizationId": "7b83257d-06ad-4e3b-985d-16a5c9d3fced",
  "sourceId": "lipas-7b83257d-06ad-4e3b-985d-16a5c9d3fced-89913-2024-01-01",
  "serviceChannelNames": [
    {
      "type": "Name",
      "value": "Utajärven jäähalli",
      "language": "fi"
    }
  ],
  "displayNameType": [
    {"type": "Name", "language": "fi"}
  ],
  "serviceChannelDescriptions": [
    {
      "type": "Summary",
      "value": "Utajärven jäähalli...",
      "language": "fi"
    },
    {
      "type": "Description",
      "value": "Utajärven jäähalli on...",
      "language": "fi"
    }
  ],
  "languages": ["fi"],
  "addresses": [
    {
      "type": "Location",
      "subType": "Single",
      "country": "FI",
      "streetAddress": {
        "municipality": "889",
        "street": [{"value": "Laitilantie 5", "language": "fi"}],
        "postalCode": "91600",
        "latitude": "7189456.123",
        "longitude": "451234.567"
      }
    }
  ],
  "publishingStatus": "Published",
  "services": ["abc-123"],
  "emails": [{"value": "palaute@utajarvi.fi", "language": "fi"}],
  "webPages": [{"url": "https://www.utajarvi.fi", "language": "fi"}],
  "phoneNumbers": [
    {
      "number": "858755700",
      "prefixNumber": "+358",
      "isFinnishServiceNumber": false,
      "language": "fi"
    }
  ]
}
```

---

## API Endpoints Reference

### Read Operations

| Endpoint | Method | Purpose | Required Privilege |
|----------|--------|---------|-------------------|
| `/actions/get-ptv-integration-candidates` | POST | Find eligible sports sites | `:ptv/audit` or `:ptv/manage` |
| `/actions/fetch-ptv-org` | POST | Get organization details | `:ptv/audit` or `:ptv/manage` |
| `/actions/fetch-ptv-services` | POST | List organization's services | `:ptv/audit` or `:ptv/manage` |
| `/actions/fetch-ptv-service-channels` | POST | List organization's channels | `:ptv/audit` or `:ptv/manage` |
| `/actions/fetch-ptv-service-channel` | POST | Get specific channel details | `:ptv/manage` |
| `/actions/fetch-ptv-service-collections` | POST | List service collections | `:ptv/audit` or `:ptv/manage` |

### Write Operations

| Endpoint | Method | Purpose | Required Privilege |
|----------|--------|---------|-------------------|
| `/actions/save-ptv-service` | POST | Create/update PTV service | `:ptv/manage` |
| `/actions/save-ptv-service-location` | POST | Create/update service channel | `:ptv/manage` |
| `/actions/save-ptv-meta` | POST | Update PTV metadata on sites | `:ptv/manage` |
| `/actions/save-ptv-audit` | POST | Save audit feedback | `:ptv/audit` |
| `/actions/send-audit-notification` | POST | Email audit results | `:ptv/audit` |

### AI Generation

| Endpoint | Method | Purpose | Required Privilege |
|----------|--------|---------|-------------------|
| `/actions/generate-ptv-descriptions` | POST | Generate descriptions from lipas-id | `:ptv/manage` |
| `/actions/generate-ptv-descriptions-from-data` | POST | Generate from provided data | `:ptv/manage` |
| `/actions/generate-ptv-service-descriptions` | POST | Generate service descriptions | `:ptv/manage` |
| `/actions/translate-to-other-langs` | POST | Translate descriptions | `:ptv/manage` |

---

## Example Payloads

### Get Integration Candidates Request

```json
{
  "city-codes": [889, 425],
  "type-codes": [2520, 2510],
  "owners": ["city", "city-main-owner"]
}
```

### Save PTV Service Request

```json
{
  "org-id": "7b83257d-06ad-4e3b-985d-16a5c9d3fced",
  "city-codes": [889],
  "source-id": "lipas-7b83257d-06ad-4e3b-985d-16a5c9d3fced-2500",
  "sub-category-id": 2500,
  "languages": ["fi"],
  "summary": {
    "fi": "Jäähallit tarjoavat mahdollisuuden jääurheiluun."
  },
  "description": {
    "fi": "Jäähallit ovat sisätiloissa toimivia luistelupaikkoja, joissa voi harrastaa jääurheilua ympäri vuoden."
  }
}
```

### Save PTV Service Location Request

```json
{
  "org-id": "7b83257d-06ad-4e3b-985d-16a5c9d3fced",
  "lipas-id": 89913,
  "ptv": {
    "org-id": "7b83257d-06ad-4e3b-985d-16a5c9d3fced",
    "sync-enabled": true,
    "service-channel-ids": [],
    "service-ids": ["abc-123-def-456"],
    "summary": {
      "fi": "Utajärven jäähalli on katettu jäähalli."
    },
    "description": {
      "fi": "Utajärven jäähalli sijaitsee Utajärven keskustassa. Hallissa on jääkiekkokaukalo ja katsomo."
    }
  }
}
```

### Save PTV Audit Request

```json
{
  "lipas-id": 89913,
  "audit": {
    "summary": {
      "status": "approved",
      "feedback": "Summary text is clear and informative."
    },
    "description": {
      "status": "changes-requested",
      "feedback": "Please add information about accessibility."
    }
  }
}
```

### Generate Descriptions Request

```json
{
  "lipas-id": 89913
}
```

### Generate Descriptions Response

```json
{
  "summary": {
    "fi": "Utajärven jäähalli on katettu jäähalli, joka tarjoaa mahdollisuuden jääurheiluun.",
    "se": "Utajärvi ishall är en täckt ishall som erbjuder möjlighet till issport.",
    "en": "Utajärvi ice hall is a covered ice rink that provides opportunities for ice sports."
  },
  "description": {
    "fi": "Utajärven jäähalli sijaitsee Utajärven keskustassa. Hallissa on jääkiekkokaukalo ja 250-paikkainen katsomo.\n\nHalli on avoinna syyskuusta maaliskuuhun. Jäähallissa voi harrastaa jääkiekkoa, taitoluistelua ja yleisöluistelua.",
    "se": "Utajärvi ishall ligger i Utajärvi centrum. Hallen har en ishockeyplan och läktare för 250 personer.\n\nHallen är öppen från september till mars. I ishallen kan man utöva ishockey, konståkning och allmän skridskoåkning.",
    "en": "Utajärvi ice hall is located in Utajärvi center. The hall has an ice hockey rink and seating for 250 spectators.\n\nThe hall is open from September to March. The ice hall offers ice hockey, figure skating, and public skating."
  }
}
```

---

## PTV Metadata Structure in Sports Site

The `:ptv` key in a sports site document contains:

```clojure
{:ptv
 {;; Organization ID in PTV
  :org-id "7b83257d-06ad-4e3b-985d-16a5c9d3fced"

  ;; Enable automatic sync on save
  :sync-enabled true

  ;; Supported languages
  :languages ["fi" "se" "en"]

  ;; Localized summary (max 150 chars per language)
  :summary {:fi "..." :se "..." :en "..."}

  ;; Localized description
  :description {:fi "..." :se "..." :en "..."}

  ;; PTV Service IDs this site is connected to
  :service-ids ["service-uuid-1"]

  ;; PTV ServiceChannel IDs (usually one)
  :service-channel-ids ["channel-uuid-1"]

  ;; Source ID for PTV API upsert operations
  :source-id "lipas-org-id-lipas-id-timestamp"

  ;; Current PTV publishing status
  :publishing-status "Published" ; or "Deleted"

  ;; Timestamp of last successful sync
  :last-sync "2024-01-15T10:30:00.000Z"

  ;; Previous type code (for change detection)
  :previous-type-code 2520

  ;; Audit information (optional)
  :audit {:timestamp "2024-01-20T14:00:00.000Z"
          :auditor-id "user-123"
          :summary {:status "approved" :feedback "Good"}
          :description {:status "changes-requested" :feedback "Add more detail"}}

  ;; Error from last sync attempt (if failed)
  :error {:message "..." :data {...}}}}
```

---

## Type Categorization and PTV Mapping

Sports site types are organized in a hierarchy:

1. **Main Category** (e.g., 2000 = "Sisäliikuntatilat" / Indoor sports facilities)
2. **Sub Category** (e.g., 2500 = "Jäähallit" / Ice-skating arenas)
3. **Type** (e.g., 2520 = "Jäähalli" / Ice hall)

Each main category and sub-category has PTV metadata:

```clojure
;; From types_new.cljc
{2000
 {:type-code 2000
  :name {:fi "Sisäliikuntatilat" :se "Inomhusidrottslokaler" :en "Indoor sports facilities"}
  :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p10416"
                        "http://www.yso.fi/onto/koko/p69660"]
        :service-classes ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.1"]}}}
```

**Ontology URLs**: Links to YSO/KOKO ontology terms describing the service type
**Service Classes**: PTV service classification codes (P27.1 = Indoor sports, P27.2 = Outdoor sports)

---

## Error Handling

When PTV sync fails, the error is stored in the site's `:ptv :error` field:

```clojure
(catch Exception e
  (let [new-ptv-data (assoc ptv :error {:message (.getMessage e)
                                         :data (ex-data e)})]
    (log/infof e "Sports site updated but PTV integration had an error")
    ;; Site is still saved, but with error information
    ...))
```

This allows:
1. The sports site save to succeed even if PTV fails
2. Administrators to see and diagnose the error
3. Retry of the sync on the next save

---

## Related Documentation

- [PTV Integration Overview](./ptv-integration.md) - High-level overview
- [PTV API Documentation](https://api.palvelutietovaranto.suomi.fi/swagger/ui/index.html) - Official PTV API docs
- [KOKO Ontology](http://finto.fi/koko/fi/) - Finnish ontology used for service classification
