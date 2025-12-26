(ns lipas.migrations.migrate-queues-to-jobs
  "Migration stub - the original migration has been run and the legacy
  queue tables have been dropped. This namespace exists only to satisfy
  the migratus migration reference.")

(defn migrate-up
  "No-op - migration already completed, legacy queues dropped."
  [_config]
  nil)

(defn migrate-down
  "No-op - cannot restore dropped queue data."
  [_config]
  nil)
