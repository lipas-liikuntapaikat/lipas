(ns lipas.backend.db.sports-site
  (:require [hugsql.core :as hugsql]
            [lipas.backend.db.utils :as utils]))

(defn marshall [sports-site user]
  (->
   {:document  sports-site
    :lipas-id  (-> sports-site :lipas-id)
    :status    (-> sports-site :status)
    :type-code (-> sports-site :type :type-code)
    :city-code (-> sports-site :location :city :city-code)
    :author-id (:id user)}
   utils/->snake-case-keywords))

(defn unmarshall [{:keys [document]}]
  (-> document
      utils/->kebab-case-keywords))

(hugsql/def-db-fns "sql/sports_site.sql")
