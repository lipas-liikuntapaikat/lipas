(ns lipas.integration.old-lipas.transform
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as spec]
   [lipas.data.types :as types]
   [lipas.integration.old-lipas.sports-site :as old]
   [lipas.utils :as utils :refer [sreplace trim]]))

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
  [m]
  (let [type-code (-> m :type :type-code)]
    (-> m

        (select-keys [:name :email :www :phone-number :renovation-years
                      :construction-year :location :properties])

        (assoc :last-modified (-> m :event-date UTC->last-modified)
               :nameSe (-> m :name-localized :se)
               :admin (if (= "unknown" (:admin m)) "no-information" (:admin m))
               :owner (if (= "unknown" (:owner m)) "no-information" (:owner m))
               :school-use (-> m :properties :school-use?)
               :free-use (-> m :properties :free-use?)
               :type (select-keys (:type m) [:type-code]))

        (assoc-in [:location :neighborhood] (-> m :location :city :neighborhood))

        (update-in [:location :city] dissoc :neighborhood)

        (update :properties #(-> %
                                 (dissoc :school-use? :free-use?)
                                 (update :surface-material
                                         (comp old/surface-materials first))
                                 (select-keys (-> type-code types/all :props keys))
                                 (assoc :info-fi (-> m :comment))
                                 (set/rename-keys old/prop-mappings-reverse)))
        old/adapt-geoms
        utils/clean
        utils/->camel-case-keywords
        fix-special-case)))

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
  (-> m
      old/add-ice-stadium-props
      ->old-lipas-sports-site*))

(defmethod ->old-lipas-sports-site 2520
  [m]
  (-> m
      old/add-ice-stadium-props
      ->old-lipas-sports-site*))

(defmethod ->old-lipas-sports-site 3110
  [m]
  (-> m
      old/add-swimming-pool-props
      ->old-lipas-sports-site*))

(defmethod ->old-lipas-sports-site 3130
  [m]
  (-> m
      old/add-swimming-pool-props
      ->old-lipas-sports-site*))

(defn ->sports-site
  "Transforms old lipas sports-site m to new LIPAS sports-site."
  [m]
  (let [props (:properties m)]
    {:lipas-id          (-> m :sportsPlaceId)
     :event-date        (-> m :lastModified last-modified->UTC)
     :status            "active"
     :name              (-> m :name)
     :marketing-name    nil
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
                           "2019-01-03T00:00:00Z"]))
