(ns user
  "Utilities for reloaded workflow using `integrant.repl`."
  (:require
   [clojure.tools.namespace.repl]
   [integrant.repl :refer [reset reset-all halt go]]
   [integrant.repl.state]))

(integrant.repl/set-prep! (fn []
                            (dissoc @(requiring-resolve 'lipas.backend.config/system-config) :lipas/nrepl)))
#_(clojure.tools.namespace.repl/set-refresh-dirs "/src")

(defn current-config []
  integrant.repl.state/config)

(defn current-system []
  integrant.repl.state/system)

(defn assert-running-system []
  (assert (current-system) "System is not running. Start the system first."))

(defn current-config []
  integrant.repl.state/config)

(defn db
  "Returns the :lipas/db key of the currently running system. Useful for
  REPL sessions when a function expects `db` as an argument."
  []
  (assert-running-system)
  (:lipas/db (current-system)))

(defn search
  "Returns the :lipas/search key of the currently running system. Useful
  for REPL sessions when a function expects `search` as an argument."
  []
  (assert-running-system)
  (:lipas/search (current-system)))

(defn reindex-search!
  []
  ((requiring-resolve 'lipas.backend.search-indexer/main) (db) (search) "search"))

(defn reindex-analytics!
  []
  ((requiring-resolve 'lipas.backend.search-indexer/main) (db) (search) "analytics"))

(defn reset-password!
  [email password]
  (let [user ((requiring-resolve 'lipas.backend.core/get-user) (db) email)]
    ((requiring-resolve 'lipas.backend.core/reset-password!) (db) user password)))

(defn reset-admin-password!
  [password]
  (reset-password! "admin@lipas.fi" password))

(comment
  (go)
  (reset)
  (reindex-search!)
  (reindex-analytics!)
  (reset-admin-password! "kissa13")

  (require '[migratus.core :as migratus])
  (migratus/create nil "activities_status" :sql)
  (migratus/create nil "roles" :edn)

  (require '[lipas.maintenance :as maintenance])
  (require '[lipas.backend.core :as core])

  (def robot (core/get-user (db) "robot@lipas.fi"))

  (maintenance/merge-types (db) (search) robot 4530 4510)
  (maintenance/merge-types (db) (search) robot 4520 4510)
  (maintenance/merge-types (db) (search) robot 4310 4320)

  )
