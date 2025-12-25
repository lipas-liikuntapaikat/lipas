# LIPAS Frontend Quick Reference

## Important Guidelines

### State Management Rules
**Critical**: Follow these rules for state placement:
1. **Component-local state (r/atom)**: Use ONLY for ephemeral UI state that NO other component needs
   - Examples: dropdown open/closed, hover states, temporary input before validation
2. **Global state (app-db)**: Use for EVERYTHING else via Re-frame events/subscriptions
   - Examples: form data, user data, API responses, shared UI state

**When in doubt, use app-db!** It's easier to debug and maintain.

### Technology Preferences
- **Validation**: Use Malli for all new code (Spec is legacy)
- **Components**: Prefer Reagent over UIx (UIx was experimental, Reagent now supports React 19+)
- **State**: Prefer Re-frame subscriptions over local atoms

## Project Commands

```bash
# Development
npx shadow-cljs watch app        # Start dev server with hot reload
npx shadow-cljs cljs-repl app   # Connect CLJS REPL

# Build
npx shadow-cljs release app      # Production build

# Server
lein run                         # Start backend server (port 8091)
```

## Common Patterns

### Creating a New Feature Module

1. **Create directory structure:**
```
src/cljs/lipas/ui/my_feature/
├── db.cljs
├── events.cljs  
├── subs.cljs
├── views.cljs
└── routes.cljs (optional)
```

2. **Define default DB state:**
```clojure
;; my_feature/db.cljs
(ns lipas.ui.my-feature.db)

(def default-db
  {:loading? false
   :data     nil
   :error    nil})
```

3. **Add to global DB:**
```clojure
;; ui/db.cljs
(def default-db
  {...
   :my-feature my-feature/default-db})
```

### Event Handler Patterns

```clojure
;; Simple DB update
(rf/reg-event-db ::set-value
  (fn [db [_ value]]
    (assoc-in db [:my-feature :value] value)))

;; HTTP request pattern
(rf/reg-event-fx ::fetch-data
  [interceptors/check-token]  ; Add interceptors
  (fn [{:keys [db]} [_ params]]
    {:db (assoc-in db [:my-feature :loading?] true)
     :http-xhrio {:method :get
                  :uri "/api/my-endpoint"
                  :params params
                  :on-success [::fetch-success]
                  :on-failure [::fetch-failure]}}))

(rf/reg-event-db ::fetch-success
  (fn [db [_ response]]
    (-> db
        (assoc-in [:my-feature :data] response)
        (assoc-in [:my-feature :loading?] false))))
```

### Subscription Patterns

```clojure
;; Simple subscription
(rf/reg-sub ::data
  (fn [db _]
    (-> db :my-feature :data)))

;; Parameterized subscription  
(rf/reg-sub ::item-by-id
  (fn [db [_ id]]
    (get-in db [:my-feature :items id])))

;; Derived subscription
(rf/reg-sub ::filtered-items
  :<- [::items]
  :<- [::filter]
  (fn [[items filter] _]
    (filter-items items filter)))
```

### Component Patterns

```clojure
;; Basic component (Reagent - PREFERRED)
(defn my-component []
  (let [data (<== [::subs/data])
        tr   (<== [:translator])]
    [mui/paper
     [mui/typography {:variant "h6"} 
      (tr :my-feature/title)]
     [display-data data]]))

;; Component with local state
;; IMPORTANT: Only use r/atom for UI-only state!
(defn stateful-component []
  (r/with-let [dropdown-open? (r/atom false)]  ; OK: Pure UI state
    (let [form-data (<== [::subs/form-data])]  ; Good: Shared state in app-db
      [:<>
       [mui/button {:on-click #(swap! dropdown-open? not)}
        "Toggle"]
       (when @dropdown-open?
         [mui/paper form-data])])))

;; Form component (state in app-db, not local atoms!)
(defn form-field [{:keys [value on-change label spec required]}]
  [text-field
   {:label     label
    :value     value     ; From subscription
    :required  required
    :spec      spec      ; Use Malli for new code!
    :on-change on-change ; Dispatches event
   }])
```

### Common Utilities Usage

```clojure
;; Subscription and dispatch helpers
(def <== (comp deref rf/subscribe))
(def ==> rf/dispatch)

;; Usage
(let [data (<== [:my-subscription])]
  [mui/button {:on-click #(==> [:my-event data])}
   "Click me"])

;; Date formatting
(utils/->human-date "2024-01-15T10:30:00Z")  ; "15.1.2024"

;; Excel export
(==> [:lipas.ui.effects/download-excel!
      {:filename "export.xlsx"
       :sheet {:name "Data"
               :data (utils/->excel-data headers rows)}}])
```

### Routing

```clojure
;; Define routes
(def routes
  ["/my-feature"
   {:name :my-feature-panel
    :controllers [{:start #(==> [::events/init])}]}
   ["/:id" {:name :my-feature-detail
            :parameters {:path {:id int?}}}]])

;; Navigate
(==> [:lipas.ui.events/navigate :my-feature-detail {:id 123}])
```

### Material-UI Components

```clojure
;; LEGACY: lipas.ui.mui wrapper (still works but redundant)
(ns lipas.ui.my-feature.views
  (:require [lipas.ui.mui :as mui]))

;; MODERN: Direct imports (preferred for new code)
(ns lipas.ui.my-feature.views
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Paper$default" :as Paper]
            [reagent.core :as r]))

;; Use Reagent adapter when needed
(def button (r/adapt-react-class Button))

;; Common components (using legacy wrapper for brevity)
[mui/grid {:container true :spacing 2}
 [mui/grid {:item true :xs 12 :md 6}
  [mui/paper {:style {:padding "1rem"}}
   [mui/typography {:variant "h6"} "Title"]
   [mui/button {:color "primary" 
                :variant "contained"
                :on-click handler}
    "Action"]]]]

;; Icons
[mui/icon-button {:on-click handler}
 [mui/icon "edit"]]
```

### Reusable Components

```clojure
;; Available in lipas.ui.components.*
(ns lipas.ui.my-feature.views
  (:require [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.forms :as forms]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.notifications :as notifications]
            [lipas.ui.components.tables :as tables]))

;; Example usage
[text-fields/text-field {:label "Email"
                         :value email
                         :spec email-schema  ; Malli!
                         :on-change handler}]

[forms/form {:read-only? false}
 {:label "Name" :value name}
 {:label "Email" :value email}]
```

### i18n Usage

```clojure
;; Get translator
(let [tr (<== [:translator])]
  (tr :my-key)                    ; Simple
  (tr :my-key-with-arg {:n 5})    ; With arguments
  (tr :nested/key))                ; Nested keys

;; Common keys structure
:my-feature/title "My Feature"
:my-feature/description "Description"
:actions/save "Save"
:actions/cancel "Cancel"
```

### Form Validation

```clojure
;; NEW CODE: Always use Malli
(def schema
  [:map
   [:name [:string {:min 1}]]
   [:email [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"]]
   [:age [:int {:min 0 :max 120}]]])

;; LEGACY: Spec (being phased out - migrate when refactoring)
;; (s/def ::email (s/and string? #(re-matches #"..." %)))

;; Validate in component
[text-field {:value value
             :spec schema  ; Malli schema (vector)
             :required true}]
```

### Available Re-frame Effects

```clojure
;; Navigation & UI
{:lipas.ui.effects/navigate! [:route-name {:params}]}
{:lipas.ui.effects/reset-scroll! true}
{:lipas.ui.effects/open-link-in-new-window! "https://..."}

;; File Export
{:lipas.ui.effects/download-excel! 
 {:filename "export.xlsx"
  :sheet {:name "Data" :data [[...]]}}
{:lipas.ui.effects/save-as! {:blob data :filename "file.txt"}}

;; Local Storage
{:lipas.ui.local-storage/set! [:key value]}
{:lipas.ui.local-storage/remove! :key}

;; Analytics (Matomo)
{:tracker/page-view! ["/path"]}
{:tracker/event! ["category" "action" "label" value]}
{:tracker/search! ["query" "category" result-count]}
{:tracker/set-dimension! [:user-type "admin"]}

;; Geolocation
{:lipas.ui.effects/request-geolocation! callback-fn}

;; Standard Re-frame & HTTP
{:http-xhrio {...}}  ; via day8.re-frame/http-fx
{:dispatch [:event]}
{:dispatch-n [[:event1] [:event2]]}
{:db new-db}
```

### HTTP Headers & Auth

```clojure
;; Automatic auth headers via interceptor
(rf/reg-event-fx ::secured-request
  [interceptors/check-token]  ; Validates & adds token
  (fn [{:keys [db]} _]
    {:http-xhrio {...}}))

;; Manual headers
{:http-xhrio {:headers {"Authorization" (str "Bearer " token)}}}
```

## File Structure Reference

```
webapp/
├── src/
│   ├── clj/          # Backend Clojure
│   ├── cljc/         # Shared Clojure/ClojureScript
│   └── cljs/         # Frontend ClojureScript
│       └── lipas/
│           └── ui/
│               ├── components/     # REUSABLE UI COMPONENTS
│               │   ├── autocompletes.cljs
│               │   ├── buttons.cljs
│               │   ├── text_fields.cljs
│               │   └── ... (forms, tables, etc.)
│               ├── mui.cljs        # Legacy MUI wrapper (prefer direct imports)
│               ├── [features]/     # Feature modules
│               ├── core.cljs       # App initialization
│               ├── db.cljs         # Global state schema
│               ├── events.cljs     # Global events
│               ├── subs.cljs       # Global subscriptions
│               ├── routes.cljs     # Routing configuration
│               ├── effects.cljs    # Custom effects
│               ├── interceptors.cljs # Custom interceptors
│               └── utils.cljs      # Utility functions
├── resources/
│   └── public/       # Static assets
├── shadow-cljs.edn   # Build configuration
└── deps.edn         # Dependencies
```

## Debugging Tips

### Re-frame 10x (Development)
- Press `Ctrl-H` to toggle panel
- View events, app-db, and subscriptions
- Time-travel debugging

### REPL Commands
```clojure
;; Inspect app-db
@(rf/subscribe [:db])

;; Dispatch events from REPL
(rf/dispatch [:my-event])

;; Check subscriptions
@(rf/subscribe [:my-sub])
```

### Common Issues

1. **Subscription not updating**: Check subscription chain and db path
2. **Event not firing**: Verify event registration and dispatch syntax  
3. **Component not re-rendering**: Ensure deref in render function
4. **HTTP failures**: Check interceptors and auth token
5. **Routing issues**: Verify route parameters and coercion

## Performance Tips

1. Use subscription layers appropriately
2. Avoid inline function creation in render
3. Use React keys in lists
4. Memoize expensive computations
5. Lazy-load large data sets
6. Use virtual scrolling for long lists

## Testing Patterns

```clojure
;; Event handler test
(deftest test-set-value
  (let [db (events/set-value {} [::events/set-value "test"])]
    (is (= "test" (get-in db [:my-feature :value])))))

;; Subscription test  
(deftest test-data-sub
  (rf-test/run-test-sync
    (rf/dispatch-sync [::events/set-data {:test "data"}])
    (is (= {:test "data"} @(rf/subscribe [::subs/data])))))
```

## Useful Links

- [Re-frame Documentation](https://day8.github.io/re-frame/)
- [Reagent Documentation](https://reagent-project.github.io/)
- [Shadow-cljs User Guide](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [Material-UI Components](https://mui.com/components/)
- [Reitit Routing](https://metosin.github.io/reitit/)