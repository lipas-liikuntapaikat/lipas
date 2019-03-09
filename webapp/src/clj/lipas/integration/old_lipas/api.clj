(ns lipas.integration.old-lipas.api
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [environ.core :refer [env]]
   [ring.util.codec :as codec]))

(def url (:old-lipas-url env))
(def user (:old-lipas-user env))
(def pass (:old-lipas-pass env))

(def fields
  ["properties" "schoolUse" "email" "constructionYear" "type.name"
   "location.sportsPlaces" "renovationYears" "admin" "www" "location.geometries"
   "name" "type.typeCode" "location.locationId" "freeUse" "location.city.name"
   "lastModified" "location.postalCode" "location.postalOffice"
   "location.city.cityCode" "phoneNumber" "location.neighborhood"
   "owner" "location.address"])

(defn get [lipas-id]
  (let [url (str url "/api/sports-places/" lipas-id)]
    (-> url slurp (json/decode true))))

(defn- query [results url {:keys [page pageSize] :as params
                           :or   {page 1 pageSize 100}}]
  (let [params (assoc params :page page :pageSize pageSize)
        url*   (str url "?" (codec/form-encode params))]
    (if-let [data (-> url* slurp (json/decode true) not-empty)]
      (recur (into results data) url (update params :page inc))
      results)))

(defn query-changed [since]
  (let [url    (str url "/api/sports-places")
        params {:modifiedAfter since
                :fields        fields}]
    (query [] url params)))

(defn- query-by-ids [lipas-ids fields]
  (let [url    (str url "/api/sports-places")
        params {:searchString (string/join "|" lipas-ids)
                :fields       fields}]
    (->> (query [] url params)
         (filter (comp (set lipas-ids) :sportsPlaceId)))))

(defn query-timestamps [lipas-ids]
  (reduce
   (fn [res lipas-ids]
     (into res (query-by-ids lipas-ids ["lastModified"])))
   []
   (partition 100 100 nil lipas-ids)))

(defn query-all-data [lipas-ids]
  (reduce
   (fn [res lipas-ids]
     (into res (query-by-ids lipas-ids fields)))
   []
   (partition 100 100 nil lipas-ids)))

(defn get-ice-stadiums []
  (let [url    (str url "/api/sports-places")
        params {:typeCodes [2520 2510]
                :fields    fields}]
    (query [] url params)))

(defn get-swimming-pools []
  (let [url    (str url "/api/sports-places")
        params {:typeCodes [3110 3130]
                :fields    fields}]
    (query [] url params)))

(defn post-integration-doc! [doc]
  (let [params {:basic-auth       [user pass]
                :content-type     :json
                :accept           :json
                :socket-timeout   10000
                :conn-timeout     10000
                :throw-exceptions true
                :body             (json/encode doc)}

        url (str url "/api/integration/doc")]
    (client/post url params)))
