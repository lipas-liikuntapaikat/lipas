(ns lipas.backend.ptv.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.gis :as gis]
            [lipas.backend.ptv.ai :as ai]
            [lipas.backend.ptv.integration :as ptv]
            [lipas.backend.search :as search]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

(defn get-ptv-integration-candidates
  [search criteria]
  (ptv/get-eligible-sites search criteria))

(defn generate-ptv-descriptions
  [{:keys [client indices] :as _search}
   lipas-id]
  (let [idx (get-in indices [:sports-site :search])
        doc (-> (search/fetch-document client idx lipas-id)
                :body
                :_source)]
    (-> (ai/generate-ptv-descriptions doc)
        :message
        :content)))

(defn generate-ptv-descriptions-from-data
  [doc]
  (let [doc (core/enrich doc)]
    (-> (ai/generate-ptv-descriptions doc)
        :message
        :content)))

(defn translate-to-other-langs
  [doc]
  (-> (ai/translate-to-other-langs doc)
      :message
      :content
      ;; Ensure the original from texts are kept as-is
      (assoc-in [:summary (keyword (:from doc))] (:summary doc))
      (assoc-in [:description (keyword (:from doc))] (:description doc))))

(defn make-overview
  [sites]
  {:city-name         (->> sites first :search-meta :location :city :name)
   :service-name      (->> sites first :search-meta :type :sub-category :name)
   :sports-facilities (for [site sites]
                        {:type (-> site :search-meta :type :name :fi)})})

(defn generate-ptv-service-descriptions
  [search
   {:keys [sub-category-id city-codes overview]}]
  (let [doc        (or overview
                       (let [type-codes (->> (types/by-sub-category sub-category-id)
                                             (map :type-code))
                             sites      (ptv/get-eligible-sites search {:type-codes type-codes
                                                                        :city-codes city-codes
                                                                        :owners     ["city" "city-main-owner"]})]
                         (make-overview sites)))]
    (-> (ai/generate-ptv-service-descriptions doc)
        :message
        :content)))

(defn upsert-ptv-service!
  [ptv {:keys [source-id] :as m}]
  (let [data (ptv-data/->ptv-service m)]
    ;; We have the source-id always?
    ; (if source-id
    ;   (ptv/update-service ptv source-id data)
    ;   (ptv/create-service ptv data))
    ;; PTV update using sourceId gives 404 if the sourceId doesn't exist yet
    (try
      (ptv/update-service ptv source-id data)
      (catch clojure.lang.ExceptionInfo e
        (if (= 404 (:status (:resp (ex-data e))))
          (ptv/create-service ptv data)
          (throw e))))))

(defn fetch-ptv-org
  [ptv org-id]
  (ptv/get-org ptv org-id))

(defn fetch-ptv-service-collections
  [ptv org-id]
  (ptv/get-org-service-collections ptv org-id))

(defn fetch-ptv-services
  [ptv org-id]
  (ptv/get-org-services ptv org-id))

(defn fetch-ptv-service-channels
  [ptv org-id]
  (ptv/get-org-service-channels ptv org-id))

(defn fetch-ptv-service-channel
  [ptv org-id service-channel-id]
  (ptv/get-org-service-channel ptv org-id service-channel-id))

(def persisted-ptv-keys [:languages
                         :summary
                         :description
                         :last-sync
                         :org-id
                         :sync-enabled
                         :service-integration
                         :descriptions-integration
                         :service-channel-integration
                         :service-ids
                         :service-channel-ids])

(defn upsert-ptv-service-location!*
  [ptv-component {:keys [org-id site ptv archive?] :as _m}]
  (let [id       (-> ptv :service-channel-ids first)
        ;; merge or just replace?
        site     (update site :ptv merge ptv)
        ;; Use the same TS for sourceId, ptv last-sync and site event-date
        now      (utils/timestamp)
        data     (ptv-data/->ptv-service-location org-id gis/wgs84->tm35fin-no-wrap now (core/enrich site))
        data     (cond-> data
                   archive? (assoc :publishingStatus "Deleted"))
        ;; Note: Update request doesn't update Service connections!

        ;; TODO: Would be nice to avoid this, by getting the previous
        ;; :ptv :service-ids value here.
        old-service-location (when id
                               (ptv/get-org-service-channel ptv-component org-id id))

        ptv-resp (if id
                   (ptv/update-service-location ptv-component id data)
                   (ptv/create-service-location ptv-component data))
        _ (when id
            ;; Update service connection changes
            (let [old-services (->> old-service-location
                                    :services
                                    (map (comp :id :service))
                                    set)
                  new-services (set (:service-ids (:ptv site)))
                  removed-services (set/difference old-services new-services)
                  new-services (set/difference new-services old-services)
                  service-channel-id (first (:service-channel-ids (:ptv site)))]
              (log/infof "Update PTV service-location, add services %s, remove services %s"
                        new-services removed-services)
              (doseq [service-id removed-services]
                (ptv/update-service-connections ptv-component org-id service-id #(disj % service-channel-id)))
              (doseq [service-id new-services]
                (ptv/update-service-connections ptv-component org-id service-id #(conj % service-channel-id)))))
        ;; Store the new PTV info to Lipas DB
        new-ptv-data (-> ptv
                         (select-keys persisted-ptv-keys)
                         (assoc :org-id (or (:org-id ptv)
                                            org-id)
                                :last-sync now
                                ;; Store the current type-code into ptv data, so this can be
                                ;; used to comapre if the services need to recalculated on site data update.
                                :previous-type-code (:type-code (:type site))
                                :source-id (:sourceId ptv-resp)
                                ;; Store the PTV status so we can ignore Lipas archived places that we already archived in PTV.
                                :publishing-status (:publishingStatus ptv-resp)
                                ;; NOTE: The ptv map might not have this value in some cases...?
                                ;; but the value merged with data from site should have it always?
                                :service-ids (:service-ids (:ptv site))
                                ;; Take the created ID from ptv response and store to Lipas DB right away.
                                ;; TODO: Is there a case where this could be multiple ids?
                                :service-channel-ids [(:id ptv-resp)])
                         (cond->
                           archive? (dissoc :source-id
                                            :service-channel-ids
                                            :delete-existing)))]

    (log/infof "Resp %s" ptv-resp)

    (log/infof "Upserted (Lipas status: %s, updated: %s) service-location %s: %s" (:status site) (boolean id) data new-ptv-data)

    [ptv-resp new-ptv-data]))

(defn upsert-ptv-service-location!
  [db ptv-component search user {:keys [lipas-id org-id ptv archive?]}]
  ;; FIXME: This is called from inside tx in save-sports-site! is that a problem?
  ;; FIXME: Separate version from this fn for use in sync-ptv! which doesn't load the
  ;; sports site from db etc.?
  (jdbc/with-db-transaction [tx db]
    (let [site     (db/get-sports-site db lipas-id)
          _        (assert (some? site) (str "Sports site " lipas-id " not found in DB"))

          [ptv-resp new-ptv-data] (upsert-ptv-service-location!* ptv-component
                                                                 {:org-id org-id
                                                                  :site site
                                                                  :ptv ptv
                                                                  :archive? archive?})]

      (let [resp (core/upsert-sports-site! tx
                                           user
                                           (assoc site
                                                  :event-date (:last-sync new-ptv-data)
                                                  :ptv new-ptv-data)
                                           false)]
        (core/index! search resp :sync))

      ;; No need to re-index for search after ptv change

      {;; Return the updated :ptv meta for sports-site, to for the app-db
       :ptv new-ptv-data
       ;; Return :id :name, same as the list endpoint that is used in the UI to show the Palvelupaikka autocomplete
       :ptv-resp {:id (:id ptv-resp)
                  :name (some (fn [x]
                                (when (and (= "Name" (:type x))
                                           (= "fi" (:language x)))
                                  (:value x)))
                              (:serviceChannelNames ptv-resp))}})))

(comment
  (require '[integrant.repl.state :as state])

  (let [ptv-component (:lipas/ptv state/system)
        org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
        services (:itemList (ptv/get-org-services ptv-component org-id))]
    (->> services
         (utils/index-by :sourceId)
         keys)))

;; Used through resolve due to circular dep
;; TODO: Check if code can be moved around to avoid this
^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn sync-ptv! [tx search ptv-component user {:keys [sports-site ptv org-id lipas-id]}]
  (try
    (let [type-code (-> sports-site :type :type-code)

          previous-sent? (ptv-data/is-sent-to-ptv? sports-site)
          candidate-now? (and (ptv-data/ptv-candidate? sports-site)
                              (ptv-data/ptv-ready? sports-site))

          ;; If it looks like site that was previously sent to PTV is no longer
          ;; a candidate, mark for it archival.
          ;; The other function will mark the document Deleted when archive flag is true
          to-archive? (and previous-sent?
                           (or (not candidate-now?)
                               (:delete-existing ptv)))

          type-code-changed? (not= type-code (:previous-type-code ptv))
          ptv  (if type-code-changed?
                 (let [types types/all
                       ;; Figure out what services are available in PTV for the site organization
                       services (:itemList (ptv/get-org-services ptv-component org-id))
                       source-id->service (->> services
                                               (utils/index-by :sourceId))

                       ;; Check if services for the current/new site type-code exist
                       missing-services-input [{:service-ids #{}
                                                :sub-category-id (-> sports-site :type :type-code types :sub-category)
                                                :sub-cateogry    (-> sports-site :search-meta :type :sub-category :name :fi)}]
                       missing-services (ptv-data/resolve-missing-services org-id source-id->service missing-services-input)

                       ;; FE doesn't update the :ptv :service-ids, that is still handled here.
                       ;; This code just presumes the user has created the possibly missing Sercices
                       ;; in the FE first.

                       _ (when (seq missing-services)
                           (throw (ex-info "Site needs a PTV Service that doesn't exists"
                                           {:missing-services missing-services})))

                       ;; Remove old service-ids from :ptv data and add the new.
                       ;; Don't touch other service-ids in the data, those could have be added manually in UI or in PTV.
                       ;; NOTE: OK, PTV updates are likely lost, because our :ptv :service-ids is what the create/update from
                       ;; Lipas previously returned, so if PTV ServiceLocation was modified after that in PTV, we lose those changes.
                       old-sports-site (assoc-in sports-site [:type :type-code] (:previous-type-code ptv))
                       old-service-ids (ptv-data/sports-site->service-ids types source-id->service old-sports-site)
                       new-service-ids (ptv-data/sports-site->service-ids types source-id->service sports-site)]
                   (log/infof "Site type changed %s => %s, service-ids updated %s => %s"
                              (:previous-type-code ptv) type-code
                              old-service-ids new-service-ids)
                   (update ptv :service-ids (fn [ids]
                                              (let [x (set ids)
                                                    x (apply disj x old-service-ids)
                                                    x (into x new-service-ids)]
                                                (vec x)))))
                 ptv)

          [_ptv-resp new-ptv-data] (upsert-ptv-service-location!* ptv-component
                                                                  {:org-id org-id
                                                                   :ptv ptv
                                                                   :site sports-site
                                                                   :archive? to-archive?})]

      (let [resp (core/upsert-sports-site! tx
                                           user
                                           (assoc sports-site
                                                  :event-date (:last-sync new-ptv-data)
                                                  :ptv new-ptv-data)
                                           false)]
        (core/index! search resp :sync))

      new-ptv-data)
    (catch Exception e
      (let [new-ptv-data (assoc ptv :error {:message (.getMessage e)
                                            :data (ex-data e)})]
        (log/infof e "Sports site updated but PTV integration had an error")
        (let [resp (core/upsert-sports-site! tx
                                             user
                                             (-> sports-site
                                                 (assoc :event-date (utils/timestamp))
                                                 (assoc :ptv new-ptv-data))
                                             false)]
          (core/index! search resp :sync)
          (:ptv resp))))))

(defn save-ptv-integration-definitions
  "Saves ptv definitions under key :ptv. Does not notify webhooks,
  integrations or analysis queues since they're not likely interested
  in this."
  [db search user lipas-id->ptv-meta]
  (jdbc/with-db-transaction [tx db]
    (doseq [[lipas-id ptv] lipas-id->ptv-meta]
      ;; TODO take when-let -> let and add assert
      (when-let [site (-> (core/get-sports-site tx lipas-id)
                          (assoc :event-date (utils/timestamp))
                          (assoc :ptv ptv))]
        (core/upsert-sports-site! tx user site)
        (core/index! search site :sync)))))

(comment
  (generate-ptv-service-descriptions
   (user/search)
   {:sub-category-id 2200
    :city-codes [992 #_92]
    })

  (generate-ptv-descriptions (user/search) 612967)
  (generate-ptv-descriptions (user/search) 506032)

  )
