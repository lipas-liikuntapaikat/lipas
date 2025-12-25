# Authentication & Authorization

> **Status**: Complete

This document describes LIPAS authentication (verifying user identity) and authorization (determining what actions users can perform).

## Overview

LIPAS uses JWT tokens for authentication and a role-based permission system for authorization. The role system is defined in shared Clojure/ClojureScript code (`lipas.roles`), enabling consistent permission checks on both backend and frontend.

## Key Files

| File | Purpose |
|------|---------|
| `src/clj/lipas/backend/jwt.clj` | JWT token creation and signing |
| `src/clj/lipas/backend/auth.clj` | Authentication backends (basic auth, token auth) |
| `src/cljc/lipas/roles.cljc` | Role definitions and `check-privilege` function (shared) |
| `src/clj/lipas/backend/middleware.clj` | Auth middleware for routes |
| `src/cljs/lipas/ui/login/events.cljs` | Frontend login flow and token refresh |
| `src/cljs/lipas/ui/user/subs.cljs` | Frontend permission subscriptions |

## JWT Token Flow

### Token Structure

Tokens are signed with HS512 using a secret from `AUTH_KEY` environment variable. The payload contains:

```clojure
{:id "user-uuid"
 :email "user@example.com"
 :username "username"
 :permissions {:roles [...]}
 :exp <expiration-instant>}
```

**Default expiration**: 6 hours

### Token Creation

```clojure
;; src/clj/lipas/backend/jwt.clj
(defn create-token [user & {:keys [terse? valid-seconds]}]
  ;; terse? = only include :id (for URL-embeddable tokens)
  ;; valid-seconds = custom expiration (default 6 hours)
  ...)
```

**Terse tokens** (used in magic links) only include `:id` and have 7-day validity.

### Token Refresh

Frontend automatically refreshes tokens every 15 minutes via `/actions/refresh-login`. The endpoint:
1. Validates the current token
2. Fetches fresh user data from database
3. Issues a new token with updated permissions

If refresh fails with 401/403, user is logged out.

## Login Methods

### 1. Password Authentication (Basic Auth)

```
POST /actions/login
Authorization: Basic base64(username:password)
```

**Backend flow** (`src/clj/lipas/backend/auth.clj`):
1. Look up user by email or username
2. Verify password hash using buddy-hashers
3. Strip password from response
4. Conform roles to use keywords and sets
5. Create JWT token
6. Return user data with token

### 2. Magic Link Authentication

```
POST /actions/order-magic-link
{:email "user@example.com", :variant :lipas, :login-url "..."}
```

**Flow**:
1. User requests magic link via email
2. Backend creates terse JWT token (7-day validity)
3. Email sent with link containing token
4. User clicks link, frontend calls `/actions/refresh-login` with token
5. Backend validates token, returns full user data with new token

### Token Storage

Frontend stores login data in localStorage:
- Key: `login-data`
- Contains: Full user object including token

On page load, frontend checks localStorage and attempts token refresh.

## Role System

### Privileges

Privileges are namespaced keywords representing specific actions:

```clojure
;; src/cljc/lipas/roles.cljc
(def privileges
  {:site/save-api      {:doc "Call save-sports-site API"}
   :site/create-edit   {:doc "Add and edit sports sites"}
   :site/view          {:doc "View sports sites"}
   :site/edit-any-status {:doc "Edit sites with any status"}
   :activity/view      {:doc "View activity data"}
   :activity/edit      {:doc "Edit activity data"}
   :loi/create-edit    {:doc "Create and edit other locations"}
   :floorball/view     {:doc "View floorball data"}
   :floorball/edit     {:doc "Edit floorball data"}
   :analysis-tool/use  {:doc "Use analysis tools"}
   :users/manage       {:doc "Manage users (admin)"}
   :org/member         {:doc "Organization member"}
   :org/manage         {:doc "Manage organization"}
   :ptv/manage         {:doc "Manage PTV integration"}
   :ptv/audit          {:doc "Audit PTV descriptions"}
   :help/manage        {:doc "Edit help content"}
   :jobs/manage        {:doc "Manage background jobs"}
   ...})
```

### Roles

Roles are bundles of privileges with optional context constraints:

| Role | Privileges | Context Keys |
|------|------------|--------------|
| `:admin` | All privileges (except `:org/member`) | None (global) |
| `:default` | `:site/view`, `:floorball/view` | None (all users) |
| `:city-manager` | Basic editing | `:city-code` (required) |
| `:type-manager` | Basic editing | `:type-code` (required) |
| `:site-manager` | Basic editing | `:lipas-id` (required) |
| `:activities-manager` | Activity editing | `:activity` (required) |
| `:floorball-manager` | Floorball editing | `:type-code` (optional) |
| `:analysis-user` | Analysis tools | None |
| `:ptv-manager` | PTV management | `:city-code` (required) |
| `:org-admin` | Organization management | `:org-id` (required) |

**Basic privileges** (shared by manager roles):
```clojure
(def basic #{:site/create-edit :site/save-api :activity/view :analysis-tool/use})
```

### User Permissions Structure

```clojure
{:permissions
 {:roles [{:role :city-manager
           :city-code #{179 91}}  ; Helsinki, Espoo
          {:role :type-manager
           :type-code #{1110}}    ; Ice rinks
          {:role :site-manager
           :lipas-id #{12345}}]}} ; Specific site
```

Context values are **sets** - a user can have permission for multiple cities, types, or sites under one role.

## Permission Checks

### The `check-privilege` Function

Central authorization function used on both backend and frontend:

```clojure
(defn check-privilege
  [user role-context required-privilege]
  ;; Returns true if user has the privilege for the given context
  ...)
```

**Parameters**:
- `user`: User map with `:permissions`
- `role-context`: Map like `{:city-code 179, :type-code 1110}`
- `required-privilege`: Keyword like `:site/create-edit`

**How it works**:
1. Iterate through user's roles
2. For each role, check if its context constraints match (or are absent)
3. Collect privileges from matching roles (always includes `:default` role)
4. Check if required privilege is in the set

### Context Matching Rules

A role matches a context when **all** its context constraints match:
- Missing constraint in role = always matches
- `::roles/any` in context = matches any value (for "can user do this anywhere?" checks)
- Set intersection for multi-value contexts (e.g., activities)

```clojure
;; Check if user can edit a specific site
(check-privilege user
                 {:city-code 179, :type-code 1110, :lipas-id 12345}
                 :site/create-edit)

;; Check if user can edit ANY site (for UI visibility)
(check-privilege user
                 {:city-code ::roles/any, :type-code ::roles/any}
                 :site/create-edit)
```

### Backend Usage

**Route-level middleware** (`src/clj/lipas/backend/middleware.clj`):

```clojure
;; Simple privilege check
["/admin/users"
 {:require-privilege :users/manage
  :handler ...}]

;; Privilege with context from request
["/org/:org-id/members"
 {:require-privilege [(fn [req] {:org-id (-> req :parameters :path :org-id)})
                      :org/manage]
  :handler ...}]
```

**Programmatic checks** (`src/clj/lipas/backend/core.clj`):

```clojure
(defn- check-permissions! [user sports-site draft?]
  (when-not (or draft?
                (new? sports-site)
                (roles/check-privilege user
                                       (roles/site-roles-context sports-site)
                                       :site/save-api))
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))
```

### Frontend Usage

**Re-frame subscription**:

```clojure
;; In component
(let [can-edit? @(rf/subscribe [:lipas.ui.user.subs/check-privilege
                                {:city-code 179}
                                :site/create-edit])]
  (when can-edit?
    [edit-button]))
```

**Direct function call** (in events):

```clojure
(roles/check-privilege (:login (:user db))
                       (roles/site-roles-context site)
                       :site/create-edit)
```

### Frontend Dev Overrides

During development, privileges can be overridden via the project devtools panel:

```clojure
;; ::user-data sub applies dev overrides
(rf/reg-sub ::user-data
  :<- [::user]
  :<- [::dev-overrides]
  (fn [[user overrides] _]
    (assoc (:login user) :dev/overrides overrides)))
```

The `check-privilege` function checks for overrides first:

```clojure
(if (contains? overrides required-privilege)
  (get overrides required-privilege)  ; Use override value
  (contains? privileges required-privilege))  ; Normal check
```

## Auto-Permission Assignment

When a user creates a new sports site without existing permission to it, they automatically receive `:site-manager` role for that site:

```clojure
;; src/clj/lipas/backend/core.clj
(defn ensure-permission! [db user {:keys [lipas-id] :as sports-site}]
  ;; If user doesn't have permission, add site-manager role
  (when (not regular-permission)
    (let [user (update-in user [:permissions :roles]
                          conj {:role :site-manager :lipas-id #{lipas-id}})]
      (db/update-user-permissions! db user))))
```

## Elasticsearch Query Filtering

For search queries, permissions can be applied as ES filters:

```clojure
;; src/cljc/lipas/roles.cljc
(defn wrap-es-query-site-has-privilege
  [query user required-privilege]
  ;; Wraps query to only return sites user can edit
  ;; Admin gets no filter; others get city/type/lipas-id filters
  ...)
```

This is used for "show only sites I can edit" search functionality.

## API Authentication

All authenticated API endpoints use JWT token authentication:

```
GET /api/endpoint
Authorization: Token <jwt-token>
```

The `token-auth` middleware extracts and validates the token, populating `:identity` in the request.

## Security Considerations

1. **Password hashing**: Uses buddy-hashers (bcrypt by default)
2. **Token signing**: HS512 with environment-configured secret
3. **Token expiration**: 6 hours default, checked on every request
4. **Refresh mechanism**: Prevents session hijacking with regular re-validation
5. **Permission checks**: Applied at both route and business logic levels
6. **No token storage on backend**: Stateless authentication
