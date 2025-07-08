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

(defn get-editable-sites
  "Get all sites user can edit using existing roles system"
  [search user]
  (let [;; Check if user has admin role
        is-admin? (roles/check-role user :admin)
        user-roles (get-in user [:permissions :roles])

        ;; For non-admin users, build ES query with role filters
        base-query (if is-admin?
                     {:query {:match_all {}}}
                     (let [city-manager-terms (for [role user-roles
                                                    :when (= (name (:role role)) "city-manager")
                                                    city-code (if (coll? (:city-code role))
                                                                (:city-code role)
                                                                [(:city-code role)])]
                                                {:term {:location.city.city-code city-code}})
                           site-manager-terms (for [role user-roles
                                                    :when (= (name (:role role)) "site-manager")
                                                    lipas-id (if (coll? (:lipas-id role))
                                                               (:lipas-id role)
                                                               [(:lipas-id role)])]
                                                {:term {:lipas-id lipas-id}})
                           all-terms (concat city-manager-terms site-manager-terms)]
                       (if (seq all-terms)
                         {:query {:bool {:should all-terms}}}
                         {:query {:match_none {}}})))

        final-query (assoc base-query
                           :size 10000
                           :_source {:includes ["lipas-id"
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
                                                "reservations-link"]})]

    (log/debug "ES Query for fetching editable sites"
               {:user-id (:id user)
                :user-roles user-roles
                :is-admin? is-admin?
                :query final-query})

    (let [search-index (get-in search [:indices :sports-site :search])
          response (search/search (:client search) search-index final-query)]
      (->> response
           :body
           :hits
           :hits
           (mapv :_source)))))

(defn mass-update-sports-sites-contacts
  "Mass update contact information for multiple sports sites using bulk operations"
  [db search ptv user lipas-ids contact-updates]
  (log/info "Starting mass update of sports sites"
            {:user-id (:id user)
             :lipas-ids lipas-ids
             :updates (keys contact-updates)})

  ;; Validate contact updates against schema
  (when-not (m/validate mass-update-contact-payload {:lipas-ids lipas-ids :updates contact-updates})
    (throw (ex-info "Invalid payload"
                    {:type :invalid-payload
                     :error (me/humanize (m/explain mass-update-contact-payload {:lipas-ids lipas-ids :updates contact-updates}))})))

  ;; Get all sites user can edit
  (let [editable-sites (get-editable-sites search user)
        editable-lipas-ids (set (map :lipas-id editable-sites))
        authorized-ids (filter editable-lipas-ids lipas-ids)
        unauthorized-ids (remove editable-lipas-ids lipas-ids)]

    (log/info "Permission check results"
              {:editable-count (count editable-sites)
               :authorized-ids authorized-ids
               :unauthorized-ids unauthorized-ids})

    ;; Validate all requested sites are authorized
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
