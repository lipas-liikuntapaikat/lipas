(ns lipas.backend.legacy.core
  (:require
   [clojure.string :as string]
   [lipas.data.types :as types]
   [qbits.spandex :as es]
   [qbits.spandex.utils :as es-utils]
   [ring.util.codec :as codec]))

;; This file contains mostly copy & pasted code from old API codebase, beware!

(defn maybe-truncate [s]
  (if (> (count s) 23)
    (subs s 0 23)
    s))

(defn create-modified-after-filter
  [timestamp]
  (when timestamp {:range {:lastModified {:gt (maybe-truncate timestamp)}}}))

(defn create-harrastuspassi-filter [harrastuspassi?]
  (when harrastuspassi?
    {:terms {:properties.mayBeShownInHarrastuspassiFi [true]}}))

(defn create-excursion-map-filter [excursion-map?]
  (when excursion-map?
    {:terms {:properties.mayBeShownInExcursionMapFi [true]}}))

(defn create-geo-filter
  "Creates geo_distance filter:
  :geo_distance {:distance ... }
                 :point {:lon ... :lat ... }}"
  [geo-params]
  (when geo-params {:geo_distance geo-params}))

(defn create-filter
  [k coll]
  (when (seq coll) {:terms {k coll}}))

(defn create-filters
  [{:keys [city-codes type-codes close-to modified-after excursion-map? harrastuspassi?]}]
  (not-empty (remove nil? [(create-filter :location.city.cityCode city-codes)
                           (create-filter :type.typeCode type-codes)
                           (create-excursion-map-filter excursion-map?)
                           (create-harrastuspassi-filter harrastuspassi?)
                           (create-geo-filter close-to)
                           (create-modified-after-filter modified-after)])))

(defn append-filters
  [params query-map]
  (if-let [filters (create-filters params)]
    (assoc-in query-map [:bool] {:filter filters})
    query-map))

(defn append-search-string
  [params query-map]
  (if-let [qs (:search-string params)]
    (assoc-in query-map [:bool :must] [{:query_string {:query qs}}])
    query-map))

(defn resolve-query
  [params]
  (if-let [query (->> {}
                      (append-filters params)
                      (append-search-string params)
                      not-empty)]
    query
    {:match_all        {}}))

(defn fetch-sports-places*
  [client params]
  (let [query {:method :get
               :url    (es-utils/url [:legacy_sports_sites_current :_search])
               :body
               {:query            (resolve-query params)
                :track_total_hits true
                :size             (:limit params)
                :from             (* (:offset params) (:limit params))}}]
    (es/request client query)))

(defn not-blank
  [map-val]
  (cond (string? map-val) (if (string/blank? map-val) nil map-val)
        (coll? map-val) (not-empty map-val)
        :else map-val))

(defn only-non-nil-recur
  "Traverses through map recursively and removes all nil values."
  [a-map]
  (reduce-kv #(if (map? %3)
                (let [fixed (only-non-nil-recur %3)]
                  (if ((complement empty?) fixed)
                    (assoc %1 %2 fixed)
                    %1))
                (if (some? (not-blank %3))
                  (assoc %1 %2 %3)
                  %1))
             {} a-map))

(defn parse-path
  "Parses keyword path from dot (.) delimited input and returns a vector
   representing the path. It doesn't like nil.

  :kissa.koira.kana => [:kissa :koira :kana]
  :kissa => [:kissa]

  Works with strings or anything that works with `keyword`.

  \"kissa.koira.kana\" => [:kissa :koira :kana]
  'kissa.koira.kana => [:kissa :koira :kana]
  "
  [k]
  {:pre [(keyword k)]}
  (into [] (map keyword (clojure.string/split (name k) #"\."))))

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

(defn select-paths
  "Similar to select-keys, just the 'key' here is a path in the nested map"
  [m & paths]
  (reduce
   (fn [result path]
     (assoc-in result path (get-in m path)))
   {}
   paths))

(defn filter-and-format
  [locale fields sp]
  (let [paths (map parse-path fields)
        formatted (format-sports-place-es sp locale)]
    (apply select-paths (cons formatted paths))))

(defn more?
  "Returns true if result set was limited considering
  page-size and requested page, otherwise false."
  [results page-size page]
  (let [total (-> results :hits :total :value)
        n     (count (-> results :hits :hits))]
    (< (+ (* page page-size) n) total)))

(defn resolve-params
  [input]
  (merge {:lang       (or (:lang input) :fi)
          :page       (or (:page input) 1)
          :limit      (or (:pageSize input) 10)
          :offset     (dec (or (:page input) 1))
          :type-codes (:typeCodes input)
          :city-codes (:cityCodes input)
          :since      {:since input}
          :fields     (:fields input)}
         (when (:closeTo input)
           {:close-to {:distance (* (-> input :closeTo :distanceKm) 1000)
                       :location.coordinates.wgs84
                       {:lon (-> input :closeTo :lon)
                        :lat (-> input :closeTo :lat)}}})))

(defn fetch-sports-places
  "Fetches list of sports-places from ElasticSearch backend."
  [client locale params fields]
  (let [data (:body (fetch-sports-places* client params))
        places (->> (map :_source (-> data :hits :hits)))
        fields (conj fields :sportsPlaceId)
        format-fn (fn [place]
                    (let [formatted (format-sports-place place locale)]
                      (filter-and-format locale fields formatted)))]
    {:partial? (more? data (:limit params) (:offset params))
     :total (-> data :hits :total :value)
     :results (map (comp only-non-nil-recur format-fn) places)}))


(defn last-page
  [total page-size]
  (int (Math/ceil (/ total page-size))))

(defn create-page-links
  [path query-params page page-size total]
  {:first (str path "/?" (codec/form-encode (assoc query-params "page" 1)))
   :next  (str path "/?" (codec/form-encode (assoc query-params "page" (inc page))))
   :prev  (str path "/?" (codec/form-encode (assoc query-params "page"
                                                   (max (dec page) 1))))
   :last  (str path "/?" (codec/form-encode (assoc query-params "page"
                                                   (last-page total page-size))))
   :total total})

(defn linked-partial-content
  [body {:keys [first last next prev total]}]
  {:status  206
   :headers {"Link" (str "<" next ">; rel=\"next\", "
                         "<" last ">; rel=\"last\", "
                         "<" first ">; rel=\"first\", "
                         "<" prev ">; rel=\"prev\"")
             "X-total-count" (str total)}
   :body    (vec body)})




#_(defn format-location
  [location locale]
  (only-non-nil-recur
   (array-map
    :locationId (:location-id location)
    :address (:address location)
    :postalCode (:postal-code location)
    :postalOffice (:postal-office location)
    :city {:name     ((locale-key :city-name locale) location)
           :cityCode (:city-code location)}
    :neighborhood ((locale-key :neighborhood locale) location)
    :geometries (when-let [geoms (:geoms location)] (to-geojson geoms))
    :coordinates {:wgs84   (parse-coords (:start-point-wgs84 location))
                  :tm35fin (parse-coords (:start-point-tm35fin location))}
    :sportsPlaces (read-string-safe (:sports-places location)))))

(defn locale-key
  [a-key locale]
  (if (= :all locale)
    (fn [sp] {:fi ((locale-key a-key :fi) sp)
              :se ((locale-key a-key :se) sp)
              :en ((locale-key a-key :en) sp)})
    (keyword (str (name a-key) "-" (name locale)))))

(defn format-sports-place
  ([sports-place locale]
   (format-sports-place sports-place locale #_#_format-location format-props-db))
  ([sports-place locale location-format-fn props-format-fn]
   {:sportsPlaceId    (:id sports-place)
    :name             (or (not-blank ((locale-key :name locale) sports-place))
                          (:name-fi sports-place))
    :marketingName    (:marketing-name sports-place)
    :type             {:typeCode (:type-code sports-place)
                       :name     ((locale-key :type-name locale) sports-place)}
    :schoolUse        (:school-sports-place sports-place)
    :freeUse          (:free-use sports-place)
    #_#_:constructionYear (parse-year (:construction-year sports-place))
    :renovationYears  (not-empty (read-string (:renovation-years sports-place)))
    #_#_:lastModified     (-> sports-place :last-modified parse-date)
    ;;:accessible (:accessible sports-place)
    :owner            ((locale-key :owner-name locale) sports-place)
    :admin            ((locale-key :admin-name locale) sports-place)
    :phoneNumber      (:phone-number sports-place)
    :reservationsLink (:reservations-link sports-place)
    :www              (:www sports-place)
    :email            (:email sports-place)
    :location         (when-let [location (:location sports-place)]
                        (apply location-format-fn [location locale]))
    :properties       (apply props-format-fn [(:props sports-place) locale])}))

(comment
  (lipas.search-indexer/-main "--legacy")

  (def sp {:properties {:fieldLengthM 50 :iceRinksCount 1 :areaM2 1250 :fieldWidthM 25}
           :schoolUse true
           :admin "city-technical-services"
           :www "www.ylitornio.fi"
           :name "Kaulirannan koulun kaukalo suomeksi"
           :type {:typeCode 1530}
           :lastModified "2025-03-03 21:50:21.778"
           :nameSe "Kaulirannan koulun kaukalo swedish"
           :location
           {:city {:cityCode 976}
            :address "Rantakyläntie 575"
            :geometries
            {:type "FeatureCollection"
             :features [{:id "f2112032-191b-426c-9d10-e69bca48588b"
                         :type "Feature"
                         :geometry {:type "Point"
                                    :coordinates [23.6766294258769 66.4580931812035]}
                         :properties {:pointId 123}}]}
            :postalCode "95635"
            :postalOffice "Ylitornio"}
           :owner "city"})

  (:school-sports-place sp))

#_(defn format-sports-place
  [sports-place]
   (let
     [formatted-sports-place
      {:sportsPlaceId (:id sports-place)
       :name             (or (:name-localized sports-place)
                             (:name sports-place))}
      #_{:sportsPlaceId    (:id sports-place)
         :name             (or (:name-localized sports-place)
                               (:name sports-place))
         :marketingName    (:marketing-name sports-place)
         :type             (let [typeCode (-> sports-place :type :type-code)]
                             {:typeCode typeCode
                              :name (-> types/all typeCode :name)})
         :schoolUse        true #_(:school-sports-place sports-place)
         :freeUse          true #_(:free-use sports-place)
         :constructionYear 1999 ;; (parse-year (:construction-year sports-place))
         :renovationYears  1999 ;; (not-empty (read-string (:renovation-years sports-place)))
         :lastModified     (-> sports-place :event-date #_parse-date)
             ;;:accessible (:accessible sports-place)
         :owner            (:owner sports-place) ;;((locale-key :owner-name locale) sports-place)
         :admin            (:admin sports-place) ;;((locale-key :admin-name locale) sports-place)
         #_#_#_#_#_#_#_#_#_#_#_#_:phoneNumber      (:phone-number sports-place)
                             :reservationsLink (:reservations-link sports-place)
                         :www              (:www sports-place)
                     :email            (:email sports-place)
                 :location         (when-let [location (:location sports-place)]
                                     (apply location-format-fn [location locale]))
             :properties       (apply props-format-fn [(:props sports-place) locale])}]
     formatted-sports-place))
