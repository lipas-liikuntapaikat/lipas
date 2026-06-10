(ns lipas.migrations.org-type-association
  "Rename the org `:type` enum value \"sports-federation\" -> \"association\"
  (manual-review item F35: \"Lajiliitto\" is too narrow; the broader concept is
  \"Yhdistys\"). The enum was introduced in the unreleased org-management PR, so
  renaming the value is allowed — but rows written with the old value (lipas-dev
  has some) must be rewritten.

  Unlike org-roles-unify this rewrites ALL revisions of the append-only `org`
  event log IN PLACE (not by appending a new revision): the old value never
  shipped, and leaving it in historical revisions would keep schema-invalid
  documents in the log (history views validate/translate the type label).
  `org_current` is a view over `org`, so current rows are covered by the same
  UPDATE. Self-contained (raw SQL, no lipas.backend.org calls) and idempotent —
  the WHERE clause makes a re-run a no-op."
  (:require [next.jdbc :as jdbc]
            [taoensso.timbre :as log]))

(defn- rewrite-type!
  "Rewrite document->>'type' = `from` to `to` across all org revisions.
  Returns the number of rows updated."
  [db from to]
  (-> (jdbc/execute-one!
        db
        [(str "UPDATE org"
              "   SET document = jsonb_set(document, '{type}', to_jsonb(?::text))"
              " WHERE document->>'type' = ?")
         to from])
      :next.jdbc/update-count))

(defn migrate-up [{:keys [db]}]
  (log/info "org-type-association: rewriting org type sports-federation -> association")
  (let [n (rewrite-type! db "sports-federation" "association")]
    (log/info "org-type-association: rewrote" n "org revisions")))

(defn migrate-down [{:keys [db]}]
  (log/info "org-type-association: reverting org type association -> sports-federation")
  (let [n (rewrite-type! db "association" "sports-federation")]
    (log/info "org-type-association: reverted" n "org revisions")))

(comment
  (require '[user])
  (def test-db (:lipas/db integrant.repl.state/system))
  (migrate-up {:db test-db})
  (migrate-down {:db test-db}))
