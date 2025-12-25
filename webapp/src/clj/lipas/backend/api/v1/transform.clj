(ns lipas.backend.api.v1.transform
  (:require
   [clojure.set :as set]
   [lipas.data.types :as types]
   [lipas.backend.api.v1.sports-place :as old]
   [lipas.utils :as utils]
   [clojure.string :as str]
   [lipas.backend.gis :as gis]))

(def helsinki-tz (java.time.ZoneId/of "Europe/Helsinki"))

(def df-out "Strict date format for outputs to old Lipas."
  (java.time.format.DateTimeFormatter/ofPattern
   "yyyy-MM-dd HH:mm:ss.SSS"))

(defn UTC->last-modified
  "Converts UTC ISO 8601 string to old Lipas last modified timestamp string.

  Old Lipas uses Europe/Helsinki TZ and format 'yyyy-MM-dd
  HH:mm:ss.SSS'."
  [s]
  (when s
    (-> (java.time.Instant/parse s)
        (.atZone helsinki-tz)
        (.toLocalDateTime)
        (.format df-out))))

(defn- fix-special-case
  "Fixes single special case where prop has totally stupid misspelled
  name which fails camelCasing. Prop name can't be changed because
  it's been exposed through legacy api for years."
  [m]
  (if-let [p (-> m :properties :pool1LengthMm)]
    (-> m
        (assoc-in [:properties :pool1LengthMM] p)
        (update :properties dissoc :pool1LengthMm))
    m))

(defn- normalize-number
  "Converts numeric values to doubles for consistent JSON serialization.
  Ensures 1 becomes 1.0 in JSON output."
  [v]
  (cond
    (integer? v) (double v)
    (number? v) v
    :else v))

(defn- normalize-properties
  "Normalizes numeric values in properties to doubles."
  [m]
  (if-let [props (:properties m)]
    (assoc m :properties
           (reduce-kv
            (fn [acc k v]
              (assoc acc k (normalize-number v)))
            {}
            props))
    m))

(defn ->old-lipas-sports-site*
  "Transforms new LIPAS sports-site m to old Lipas sports-site."
  ([m]
   (->old-lipas-sports-site* m (-> m :type :type-code types/all :props keys)))
  ([m prop-keys]
   (let [type-code (-> m :type :type-code)]
     (-> m

         (select-keys [:name :marketing-name :email :www :phone-number :renovation-years
                       :construction-year :location :properties :reservations-link])

         (assoc :last-modified (-> m :event-date UTC->last-modified)
                :name {:fi (:name m)
                       :se (-> m :name-localized :se)
                       :en (-> m :name-localized :en)}
                :admin (if (= "unknown" (:admin m)) "no-information" (:admin m))
                :owner (if (= "unknown" (:owner m)) "no-information" (:owner m))
                :school-use (-> m :properties :school-use?)
                :free-use (-> m :properties :free-use?)
                :type (select-keys (:type m) [:type-code]))

         (assoc-in [:location :neighborhood] (-> m :location :city :neighborhood))

         (update-in [:location :city] dissoc :neighborhood)

         (update-in [:location :address] #(if (< 100 (count %)) (subs % 0 100) %))

         (update :properties #(-> %
                                  (dissoc :school-use? :free-use?)
                                  (update :surface-material
                                          (comp old/surface-materials first))
                                  (select-keys prop-keys)
                                  (assoc :info-fi (-> m :comment))
                                  (update :parkour-hall-equipment-and-structures
                                          (fn [coll] (not-empty (str/join "," coll))))
                                  (update :travel-modes
                                          (fn [coll] (not-empty (str/join "," coll))))
                                  (set/rename-keys old/prop-mappings-reverse)))
         old/adapt-geoms
         utils/clean
         utils/->camel-case-keywords
         fix-special-case
         normalize-properties))))

(defmulti ->old-lipas-sports-site
  "Transforms New LIPAS sports-site to old Lipas sports-site. Details
  may depend on sports-sites :type-code and therefore each type-code
  can have their own implementation."
  (comp :type-code :type))

(defmethod ->old-lipas-sports-site :default
  [m]
  (->old-lipas-sports-site* m))

(defmethod ->old-lipas-sports-site 2510
  [m]
  (let [m1 (-> m
               (update :properties select-keys (-> 2510 types/all :props keys))
               old/add-ice-stadium-props)]
    (->old-lipas-sports-site* m1 (-> m1 :properties keys))))

(defmethod ->old-lipas-sports-site 2520
  [m]
  (let [m1 (-> m
               (update :properties select-keys (-> 2520 types/all :props keys))
               old/add-ice-stadium-props)]
    (->old-lipas-sports-site* m1 (-> m1 :properties keys))))

(defmethod ->old-lipas-sports-site 3110
  [m]
  (let [m1 (-> m
               (update :properties select-keys (-> 3110 types/all :props keys))
               (update :properties dissoc :area-m2)
               old/add-swimming-pool-props)]
    (->old-lipas-sports-site* m1 (-> m1 :properties keys))))

(defmethod ->old-lipas-sports-site 3130
  [m]
  (let [m1 (-> m
               (update :properties select-keys (-> 3130 types/all :props keys))
               (update :properties dissoc :area-m2)
               old/add-swimming-pool-props)]
    (->old-lipas-sports-site* m1 (-> m1 :properties keys))))

;; Swap Golfkenttä (alue) -> Golfkenttä (piste) for backwards
;; compatibility with legacy api and geoserver.
(defmethod ->old-lipas-sports-site 1650
  [m]
  (let [m1 (-> m
               (update :properties select-keys (-> 1620 types/all :props keys))
               (assoc-in [:type :type-code] 1620)
               (update-in [:location :geometries] gis/->centroid-point))]
    (->old-lipas-sports-site* m1 (-> m1 :properties keys))))
