(ns lipas.repl
  "Utilities for reloaded workflow using `integrant.repl`."
  (:require
   [clojure.tools.namespace.repl]
   [integrant.core :as ig]
   [integrant.repl]
   [integrant.repl.state]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.system :as system]
   [lipas.integration.core :as integrations]
   [lipas.maintenance :as maintenance]
   [lipas.search-indexer :as search-indexer]
   [lipas.worker :as worker]))

(def dev-config (dissoc config/system-config :lipas/nrepl))

(integrant.repl/set-prep! #(identity dev-config))
#_(clojure.tools.namespace.repl/set-refresh-dirs "/src")

(defn current-config []
  integrant.repl.state/config)

(defn current-system []
  integrant.repl.state/system)

(defn assert-running-system []
  (assert (current-system) "System is not running. Start the system first."))

(defn current-config []
  integrant.repl.state/config)

(defn start! []
  (integrant.repl/go))

(defn stop! []
  (integrant.repl/halt))

(defn resume! []
  (integrant.repl/resume))

(defn reload!
  "Reloads code for changed files in classpath and restarts the system."
  []
  (integrant.repl/reset))

(defn reload-all!
  "Reloads code for all files in classpath and restarts the system."
  []
  (integrant.repl/reset-all))

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
  (search-indexer/main (db) (search) "search"))

(defn reindex-analytics!
  []
  (search-indexer/main (db) (search) "analytics"))

(defn reset-password!
  [email password]
  (let [user (core/get-user (db) email)]
    (core/reset-password! (db) user password)))

(defn reset-admin-password!
  [password]
  (reset-password! "admin@lipas.fi" password))

(comment
  (start!)
  (reload!)
  (reindex-search!)
  (reindex-analytics!)
  (reset-admin-password! "kissa13")
  )
