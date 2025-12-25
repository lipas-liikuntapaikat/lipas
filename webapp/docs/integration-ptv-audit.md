# PTV Audit Feature Documentation

## Overview

The PTV Audit feature allows authorized users to review and provide feedback on PTV (Finnish Public Service Registry) descriptions and summaries for sports facilities before they are published to the public service registry.

### Purpose

- **Quality Control**: Ensure PTV descriptions and summaries are accurate and appropriate
- **Feedback Loop**: Provide structured feedback to content creators
- **Audit Trail**: Track who audited content and when
- **Status Tracking**: Distinguish between pending and completed audits

## User Workflow

### Accessing the Audit Interface

1. Users with `:ptv/audit` privilege can access the audit interface
2. Select a PTV organization from the organization selector
3. Navigate to the "Auditointi" (Audit) tab

### Auditing Process

1. **Select Tab**:
   - **Odottavat (Pending)**: Sites without complete audits
   - **Valmiit (Completed)**: Sites with at least one field audited

2. **Select Site**: Click on a site from the list to view its details

3. **Review Content**:
   - View PTV service location preview
   - Review Summary (Finnish)
   - Review Description (Finnish)
   - See previous audit information if available

4. **Provide Feedback**:
   - Select status for each field:
     - **Hyväksytty (Approved)**: Content is acceptable
     - **Muutospyynnöt (Changes Requested)**: Content needs revision
   - Write optional feedback (max 1000 characters)

5. **Save Audit**: Click "Tallenna" (Save) to submit the audit

## Architecture

### Frontend Structure

```
src/cljs/lipas/ui/ptv/
├── audit.cljs           # UI components for audit interface
├── events.cljs          # Re-frame events for audit actions
├── subs.cljs            # Re-frame subscriptions for audit state
└── components.cljs      # Shared PTV components (service location preview)

src/cljc/lipas/i18n/
├── fi/ptv_audit.edn     # Finnish translations
├── se/ptv_audit.edn     # Swedish translations
└── en/ptv_audit.edn     # English translations
```

### Backend Structure

```
src/clj/lipas/backend/ptv/
├── handler.clj          # HTTP endpoint handlers
└── core.clj             # Business logic for audit operations

src/cljc/lipas/schema/sports_sites/
└── ptv.cljc            # Shared Malli schemas for validation
```

## Data Model

### Schemas

The PTV audit feature uses three Malli schemas defined in `src/cljc/lipas/schema/sports_sites/ptv.cljc`:

#### 1. `audit-status-enum`
```clojure
[:enum "approved" "changes-requested"]
```
Valid status values for audit feedback.

#### 2. `audit-field`
```clojure
[:map
 {:closed true}
 [:status audit-status-enum]
 [:feedback [:string {:min 0 :max 1000}]]]
```
Schema for individual field audit (summary or description).
- `status`: Required - approval status
- `feedback`: Required - can be empty string, max 1000 characters

#### 3. `audit-data`
```clojure
[:map
 {:closed true}
 [:summary {:optional true} audit-field]
 [:description {:optional true} audit-field]]
```
Schema for audit data **sent from frontend to backend**.
- Both fields optional (user may audit just one field)
- Closed map - rejects extra keys

#### 4. `ptv-audit`
```clojure
[:map
 {:closed true}
 [:timestamp [:string {:min 24 :max 30}]]
 [:auditor-id :string]
 [:summary {:optional true} audit-field]
 [:description {:optional true} audit-field]]
```
Schema for complete audit data **stored in database**.
- Includes backend-added metadata (timestamp, auditor-id)
- Used for storage and retrieval

### Data Flow

```
User Input
    ↓
Frontend State (audit-data schema)
    ↓
HTTP POST /actions/save-ptv-audit
    ↓
Backend Validation (audit-data schema)
    ↓
Add :timestamp and :auditor-id
    ↓
Save to Database (ptv-audit schema)
    ↓
Return to Frontend
    ↓
Frontend State (ptv-audit schema with metadata)
```

### State Structure

Audit data is stored in Re-frame app-db:

```clojure
{:ptv
 {:org
  {"org-id-123"
   {:data
    {:sports-sites
     {12345
      {:lipas-id 12345
       :name "Example Sports Facility"
       :ptv
       {:audit
        {:summary {:status "approved"
                   :feedback "Looks good!"}
         :description {:status "changes-requested"
                       :feedback "Please add more details about accessibility"}
         :timestamp "2025-11-17T10:30:00.000Z"
         :auditor-id "user-456"}}}}}}}
 :audit
 {:selected-tab "todo"        ; or "completed"
  :saving? false}
 :selected-audit-site {...}}} ; Currently selected site
```

## Validation Strategy (Defense in Depth)

The feature implements three layers of validation:

### 1. UI Input Constraints
```clojure
;; TextField with maxLength prevents typing >1000 chars
{:inputProps #js{:maxLength 1000}}
```
**Purpose**: Prevent users from entering invalid data

### 2. Save Button State (Subscription)
```clojure
(rf/reg-sub ::site-audit-data-valid?
  (fn [[_ lipas-id]]
    (rf/subscribe [::site-audit-data lipas-id]))
  (fn [audit-data _]
    (and (seq audit-data)
         ;; Select only user-editable fields for validation
         ;; Backend may add metadata (:timestamp, :auditor-id, etc.)
         (m/validate ptv-schema/audit-data
                     (select-keys audit-data [:summary :description])))))
```
**Purpose**: Disable save button if data doesn't match schema
**Key Design**: Uses `select-keys` to validate only user-editable fields, ignoring backend metadata

### 3. Backend Validation
```clojure
;; In handler.clj
:parameters {:body [:map
                    [:lipas-id #'sports-sites-schema/lipas-id]
                    [:audit #'ptv-schema/audit-data]]}
```
**Purpose**: Final safety check before database write

### Why This Approach?

- **Robust**: Multiple validation points catch different error scenarios
- **User-Friendly**: Button state prevents invalid submissions
- **Secure**: Backend validation prevents API manipulation
- **Maintainable**: Shared schemas ensure frontend/backend consistency
- **Future-Proof**: `select-keys` approach automatically handles new metadata fields

## API Endpoint

### POST `/actions/save-ptv-audit`

**Authorization**: Requires `:ptv/audit` privilege

**Request Body**:
```clojure
{:lipas-id 12345
 :audit {:summary {:status "approved"
                   :feedback ""}
         :description {:status "changes-requested"
                       :feedback "Please add accessibility information"}}}
```

**Response** (200 OK):
```clojure
{:summary {:status "approved"
           :feedback ""}
 :description {:status "changes-requested"
               :feedback "Please add accessibility information"}
 :timestamp "2025-11-17T10:30:00.000Z"
 :auditor-id "user-456"}
```

**Error Response** (404 Not Found):
```clojure
{:error "Sports site not found"}
```

## Key Components

### UI Components (src/cljs/lipas/ui/ptv/audit.cljs)

#### `content-panel`
Displays a single field's content (summary or description) with previous audit information.

**Props**:
- `tr`: Translator function
- `field`: `:summary` or `:description`
- `content`: The text content to display
- `site-audit-data`: Existing audit data for this site

#### `field-form`
Form controls for auditing a single field (status radio buttons + feedback textarea).

**Props**:
- `tr`: Translator function
- `field`: `:summary` or `:description`
- `lipas-id`: Sports site identifier

**Features**:
- Radio buttons for status selection
- Textarea with character counter (max 1000)
- Real-time validation feedback
- Uses patched textarea component to prevent caret jumping

#### `site-form`
Complete audit form for a sports site with save button.

**Props**:
- `tr`: Translator function
- `lipas-id`: Sports site identifier
- `site`: Sports site data

**Features**:
- PTV service location preview
- Content panels for summary and description
- Field forms for audit feedback
- Single save button controlled by validation subscription

#### `site-list-item`
List item showing a site with visual status indicator.

**Props**:
- `site`: Sports site data
- `selected?`: Whether this site is currently selected
- `on-select`: Callback function when site is clicked

**Status Indicators**:
- Green dot: Both fields audited
- Orange dot: One field audited
- Blue dot: No fields audited

#### `main-view`
Top-level component for the audit interface.

**Features**:
- Tab navigation (Todo/Completed)
- Site count display
- Split view with site list and audit form
- Responsive layout

### Events (src/cljs/lipas/ui/ptv/events.cljs)

#### `::select-audit-tab`
```clojure
(rf/dispatch [:lipas.ui.ptv.events/select-audit-tab "todo"])
```
Switches between "todo" and "completed" tabs.

#### `::select-audit-site`
```clojure
(rf/dispatch [:lipas.ui.ptv.events/select-audit-site site])
```
Selects a site for auditing.

#### `::update-audit-status`
```clojure
(rf/dispatch [:lipas.ui.ptv.events/update-audit-status lipas-id :summary "approved"])
```
Updates the status for a field and initializes feedback to empty string if not present.

#### `::update-audit-feedback`
```clojure
(rf/dispatch [:lipas.ui.ptv.events/update-audit-feedback lipas-id :summary "Looks good!"])
```
Updates the feedback text for a field.

#### `::save-ptv-audit`
```clojure
(rf/dispatch [:lipas.ui.ptv.events/save-ptv-audit lipas-id audit-data])
```
Saves the audit to the backend.

**Validation**: None at event level (relies on subscription to prevent invalid calls)

### Subscriptions (src/cljs/lipas/ui/ptv/subs.cljs)

#### `::site-audit-data`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data lipas-id])
```
Returns the complete audit data for a site (includes backend metadata).

#### `::site-audit-field-status`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/site-audit-field-status lipas-id :summary])
```
Returns the status for a specific field.

#### `::site-audit-field-feedback`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/site-audit-field-feedback lipas-id :summary])
```
Returns the feedback text for a specific field.

#### `::site-audit-data-valid?`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data-valid? lipas-id])
```
Returns true if the audit data is valid according to the schema.

**Implementation Note**: Uses `select-keys` to extract only user-editable fields before validation, making it future-proof against new metadata fields.

#### `::auditable-sites`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/auditable-sites org-id :todo])
```
Returns filtered list of sites based on audit status.

**Filters**:
- `:todo`: Sites without complete audits (no status on at least one field)
- `:completed`: Sites with at least one field audited

#### `::saving-audit?`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/saving-audit?])
```
Returns true when save operation is in progress.

#### `::has-audit-privilege?`
```clojure
@(rf/subscribe [:lipas.ui.ptv.subs/has-audit-privilege?])
```
Returns true if current user has `:ptv/audit` privilege.

## Backend Implementation

### Handler (src/clj/lipas/backend/ptv/handler.clj)

The handler defines the `/actions/save-ptv-audit` endpoint:

```clojure
["/actions/save-ptv-audit"
 {:post
  {:require-privilege :ptv/audit
   :parameters {:body [:map
                       [:lipas-id #'sports-sites-schema/lipas-id]
                       [:audit #'ptv-schema/audit-data]]}
   :handler
   (fn [req]
     (let [body (-> req :parameters :body)]
       (if-let [result (ptv-core/save-ptv-audit db search (:identity req) body)]
         {:status 200 :body result}
         {:status 404 :body {:error "Sports site not found"}})))}}]
```

**Authorization**: Uses `:ptv/audit` privilege check

### Business Logic (src/clj/lipas/backend/ptv/core.clj)

The `save-ptv-audit` function:

1. **Adds Metadata**:
   ```clojure
   (let [now (utils/timestamp)
         user-id (str (or (:id user) (get-in user [:login :user :id])))
         audit-with-meta (assoc audit
                                :timestamp now
                                :auditor-id user-id)]
     ...)
   ```

2. **Saves to Database**: Updates the sports site document with audit information

3. **Reindexes Search**: Ensures audit data is searchable (async)

4. **Returns Full Audit**: Returns audit data with timestamp and auditor-id

## Internationalization

### Translation Files

Translations are organized by language in `src/cljc/lipas/i18n/{lang}/ptv_audit.edn`:

#### Finnish (`fi/ptv_audit.edn`)
```clojure
{:headline "PTV tekstien auditointi"
 :description "Tässä näkymässä voit auditoida liikuntapaikkojen PTV-kuvauksia ja tiivistelmiä."
 :status "Tila"
 :feedback "Palaute"
 ;; ... more keys
 }
```

#### Swedish (`se/ptv_audit.edn`)
```clojure
{:headline "Granskning av PTV-texter"
 :description "I denna vy kan du granska PTV-beskrivningar och sammanfattningar för idrottsplatser."
 :status "Status"
 :feedback "Feedback"
 ;; ... more keys
 }
```

#### English (`en/ptv_audit.edn`)
```clojure
{:headline "PTV text auditing"
 :description "In this view you can audit PTV descriptions and summaries for sports facilities."
 :status "Status"
 :feedback "Feedback"
 ;; ... more keys
 }
```

### Usage in Components

```clojure
(let [tr (<== [:lipas.ui.subs/translator])]
  (tr :ptv.audit/headline))  ; Returns translated string
```

## Key Design Decisions

### 1. Closed Map Schemas with `select-keys` Validation

**Decision**: Use `{:closed true}` on schemas and `select-keys` in validation

**Rationale**:
- Closed maps catch typos and prevent accidental extra keys
- `select-keys` in subscription validates only user-editable fields
- Future-proof: automatically ignores any new metadata fields backend adds
- Clear separation between user input and stored data

**Alternative Considered**: Using `dissoc` to remove specific metadata keys
**Why Rejected**: Fragile - requires updating code for each new metadata field

### 2. Two Separate Schemas (audit-data vs ptv-audit)

**Decision**: Separate schemas for "what users send" vs "what we store"

**Rationale**:
- `audit-data`: Frontend → Backend payload (no metadata)
- `ptv-audit`: Backend → Frontend response and storage (with metadata)
- Clear contract between frontend and backend
- Backend adds timestamp/auditor-id - users can't manipulate these

**Alternative Considered**: Single schema with optional metadata
**Why Rejected**: Less explicit about who controls which fields

### 3. No Event-Level Validation

**Decision**: Validation in subscription only, not in save event

**Rationale**:
- Subscription controls button state - prevents invalid submissions
- Backend provides final validation safety net
- Avoids redundant validation logic
- Simpler event handler code

**See**: Defense in depth section for complete validation strategy

### 4. Initialize Feedback to Empty String

**Decision**: When status is set, ensure feedback exists (even if empty)

**Rationale**:
- Schema requires `:feedback` field (min 0, max 1000)
- User may select status without typing feedback
- Empty string is valid and semantically correct
- Prevents validation errors on save

**Implementation**:
```clojure
(rf/reg-event-db ::update-audit-status
  (fn [db [_ lipas-id field status]]
    (-> db
        (assoc-in [...path :status] status)
        (update-in [...path :feedback] #(or % "")))))
```

### 5. Single Save Button for Both Fields

**Decision**: One save button saves both summary and description audits

**Rationale**:
- Simpler UX - users don't need to save each field separately
- Atomic operation - both fields saved together or not at all
- Backend can add single timestamp for the audit session

**Alternative Considered**: Separate save buttons per field
**Why Rejected**: More complex UX, more API calls, inconsistent timestamps

## Testing

### Manual Testing Checklist

**Setup**:
1. User has `:ptv/audit` privilege
2. Organization selected with sports sites having PTV data

**Test Cases**:

**New Audit (Todo Tab)**:
- [ ] Can select a site from todo list
- [ ] Content displays correctly
- [ ] Can select status for summary
- [ ] Can write feedback (respects 1000 char limit)
- [ ] Character counter updates in real-time
- [ ] Save button enabled when at least one field has status
- [ ] Save button disabled when no status selected
- [ ] Save succeeds and shows success notification
- [ ] Site moves to completed tab after save

**Edit Existing Audit (Completed Tab)**:
- [ ] Can select a site from completed list
- [ ] Previous audit info displays correctly
- [ ] Can change status
- [ ] Can edit feedback
- [ ] Save button enabled for previously saved audits
- [ ] Save succeeds and updates audit data
- [ ] Timestamp and auditor-id preserved

**Validation**:
- [ ] Cannot type more than 1000 characters in feedback
- [ ] Character counter shows error when at limit
- [ ] Save button disabled when validation fails
- [ ] Empty feedback is allowed

**UI/UX**:
- [ ] Status indicators show correct colors (green/orange/blue)
- [ ] Site counts update correctly in tabs
- [ ] Selection state persists when switching tabs
- [ ] No caret jumping in textarea (uses patched component)

### Automated Testing

Test the Malli schemas:
```clojure
(require '[malli.core :as m]
         '[lipas.schema.sports-sites.ptv :as ptv-schema])

;; Valid audit-data
(m/validate ptv-schema/audit-data
            {:summary {:status "approved"
                       :feedback ""}})
;; => true

;; Invalid - missing feedback
(m/validate ptv-schema/audit-data
            {:summary {:status "approved"}})
;; => false

;; Valid ptv-audit (with metadata)
(m/validate ptv-schema/ptv-audit
            {:summary {:status "approved"
                       :feedback ""}
             :timestamp "2025-11-17T10:30:00.000Z"
             :auditor-id "user-123"})
;; => true
```

## Troubleshooting

### Save Button Always Disabled

**Symptoms**: Save button never enables, even with valid input

**Possible Causes**:
1. No status selected for any field
2. Validation subscription returning false
3. `saving?` flag stuck as true

**Debug**:
```clojure
;; In browser console
@(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data lipas-id])
@(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data-valid? lipas-id])
@(rf/subscribe [:lipas.ui.ptv.subs/saving-audit?])
```

### Feedback Text Not Saving

**Symptoms**: Status saves but feedback is lost

**Possible Causes**:
1. Feedback not initialized when status set
2. Event dispatched with wrong parameters

**Fix**: Ensure `::update-audit-status` initializes feedback:
```clojure
(update-in [...path :feedback] #(or % ""))
```

### Validation Errors After Backend Save

**Symptoms**: Save succeeds but validation fails on subsequent edits

**Cause**: Backend adds metadata that fails closed map validation

**Fix**: Subscription should use `select-keys` to validate only user fields:
```clojure
(select-keys audit-data [:summary :description])
```

## Future Enhancements

### Potential Improvements

1. **Bulk Audit**: Audit multiple sites at once
2. **Audit History**: Show full audit trail with all previous audits
3. **Comments**: Allow auditors to comment on specific parts of text
4. **Notifications**: Notify content creators when audits are completed
5. **Reports**: Generate audit statistics and reports
6. **Multi-language Audit**: Support auditing Swedish and English content
7. **Collaborative Audit**: Multiple auditors can review same content
8. **Audit Templates**: Predefined feedback templates for common issues

### Migration Considerations

If changing the schema:
1. Consider backward compatibility with existing audit data
2. Write database migration if structure changes
3. Update both frontend and backend schemas together
4. Test with existing audit data before deploying

## Related Documentation

- [PTV Integration](./ptv-integration.md) - Overall PTV integration documentation
- [Internationalization](./i18n.md) - Translation system documentation
- [Roles and Privileges](../src/cljc/lipas/roles.clj) - Authorization system
- [Malli Schemas](../src/cljc/lipas/schema/) - Data validation schemas
