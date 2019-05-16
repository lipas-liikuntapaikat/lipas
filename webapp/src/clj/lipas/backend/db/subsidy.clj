(ns lipas.backend.db.subsidy
  (:require
   [hugsql.core :as hugsql]))

(defn marshall [subsidy]
  {:year (:year subsidy)
   :data subsidy})

(defn unmarshall [{:keys [data id]}]
  (assoc data :id id))

(hugsql/def-db-fns "sql/subsidy.sql")
