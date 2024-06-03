(ns lipas.backend.db.webhook
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(defn marshall [entry]
  (utils/->snake-case-keywords entry))

(defn unmarshall [entry]
  (utils/->kebab-case-keywords entry))

(hugsql/def-db-fns "sql/webhook.sql")
