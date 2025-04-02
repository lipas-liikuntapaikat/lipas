(ns lipas-api.core
  (:require [lipas-api.categories :as categories]
            [lipas-api.db.util :as db-util]
            [lipas-api.search.cli :as es]
            [lipas-api.sports-places :refer [filter-and-format
                                             join-data-single
                                             format-sports-place-db
                                             format-sports-place-es]]
            [lipas-api.types :as types]
            [clojure.set :as set]
            [lipas-api.util :refer [only-non-nil-recur] :as util]))

(defn fetch-sports-places
  "Fetches list of sports-places from ElasticSearch backend."
  [search locale params fields]
  (let [data (:body (es/fetch-sports-places search params))
        places (map :_source (-> data :hits :hits))
        fields (conj fields :sportsPlaceId)]
    {:partial? (es/more? data (:limit params) (:offset params))
     :total (-> data :hits :total :value)
     :results (map (comp only-non-nil-recur
                         (partial filter-and-format locale fields)) places)}))

(defn fetch-sports-place
  "Fetches single sports-place from database."
  [db locale sports-place-id]
  (let [data (db-util/fetch-sports-place-data-single db sports-place-id)]
    (when-not (empty? (:place data))
      (let [place (join-data-single data)]
        (only-non-nil-recur (format-sports-place-db place locale))))))

(defn fetch-sports-place-es
  "Fetches single sports-place from search engine index."
  [search locale sports-place-id]
  (try
    (-> (es/get search {:id sports-place-id})
        :body
        :_source
        (format-sports-place-es locale)
        only-non-nil-recur)
    (catch Exception ex nil)))

(defn index
  "Fetches sports-place data with all locales from the db
  and sends indexing request to search engine."
  ([db search sports-place-id]
   (index db search sports-place-id false))
  ([db search sports-place-id sync?]
   (try
     (let [place (fetch-sports-place db :all sports-place-id)]
       (es/index search {:id sports-place-id :data place :sync? sync?}))
     (catch Exception e (prn e)))))

(defn create-sports-place!
  "Stores given sports-place into database and search engine."
  [db search sports-place user]
  (let [id (.create-sports-place db sports-place user)]
    (index db search id :sync)
    id))

(defn update-sports-place!
  "Updates a sports-place in database and search engine."
  [db search sports-place user]
  (let [id (:sportsPlaceId sports-place)]
    (.update-sports-place db sports-place user)
    (index db search id :sync)
    id))

(defn delete-sports-place!
  "Permanently deletes a sports-place from database and search engine."
  [db search sports-place-id]
  (.delete-sports-place db sports-place-id)
  (try (es/delete search {:id sports-place-id :sync? true})
       (catch Exception e (prn e)))
  sports-place-id)

(defn fetch-deleted-ids
  "Fetches deleted sportsPlaceIds since given date string.
  `since` defaults to \"1970-01-01 00:00:00.000\""
  [db since]
  (let [since (or since "1970-01-01 00:00:00.000")
        kmap  {:deleted-at :deletedAt :sports-place-id :sportsPlaceId}]
    (->> (.fetch-deleted-ids db since)
         (map #(set/rename-keys % kmap)))))

(defn migrate-sports-place*!
  "Adds a sports-place database and search engine, including given id."
  [db search sports-place user]
  (let [id (:sportsPlaceId sports-place)]
    (.migrate-sports-place db sports-place user)
    (index db search id :sync)
    id))

(defn migrate-sports-place!
  "Migrates sports-place changes from external system"
  [db search {:keys [op id data]} user]
  (let [data    (assoc data :sportsPlaceId id)
        exists? (some? (.fetch-sports-place db id))
        delete? (= "delete" op)]
    {:id  id :op op :status "success"
     :res (cond
            delete?       (delete-sports-place! db search id)
            exists?       (update-sports-place! db search data user)
            (not exists?) (migrate-sports-place*! db search data user))}))

(defn migrate-sports-places!
  "Migrates sports-places from external system"
  [db search ops user]
  (reduce
   (fn [res op]
     (conj res (migrate-sports-place! db search op user)))
   []
   ops))

(defn fetch-types
  "Fetch list of sports-place types"
  [db locale]
  (map #(types/format-type-list-item % locale) (.fetch-types db)))

(defn fetch-type
  "Fetch sports-place type"
  [db locale type-code]
  (let [type  (first (.fetch-types db [type-code]))]
    (when type
      (let [props (.fetch-types-props db [type-code])]
        (types/format-type type props locale)))))

(defn fetch-categories
  "Fetch sports-place type categories"
  [db locale]
  (let [main-cats (.fetch-main-categories db)
        sub-cats  (.fetch-sub-categories db)]
    (map #(categories/format-category % sub-cats locale) main-cats)))
