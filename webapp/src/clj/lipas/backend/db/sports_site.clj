(ns lipas.backend.db.sports-site
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(defn marshall
  ([sports-site user]
   (marshall sports-site user "published"))
  ([sports-site user status]
   (->
    {:event-date  (-> sports-site :event-date)
     :lipas-id    (-> sports-site :lipas-id)
     :status      status
     :type-code   (-> sports-site :type :type-code)
     :city-code   (-> sports-site :location :city :city-code)
     :author-id   (-> user :id)}
    utils/->snake-case-keywords
    (assoc :document sports-site))))

(defn unmarshall [{:keys [document author_id status] :as doc}]
  (when doc
    (with-meta document {:author-id author_id :doc-status status})))

(hugsql/def-db-fns "sql/sports_site.sql")
