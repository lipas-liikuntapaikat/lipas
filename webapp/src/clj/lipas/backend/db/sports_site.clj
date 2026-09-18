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

(defn- strip-legacy-audit
  "Site audits moved to ptv_site_audit (migration 20260918100100); revisions
   written before that still carry [:ptv :audit]. Hide it so no reader —
   and no client round-tripping the document — sees a stale copy."
  [document]
  (cond-> document
    (get-in document [:ptv :audit]) (update :ptv dissoc :audit)))

(defn unmarshall [{:keys [id document author_id status acting_org_id] :as doc}]
  (when doc
    (with-meta (strip-legacy-audit document)
      {:id            id ; the revision's row id
       :author-id     author_id
       :doc-status    status
       :acting-org-id acting_org_id})))

(defn unmarshall-history-row
  "Like `unmarshall`, but also keeps the revision's own row id and
   created-at, needed to identify individual rows in the full
   (non-deduplicated) event log."
  [{:keys [id created_at document author_id status] :as doc}]
  (when doc
    (with-meta (strip-legacy-audit document)
      {:id id
       :created-at created_at
       :author-id author_id
       :doc-status status})))

(hugsql/def-db-fns "sql/sports_site.sql")
