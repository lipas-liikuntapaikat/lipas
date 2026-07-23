(ns lipas.migrations.help-v1-to-v2
  "One-shot migration: publishes the v1 shared-structure help document as
  the fi help-v2 tree (regenerated slugs, old slugs kept as aliases) and
  empty se/en trees. v1 history is left untouched. See lipas.backend.help
  for the transform; this just wires it into the migration sequence so it
  runs automatically instead of via a manual REPL step.

  Safe to run against an environment with no v1 data (nothing to do) or
  one already migrated (skipped) - both are logged, not thrown."
  (:require [lipas.backend.db.db :as db]
            [lipas.backend.help :as help]
            [taoensso.timbre :as log]))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: help-v1-to-v2")
  (cond
    (nil? (db/get-versioned-data db "help" "active"))
    (log/info "No v1 help data found, nothing to migrate")

    (some #(seq (db/get-versioned-data db % "active"))
          (vals help/locale->type))
    (log/info "help-v2 content already present, skipping")

    :else
    (log/info "Migration complete: help-v1-to-v2" (help/migrate-v1->v2! db))))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for help-v1-to-v2 migration"))

(comment
  ;; Test locally
  (require '[user])
  (def test-db (user/db))

  ;; Dry-run checks the migration itself will make
  (db/get-versioned-data test-db "help" "active")
  (some #(seq (db/get-versioned-data test-db % "active")) (vals help/locale->type))

  ;; Run it
  (migrate-up {:db test-db})

  ;; Verify
  (help/get-help-data test-db))
