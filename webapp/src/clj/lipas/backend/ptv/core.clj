(ns lipas.backend.ptv.core
  (:require [clojure.java.jdbc :as jdbc]
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
   {:keys [_id sub-category-id city-codes overview]}]
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
  ;; FIXME: Does ->ptv-service need something from the component config?
  (let [data (ptv-data/->ptv-service m)]
    (if source-id
      (ptv/update-service ptv source-id data)
      (ptv/create-service ptv data))))

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

(defn upsert-ptv-service-location!
  [db ptv-component user {:keys [org-id lipas-id ptv archive?] :as _m}]
  (jdbc/with-db-transaction [tx db]
    (let [site     (db/get-sports-site db lipas-id)
          _        (assert (some? site) (str "Sports site " lipas-id " not found in DB"))

          id       (-> ptv :service-channel-ids first)
          ;; merge or just replace?
          site     (update site :ptv merge ptv)
          ;; Use the same TS for sourceId, ptv last-sync and site event-date
          now      (utils/timestamp)
          data     (ptv-data/->ptv-service-location org-id gis/wgs84->tm35fin-no-wrap now (core/enrich site))
          data     (cond-> data
                     archive? (assoc :publishingStatus "Deleted"))
          ptv-resp (if id
                     (ptv/update-service-location ptv-component id data)
                     (ptv/create-service-location ptv-component data))
          ;; Store the new PTV info to Lipas DB
          new-ptv-data (-> ptv
                           (select-keys persisted-ptv-keys)
                           (assoc :last-sync now
                                  ;; Store the current type-code into ptv data, so this can be
                                  ;; used to comapre if the services need to recalculated on site data update.
                                  :previous-type-code (:type-code (:type site))
                                  :source-id (:sourceId ptv-resp)
                                  ;; Store the PTV status so we can ignore Lipas archived places that we already archived in PTV.
                                  :publishing-status (:publishingStatus ptv-resp)
                                  ;; Take the created ID from ptv response and store to Lipas DB right away.
                                  ;; TODO: Is there a case where this could be multiple ids?
                                  :service-channel-ids (set [(:id ptv-resp)]))
                           (cond->
                             archive? (dissoc :source-id
                                              :service-channel-ids)))]

      (log/infof "Upserted (Lipas status: %s, updated: %s) service-location %s: %s" (:status site) (boolean id) data new-ptv-data)

      (core/upsert-sports-site! tx
                                user
                                (assoc site
                                       :event-date now
                                       :ptv new-ptv-data)
                                false)
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

  (let [ptv-component (:lipas/ptv integrant.repl.state/system)
        org-id ptv-data/liminka-org-id-test
        services (:itemList (ptv/get-org-services ptv-component org-id))]
    (->> services
         (utils/index-by :sourceId)
         keys)))

;; Used through resolve due to circular dep
;; TODO: Check if code can be moved around to avoid this
^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn sync-ptv! [tx search ptv-component user {:keys [sports-site ptv org-id lipas-id]}]
  (let [type-code (-> sports-site :type :type-code)

        previous-sent? (ptv-data/is-sent-to-ptv? sports-site)
        candidate-now? (and (ptv-data/ptv-candidate? sports-site)
                            (ptv-data/ptv-ready? sports-site))

        to-archive? (and previous-sent?
                         (not candidate-now?))

        ;; TODO: to-archive
        ;; 1. set PTV status Deleted
        ;; 2. remove :ptv :source-id, :service-channel-ids -> if restored, a new service-location is created

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

                     _ (log/infof "Missing services? %s" (pr-str missing-services))


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
                 (update ptv :service-ids (fn [x]
                                            (->> x
                                                 (remove (fn [y] (contains? old-service-ids y)))
                                                 (into new-service-ids)))))
               ptv)
        resp (upsert-ptv-service-location! tx ptv-component user {:org-id org-id
                                                                  :ptv ptv
                                                                  :lipas-id lipas-id
                                                                  :archive? to-archive?})]
    resp))

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
