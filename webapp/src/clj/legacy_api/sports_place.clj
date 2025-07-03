(ns legacy-api.sports-place
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [legacy-api.util :refer [parse-path parse-year select-paths]]
   [lipas.data.admins :as admins]
   [lipas.data.owners :as owners]
   [lipas.data.types :as types]
   [lipas.data.types-old :as types-old]
   [lipas.utils :as utils]))

;; Surface materials mapping (moved from sports-place.clj)
(def surface-materials
  {"gravel" "Sora"
   "fiberglass" "Lasikuitu"
   "brick-crush" "Tiilimurska"
   "water" "Vesi"
   "concrete" "Betoni"
   "textile" "Tekstiili"
   "asphalt" "Asfaltti"
   "ceramic" "Keraaminen"
   "rock-dust" "Kivituhka"
   "deinked-pulp" "Siistausmassa"
   "stone" "Kivi"
   "metal" "Metalli"
   "soil" "Maa"
   "woodchips" "Hake"
   "grass" "Nurmi"
   "synthetic" "Muovi / synteettinen"
   "sand" "Hiekka"
   "artificial-turf" "Tekonurmi"
   "wood" "Puu"
   "sawdust" "Sahanpuru"
   "sand-infilled-artificial-turf" "Hiekkatekonurmi"})

(def df-in (java.time.format.DateTimeFormatter/ofPattern
            "yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]"))

(def df-out (java.time.format.DateTimeFormatter/ofPattern
             "yyyy-MM-dd HH:mm:ss.SSS"))

(defn parse-date
  "Parses a date string using the defined input formatter."
  [x]
  (try
    (when x
      (when-not (string? x)
        (throw (ex-info "Date input must be a string"
                        {:type :invalid-input
                         :date x
                         :date-type (type x)})))

      (-> x
          (java.time.LocalDateTime/parse df-in)
          (.format df-out)))
    (catch java.time.format.DateTimeParseException ex
      (throw (ex-info "Invalid date format"
                      {:type :date-parse-error
                       :date x
                       :expected-format "yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]"}
                      ex)))
    (catch clojure.lang.ExceptionInfo ex
      (throw ex)) ; Re-throw our own exceptions
    (catch Exception ex
      (throw (ex-info "Unexpected error parsing date"
                      {:type :unexpected-error
                       :date x}
                      ex)))))

(defn convert-iso8601-to-legacy
  "Converts ISO8601 timestamp (2021-09-16T08:49:04.675Z) to legacy format (2021-09-16 08:49:04.675)"
  [iso-timestamp]
  (try
    (when iso-timestamp
      (when-not (string? iso-timestamp)
        (throw (ex-info "Timestamp must be a string"
                        {:type :invalid-input
                         :timestamp iso-timestamp
                         :timestamp-type (type iso-timestamp)})))

      (-> iso-timestamp
          (str/replace #"T" " ")
          (str/replace #"Z$" "")
          ;; Ensure we have 3 decimal places for milliseconds
          (as-> s (if (re-find #"\.\d{1,3}$" s)
                    s
                    (str s ".000")))))
    (catch clojure.lang.ExceptionInfo ex
      (if (= :invalid-input (:type (ex-data ex)))
        (throw ex)
        (throw (ex-info "Failed to convert timestamp format"
                        {:type :conversion-error
                         :timestamp iso-timestamp}
                        ex))))
    (catch Exception ex
      (throw (ex-info "Unexpected error converting timestamp"
                      {:type :unexpected-error
                       :timestamp iso-timestamp}
                      ex)))))

(comment
  (parse-date "2014-10-02 12:50:37.123")
  (parse-date "KEKKONEN")
  (parse-date "2014-10-02 12:50:37.12")
  (parse-date "2014-10-02 12:50:37.1")
  (parse-date "2014-10-02 12:50:37"))

(defn format-sports-place
  [sports-place locale location-format-fn]
  {:sportsPlaceId (:id sports-place)
   :name (:name sports-place)
   :marketingName (:marketingName sports-place)
   :type {:typeCode (-> sports-place :type :typeCode)
          :name (-> (types-old/all
                     (-> sports-place :type :typeCode))
                    :name)}
   :schoolUse (:schoolUse sports-place)
   :freeUse (:freeUse sports-place)
   :constructionYear (parse-year (:constructionYear sports-place))
   :renovationYears (:renovationYears sports-place)
   :lastModified (-> sports-place :lastModified parse-date)
   :owner (owners/all (-> sports-place :owner))
   :admin (admins/all (-> sports-place :admin))
   :phoneNumber (:phoneNumber sports-place)
   :reservationsLink (:reservationsLink sports-place)
   :www (:www sports-place)
   :email (:email sports-place)
   :location (when-let [location (:location sports-place)]
               (apply location-format-fn [location locale (:sportsPlaceId sports-place)]))
   :properties (:properties sports-place)})

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
      ;; Handle owner and admin fields - convert enum keys to localized strings
      (update :owner #(or (get-in % [locale]) ; Preserve if already localized
                          (get-in owners/all [% locale]) ; Convert enum key to localized string
                          (get-in owners/all [% :en]) ; Fallback to English
                          %)) ; Fallback to original value
      (update :admin #(or (get-in % [locale]) ; Preserve if already localized
                          (get-in admins/all [% locale]) ; Convert enum key to localized string
                          (get-in admins/all [% :en]) ; Fallback to English
                          %)) ; Fallback to original value
      ;; Handle lastModified field conversion
      (update :lastModified #(or % (convert-iso8601-to-legacy (:event-date sports-place))))
      ;; Extract schoolUse and freeUse from properties for legacy API compatibility
      ;; Only if they don't already exist at the top level
      (update :schoolUse #(or % (get-in sports-place [:properties :school-use?])))
      (update :freeUse #(or % (get-in sports-place [:properties :free-use?])))))

(defn filter-and-format
  [locale fields sp]
  (let [formatted (format-sports-place-es sp locale)]
    (if (empty? fields)
      formatted ; Return all formatted data if no specific fields requested
      (let [paths (map parse-path fields)]
        (apply select-paths (cons formatted paths))))))

(def prop-mappings
  {:liftsCount :lifts-count,
   :boxingRingsCount :boxing-rings-count,
   :floorballFieldsCount :floorball-fields-count,
   :pistolShooting :pistol-shooting?,
   :shotputCount :shotput-count,
   :swimmingPoolCount :swimming-pool-count,
   :pool1TemperatureC :pool-temperature-c,
   :classifiedRoute :classified-route,
   :equipmentRental :equipment-rental?,
   :ligthing :ligthing?,
   :kiosk :kiosk?,
   :fieldsCount :fields-count,
   :kPoint :k-point,
   :skiService :ski-service?,
   :fieldLengthM :field-length-m,
   :showJumping :show-jumping?,
   :skiTrack :ski-track?,
   :cosmicBowling :cosmic-bowling?,
   :field3AreaM2 :field-3-area-m2,
   :polevaultPlacesCount :polevault-places-count,
   :spinningHall :spinning-hall?,
   :climbingWall :climbing-wall?,
   :iceClimbing :ice-climbing?,
   :sauna :sauna?,
   :trackWidthM :track-width-m,
   :routeWidthM :route-width-m,
   :basketballFieldsCount :basketball-fields-count,
   :pier :pier?,
   :radioAndTvCapabilities :radio-and-tv-capabilities?,
   :bowlingLanesCount :bowling-lanes-count,
   :squashCourtsCount :squash-courts-count,
   :altitudeDifference :altitude-difference,
   :gymnasticsSpace :gymnastics-space?,
   :climbingWallWidthM :climbing-wall-width-m,
   :shower :shower?,
   :skijumpHillMaterial :skijump-hill-material,
   :summerUsage :summer-usage?,
   :hammerThrowPlacesCount :hammer-throw-places-count,
   :parkingPlace :parking-place?,
   :trackLengthM :track-length-m,
   :climbingRoutesCount :climbing-routes-count,
   :field3WidthM :field-3-width-m,
   :skiTrackTraditional :ski-track-traditional?,
   :litSlopesCount :lit-slopes-count,
   :coveredStandPersonCount :covered-stand-person-count,
   :surfaceMaterial :surface-material,
   :iceReduction :ice-reduction?,
   :pool1MaxDepthM :pool-max-depth-m,
   :poolWaterAreaM2 :pool-water-area-m2,
   :shortestSlopeM :shortest-slope-m,
   :badmintonCourtsCount :badminton-courts-count,
   :green :green?,
   :fieldWidthM :field-width-m,
   :oldLipasTypecode :old-lipas-typecode,
   :accessibilityInfo :accessibility-info,
   :trainingWall :training-wall?,
   :lightRoof :light-roof?,
   :basketballFieldType :basketball-field-type,
   :javelinThrowPlacesCount :javelin-throw-places-count,
   :field1LengthM :field-1-length-m,
   :areaKm2 :area-km2,
   :airGunShooting :air-gun-shooting?,
   :skiTrackFreestyle :ski-track-freestyle?,
   :jumpsCount :jumps-count,
   :pool1LengthMM :pool-length-m,
   :automatedTiming :automated-timing?,
   :surfaceMaterialInfo :surface-material-info,
   :adpReadiness :adp-readiness?,
   :weightLiftingSpotsCount :weight-lifting-spots-count,
   :euBeach :eu-beach?,
   :snowparkOrStreet :snowpark-or-street?,
   :restPlacesCount :rest-places-count,
   :playground :playground?,
   :longjumpPlacesCount :longjump-places-count,
   :tatamisCount :tatamis-count,
   :holesCount :holes-count,
   :halfpipeCount :halfpipe-count,
   :beachLengthM :beach-length-m,
   :handballFieldsCount :handball-fields-count,
   :matchClock :match-clock?,
   :sprintLanesCount :sprint-lanes-count,
   :hallLengthM :hall-length-m,
   :exerciseMachinesCount :exercise-machines-count,
   :wrestlingMatsCount :wrestling-mats-count,
   :volleyballFieldsCount :volleyball-fields-count,
   :tennisCourtsCount :tennis-courts-count,
   :mayBeShownInExcursionMapFi :may-be-shown-in-excursion-map-fi?,
   :mayBeShownInHarrastuspassiFi :may-be-shown-in-harrastuspassi-fi?,
   :trainingSpotSurfaceMaterial :training-spot-surface-material,
   :longestSlopeM :longest-slope-m,
   :heightM :height-m,
   :toilet :toilet?,
   :winterSwimming :winter-swimming?,
   :shotgunShooting :shotgun-shooting?,
   :curlingLanesCount :curling-lanes-count,
   :climbingWallHeightM :climbing-wall-height-m,
   :litRouteLengthKm :lit-route-length-km,
   :throwintSportsPlaces :throwing-sports-spots-count,
   :changingRooms :changing-rooms?,
   :groupExerciseRoomsCount :group-exercise-rooms-count,
   :archery :archery?,
   :maxVerticalDifference :max-vertical-difference,
   :futsalFieldsCount :futsal-fields-count,
   :automatedScoring :automated-scoring?,
   :innerLaneLengthM :inner-lane-length-m,
   :field1WidthM :field-1-width-m,
   :landingPlacesCount :landing-places-count,
   :sprintTrackLengthM :sprint-track-length-m,
   :footballFieldsCount :football-fields-count,
   :otherPoolsCount :other-pools-count,
   :pool1TracksCount :pool-tracks-count,
   :gymnasticRoutinesCount :gymnastic-routines-count,
   :shootingPositionsCount :shooting-positions-count,
   :freestyleSlope :freestyle-slope?,
   :scoreboard :scoreboard?,
   :pool1WidthM :pool-width-m,
   :rifleShooting :rifle-shooting?,
   :finishLineCamera :finish-line-camera?,
   :runningTrackSurfaceMaterial :running-track-surface-material,
   :field1AreaM2 :field-1-area-m2,
   :horseCarriageAllowed :horse-carriage-allowed?,
   :circularLanesCount :circular-lanes-count,
   :standCapacityPerson :stand-capacity-person,
   :discusThrowPlaces :discus-throw-places,
   :pPoint :p-point,
   :field2LengthM :field-2-length-m,
   :tableTennisCount :table-tennis-count,
   :boatPlacesCount :boat-places-count,
   :field2AreaM2 :field-2-area-m2,
   :otherPlatforms :other-platforms?,
   :iceRinksCount :ice-rinks-count,
   :boatLaunchingSpot :boat-launching-spot?,
   :tobogganRun :toboggan-run?,
   :field2WidthM :field-2-width-m,
   :areaM2 :area-m2,
   :trackType :track-type,
   :routeLengthKm :route-length-km,
   :loudspeakers :loudspeakers?,
   :outdoorExerciseMachines :outdoor-exercise-machines?,
   :slopesCount :slopes-count,
   :field3LengthM :field-3-length-m,
   :fencingBasesCount :fencing-bases-count,
   :hallWidthM :hall-width-m,
   :heating :heating?,
   :winterUsage :winter-usage?,
   :plasticOutrun :plastic-outrun?,
   :highjumpPlacesCount :highjump-places-count,
   :freeRifleShooting :free-rifle-shooting?,
   :range :range?,
   :pool1MinDepthM :pool-min-depth-m,
   :inrunsMaterial :inruns-material,
   :skijumpHillType :skijump-hill-type
   :7m5PlatformsCount :platforms-7.5m-count,
   :waterslidesTotalLengthM :waterslides-total-length-m,
   :10mPlatformsCount :platforms-10m-count,
   :pool4TemperatureC :pool-4-temperature-c,
   :pool5LengthM :pool-5-length-m,
   :5mPlatformsCount :platforms-5m-count,
   :pool5WidthM :pool-5-width-m,
   :3mPlatformsCount :platforms-3m-count,
   :pool5MinDepthM :pool-5-min-depth-m,
   :pool4MinDepthM :pool-4-min-depth-m,
   :waterSlidesCount :water-slides-count,
   :pool5MaxDepthM :pool-5-max-depth-m,
   :pool2LengthM :pool-2-length-m,
   :infoFi :info-fi,
   :pool3TracksCount :pool-3-tracks-count,
   :pool2MinDepthM :pool-2-min-depth-m,
   :pool5TemperatureC :pool-5-temperature-c,
   :pool3WidthM :pool-3-width-m,
   :pool3LengthM :pool-3-length-m,
   :pool2TracksCount :pool-2-tracks-count,
   :pool3MinDepthM :pool-3-min-depth-m,
   :pool5TracksCount :pool-5-tracks-count,
   :pool4WidthM :pool-4-width-m,
   :pool4MaxDepthM :pool-4-max-depth-m,
   :1mPlatformsCount :platforms-1m-count,
   :pool3TemperatureC :pool-3-temperature-c,
   :pool2WidthM :pool-2-width-m,
   :pool4TracksCount :pool-4-tracks-count,
   :pool3MaxDepthM :pool-3-max-depth-m,
   :pool2MaxDepthM :pool-2-max-depth-m,
   :pool4LengthM :pool-4-length-m,
   :pool2TemperatureC :pool-2-temperature-c
   :padelCourtsCount :padel-courts-count
   :rapidCanoeingCentre :rapid-canoeing-centre?
   :canoeingClub :canoeing-club?
   :activityServiceCompany :activity-service-company?
   :bikeOrienteering? :bike-orienteering?,
   :field2FlexibleRink? :field-2-flexible-rink?,
   :sportSpecification :sport-specification,
   :travelModeInfo :travel-mode-info,
   :spaceDivisible :space-divisible,
   :auxiliaryTrainingArea? :auxiliary-training-area?,
   :pyramidTablesCount :pyramid-tables-count,
   :lightingInfo :lighting-info,
   :customerServicePoint? :customer-service-point?,
   :parkourHallEquipmentAndStructures
   :parkour-hall-equipment-and-structures,
   :waterPoint :water-point,
   :activeSpaceWidthM :active-space-width-m,
   :fitnessStairsLengthM :fitness-stairs-length-m,
   :mirrorWall? :mirror-wall?,
   :sleddingHill? :sledding-hill?,
   :highestObstacleM :highest-obstacle-m,
   :skiOrienteering? :ski-orienteering?,
   :freeUse :free-use,
   :totalBilliardTablesCount :total-billiard-tables-count,
   :boatingServiceClass :boating-service-class,
   :kaisaTablesCount :kaisa-tables-count,
   :yearRoundUse? :year-round-use?,
   :field1FlexibleRink? :field-1-flexible-rink?,
   :mobileOrienteering? :mobile-orienteering?,
   :field3FlexibleRink? :field-3-flexible-rink?,
   :freeCustomerUse? :free-customer-use?,
   :travelModes :travel-modes,
   :heightOfBasketOrNetAdjustable?
   :height-of-basket-or-net-adjustable?,
   :caromTablesCount :carom-tables-count,
   :changingRoomsM2 :changing-rooms-m2,
   :poolTablesCount :pool-tables-count,
   :ringetteBoundaryMarkings? :ringette-boundary-markings?,
   :hsPoint :hs-point,
   :snookerTablesCount :snooker-tables-count,
   :activeSpaceLengthM :active-space-length-m})

(def prop-mappings-reverse (set/map-invert prop-mappings))

(def surface-materials
  {"gravel" "Sora",
   "fiberglass" "Lasikuitu",
   "brick-crush" "Tiilimurska",
   "water" "Vesi",
   "concrete" "Betoni",
   "textile" "Tekstiili",
   "asphalt" "Asfaltti",
   "ceramic" "Keraaminen",
   "rock-dust" "Kivituhka",
   "deinked-pulp" "Siistausmassa",
   "stone" "Kivi",
   "metal" "Metalli",
   "soil" "Maa",
   "woodchips" "Hake",
   "grass" "Nurmi",
   "synthetic" "Muovi / synteettinen",
   "sand" "Hiekka",
   "artificial-turf" "Tekonurmi",
   "wood" "Puu",
   "sawdust" "Sahanpuru",
   "sand-infilled-artificial-turf" "Hiekkatekonurmi"})

(defn- add-point-props [fs]
  (utils/mapv-indexed
   (fn [idx f]
     (assoc f :properties {:pointId 123})) fs))

(defn- add-route-props [fs]
  (utils/mapv-indexed
   (fn [idx f]
     (assoc f :properties {:routeCollectionName "routeColl_1"
                           :routeName "route_1"
                           :routeSegmentName (str "segment_" idx)})) fs))

(defn- add-area-props [fs]
  (utils/mapv-indexed
   (fn [idx f]
     (assoc f :properties {:areaName "area_1"
                           :areaSegmentName (str "segment_" idx)})) fs))

(defn adapt-geoms [s]
  (let [geom-type (-> s :type :type-code types/all :geometry-type)
        path [:location :geometries :features]]
    (case geom-type
      "Point" (update-in s path add-point-props)
      "Polygon" (update-in s path add-area-props)
      "LineString" (update-in s path add-route-props))))

(defn add-ice-stadium-props
  "Extracts old Lipas ice stadium props from new LIPAS sport site m and
  merges them into existing :properties of m."
  [m]
  (->> (:rinks m)
       (take 3)
       (map-indexed
        (fn [idx rink]
          (let [n (inc idx)
                length (:length-m rink)
                width (:width-m rink)]
            {(keyword (str "field-" n "-length-m")) length
             (keyword (str "field-" n "-width-m")) width
             (keyword (str "field-" n "-area-m2")) (when (and length width)
                                                     (* length width))})))
       (apply merge)
       (merge
        {:ice-rinks-count (-> m :rinks count)
          ;; These come from 'normal properties' again
         #_#_:area-m2 (-> m :building :total-surface-area-m2)
         #_#_:stand-capacity-person (-> m :building :seating-capacity)
         #_#_:surface-material (-> m :building :envelope :base-floor-structure)})))

(defn add-swimming-pool-props
  "Extracts old Lipas swimming pool props from new LIPAS sport site m
  and merges them into existing :properties of m."
  [m]
  (->> (:pools m)
       (sort-by :length-m utils/reverse-cmp)
       (take 5)
       (map-indexed
        (fn [idx pool]
          (let [n (inc idx)
                 ;; There's a typo in old-lipas pool 1 length prop and
                 ;; we 'fix' it here. Seriously.
                length-key (str "-length-m" (when (= 1 n) "-m"))]
            {(keyword (str "pool-" n length-key)) (:length-m pool)
             (keyword (str "pool-" n "-width-m")) (:width-m pool)
             (keyword (str "pool-" n "-temperature-c")) (:temperature-c pool)
             (keyword (str "pool-" n "-max-depth-m")) (:max-depth-m pool)
             (keyword (str "pool-" n "-min-depth-m")) (:min-depth-m pool)})))
       (apply merge)
       (merge
        {;; These come from 'normal properties' again
         #_#_:swimming-pool-count (-> m :pools count)
         #_#_:pool-water-area-m2 (-> m :building :total-water-area-m2)
         #_#_:area-m2 (-> m :building :total-surface-area-m2)
         #_#_:stand-capacity-person (-> m :building :seating-capacity)
         #_#_:kiosk? (-> m :facilities :kiosk?)
         #_#_:1m-platforms-count (-> m :facilities :platforms-1m-count)
         #_#_:3m-platforms-count (-> m :facilities :platforms-3m-count)
         #_#_:5m-platforms-count (-> m :facilities :platforms-5m-count)
         #_#_:7m5-platforms-count (-> m :facilities :platforms-7.5m-count)
         #_#_:10m-platforms-count (-> m :facilities :platforms-10m-count)
         :water-slides-count (-> m :slides count)
         :waterslides-total-length-m (->> (:slides m)
                                          (map :length-m)
                                          (reduce utils/+safe))})))

;; Geometry adaptation function (moved from sports-place.clj)
(defn adapt-geoms [sports-site]
  ;; Basic geometry adaptation - the actual implementation should be moved here
  sports-site)

;; Ice stadium specific properties (moved from sports-place.clj)
(defn add-ice-stadium-props [sports-site]
  ;; Ice stadium specific property extraction
  sports-site)

;; Swimming pool specific properties (moved from sports-place.clj)
(defn add-swimming-pool-props [sports-site]
  ;; Swimming pool specific property extraction
  sports-site)
