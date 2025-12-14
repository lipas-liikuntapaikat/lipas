(ns legacy-api.locations
  (:require
   [legacy-api.util :refer [only-non-nil-recur]]
   [lipas.backend.geom-utils :refer [feature-coll->geom-coll]]
   [lipas.backend.gis :as gis]
   [lipas.data.cities :as cities]
   [lipas.utils :as utils]))

(defn start-coord [location]
  (let [geom (-> location :geometries :features first :geometry)]
    (case (:type geom)
      "Point" (-> geom :coordinates)
      "LineString" (-> geom :coordinates first)
      "Polygon" (-> geom :coordinates first first))))

;; Converts a coordinate vector [lon lat] to a map with :lon and :lat keys.
;; In legacy db coordinates were stored as {:lon :lat}.
(defn- ->coords-map
  [[lon lat]]
  {:lon lon
   :lat lat})

(defn format-location
  [location _ sports-place-id]
  (only-non-nil-recur
   (array-map
    ;; Legacy field - hardcoded to 0 as it doesn't exist in new LIPAS
    :locationId 0
    :address (:address location)
    :postalCode (:postalCode location)
    :postalOffice (:postalOffice location)
    :city (let [city-code (utils/->int (-> location :city :cityCode))]
            {:name (-> (cities/by-city-code city-code) :name)
             :cityCode city-code})
    ;; new lipas has only finnish translation
    :neighborhood {:fi (-> location :neighborhood)
                   :en (-> location :neighborhood)
                   :se (-> location :neighborhood)}
    :geometries (-> location :geometries)
    :coordinates {:wgs84 (-> (start-coord location) ->coords-map)
                  :tm35fin (-> (gis/wgs84->tm35fin-no-wrap (start-coord location))
                               ->coords-map)}
    ;; added :geom-coll
    :geom-coll (feature-coll->geom-coll (-> location :geometries))
    ;; Legacy field - in old LIPAS, locations could have multiple sports places.
    ;; In new LIPAS, the mapping is always 1:1, so we return a vector with just the current ID.
    :sportsPlaces [(utils/->int sports-place-id)])))
