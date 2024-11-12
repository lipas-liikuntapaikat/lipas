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
      :content))

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
  [ptv {:keys [id] :as m}]
  ;; FIXME: Does ->ptv-service need something from the component config?
  (let [data (ptv-data/->ptv-service m)]
    (if id
      (ptv/update-service ptv id data)
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
  [db ptv-component user {:keys [org lipas-id ptv] :as _m}]
  (jdbc/with-db-transaction [tx db]
    (let [site     (db/get-sports-site db lipas-id)
          _        (assert (some? site) (str "Sports site " lipas-id " not found in DB"))

          id       (-> ptv :service-channel-ids first)
          ;; merge or just replace?
          site     (update site :ptv merge ptv)
          ;; Use the same TS for sourceId, ptv last-sync and site event-date
          now      (utils/timestamp)
          data     (ptv-data/->ptv-service-location org gis/wgs84->tm35fin-no-wrap now (core/enrich site))
          ptv-resp (if id
                     (ptv/update-service-location ptv-component id data)
                     (ptv/create-service-location ptv-component data))
          ;; Store the new PTV info to Lipas DB
          new-ptv-data (-> ptv
                           (select-keys persisted-ptv-keys)
                           (assoc :last-sync now
                                  :source-id (:sourceId ptv-resp)
                                  ;; Store the PTV status so we can ignore Lipas archived places that we already archived in PTV.
                                  :publishing-status (:publishingStatus ptv-resp)
                                  ;; Take the created ID from ptv response and store to Lipas DB right away.
                                  ;; TODO: Is there a case where this could be multiple ids?
                                  :service-channel-ids (set [(:id ptv-resp)])))]

      (log/infof "Upserted (Lipas status: %s, updated: %s) service-location %s: %s" (:status site) (boolean id) data new-ptv-data)

      (core/upsert-sports-site! tx
                                user
                                (-> site
                                    (assoc :event-date now)
                                    (assoc :ptv new-ptv-data))
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
