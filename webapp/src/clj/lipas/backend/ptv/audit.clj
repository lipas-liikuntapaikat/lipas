(ns lipas.backend.ptv.audit
  "DVV's katselmointi verdicts on PTV texts — the business logic over the
   ptv_site_audit / ptv_service_audit tables. Audits are information about
   a site or service, not part of its document — and not part of the
   search index either: they are stored apart, served by audit-only
   endpoints (site-audits / service-audits) that the frontend caches
   apart from its sites and services, and stripped from whatever clients
   send back. This namespace is a leaf (db accessors + lipas.data.ptv
   only) so both lipas.backend.core (the generic site save) and
   lipas.backend.ptv.core (the PTV endpoints) can call it; anything that
   needs the PTV API happens in ptv.core before calling in here."
  (:require [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.db.ptv-service-audit :as ptv-service-audit-db]
            [lipas.backend.db.ptv-site-audit :as ptv-site-audit-db]
            [lipas.backend.db.sports-site :as sports-site-db]
            [lipas.utils :as utils]))

;;; Sites ;;;

(defn site-audits
  "Current audits of the given sites, [{:lipas-id n :audit map} ...] —
   sites without one are absent. What /actions/fetch-ptv-site-audits
   serves."
  [db lipas-ids]
  (->> (ptv-site-audit-db/get-current-by-lipas-ids db lipas-ids)
       (mapv (fn [[lipas-id audit]] {:lipas-id lipas-id :audit audit}))))

(defn with-site-audits
  "The sites with their current audit joined in as [:ptv :audit], resolved
   in one query for the whole collection — for backend readers that need
   the merged view the frontend builds for itself (the notification data).
   Any audit already in a document is a legacy copy and is replaced."
  [db sites]
  (let [audit-by-lipas-id (ptv-site-audit-db/get-current-by-lipas-ids db (map :lipas-id sites))]
    (mapv (fn [site]
            (let [audit (get audit-by-lipas-id (:lipas-id site))]
              (cond-> (sports-site-db/strip-audit site)
                audit (assoc-in [:ptv :audit] audit))))
          sites)))

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

(defn save-service-audit!
  "Appends the auditor's verdicts on a PTV Service of `org` (a LIPAS org
   map) to ptv_service_audit, anchored to `current`, the lineage's current
   ptv_service revision (see lipas.backend.ptv.core/save-ptv-service-audit
   for how it is resolved), and returns the stored audit map."
  [db user org current {:keys [service-id audit]}]
  (let [now (utils/timestamp)
        user-id (or (:id user) (get-in user [:login :user :id]))
        audit* (assoc audit :timestamp now :auditor-id (str user-id))]
    (ptv-service-audit-db/insert-audit!
      db
      {:org-id (:id org)
       :source-id (:source-id current)
       :service-id (or (:service-id current) service-id)
       :service-revision-id (:id current)
       :auditor-id user-id
       :event-date now
       :document audit*})
    audit*))

(defn service-audits
  "Current audits of the org's audited services,
   [{:service-id \"<uuid>\" :source-id s :audit map} ...]. What
   /actions/fetch-ptv-service-audits serves; the frontend joins it with
   the live PTV service list by service id. An audit row that predates
   its lineage's PTV UUID takes the id from the lineage's current
   revision; a lineage without one anywhere is not served, as nothing
   could be joined to it."
  [db org-id]
  (let [service-id-by-source-id (into {}
                                      (map (juxt :source-id :service-id))
                                      (ptv-service-db/get-current-by-org db org-id))]
    (->> (ptv-service-audit-db/get-current-by-org db org-id)
         (keep (fn [{:keys [service-id source-id document]}]
                 (when-let [service-id (or service-id
                                           (get service-id-by-source-id source-id))]
                   {:service-id (str service-id)
                    :source-id source-id
                    :audit document})))
         vec)))

(defn current-service-audits
  "service-id (string) -> current audit map for the org's audited
   services."
  [db org-id]
  (into {} (map (juxt :service-id :audit)) (service-audits db org-id)))
