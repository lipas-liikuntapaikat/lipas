(ns lipas.backend.osrm
  (:require
   [cemerick.url :as url]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [environ.core :refer [env]]
   [lipas.backend.gis :as gis])
  (:import
   [com.google.maps.internal PolylineEncoding]
   [com.google.maps.model LatLng]))

(def profiles
  {:car     {:url (:osrm-car-url env)}
   :bicycle {:url (:osrm-bicycle-url env)}
   :foot    {:url (:osrm-foot-url env)}})

(defn ->google-polyline [coords]
  (->> coords
       (map #(LatLng. (second %) (first %)))
       (PolylineEncoding/encode)))

(defn <-google-polyline [s]
  (->> s
       (PolylineEncoding/decode)
       (map #(vector (.-lng %) (.-lat %)))))

(defn resolve-sources [fcoll]
  (if (gis/point? fcoll)
    [(-> fcoll :features first :geometry :coordinates (->> (str/join ",")))]
    (-> fcoll
        gis/->single-linestring-coords
        (->> (map #(str/join "," %))))))

(defn make-url
  [{:keys [sources destinations profile]}]
  (let [base-url (-> profiles profile :url)]
    (prn "OSRM URL: " base-url)
    (str base-url
         (->> (into [] cat [sources destinations])
              (str/join ";"))
         "?"
         (url/map->query
          {:annotations    "distance,duration"
           :generate_hints false
           :sources        (str/join ";" (range 0 (count sources)))
           :destinations   (str/join ";" (range (count sources)
                                                (+ (count sources)
                                                   (count destinations))))}))))

(defn get-data
  [m]
  (-> m make-url client/get :body (json/decode keyword)))

(defn get-distances-and-travel-times
  [{:keys [profiles]
    :or   {profiles [:car :bicycle :foot]}
    :as   m}]
  (->> profiles
       (mapv (fn [p] (vector p (future (get-data (assoc m :profile p))))))
       (reduce (fn [res [p f]] (assoc res p (deref f))) {})))

(comment
  (def destinations
    ["25.1048346953729,62.5375762900109"
     "25.1242598119357,62.5378383310659"
     "25.1631111977754,62.5383543185465"])

  (def sources ["27.9601046796022,70.0837473555685"])
  (def params
    {:sources sources
     :destinations destinations
     :profile :bicycle})

  (make-url params)
  (def bicycle (get-data params))
  (def foot (get-data (assoc params :profile :foot)))
  (def car (get-data (assoc params :profile :car)))

  [(:durations foot)
   (:durations bicycle)
   (:durations car)]

  (time
   (get-distances-and-travel-times
    (assoc params :profiles [:car :bicycle :foot])))



  )
