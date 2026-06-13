(ns lipas.backend.bulk-operations.core
  "CQRS-style actions for backend operations"
  (:require [clojure.java.jdbc :as jdbc]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.search :as search]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.error :as me]
            [taoensso.timbre :as log]))

;; Schema for mass update payload
(def mass-update-contact-payload
  [:map
   [:lipas-ids [:vector #'sites-schema/lipas-id]]
   [:updates [:map
              [:email {:optional true} [:maybe sites-schema/email]]
              [:phone-number {:optional true} [:maybe sites-schema/phone-number]]
              [:www {:optional true} [:maybe sites-schema/www]]
              [:reservations-link {:optional true} [:maybe sites-schema/reservations-link]]]]])

(defn get-org-editable-sites
  "Bulk-update candidates for an org: the sites it may edit (owned ∪ granted),
  i.e. `search-meta.editor-org-ids` contains the org-id. Each result carries an
  `:owned?` flag (owner-org-id = org) so the UI can filter owned vs granted.

  Replaces the old per-user, hand-rolled role query: bulk update is now an org
  operation, so the candidate set is simply the org's editable sites and the
  caller is already gated by `:site/create-edit` for the org (org-editor)."
  [search org-id]
  (let [org-id (str org-id)
        query {:query {:bool {:filter [{:term {:search-meta.editor-org-ids org-id}}]}}
               :size 10000
               :_source {:includes ["lipas-id"
                                    "event-date"
                                    "location.city.city-code"
                                    "location.city.city-name"
                                    "name"
                                    "type.type-code"
                                    "type.name"
                                    "admin"
                                    "owner"
                                    "email"
                                    "phone-number"
                                    "www"
                                    "reservations-link"
                                    "search-meta.owner-org-id"]}}
        search-index (get-in search [:indices :sports-site :search])
        response (search/search (:client search) search-index query)]
    (->> response
         :body
         :hits
         :hits
         (mapv (fn [hit]
                 (let [src (:_source hit)]
                   (-> src
                       (assoc :owned? (= org-id (get-in src [:search-meta :owner-org-id])))
                       (dissoc :search-meta))))))))

(def ^:private contact-update-keys
  "The only document fields a bulk contact update may touch."
  [:email :phone-number :www :reservations-link])

(defn mass-update-org-sites-contacts!
  "Mass-update contact info for an org's sites. The authorized set is the org's
  editable sites (owned ∪ granted); any requested lipas-id outside it is
  rejected. The caller must already hold `:site/create-edit` for the org.

  ES (`get-org-editable-sites`) is used for CANDIDATE LISTING only. The write
  path is sourced and authorized from the DB:

  - the stored current revisions are batch-loaded from the DB and the contact
    updates are merged onto THEM (never onto the stale ES `_source`, which can
    lag behind the DB — e.g. a just-approved takeover not yet reindexed),
  - the org's editability (owner ∪ edit-grants) is checked against those DB
    revisions, so a grant revoked in the DB blocks the update even when ES
    still lists the site as editable,
  - each save goes through `core/upsert-sports-site!` — the same enforcement
    point as the regular save endpoint (per-user privilege over stored AND
    submitted docs, ownership/edit-grant invariants, owner lock). The merged
    doc is submitted WITHOUT :owner-org-id/:edit-grants so the server-side
    carry-forward keeps the freshest stored values authoritative.

  All-or-nothing: any unauthorized lipas-id rejects the whole request (the
  existing handler/FE contract), and the DB writes share one transaction. ES
  is bulk-indexed AFTER the transaction commits, from the documents that were
  actually written to the DB."
  [db search _ptv user org-id lipas-ids contact-updates]
  (log/info "Starting org mass update of sports sites"
            {:user-id (:id user) :org-id org-id :lipas-ids lipas-ids :updates (keys contact-updates)})

  ;; Validate contact updates against schema
  (when-not (m/validate mass-update-contact-payload {:lipas-ids lipas-ids :updates contact-updates})
    (throw (ex-info "Invalid payload"
                    {:type :invalid-payload
                     :error (me/humanize (m/explain mass-update-contact-payload {:lipas-ids lipas-ids :updates contact-updates}))})))

  (let [org-id (str org-id)
        ;; User-input boundary: the payload map is open (malli/route maps are
        ;; not :closed), so whitelist the contact fields — nothing else may
        ;; ride into the document.
        contact-updates (select-keys contact-updates contact-update-keys)
        lipas-ids (vec (distinct lipas-ids))
        ;; Authoritative current state from the DB — NOT the ES cache.
        stored-by-id (->> (db/get-sports-sites-by-lipas-ids db lipas-ids)
                          (utils/index-by :lipas-id))
        org-may-edit? (fn [site]
                        (and site
                             (or (= org-id (some-> site :owner-org-id str))
                                 (contains? (set (map str (:edit-grants site))) org-id))))
        unauthorized-ids (vec (remove (comp org-may-edit? stored-by-id) lipas-ids))]

    (log/info "Permission check results (against DB state)"
              {:requested-count (count lipas-ids)
               :unauthorized-ids unauthorized-ids})

    ;; Validate all requested sites are authorized (within the org's editable
    ;; set per the DB's current revisions)
    (when (seq unauthorized-ids)
      (throw (ex-info "Permission denied for sites"
                      {:unauthorized-lipas-ids unauthorized-ids})))

    ;; Save through core/upsert-sports-site! (single enforcement point) inside
    ;; one transaction; collect what was actually written for ES indexing.
    (let [saved-sites
          (jdbc/with-db-transaction [tx db]
            (mapv (fn [lipas-id]
                    (let [updated-site
                          (-> (stored-by-id lipas-id)
                              (merge contact-updates)
                              (assoc
                               ;; fresh per-revision timestamp — reusing the
                               ;; stored :event-date would collide with the
                               ;; previous revision (FE keys history by it)
                               :event-date (utils/timestamp)
                               ;; audit: on whose behalf the bulk op runs.
                               ;; Stamped by this trusted path AFTER the
                               ;; user-input whitelist above.
                               :acting-org-id org-id)
                              ;; let core's carry-forward keep the stored
                              ;; (freshest) ownership fields authoritative
                              (dissoc :owner-org-id :edit-grants))]
                      (log/debug "Updating site contact info"
                                 {:lipas-id lipas-id :changes contact-updates})
                      (core/upsert-sports-site! tx user updated-site)))
                  lipas-ids))]

      ;; Bulk index to ES outside the transaction (an ES failure must not roll
      ;; back committed DB data; worst case ES lags and is fixed by reindexing)
      ;; using the documents as they were written to the DB.
      (let [search-index (get-in search [:indices :sports-site :search])
            ;; resolved once per batch; keeps :search-meta :owner-org-name
            ;; (F15) present on re-indexed org-owned docs
            org-names (core/org-names db)
            enriched-sites (map #(core/enrich % org-names) saved-sites)
            bulk-data (search/->bulk search-index :lipas-id enriched-sites)]
        (log/debug "Bulk indexing" (count enriched-sites) "sports sites")
        (search/bulk-index-sync! (:client search) bulk-data))

      ;; NOTE: Background jobs deliberately not enqueued: current background
      ;; processes are relevant only if geoms change and bulk-ops don't touch
      ;; geoms. TODO: If/when webhooks are enabled again, they need to be
      ;; added here!

      (log/info "Mass update completed"
                {:updated-count (count saved-sites)
                 :total-requested (count lipas-ids)})

      {:updated-sites (mapv :lipas-id saved-sites)
       :total-updated (count saved-sites)})))
