(ns lipas-api.locations
  (:require
   [lipas-api.geometries :refer [parse-coords to-geojson]]
   [lipas-api.util :refer [locale-key only-non-nil-recur read-string-safe]]
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

(defn join-geoms
  [locations geoms]
  (map #(assoc % :geoms (find-geoms % geoms)) locations))

(defn format-location
  [location locale]
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
    #_#_:coordinates {:wgs84   (parse-coords (:start-point-wgs84 location))
                      :tm35fin (parse-coords (:start-point-tm35fin location))}
    :sportsPlaces (:sportPlaceId ()))))





(comment
  l
  (def l {:address "Rantakyläntie 575",
          :city {:cityCode 976},
          :geometries {:features [{:geometry {:coordinates [23.6766294258769 66.4580931812035], :type "Point"},
                                   :id "f2112032-191b-426c-9d10-e69bca48588b",
                                   :properties {:pointId 123},
                                   :type "Feature"}],
                       :type "FeatureCollection"},
          :neighborhood "Kuntaosa",
          :postalCode "95635",
          :postalOffice "Ylitornio"})
  (cities/by-city-code 20)

  )
