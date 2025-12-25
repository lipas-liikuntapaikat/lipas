# Backend Architecture

This document describes the LIPAS backend architecture built with Clojure, Ring, Reitit, and Integrant.

## System Overview

The backend follows a layered architecture:

```
HTTP Request
     ↓
┌─────────────────────────────────────────┐
│  Ring Handler (lipas.backend.handler)   │  ← Route definitions, middleware stack
├─────────────────────────────────────────┤
│  Middleware Stack                        │  ← Auth, CORS, coercion, exceptions
├─────────────────────────────────────────┤
│  Business Logic (lipas.backend.core)    │  ← Core operations, validations
├─────────────────────────────────────────┤
│  Data Layer                              │  ← PostgreSQL (db.db), Elasticsearch (search)
└─────────────────────────────────────────┘
```

## Key Files

| File | Purpose |
|------|---------|
| `src/clj/lipas/backend/system.clj` | Integrant component definitions and lifecycle |
| `src/clj/lipas/backend/config.clj` | Environment configuration |
| `src/clj/lipas/backend/handler.clj` | Ring handler and route definitions |
| `src/clj/lipas/backend/middleware.clj` | Custom middleware (auth, CORS, privileges) |
| `src/clj/lipas/backend/core.clj` | Business logic layer |
| `src/clj/lipas/backend/auth.clj` | Authentication backends |
| `src/clj/lipas/backend/jwt.clj` | JWT token creation and signing |
| `src/cljc/lipas/roles.cljc` | Authorization and privilege definitions |

## System Components (Integrant)

The system is configured using [Integrant](https://github.com/weavejester/integrant), a data-driven architecture with explicit dependency management.

### Component Dependency Graph

```
:lipas/db ─────────────────────────────────┐
                                           │
:lipas/emailer ──────────────────────────┐ │
                                         │ │
:lipas/search ────────────────────────┐  │ │
                                      │  │ │
:lipas/mailchimp ──────────────────┐  │  │ │
                                   │  │  │ │
:lipas/aws ─────────────────────┐  │  │  │ │
                                │  │  │  │ │
:lipas/ptv (ref :lipas/db) ──┐  │  │  │  │ │
                             │  │  │  │  │ │
                             ↓  ↓  ↓  ↓  ↓ ↓
                          :lipas/app ──────────→ :lipas/server
                                                 (Jetty on :8091)
```

### Component Initialization

Components are defined in `system.clj` using `ig/init-key`:

```clojure
;; Database connection pool
(defmethod ig/init-key :lipas/db [_ db-spec]
  (if (:dev db-spec)
    db-spec  ; No pooling in dev
    (db/setup-connection-pool db-spec)))

;; Elasticsearch client with auto-index creation
(defmethod ig/init-key :lipas/search [_ config]
  (let [client (search/create-cli config)]
    ;; Ensure all required indices exist
    {:client client
     :indices (:indices config)
     :mappings search/mappings}))

;; Ring application with injected dependencies
(defmethod ig/init-key :lipas/app [_ config]
  (handler/create-app config))

;; Jetty HTTP server
(defmethod ig/init-key :lipas/server [_ {:keys [app port]}]
  (jetty/run-jetty app {:port port :join? false}))
```

### Available System Components

| Component | Description |
|-----------|-------------|
| `:lipas/db` | PostgreSQL connection (HikariCP pool in prod, direct in dev) |
| `:lipas/search` | Elasticsearch client with index configurations |
| `:lipas/emailer` | SMTP email sender |
| `:lipas/mailchimp` | Newsletter integration |
| `:lipas/aws` | AWS credentials for S3 |
| `:lipas/ptv` | Finnish Public Service Directory integration |
| `:lipas/app` | Ring application handler |
| `:lipas/server` | Jetty HTTP server |
| `:lipas/nrepl` | nREPL server for development (port 7888) |

## Configuration

Configuration is managed in `config.clj` using environment variables:

```clojure
(defn env! [kw]
  "Throws exception if environment variable is missing"
  (if (contains? e/env kw)
    (kw e/env)
    (throw (Exception. (str "Environment variable not set: " kw)))))
```

### Required Environment Variables

**Database:**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `ENVIRONMENT` - "dev" or "prod" (affects connection pooling)

**Elasticsearch:**
- `SEARCH_HOST`, `SEARCH_USER`, `SEARCH_PASS`

**Email:**
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`
- `SMTP_USER`, `SMTP_PASS` (optional for local testing)

**Authentication:**
- `AUTH_KEY` - Secret key for JWT signing

See `.env.sample.sh` for the complete list.

## Handler and Routing

Routes are defined in `handler.clj` using [Reitit](https://github.com/metosin/reitit):

```clojure
(defn create-app [{:keys [db emailer search mailchimp aws ptv] :as ctx}]
  (ring/ring-handler
   (ring/router
    [["/api" {...}]
     (v1/routes ctx)   ; Legacy API
     (v2/routes ctx)]  ; Modern API
    {:data {:middleware [...]}})
   (ring/routes ...)))
```

### API Structure

| Path | Description |
|------|-------------|
| `/api/*` | Main application API (internal use) |
| `/v1/*` | Legacy public API (backwards compatible) |
| `/v2/*` | Modern public API |
| `/api/swagger.json` | OpenAPI specification |
| `/api/swagger-ui` | Interactive API documentation |

### Route Definition Pattern

Routes use Reitit's data-driven approach with middleware and coercion:

```clojure
["/api/sports-sites/:lipas-id"
 {:get
  {:coercion reitit.coercion.malli/coercion
   :parameters {:path {:lipas-id int?}
                :query [:map [:lang {:optional true} [:enum "fi" "en" "se" "all"]]]}
   :responses {200 {:body sports-site-schema/sports-site-compat}
               404 {:body [:map]}}
   :handler (fn [req]
              (let [lipas-id (-> req :parameters :path :lipas-id)
                    locale (-> req :parameters :query :lang keyword)]
                (if-let [res (core/get-sports-site2 search lipas-id locale)]
                  {:status 200 :body res}
                  {:status 404 :body {:message "Not found"}})))}}]
```

## Middleware Stack

The middleware stack is applied in order (first to last):

```clojure
{:middleware
 [params/wrap-params           ; Parse query and form params
  muuntaja/format-negotiate    ; Content negotiation
  muuntaja/format-response     ; Encode response body
  mw/cors-middleware           ; CORS headers
  exceptions-mw                ; Exception handling
  muuntaja/format-request      ; Decode request body
  coercion/coerce-response     ; Validate/coerce response
  coercion/coerce-request      ; Validate/coerce request params
  mw/privilege-middleware]}    ; Authorization check
```

### Custom Middleware

#### CORS Middleware (`mw/cors-middleware`)

Applied to routes with `:cors true`:

```clojure
(def cors-middleware
  {:name ::cors-middleware
   :compile
   (fn [route-data _opts]
     (when (:cors route-data)
       (fn [next-handler]
         (fn [request]
           (let [response (if (= :options (:request-method request))
                            {:status 200}
                            (next-handler request))]
             (add-cors-headers response))))))})
```

#### Privilege Middleware (`mw/privilege-middleware`)

Declarative authorization based on route data:

```clojure
;; Simple privilege check
["/users" {:require-privilege :users/manage ...}]

;; Context-aware privilege check (from request params)
["/orgs/:org-id"
 {:require-privilege [(fn [req] {:org-id (-> req :parameters :path :org-id)})
                      :org/manage]}]
```

The middleware:
1. Automatically applies `token-auth` and `auth` middleware
2. Extracts user identity from JWT token
3. Checks privilege using `lipas.roles/check-privilege`
4. Returns 403 if unauthorized

## Authentication & Authorization

LIPAS uses JWT tokens for authentication and a role-based permission system for authorization. The role system is defined in shared code (`lipas.roles`), enabling consistent permission checks on both backend and frontend.

**For complete details, see [Authentication & Authorization](auth.md).**

### Quick Reference

**Authentication methods:**
- Basic Auth for login (`/api/actions/login`)
- JWT tokens for all other authenticated requests (6-hour validity)
- Magic links for passwordless login (7-day validity)

**Authorization via middleware:**
```clojure
;; Simple privilege check
["/users" {:require-privilege :users/manage ...}]

;; Context-aware check (from request params)
["/orgs/:org-id" {:require-privilege [(fn [req] {:org-id (-> req :path :org-id)}) :org/manage]}]
```

**Programmatic checks:**
```clojure
(roles/check-privilege user
                       {:city-code 179 :type-code 1110}
                       :site/create-edit)
```

## Business Logic Layer

The `lipas.backend.core` namespace contains all business logic:

### Sports Site Operations

```clojure
;; Read operations
(get-sports-site db lipas-id)           ; From PostgreSQL
(get-sports-site2 search lipas-id)      ; From Elasticsearch (preferred)
(get-sports-site-history db lipas-id)   ; All revisions

;; Write operations
(save-sports-site! db search ptv user sports-site draft?)
```

### Save Flow

```
save-sports-site!
     │
     ├─→ check-permissions! (throws if unauthorized)
     │
     ├─→ jdbc/with-db-transaction
     │        │
     │        ├─→ upsert-sports-site! → PostgreSQL
     │        │
     │        ├─→ index! → Elasticsearch (main index)
     │        │
     │        ├─→ index-legacy-sports-place! → Elasticsearch (legacy index)
     │        │
     │        ├─→ jobs/enqueue-job! "elevation" (for routes)
     │        │
     │        ├─→ jobs/enqueue-job! "analysis"
     │        │
     │        └─→ sync-ptv! (if PTV integration enabled)
     │
     └─→ Return saved sports site
```

### Data Enrichment

Before indexing to Elasticsearch, sports sites are enriched with search metadata:

```clojure
(defn enrich* [sports-site]
  (assoc sports-site :search-meta
         {:name (->sortable-name (:name sports-site))
          :location {:wgs84-point ... :city {:name ...} :province {:name ...}}
          :type {:name ... :tags ... :main-category {:name ...}}
          :admin {:name ...}
          :owner {:name ...}
          ...}))
```

## Error Handling

### Exception Types

Custom exception types are thrown using `ex-info`:

```clojure
(throw (ex-info "Username is already in use!"
                {:type :username-conflict}))

(throw (ex-info "User doesn't have enough permissions!"
                {:type :no-permission}))
```

### Exception Handlers

Exception handlers map types to HTTP responses:

```clojure
(def exception-handlers
  {:username-conflict (exception-handler 409 :username-conflict)
   :email-conflict    (exception-handler 409 :email-conflict)
   :no-permission     (exception-handler 403 :no-permission)
   :user-not-found    (exception-handler 404 :user-not-found)
   :invalid-payload   (exception-handler 400 :invalid-payload)
   ::exception/default (exception-handler 500 :internal-server-error :print-stack)})
```

### Error Response Format

Standard error response:

```json
{
  "message": "User doesn't have enough permissions!",
  "type": "no-permission"
}
```

Legacy API (V1) uses a different format for validation errors:

```json
{
  "errors": {
    "fieldName": ["error message"]
  }
}
```

## Request/Response Flow

### Typical Request Lifecycle

```
1. HTTP Request arrives at Jetty
     ↓
2. Ring handler matches route
     ↓
3. Middleware stack (outside-in):
   - Parse params
   - Content negotiation
   - CORS check
   ↓
4. Request body decoded (JSON/Transit)
     ↓
5. Parameters coerced and validated (Malli)
     ↓
6. Authorization check (privilege-middleware)
     ↓
7. Handler function executes
     ↓
8. Response coerced and validated
     ↓
9. Response body encoded
     ↓
10. CORS headers added
     ↓
11. HTTP Response sent
```

### Exception Flow

```
Handler throws exception
     ↓
exceptions-mw catches it
     ↓
Look up handler by exception type
     ↓
Return appropriate HTTP response
     ↓
CORS headers added (always, even for errors)
```

## Development

### Starting the System

```clojure
;; In REPL
(user/reset)  ; Reloads code and restarts system
```

### Accessing Components

```clojure
;; Via integrant.repl.state
(def db (:lipas/db integrant.repl.state/system))
(def search (:lipas/search integrant.repl.state/system))

;; Via user namespace helpers
(user/db)
(user/search)
```

### Adding a New Route

1. Define handler in appropriate namespace
2. Add route to `handler.clj` or dedicated routes file
3. Add privilege to `lipas.roles/privileges` if needed
4. Add role with privilege to `lipas.roles/roles` if needed
5. Use `:require-privilege` in route data for authorization

### Adding a New Component

1. Define config in `config.clj`:
   ```clojure
   :new-component {:setting (env! :setting)}
   ```

2. Define init/halt in `system.clj`:
   ```clojure
   (defmethod ig/init-key :lipas/new-component [_ config] ...)
   (defmethod ig/halt-key! :lipas/new-component [_ instance] ...)
   ```

3. Add as dependency to `:lipas/app` if handlers need it:
   ```clojure
   :app {:new-component (ig/ref :lipas/new-component) ...}
   ```
