# LIPAS Frontend Documentation

## Overview

LIPAS (lipas.fi) is a comprehensive sports facility management system with a ClojureScript-based frontend built on modern functional reactive programming principles. The application provides extensive functionality for managing sports facilities, analyzing accessibility, tracking energy consumption, and providing statistical insights.

**Codebase Maturity**: This is a mature production codebase that has been actively developed and maintained for over 7 years.

## Technology Stack

### Core Technologies
- **ClojureScript**: Primary language for frontend development
- **Re-frame**: State management and event handling framework (v1.4.3)
- **Reagent**: React wrapper for ClojureScript (v2.0.0-alpha2) - **Preferred approach**
- **UIx**: Modern React hooks integration (v1.1.1) - Limited use recommended
- **Shadow-cljs**: Build tool and hot-reload development (v2.28.16)

#### React Library Context
**Important**: UIx was introduced approximately one year ago when Reagent's future React 19+ support was uncertain. Now that Reagent supports modern React versions, the codebase contains a mix of both approaches. While both libraries interoperate well, **new development should prefer Reagent with traditional Re-frame patterns** for consistency and maintainability. UIx usage should be limited to specific cases where React hooks provide clear benefits.

### UI Framework
- **Material-UI (MUI)**: Component library for consistent UI design
  - Legacy wrapper: `lipas.ui.mui` namespace contains redundant wrappers
  - Modern approach: Import MUI components directly in namespaces as needed
- **Custom JYU theme**: Branded dark/light themes with University of Jyväskylä colors
- **Reusable Components**: Custom component library in `lipas.ui.components.*`
  - `autocompletes.cljs` - Autocomplete components
  - `buttons.cljs` - Button variants
  - `checkboxes.cljs` - Checkbox components
  - `dialogs.cljs` - Dialog utilities
  - `forms.cljs` - Form layouts and helpers
  - `layouts.cljs` - Layout components
  - `lists.cljs` - List components
  - `misc.cljs` - Miscellaneous utilities
  - `notifications.cljs` - Notification system
  - `selects.cljs` - Select/dropdown components
  - `tables.cljs` - Table components
  - `text_fields.cljs` - Text field variants

### Data Visualization
- **Recharts**: Primary charting library for interactive visualizations
- **D3.js**: Advanced data manipulation and custom visualizations
- **Custom chart components**: Specialized charts for energy, finance, and statistics

### Supporting Libraries
- **Reitit**: Frontend routing with coercion support
- **Tongue**: Internationalization (i18n) for Finnish, Swedish, and English
- **Malli**: Schema validation and coercion (actively replacing Spec)
- **Spec**: Legacy validation (being phased out)
- **Ajax/HTTP**: day8.re-frame/http-fx for API communication

#### Validation Library Transition
The codebase is transitioning from Clojure Spec to Malli for data validation. **All new code must use Malli** for validation and schemas. Spec remains in legacy code but is being systematically replaced. When refactoring existing code, consider migrating Spec validations to Malli schemas.

## Architecture Patterns

### 1. Feature-Based Module Organization

The frontend follows a clean feature-based architecture where each major feature is self-contained:

```
src/cljs/lipas/ui/
├── [feature-name]/
│   ├── db.cljs        # Feature-specific database schema
│   ├── events.cljs    # Re-frame event handlers
│   ├── subs.cljs      # Re-frame subscriptions
│   ├── views.cljs     # UI components
│   └── routes.cljs    # Feature routing (if applicable)
```

**Key Features Modules:**
- `sports-sites/` - Core sports facility management
- `map/` - Interactive map functionality with editing
- `analysis/` - Diversity, heatmap, and reachability analysis
- `energy/` - Energy consumption tracking
- `stats/` - Statistical analysis and reporting
- `ptv/` - PTV (Palvelutietovaranto) integration
- `accessibility/` - Accessibility statement management

### 2. State Management (Re-frame)

#### State Management Principles

**Critical Best Practice**: State placement follows a simple rule:
- **Component-local state (r/atom)**: Use ONLY for ephemeral UI state that the rest of the application never needs (e.g., dropdown open/closed, hover states, temporary form input before validation)
- **Global state (app-db)**: Use for ALL other state via Re-frame events and subscriptions

If there's any possibility that another part of the application might need the state, it belongs in app-db. This ensures predictable data flow and easier debugging.

#### Global State Structure
```clojure
{:active-panel    :front-page-panel
 :logged-in?      false
 :translator      (i18n/->tr-fn :fi)
 :user            {...}
 :sports-sites    {...}
 :map             {...}
 :analysis        {...}
 ;; Feature-specific sub-states
}
```

#### Event Patterns
- **Naming Convention**: `::feature/action-name`
- **HTTP Events**: Paired success/failure handlers
- **Interceptors**: Token validation, logging

```clojure
;; Standard event pattern
(rf/reg-event-fx ::save-sports-site
  [interceptors/check-token]
  (fn [{:keys [db]} [_ site]]
    {:db (assoc-in db [:sports-sites :saving?] true)
     :http-xhrio {...}}))
```

#### Subscription Patterns
- **Layer 2 Subscriptions**: Direct DB queries
- **Layer 3 Subscriptions**: Computed/derived data
- **Memoization**: Built-in Re-frame caching

### 3. Component Patterns

#### Reagent Components (Preferred)
```clojure
(defn component-name
  "Documentation string"
  [{:keys [prop1 prop2]}]  ; Props destructuring
  ;; Use r/atom ONLY for truly local UI state
  (let [dropdown-open? (r/atom false)  ; OK: Pure UI state
        ;; Everything else via subscriptions
        form-data    (<== [:form-data])  ; Good: Shared state
        user         (<== [:current-user])]
    (fn [{:keys [prop1 prop2]}]  ; Render function
      [mui/paper
       [child-component {:data form-data}]])))
```

#### UIx Hooks Integration (Use Sparingly)
```clojure
;; Only use when hooks provide clear benefits
(defn hook-based-component []
  (let [data (use-subscribe [:subscription-query])]
    [:div data]))
```

### 4. Available Re-frame Effects

The application provides several custom Re-frame effects for side-effects management:

#### Navigation & UI Effects
- `::effects/navigate!` - Programmatic navigation
- `::effects/reset-scroll!` - Reset window scroll position
- `::effects/open-link-in-new-window!` - Open external links

#### File & Data Export
- `::effects/download-excel!` - Generate and download Excel files
- `::effects/save-as!` - Save blob data as file

#### Local Storage
- `::local-storage/set!` - Persist data to localStorage
- `::local-storage/remove!` - Remove localStorage data
- `::local-storage/get` - (Co-effect) Read from localStorage

#### Analytics & Tracking
- `:tracker/page-view!` - Track page views in Matomo
- `:tracker/event!` - Track custom events
- `:tracker/search!` - Track search queries
- `:tracker/set-dimension!` - Set custom dimensions

#### Geolocation
- `::effects/request-geolocation!` - Request user's location

#### Built-in Effects
- `:http-xhrio` - HTTP requests (via day8.re-frame/http-fx)
- `:dispatch` - Dispatch another event
- `:dispatch-n` - Dispatch multiple events
- `:db` - Update app-db

### 5. Routing Architecture

Using Reitit for declarative routing:

```clojure
["/"
 {:name :root}
 ["sports-sites"
  ["/:lipas-id" {:name :sports-site
                 :parameters {:path {:lipas-id int?}}}]]]
```

## Best Practices

### 1. Re-frame Event Handling

#### Event Organization
- **Namespace events** by feature: `::sports-sites/save`
- **Use interceptors** for cross-cutting concerns
- **Separate HTTP handling** into request/success/failure triplets

#### State Management Rules
1. **Never use component-local state for shared data** - If multiple components need it, use app-db
2. **Minimize r/atom usage** - Default to subscriptions unless truly local
3. **Document state shape** - Define schemas in db.cljs files
4. **Use derived subscriptions** - Compute data in subscriptions, not components

### 2. Component Design

#### Controlled Components
All form inputs use controlled components with Re-frame state:

```clojure
;; Good: Form state in app-db
[text-field {:value     (<== [:form-field :email])
             :on-change #(==> [:update-form-field :email %])}]

;; Bad: Form state in local atom (unless truly ephemeral)
(let [email (r/atom "")]
  [text-field {:value @email
               :on-change #(reset! email %)}])
```

#### Component Composition
- Small, focused components
- Props drilling minimized via subscriptions
- **Reusable UI components** in `lipas.ui.components.*` namespace
- **MUI imports**: Prefer direct imports over `lipas.ui.mui` wrapper

### 3. Data Flow

#### Unidirectional Data Flow
1. User interaction triggers event
2. Event handler updates app-db
3. Subscriptions detect changes
4. Components re-render automatically

#### API Integration Pattern
```clojure
Event dispatch → HTTP request → Success/Failure handler → DB update → UI update
```

### 4. Form Handling

#### Validation (Use Malli for New Code)
- **Client-side**: Malli schemas (new) or Spec validation (legacy)
- **Real-time feedback**: Error display on blur/change
- **Server validation**: Handled in HTTP response

```clojure
;; Modern approach with Malli
(def email-schema
  [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"])

;; Legacy code may still use Spec
(defn error? [spec value required]
  (if (and spec (or value required))
    (if (vector? spec)  ; Malli schema
      ((complement m/validate) spec value)
      ((complement s/valid?) spec value))  ; Spec
    false))
```

### 5. Internationalization (i18n)

#### Translation Pattern
```clojure
(def tr (<== [:translator]))  ; Get current translator
(tr :sports-sites/title)      ; Use translation key
```

#### Locale Management
- Supports Finnish (fi), Swedish (se), English (en)
- Dynamic locale switching
- Localized data structures for cities, types, etc.

### 6. State Persistence

#### Local Storage
```clojure
;; Automatic persistence of login data
(rf/reg-fx ::local-storage/set!
  (fn [[k v]]
    (ls-set! k v)))
```

#### Session Management
- JWT token validation via interceptors
- Automatic logout on token expiration
- Role-based access control

### 7. Performance Optimizations

#### React Optimization
- **Reagent atom deref**: Only in render functions
- **Memoized subscriptions**: Automatic via Re-frame
- **React keys**: Proper key usage in lists

#### Code Splitting
- Shadow-cljs module system (configured but not fully utilized)
- Potential for lazy-loading heavy features

### 8. Error Handling

#### Global Error Boundary
- HTTP errors dispatched to notification system
- User-friendly error messages via i18n
- Automatic retry mechanisms for transient failures

#### Form Error Display
```clojure
[text-field {:error (error? spec value required)
             :helper-text (when error? (tr :error/invalid-input))}]
```

## UI/UX Patterns

### 1. Material Design Implementation

#### Custom Theme
```clojure
(def jyu-theme-light
  {:palette {:primary   {:main "#E51E56"}
             :secondary {:main "#009FC5"}}
   :typography {:fontFamily "Open Sans, sans-serif"}})
```

#### Responsive Design
- Grid system with breakpoints (xs, sm, md, lg, xl)
- Mobile-first approach
- Drawer navigation for mobile

### 2. Data Visualization

#### Chart Components
- **Energy charts**: Monthly/yearly consumption
- **Finance charts**: Budget analysis with drill-down
- **Statistics**: Age structure, sports participation
- **Map visualizations**: Heatmaps, accessibility zones

#### Interactive Features
- Click handlers for drill-down
- Tooltips with detailed information
- Legend filtering
- Export to Excel functionality

### 3. Map Integration

#### OpenLayers Integration
- Interactive facility placement
- Route editing for trails
- Geometry protection mechanisms
- Multi-layer support (satellite, street, terrain)

#### Editing Capabilities
```clojure
;; Geometry editing with protection
(defn start-editing [lipas-id]
  {:dispatch [:map/start-editing lipas-id]
   :map/enable-draw-tool! true})
```

### 4. Accessibility Features

#### WCAG Compliance
- Semantic HTML via MUI components
- ARIA labels and roles
- Keyboard navigation support
- Screen reader compatibility

#### Accessibility Statements
- Integration with external accessibility service
- Display of facility accessibility information
- Editable statements for authorized users

## Development Workflow

### 1. Hot Reload Development

```bash
# Start shadow-cljs watch
npx shadow-cljs watch app

# REPL connection
npx shadow-cljs cljs-repl app
```

### 2. State Inspection

#### Re-frame 10x
- Enabled in development
- Event replay
- State inspection
- Performance profiling

### 3. Testing Approach

While test files weren't examined, the architecture supports:
- Unit tests for pure functions
- Integration tests for event handlers
- Component testing with React Testing Library

Currently no automated UI tests exist.

## Security Patterns

### 1. Authentication

#### JWT Token Management
```clojure
(defn jwt-expired? [token]
  (let [now (-> (js/Date.) .getTime (/ 1000))
        exp (-> token decode-jwt-payload :exp)]
    (> now exp)))
```

### 2. Authorization

#### Role-Based Access
```clojure
(defn permission-to-edit? [user facility]
  (or (roles/admin? user)
      (roles/owner? user facility)))
```

### 3. Data Sanitization

- XSS prevention via React
- Input validation with Malli (new) / Spec (legacy)
- SQL injection prevention (backend)

## Common Utilities

### 1. Subscription/Dispatch Helpers

```clojure
(def <== (comp deref rf/subscribe))  ; Subscribe helper
(def ==> rf/dispatch)                 ; Dispatch helper
```

### 2. Data Transformation

```clojure
;; Excel export utilities
(defn ->excel-data [headers coll]
  (let [header-row (->excel-row headers (into {} headers))]
    (into [header-row]
          (mapv (partial ->excel-row headers) coll))))
```

### 3. Date/Time Handling

```clojure
(defn ->human-date [iso-string]
  (when iso-string
    (let [date (js/Date. iso-string)]
      (.toLocaleDateString date "fi-FI"))))
```

## Monitoring and Analytics

### 1. User Analytics

#### Matomo Integration
```clojure
(rf/reg-fx :tracker/page-view!
  (fn [[path]]
    (.push (.-_paq js/window) #js ["trackPageView" path])))
```

### 2. Error Tracking

- Console logging in development
- Structured error events
- User feedback mechanism

## Migration Guidelines

### Moving from Spec to Malli

When refactoring code that uses Spec:

```clojure
;; Old (Spec)
(s/def ::email (s/and string? #(re-matches #"^[^\s@]+@[^\s@]+\.[^\s@]+$" %)))

;; New (Malli)
(def email-schema
  [:and
   :string
   [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"]])
```

### Moving from UIx to Reagent

When refactoring UIx components:

```clojure
;; UIx approach (avoid for new code)
(defn uix-component []
  (let [data (use-subscribe [:data])]
    [:div data]))

;; Reagent approach (preferred)
(defn reagent-component []
  (let [data (<== [:data])]
    [:div data]))
```

### Moving from MUI Wrapper to Direct Imports

The `lipas.ui.mui` wrapper is legacy. For new code, import MUI components directly:

```clojure
;; Old (wrapper approach)
(ns my-ns
  (:require [lipas.ui.mui :as mui]))

[mui/button {:variant "contained"} "Click"]

;; New (direct import)
(ns my-ns
  (:require ["@mui/material/Button$default" :as Button]
            [reagent.core :as r]))

(def button (r/adapt-react-class Button))
[button {:variant "contained"} "Click"]
```

Note: The wrapper still works and is used throughout the codebase, but direct imports reduce indirection and make dependencies clearer.

## Future Improvements

### Recommended Enhancements

1. **Complete Malli Migration**: Finish replacing all Spec validations
2. **Standardize on Reagent**: Gradually refactor UIx components to Reagent
3. **Code Splitting**: Implement lazy loading for large features
4. **Testing Coverage**: Expand automated testing
5. **Performance**: Implement virtual scrolling for large lists
6. **Offline Support**: Add service worker for offline functionality
7. **Component Library**: Extract reusable components
8. **Documentation**: Add inline documentation and examples

## Conclusion

The LIPAS frontend represents 7+ years of production-tested ClojureScript development, demonstrating:
- Mature architectural patterns that have scaled with the application
- Clean separation of concerns with feature-based modules
- Predictable state management with clear rules
- Pragmatic technology choices with ongoing modernization
- Strong emphasis on maintainability and developer experience

The codebase successfully balances functional programming principles with real-world requirements, resulting in a robust and extensible application that continues to evolve with modern web standards while maintaining backwards compatibility and stability.
