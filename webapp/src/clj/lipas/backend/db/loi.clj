(ns lipas.backend.db.loi
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(defn marshall
  [loi user]
  (->
   {:event-date (-> loi :event-date)
    :id         (-> loi :id)
    :status     (-> loi :status)
    :loi-type   (-> loi :loi-type)
    :author-id  (-> user :id)}
    utils/->snake-case-keywords
    (assoc :document loi)))

(defn unmarshall
  [{:keys [document]}]
  document)

(hugsql/def-db-fns "sql/loi.sql")

(comment
  (ns-publics *ns*)
  )
