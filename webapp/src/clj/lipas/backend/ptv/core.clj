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
   {:keys [lipas-id]}]
  (let [idx (get-in indices [:sports-site :search])
        doc (-> (search/fetch-document client idx lipas-id) :body :_source)]
    (-> (ai/generate-ptv-descriptions doc)
        :message
        :content)))

(defn make-overview
  [sites]
  {:city-name         (->> sites first :search-meta :location :city :name)
   :service-name      (->> sites first :search-meta :type :sub-category :name)
   :sports-facilities (for [site sites]
                        {:type (-> site :search-meta :type :name :fi)})})

(defn generate-ptv-service-descriptions
  [search
   {:keys [_id sub-category-id city-codes]}]
  (let [type-codes (->> (types/by-sub-category sub-category-id)
                        (map :type-code))
        sites      (ptv/get-eligible-sites search {:type-codes type-codes
                                                   :city-codes city-codes
                                                   :owners     ["city" "city-main-owner"]})
        doc        (make-overview sites)]
    (-> (ai/generate-ptv-service-descriptions doc)
        :message
        :content)))

(defn upsert-ptv-service!
  [{:keys [id source-id] :as m}]
  {:pre [(some? source-id)]}
  (let [config nil
        data   (ptv-data/->ptv-service (merge config m))]
    (if id
      (ptv/update-service config id data)
      (ptv/create-service config data))))

(defn fetch-ptv-services
  [{:keys [org-id] :as _m}]
  {:pre [(some? org-id)]}
  (ptv/get-org-services {} org-id))

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
  [db search user {:keys [org ptv-meta sports-site] :as _m}]
  (assert (:lipas-id sports-site))
  (let [site     (db/get-sports-site db (:lipas-id sports-site))
        _        (assert (some? site)
                         (str "Sports site " (:lipas-id sports-site) " not found in DB"))
        ;; NOTE: Is this available always? Where is ptv-meta originally intitialized?
        config   {:org-id (:org-id ptv-meta)}
        ;; This is the service-channel-id in Lipas DB (which won't exist for new service-locations)
        ;; id (first (get-in site [:ptv :service-channel-ids]))
        ;; This is the ID from UI, possibly updated/set with "Liitä tähän palvelupaikkaan"
        ;; We probably want to use this always?
        id (-> sports-site :ptv :service-channel-ids first)
        ;; _ (log/infof "FOO: %s %s" id (-> sports-site :ptv :service-channel-ids))
        site     (update site :ptv merge ptv-meta)
        _ (log/infof "Site data: %s" site)
        data     (ptv-data/->ptv-service-location org gis/wgs84->tm35fin-no-wrap (core/enrich site))
        _ (log/infof "Created data: %s" data)
        ptv-resp (if id
                   (ptv/update-service-location config id data)
                   (ptv/create-service-location config data))
        now      (utils/timestamp)
        to-persist (-> ptv-meta
                       (select-keys persisted-ptv-keys)
                       (assoc :last-sync now
                              ;; Store the PTV status so we can ignore Lipas archived places that we already archived in PTV.
                              :publishing-status (:publishingStatus ptv-resp)
                              ;; Take the created ID from ptv response and store to Lipas DB right away.
                              ;; TODO: Is there a case where this could be multiple ids?
                              :service-channel-ids (set [(:id ptv-resp)])))]

    (log/infof "Upserted (Lipas status: %s, updated: %s) service-location %s: %s" (:status site) (boolean id) data to-persist)

    (core/save-sports-site! db search user
                            (-> site
                                (assoc :event-date now)
                                (assoc :ptv to-persist)))

    {;; Return the updated :ptv meta for sports-site, to for the app-db
     :ptv-meta to-persist
     ;; Return :id :name, same as the list endpoint that is used in the UI to show the Palvelupaikka autocomplete
     :ptv-resp {:id (:id ptv-resp)
                :name (some (fn [x]
                              (when (and (= "Name" (:type x))
                                         (= "fi" (:language x)))
                                (:value x)))
                            (:serviceChannelNames ptv-resp))}}))

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
