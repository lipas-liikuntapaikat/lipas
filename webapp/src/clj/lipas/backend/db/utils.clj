(ns lipas.backend.db.utils
  (:require
   [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
   [camel-snake-kebab.extras :refer [transform-keys]]
   [cheshire.core :as j]
   [clojure.java.jdbc :as jdbc]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as rs])
  (:import
   (org.postgresql.util PGobject)
   (java.sql PreparedStatement)))

(comment (<-json (->json {:kissa "koira"})))
(def <-json #(j/decode % true))
(def ->json j/encode)

(defn ->kebab-case-keywords [user]
  (transform-keys ->kebab-case user))

(defn ->snake-case-keywords [user]
  (transform-keys ->snake_case user))

;;; Automatically transform clojure maps -> jsonb ;;;

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (some-> value <-json (with-meta {:pgtype type}))
      value)))

;; clojure.java.jdbc

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [m] (->pgobject m)))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentVector
  (sql-value [m] (->pgobject m)))

(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [^PGobject v _metadata _idx]
    (<-pgobject v)))

;; next.jdbc

(extend-protocol prepare/SettableParameter
    clojure.lang.IPersistentMap
    (set-parameter [m ^PreparedStatement s i]
      (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

(extend-protocol rs/ReadableColumn
    org.postgresql.util.PGobject
    (read-column-by-label [^PGobject v _]
      (<-pgobject v))
    (read-column-by-index [^PGobject v _2 _3]
      (<-pgobject v)))
