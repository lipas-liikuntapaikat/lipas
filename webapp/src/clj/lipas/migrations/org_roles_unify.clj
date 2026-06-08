(ns lipas.migrations.org-roles-unify
  "Collapse each org member's `{:org-role :templates}` split into a single
  `:roles` list (the refactor in docs/wip/org-management.md §13).

  Target per-member shape: `{:user-id … :roles [\"admin\" \"editor\" …]}` where
  `\"admin\"` is the reserved engine role (org management) and every other key is
  a role-template catalog key. `:roles` is `:templates` plus `\"admin\"` when the
  old `:org-role` was \"admin\"; membership itself still confers the `:org/member`
  baseline (projected, never stored).

  Self-contained on purpose: it reshapes the raw jsonb `document` with `next.jdbc`
  and never calls `lipas.backend.org/*`. That namespace evolves with the live
  schema, so calling it from a migration breaks a from-scratch run (see the
  ptv-organizations from-scratch bug). This migration runs AFTER `org-event-log`
  (which seeds old-shape members) on fresh DBs and over existing dev/staging DBs
  alike — one new `org` revision per org whose members actually change shape."
  (:require [lipas.backend.db.utils] ; jsonb <-> Clojure protocol extensions
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(defn- reshape-member
  "{:org-role .. :templates [..]} -> {:roles [..]} (admin folded in). Idempotent:
  a member already in `:roles` shape is left untouched, so a re-run never wipes
  the list back to `[]`."
  [m]
  (if (contains? m :roles)
    m
    (-> m
        (assoc :roles (cond-> (vec (:templates m))
                        (= "admin" (:org-role m)) (conj "admin")))
        (dissoc :org-role :templates))))

(defn- unshape-member
  "Inverse of reshape-member, for the down migration. Idempotent: a member
  already in old shape (no `:roles`) is left untouched."
  [m]
  (if (contains? m :roles)
    (let [roles (set (:roles m))]
      (-> m
          (assoc :org-role  (if (contains? roles "admin") "admin" "member")
                 :templates (vec (remove #{"admin"} (:roles m))))
          (dissoc :roles)))
    m))

(defn- reshape-all
  "Read every current org, apply `member-fn` to each member, and append one new
  revision per org whose members actually change. Returns the number of orgs
  rewritten."
  [db member-fn]
  (let [rows (jdbc/execute! db ["SELECT org_id, document FROM org_current"]
                            {:builder-fn rs/as-unqualified-kebab-maps})]
    (reduce
      (fn [n {:keys [org-id document]}]
        (let [members  (vec (:members document))
              reshaped (mapv member-fn members)]
          (if (= members reshaped)
            n
            (do
              (jdbc/execute! db
                             ["INSERT INTO org (org_id, author_id, status, document) VALUES (?,?,?,?)"
                              org-id nil "active" (assoc document :members reshaped)])
              (inc n)))))
      0 rows)))

(defn migrate-up [{:keys [db]}]
  (log/info "org-roles-unify: collapsing member :org-role + :templates -> :roles")
  (let [n (reshape-all db reshape-member)]
    (log/info "org-roles-unify: rewrote" n "orgs")))

(defn migrate-down [{:keys [db]}]
  (log/info "org-roles-unify: reverting member :roles -> :org-role + :templates")
  (let [n (reshape-all db unshape-member)]
    (log/info "org-roles-unify: reverted" n "orgs")))

(comment
  (require '[user])
  (def test-db (:lipas/db integrant.repl.state/system))
  (migrate-up {:db test-db})
  (migrate-down {:db test-db}))
