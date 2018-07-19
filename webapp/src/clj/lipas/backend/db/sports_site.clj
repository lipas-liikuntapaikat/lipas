(ns lipas.backend.db.sports-site
  (:require [hugsql.core :as hugsql]
            [lipas.backend.db.utils :as utils]))

(defn marshall [sports-site user]
  (->
   {:event-date (-> sports-site :event-date)
    :lipas-id   (-> sports-site :lipas-id)
    :status     (-> sports-site :status)
    :type-code  (-> sports-site :type :type-code)
    :city-code  (-> sports-site :location :city :city-code)
    :author-id  (-> user :id utils/->uuid)}
   utils/->snake-case-keywords
   (assoc :document sports-site)))

(defn unmarshall [{:keys [document]}]
  document)

(hugsql/def-db-fns "sql/sports_site.sql")
