(ns lipas.backend.db.reminder
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(defn marshall [entry]
  (utils/->snake-case-keywords entry))

(defn unmarshall [entry]
  (-> entry
      utils/->kebab-case-keywords))

(hugsql/def-db-fns "sql/reminder.sql")
