(ns lipas.backend.db.user
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(defn marshall [{:keys [history] :as user}]
  (-> user
      (dissoc :history)
      (utils/->snake-case-keywords)
      (assoc :history history)))

(defn unmarshall [{:keys [history] :as user}]
  (when (not-empty user)
    (-> user
        (dissoc :history)
        (utils/->kebab-case-keywords)
        (assoc :history history))))

(hugsql/def-db-fns "sql/user.sql")
