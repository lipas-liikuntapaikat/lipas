(ns lipas.backend.db.sports-site
  (:refer-clojure :exclude [get])
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(defn marshall
  ([sports-site user]
   (marshall sports-site user "published"))
  ([sports-site user status]
   (->
    {:event-date    (-> sports-site :event-date)
     :lipas-id      (-> sports-site :lipas-id)
     :status        status
     :type-code     (-> sports-site :type :type-code)
     :city-code     (-> sports-site :location :city :city-code)
     :author-id     (-> user :id)
     ;; "on whose behalf" — set by the caller for org/take-over/grant edits;
     ;; kept out of the document body (it's per-revision audit metadata).
     :acting-org-id (-> sports-site :acting-org-id)}
    utils/->snake-case-keywords
    (assoc :document (dissoc sports-site :acting-org-id)))))

(defn unmarshall [{:keys [document author_id status acting_org_id] :as doc}]
  (when doc
    (with-meta document {:author-id     author_id
                         :doc-status    status
                         :acting-org-id acting_org_id})))

(hugsql/def-db-fns "sql/sports_site.sql")
