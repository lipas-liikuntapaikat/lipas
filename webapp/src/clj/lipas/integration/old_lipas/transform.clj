(ns lipas.integration.old-lipas.transform
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as spec]
   [lipas.data.types :as types]
   [lipas.integration.old-lipas.sports-site :as old]
   [lipas.utils :as utils :refer [sreplace trim]]
   [clojure.string :as str]
   [lipas.backend.gis :as gis]))

(def helsinki-tz (java.time.ZoneId/of "Europe/Helsinki"))

(def df-in "Loose date format for inputs from old Lipas."
  (java.time.format.DateTimeFormatter/ofPattern
   "yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]"))

(def df-out "Strict date format for outputs to old Lipas."
  (java.time.format.DateTimeFormatter/ofPattern
   "yyyy-MM-dd HH:mm:ss.SSS"))

(def df-iso "Strict ISO date format with fixed precision (used in new LIPAS)."
  (java.time.format.DateTimeFormatter/ofPattern
   "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

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

(defn last-modified->UTC
  "Converts old Lipas last modified timestamp string to UTC ISO 8601 string.

  Old Lipas uses Europe/Helsinki TZ and format 'yyyy-MM-dd
  HH:mm:ss.SSS'."
  [s]
  (when s
    (-> (java.time.LocalDateTime/parse s df-in)
        (java.time.ZonedDateTime/of helsinki-tz)
        (.withZoneSameInstant java.time.ZoneOffset/UTC)
        (.format df-iso))))

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
                :nameSe (-> m :name-localized :se)
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
         fix-special-case))))

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

(defn ->sports-site
  "Transforms old lipas sports-site m to new LIPAS sports-site."
  [m]
  (let [props (:properties m)]
    {:lipas-id          (-> m :sportsPlaceId)
     :event-date        (-> m :lastModified last-modified->UTC)
     :status            "active"
     :name              (-> m :name)
     :marketing-name    (-> m :marketingName)
     :admin             (get old/admins (:admin m))
     :owner             (get old/owners (:owner m))
     :www               (-> m :www trim not-empty)
     :email             (-> m :email trim
                            (sreplace " " "")
                            (sreplace "(at)" "@")
                            (sreplace "[at]" "@")
                            (as-> $ (if (spec/valid? :lipas/email $) $ ""))
                            not-empty)
     :comment           (-> m :properties :infoFi trim not-empty)
     :properties        (-> props
                            (select-keys (keys old/prop-mappings))
                            (set/rename-keys old/prop-mappings)
                            (dissoc :info-fi)
                            (assoc :school-use? (-> m :schoolUse))
                            (assoc :free-use? (-> m :freeUse))
                            (assoc :surface-material
                                   (first (old/resolve-surface-material props)))
                            (assoc :surface-material-info
                                   (second (old/resolve-surface-material props))))
     :reservations-link (-> m :reservationsLink trim not-empty)
     :phone-number      (-> m :phoneNumber trim not-empty)
     :construction-year (-> m :constructionYear)
     :renovation-years  (-> m :renovationYears)
     :location
     {:address       (-> m :location :address trim not-empty)
      :geometries    (-> m :location :geometries
                         (update :features
                                 (fn [fs] (mapv #(dissoc % :properties) fs))))
      :postal-code   (-> m :location :postalCode trim (->>
                                                       (take 5)
                                                       (reduce str)) not-empty)
      :postal-office (-> m :location :postalOffice trim not-empty)
      :city
      {:city-code    (-> m :location :city :cityCode)
       :neighborhood (-> m :location :neighborhood trim not-empty)}}
     :type
     {:type-code (-> m :type :typeCode)}}))

(defn es-dump->sports-site
  "Transforms old Lipas sports site m from old Lipas ElasticSearch dump
  to new LIPAS sports site."
  [m]
  (-> m
      (assoc :name (-> m :name :fi))
      (assoc :admin (-> m :admin :fi))
      (assoc :owner (-> m :owner :fi))
      (assoc-in [:location :address] (or (-> m :location :address)
                                         "-"))
      (assoc-in [:location :postalCode] (or (-> m :location :postalCode)
                                            "00000"))
      (assoc-in [:location :neighborhood] (-> m :location :neighborhood :fi))
      (assoc :lastModified (or (-> m :lastModified not-empty)
                               "1980-01-01 02:00:00.000"))
      ->sports-site
      utils/clean))

(comment

  (map last-modified->UTC ["2019-01-03 00:00:00.000"
                           "2019-01-03 00:00:00.00"
                           "2019-01-03 00:00:00"])
  (map UTC->last-modified ["2019-01-03T00:00:00.000Z"
                           "2019-01-03T00:00:00.00Z"
                           "2019-01-03T00:00:00Z"])

  (def m1
    {:properties {},
     :water-treatment {:comment "Rikkihappo, Kempac",
                       :ozonation? true,
                       :activated-carbon? true,
                       :filtering-methods ["open-sand"]},
     :email "uimahalli@kirkkonummi.fi",
     :phone-number "040-126 9412",
     :building {:total-volume-m3 20100,
                :staff-count 11,
                :ventilation-units-count 5,
                :main-construction-materials ["concrete" "wood"],
                :main-designers "ProArk Oy",
                :total-pool-room-area-m2 800,
                :heat-source "district-heating",
                :total-surface-area-m2 4120,
                :total-water-area-m2 560,
                :ceiling-structures ["wood"],
                :supporting-structures ["concrete" "wood"]},
     :admin "city-sports",
     :www "http://www.kirkkonummi.fi/liikuntajaulkoilu/uimahalli",
     :name "Kirkkonummen uimahalli",
     :slides [{:length-m 42}],
     :construction-year 2001,
     :type {:type-code 3110},
     :lipas-id 505849,
     :pools [{:type "main-pool",
              :area-m2 340,
              :width-m 12,
              :length-m 25,
              :volume-m3 800,
              :depth-max-m 4,
              :depth-min-m 1.2,
              :temperature-c 27}
             {:type "teaching-pool",
              :area-m2 135,
              :width-m 10,
              :length-m 12,
              :volume-m3 80,
              :depth-max-m 1,
              :depth-min-m 0.3,
              :temperature-c 27}
             {:type "therapy-pool",
              :area-m2 77,
              :width-m 8.3,
              :length-m 9,
              :volume-m3 100,
              :depth-max-m 1.35,
              :depth-min-m 1,
              :temperature-c 31}
             {:type "childrens-pool",
              :area-m2 6.2,
              :width-m 2,
              :length-m 3,
              :volume-m3 1,
              :depth-max-m 0.5,
              :depth-min-m 0.25,
              :temperature-c 30}],
     :conditions {:daily-open-hours 12, :open-days-in-year 320},
     :saunas [{:type "sauna"}
              {:type "sauna"}
              {:type "sauna"}
              {:type "sauna"}
              {:type "sauna"}
              {:type "sauna"}
              {:type "sauna"}],
     :status "active",
     :comment "Vedenkulutuslukemat vaillinaisia v. 2016 osalta (mittari ei ole toiminut oikein).",
     :event-date "2020-01-22T17:55:03.570Z",
     :name-localized {:se "Kyrkslätt símhall"},
     :energy-saving {:shower-water-recovery true},
     :facilities {:showers-men-count 13,
                  :lockers-men-count 98,
                  :platforms-5m-count 0,
                  :kiosk? true,
                  :platforms-10m-count 0,
                  :hydro-massage-spots-count 6,
                  :lockers-women-count 95,
                  :platforms-7.5m-count 0,
                  :platforms-1m-count 1,
                  :showers-women-count 14,
                  :platforms-3m-count 1},
     :location {:city {:city-code 257},
                :address "Gesterbyntie 41",
                :geometries {:type "FeatureCollection",
                             :features [{:type "Feature",
                                         :geometry {:type "Point",
                                                    :coordinates [24.4466521313197
                                                                  60.1281667315472]}}]},
                :postal-code "02400",
                :postal-office "Kirkkonummi"},
     :owner "city",
     :hall-id "UU056000"})

  (->old-lipas-sports-site m1))
