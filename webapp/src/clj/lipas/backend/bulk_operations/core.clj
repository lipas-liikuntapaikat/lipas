(ns lipas.backend.bulk-operations.core
  "CQRS-style actions for backend operations"
  (:require [clojure.java.jdbc :as jdbc]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.search :as search]
            [lipas.data.types :as types]
            [lipas.jobs.core :as jobs]
            [lipas.roles :as roles]
            [lipas.schema.sports-sites :as sites-schema]
            [malli.core :as m]
            [malli.error :as me]
            [taoensso.timbre :as log]))

;; Schema for mass update payload
(def mass-update-contact-payload
  [:map
   [:lipas-ids [:vector :int]]
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

(defn mass-update-org-sites-contacts!
  "Mass-update contact info for an org's sites. The authorized set is the org's
  editable sites (owned ∪ granted); any requested lipas-id outside it is
  rejected. The caller must already hold `:site/create-edit` for the org."
  [db search _ptv user org-id lipas-ids contact-updates]
  (log/info "Starting org mass update of sports sites"
            {:user-id (:id user) :org-id org-id :lipas-ids lipas-ids :updates (keys contact-updates)})

  ;; Validate contact updates against schema
  (when-not (m/validate mass-update-contact-payload {:lipas-ids lipas-ids :updates contact-updates})
    (throw (ex-info "Invalid payload"
                    {:type :invalid-payload
                     :error (me/humanize (m/explain mass-update-contact-payload {:lipas-ids lipas-ids :updates contact-updates}))})))

  ;; Authorize against the org's editable sites
  (let [editable-sites (get-org-editable-sites search org-id)
        editable-lipas-ids (set (map :lipas-id editable-sites))
        authorized-ids (filter editable-lipas-ids lipas-ids)
        unauthorized-ids (remove editable-lipas-ids lipas-ids)]

    (log/info "Permission check results"
              {:editable-count (count editable-sites)
               :authorized-ids authorized-ids
               :unauthorized-ids unauthorized-ids})

    ;; Validate all requested sites are authorized (within the org's editable set)
    (when (seq unauthorized-ids)
      (throw (ex-info "Permission denied for sites"
                      {:unauthorized-lipas-ids unauthorized-ids})))

    ;; Batch fetch all sites from ES for efficiency
    (let [sites-query {:query {:terms {:lipas-id authorized-ids}}
                       :size (count authorized-ids)
                       :_source {:excludes ["search-meta"]}}
          sites-response (core/search search sites-query)
          sites-by-id (->> sites-response
                           :body
                           :hits
                           :hits
                           (map (fn [hit] [(:lipas-id (:_source hit)) (:_source hit)]))
                           (into {}))

          ;; Update each site in memory
          updated-sites-data (for [lipas-id authorized-ids
                                   :let [current-site (get sites-by-id lipas-id)]
                                   :when current-site]
                               (let [;; Merge contact updates into current site
                                     updated-site (merge current-site contact-updates)]

                                 (log/debug "Updating site contact info"
                                            {:lipas-id lipas-id
                                             :changes contact-updates})

                                 {:lipas-id lipas-id
                                  :updated-site updated-site}))]

      ;; Use transaction for database updates and bulk indexing for ES
      (jdbc/with-db-transaction [tx db]
        ;; Save all sites to database
        (doseq [{:keys [lipas-id updated-site]} updated-sites-data]
          ;; Update in database (using existing method for now)
          (db/upsert-sports-site! tx user updated-site false))

        ;; Prepare bulk index data for Elasticsearch
        (let [search-index (get-in search [:indices :sports-site :search])
              enriched-sites (map #(core/enrich (:updated-site %)) updated-sites-data)
              bulk-data (search/->bulk search-index :lipas-id enriched-sites)]

          ;; Perform bulk indexing
          (log/debug "Bulk indexing" (count enriched-sites) "sports sites")
          (search/bulk-index-sync! (:client search) bulk-data)

          ;; Process background jobs for all updated sites

          ;; NOTE: Disabled for now, because current background
          ;; processes are relevant only, if geoms change and bulk-ops
          ;; don't touch geoms.
          ;;
          ;; TODO: If/when webhooks are enabled again, they need to be
          ;; added here!
          ;;
          #_(let [correlation-id (jobs/gen-correlation-id)]
              (jobs/with-correlation-context correlation-id
                (fn []
                  (doseq [{:keys [lipas-id updated-site]} updated-sites-data]
                    (let [route? (-> updated-site :type :type-code types/all :geometry-type #{"LineString"})]

                      #_(when route?
                          (jobs/enqueue-job! tx "elevation"
                                             {:lipas-id lipas-id}
                                             {:correlation-id correlation-id
                                              :priority 70}))

                      (when-not route?
                      ;; Integration queue (being phased out)
                        (core/add-to-integration-out-queue! tx updated-site))

                    ;; Analysis job
                      (jobs/enqueue-job! tx "analysis"
                                         {:lipas-id lipas-id}
                                         {:correlation-id correlation-id
                                          :priority 80}))))))))

      (log/info "Mass update completed"
                {:updated-count (count updated-sites-data)
                 :total-requested (count lipas-ids)})

      {:updated-sites (mapv :lipas-id updated-sites-data)
       :total-updated (count updated-sites-data)})))
