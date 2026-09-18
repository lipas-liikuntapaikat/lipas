(ns lipas.backend.ptv.audit
  "DVV's katselmointi verdicts on PTV texts — the business logic over the
   ptv_site_audit / ptv_service_audit tables. Audits are information about
   a site or service, not part of its document — and not part of the
   search index either: they are stored apart, joined into the responses
   of the PTV views (see with-site-audits / service-docs), and stripped
   from whatever clients send back. This namespace is a leaf (db accessors
   + lipas.data.ptv only) so both lipas.backend.core (the generic site
   save) and lipas.backend.ptv.core (the PTV endpoints) can call it."
  (:require [clojure.string :as str]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.db.ptv-service-audit :as ptv-service-audit-db]
            [lipas.backend.db.ptv-site-audit :as ptv-site-audit-db]
            [lipas.backend.ptv.integration :as ptv]
            [lipas.data.ptv :as ptv-data]
            [lipas.utils :as utils]))

;;; Sites ;;;

(defn strip-site-audit
  "Site without a [:ptv :audit] key. Clients round-trip the site documents
   the PTV views serve (audit included) and pre-move revisions still carry
   one in the database; neither may land in a revision."
  [site]
  (cond-> site
    (get-in site [:ptv :audit]) (update :ptv dissoc :audit)))

(defn with-site-audits
  "The sites with their current audit joined in as [:ptv :audit] — the
   shape the PTV views read it from — resolved in one query for the whole
   collection. Any audit already in a document is a legacy copy and is
   replaced."
  [db sites]
  (let [audit-by-lipas-id (ptv-site-audit-db/get-current-by-lipas-ids db (map :lipas-id sites))]
    (mapv (fn [site]
            (let [audit (get audit-by-lipas-id (:lipas-id site))]
              (cond-> (strip-site-audit site)
                audit (assoc-in [:ptv :audit] audit))))
          sites)))

(defn with-site-audit
  "One site with its current audit joined in (see with-site-audits)."
  [db site]
  (first (with-site-audits db [site])))

(defn save-site-audit!
  "Appends the auditor's verdicts on `site` (the current revision, as read
   from the database — its metadata carries the revision id) to
   ptv_site_audit and returns the stored audit map. The site document is
   untouched: no revision, no :event-date change (the PTV views compare
   :event-date against :last-sync to tell whether a site is in sync), and
   nothing to reindex — audits are joined in at read time."
  [db user site audit]
  (let [now (utils/timestamp)
        user-id (str (or (:id user) (get-in user [:login :user :id])))
        audit* (assoc audit :timestamp now :auditor-id user-id)]
    (ptv-site-audit-db/insert-audit! db {:lipas-id (:lipas-id site)
                                         :site-revision-id (:id (meta site))
                                         :auditor-id user-id
                                         :event-date now
                                         :document audit*})
    audit*))

;;; Services ;;;

(defn- resolve-service-revision!
  "The lineage's current ptv_service revision for the service, by PTV
   service id then by source-id. When the service has never been
   persisted, takes an initial content revision from live PTV so the
   lineage — and the audit's revision reference — exist. Only
   sourceId-bearing (LIPAS-managed or adopted) services form auditable
   lineages; nil otherwise."
  [db ptv {:keys [org-id ptv-org-id user-id now service-id source-id]}]
  (or (ptv-service-db/get-current-by-service-id db org-id service-id)
      (when source-id
        (ptv-service-db/get-current db org-id source-id))
      (when ptv-org-id
        (let [svc (ptv/get-service ptv ptv-org-id (str service-id))]
          (when-not (str/blank? (:sourceId svc))
            (let [doc (ptv-data/->service-document ptv-org-id svc)]
              (ptv-service-db/insert-service-rev!
                db
                {:org-id org-id
                 :source-id (:source-id doc)
                 :service-id service-id
                 :status "active"
                 :author-id user-id
                 :event-date now
                 :document doc})))))))

(defn save-service-audit!
  "Appends the auditor's verdicts on a PTV Service of `org` (a LIPAS org
   map) to ptv_service_audit, anchored to the lineage's current
   ptv_service revision, and returns the stored audit map — or nil when
   the service can't be resolved (see resolve-service-revision!)."
  [db ptv user org {:keys [service-id source-id audit]}]
  (let [now (utils/timestamp)
        user-id (or (:id user) (get-in user [:login :user :id]))
        current (resolve-service-revision! db ptv {:org-id (:id org)
                                                   :ptv-org-id (-> org :ptv-data :org-id)
                                                   :user-id user-id
                                                   :now now
                                                   :service-id service-id
                                                   :source-id source-id})]
    (when current
      (let [audit* (assoc audit :timestamp now :auditor-id (str user-id))]
        (ptv-service-audit-db/insert-audit!
          db
          {:org-id (:id org)
           :source-id (:source-id current)
           :service-id (or (:service-id current) service-id)
           :service-revision-id (:id current)
           :auditor-id user-id
           :event-date now
           :document audit*})
        audit*))))

(defn current-service-audits
  "service-id (string) -> current audit map for the org's audited
   services."
  [db org-id]
  (->> (ptv-service-audit-db/get-current-by-org db org-id)
       (into {} (map (juxt (comp str :service-id) :document)))))

(defn service-docs
  "The org's current ptv_service revisions with their current audit joined
   in as [:document :audit] — the shape the frontend joins with the live
   PTV service list."
  [db org-id]
  (let [audit-by-source-id (->> (ptv-service-audit-db/get-current-by-org db org-id)
                                (into {} (map (juxt :source-id :document))))]
    (->> (ptv-service-db/get-current-by-org db org-id)
         (mapv (fn [row]
                 (let [audit (get audit-by-source-id (:source-id row))]
                   (cond-> (-> row
                               (select-keys [:source-id :service-id :event-date :status :document])
                               (update :service-id str)
                               (update :event-date str))
                     audit (assoc-in [:document :audit] audit))))))))
