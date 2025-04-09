(ns lipas-api.sports-places
  (:require
   [lipas-api.locations :refer [format-location join-geoms]]
   [lipas-api.properties :refer [format-props-db]]
   [lipas-api.util :refer [parse-path parse-year select-paths]]
   [lipas.backend.core :refer [feature-coll->geom-coll]]
   [lipas.data.owners :as owners]
   [lipas.data.prop-types :as props]
   [lipas.data.types-old :as types-old]))

(def df-in (java.time.format.DateTimeFormatter/ofPattern
            "yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]"))

(def df-out (java.time.format.DateTimeFormatter/ofPattern
             "yyyy-MM-dd HH:mm:ss.SSS"))

(defn parse-date [x]
  (try
    (-> x
        (java.time.LocalDateTime/parse df-in)
        (.format df-out))
    (catch Exception e)))

(comment
  (parse-date "2014-10-02 12:50:37.123")
  (parse-date "2014-10-02 12:50:37.12")
  (parse-date "2014-10-02 12:50:37.1")
  (parse-date "2014-10-02 12:50:37"))

(defn format-owner [sp]
  (assoc sp :owner (-> owners/all (get "city"))))

(defn fill-properties [m]
  (reduce (fn [acc k] (assoc-in acc [:props k] (props/all k)))
          m
          (-> m :props keys)))

(defn format-sports-place
  [sports-place locale location-format-fn props-format-fn]
  {:sportsPlaceId    (:id sports-place)
   :name             (:name sports-place)
   :marketingName    (:marketingName sports-place)
   :type             {:typeCode (-> sports-place :type :typeCode)
                      :name     (-> (types-old/all
                                     (-> sports-place :type :typeCode))
                                    :name)}
   :schoolUse        (:schoolUse sports-place)
   :freeUse          (:freeUse sports-place)
   :constructionYear (parse-year (:constructionYear sports-place))
   :renovationYears  (:renovationYears sports-place)
   :lastModified     (-> sports-place :lastModified parse-date)
   :owner            (owners/all (-> sports-place :owner))
   :admin            (lipas.data.admins/all (-> sports-place :admin))
   :phoneNumber      (:phoneNumber sports-place)
   :reservationsLink (:reservationsLink sports-place)
   :www              (:www sports-place)
   :email            (:email sports-place)
   :location         (when-let [location (:location sports-place)]
                       (apply location-format-fn [location locale (:sportsPlaceId sports-place)]))
   :properties       (:properties sports-place)})




(comment
  #_(format-sports-place sp :all format-location format-props-db)
  #_(format-location (:location sp) :all)

  (def sp {:admin "city-technical-services",
           :constructionYear 1966,
           :email "julkinen@sahkoposti.fi",
           :freeUse true,
           :id 74782,
           :lastModified "2025-04-01 16:16:54.222",
           :location {:address "Rantakyläntie 575",
                      :city {:cityCode 976},
                      :geometries {:features [{:geometry {:coordinates [23.6766294258769 66.4580931812035], :type "Point"},
                                               :id "f2112032-191b-426c-9d10-e69bca48588b",
                                               :properties {:pointId 123},
                                               :type "Feature"}],
                                   :type "FeatureCollection"},
                      :neighborhood "Kuntaosa",
                      :postalCode "95635",
                      :postalOffice "Ylitornio"},
           :marketingName "markkinointinimi",
           :name {:en "Kaulirannan koulun kaukalo english",
                  :fi "Kaulirannan koulun kaukalo suomeksi",
                  :se "Kaulirannan koulun kaukalo swedish"},
           :owner "city",
           :phoneNumber "0415072725",
           :properties {:areaM2 1250,
                        :changingRooms true,
                        :fieldLengthM 50,
                        :fieldWidthM 25,
                        :iceRinksCount 1,
                        :infoFi "lisätieto",
                        :lightRoof true,
                        :ligthing true,
                        :matchClock true,
                        :mayBeShownInHarrastuspassiFi true,
                        :toilet true},
           :renovationYears [1966],
           :reservationsLink "tilavaraukset",
           :schoolUse true,
           :type {:typeCode 1530},
           :www "www.ylitornio.fi"})

  ((requiring-resolve 'lipas.search-indexer/index-legacy-sports-site!) (user/db) (user/search) "legacy-2025-03-31t15-49-55-720612" 74782)
  )

;;
;; DB backend
;;

(defn format-sports-place-db
  [sports-place locale]
  (format-sports-place sports-place locale format-location format-props-db))

(defn find-props
  [sports-place props]
  (filter #(= (:id sports-place) (:sports-place-id %)) props))

(defn join-props
  [sports-places props]
  (map #(assoc % :props (find-props % props)) sports-places))

(defn find-location
  [sports-place locations]
  (first (filter #(= (:location-id %) (:location-id sports-place)) locations)))

(defn join-location
  [sports-place location]
  (let [with-sps (->
                  location
                  (assoc :start-point-tm35fin (:start-point-3067 sports-place))
                  (assoc :start-point-wgs84 (:start-point-4326 sports-place)))]
    (assoc sports-place :location with-sps)))

(defn join-locations
  [sports-places locations]
  (map #(join-location % (find-location % locations)) sports-places))

(defn join-data
  [{places :places props :props locations :locations geoms :geoms}]
  (let [with-props (join-props places props)
        locs-geoms (join-geoms locations geoms)
        with-locs (join-locations with-props locs-geoms)]
    with-locs))

(defn join-data-single
  [{place :place props :props location :location geoms :geoms}]
  (let [loc-geo (assoc location :geoms geoms)]
    (-> place
        (assoc :props props)
        (join-location loc-geo))))

;;
;; Elastic Search Backend
;;

(defn update-with-locale
  [sp locale fallback-locale path]
  (let [value (or (get-in sp (conj path locale))
                  (get-in sp (conj path fallback-locale)))]
    (assoc-in sp path value)))

(defn format-sports-place-es
  [sports-place locale]
  (-> sports-place
      (update :location dissoc :geom-coll)
      (update-with-locale locale :fi [:name])
      (update-with-locale locale :fi [:type :name])
      (update-with-locale locale :fi [:location :city :name])
      (update-with-locale locale :fi [:location :neighborhood])
      (update-with-locale locale :fi [:owner])
      (update-with-locale locale :fi [:admin])))

(defn filter-and-format
  [locale fields sp]
  (let [paths (map parse-path fields)
        formatted (format-sports-place-es sp locale)]
    (apply select-paths (cons formatted paths))))
