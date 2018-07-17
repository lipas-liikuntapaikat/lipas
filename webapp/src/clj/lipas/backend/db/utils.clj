(ns lipas.backend.db.utils
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as j]
            [clojure.java.jdbc :as jdbc])
  (:import [org.postgresql.util PGobject]))

(comment (<-json (->json {:kissa "koira"})))
(def <-json #(j/decode % true))
(def ->json j/encode)

(defn ->kebab-case-keywords [user]
  (transform-keys ->kebab-case user))

(defn ->snake-case-keywords [user]
  (transform-keys ->snake_case user))

;;; Automatically transform clojure maps -> jsonb ;;;

(defn ->pgobject [m]
  (doto (PGobject.)
    ;; eventually we should properly determine the actual type
    (.setType "jsonb")
    (.setValue (->json m))))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [m] (->pgobject m)))

(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (if (#{"jsonb" "json"} type)
        (<-json value)
        value))))
