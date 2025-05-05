(ns lipas-api.locations
  (:require
   [lipas-api.util :refer [only-non-nil-recur]]
   [lipas.backend.core :refer [feature-coll->geom-coll]]
   [lipas.backend.gis :as gis]
   [lipas.data.cities :as cities]))

(defn find-geoms
  ;; Pick geometries attached to a location.
  ;; A location can have only one kind of geoms.
  ;; Returns a map { :points ... :routes ... :areas ... }
  [location geoms]
  (let [p (:point-id location)
        r (:route-id location)
        a (:area-id location)]
    (cond
      p {:points (filter #(= p (:point-id %)) (:points geoms))}
      r {:routes (filter #(= r (:route-coll-id %)) (:routes geoms))}
      a {:areas (filter #(= a (:area-id %)) (:areas geoms))}
      :else nil)))

(defn start-coord [location]
  (let [geom (-> location :geometries :features first :geometry)]
    (case (:type geom)
      "Point"      (-> geom :coordinates)
      "LineString" (-> geom :coordinates first)
      "Polygon"    (-> geom :coordinates first first))))

(defn join-geoms
  [locations geoms]
  (map #(assoc % :geoms (find-geoms % geoms)) locations))

;; Converts a coordinate vector [lon lat] to a map with :lon and :lat keys.
;; In legacy db coordinates were stored as {:lon :lat}.
(defn- ->coords-map
  [[lon lat]]
  {:lon lon
   :lat lat})


(defn format-location
  [location locale id]
  (only-non-nil-recur
   (array-map
    :address (:address location)
    :postalCode (:postalCode location)
    :postalOffice (:postalOffice location)
    :city {:name     (-> (cities/by-city-code (-> location :city :cityCode))
                         :name)
           :cityCode (-> location :city :cityCode)}
    ;; new lipas has only finnish translation
    :neighborhood {:fi (-> location :neighborhood)
                   :en (-> location :neighborhood)
                   :se (-> location :neighborhood)}
    :geometries (-> location :geometries)
    :coordinates {:wgs84 (-> (start-coord location) ->coords-map)
                  :tm35fin (-> (gis/wgs84->tm35fin-no-wrap (start-coord location))
                               ->coords-map)}
    ;; added :geom-coll
    :geom-coll        (feature-coll->geom-coll (-> location :geometries))
    ;; what is this?
    :sportsPlaces (:sportPlaceId [1234]))))
