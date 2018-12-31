(ns lipas.integration.old-lipas.sports-site
  (:require
   [clojure.set :as set]
   [lipas.data.types :as types]
   [clojure.string :as string]
   [lipas.data.admins :as admins]
   [lipas.data.owners :as owners]
   [lipas.utils :as utils :refer [sreplace trim]]))

(def admins (utils/index-by (comp :fi second) first admins/all))
(def owners (utils/index-by (comp :fi second) first owners/all))

(def prop-mappings
  {:liftsCount                  :lifts-count,
   :boxingRingsCount            :boxing-rings-count,
   :floorballFieldsCount        :floorball-fields-count,
   :pistolShooting              :pistol-shooting?,
   :shotputCount                :shotput-count,
   :swimmingPoolCount           :swimming-pool-count,
   :pool1TemperatureC           :pool-temperature-c,
   :classifiedRoute             :classified-route,
   :equipmentRental             :equipment-rental?,
   :ligthing                    :ligthing?,
   :kiosk                       :kiosk?,
   :fieldsCount                 :fields-count,
   :kPoint                      :k-point,
   :skiService                  :ski-service?,
   :fieldLengthM                :field-length-m,
   :showJumping                 :show-jumping?,
   :skiTrack                    :ski-track?,
   :cosmicBowling               :cosmic-bowling?,
   :field3AreaM2                :field-3-area-m2,
   :polevaultPlacesCount        :polevault-places-count,
   :spinningHall                :spinning-hall?,
   :climbingWall                :climbing-wall?,
   :iceClimbing                 :ice-climbing?,
   :sauna                       :sauna?,
   :trackWidthM                 :track-width-m,
   :routeWidthM                 :route-width-m,
   :basketballFieldsCount       :basketball-fields-count,
   :pier                        :pier?,
   :radioAndTvCapabilities      :radio-and-tv-capabilities?,
   :bowlingLanesCount           :bowling-lanes-count,
   :squashCourtsCount           :squash-courts-count,
   :altitudeDifference          :altitude-difference,
   :gymnasticsSpace             :gymnastics-space?,
   :climbingWallWidthM          :climbing-wall-width-m,
   :shower                      :shower?,
   :skijumpHillMaterial         :skijump-hill-material,
   :summerUsage                 :summer-usage?,
   :hammerThrowPlacesCount      :hammer-throw-places-count,
   :parkingPlace                :parking-place?,
   :trackLengthM                :track-length-m,
   :climbingRoutesCount         :climbing-routes-count,
   :field3WidthM                :field-3-width-m,
   :skiTrackTraditional         :ski-track-traditional?,
   :litSlopesCount              :lit-slopes-count,
   :coveredStandPersonCount     :covered-stand-person-count,
   :surfaceMaterial             :surface-material,
   :iceReduction                :ice-reduction?,
   :pool1MaxDepthM              :pool-max-depth-m,
   :poolWaterAreaM2             :pool-water-area-m2,
   :shortestSlopeM              :shortest-slope-m,
   :badmintonCourtsCount        :badminton-courts-count,
   :green                       :green?,
   :fieldWidthM                 :field-width-m,
   :oldLipasTypecode            :old-lipas-typecode,
   :accessibilityInfo           :accessibility-info,
   :trainingWall                :training-wall?,
   :lightRoof                   :light-roof?,
   :basketballFieldType         :basketball-field-type,
   :javelinThrowPlacesCount     :javelin-throw-places-count,
   :field1LengthM               :field-1-length-m,
   :areaKm2                     :area-km2,
   :airGunShooting              :air-gun-shooting?,
   :skiTrackFreestyle           :ski-track-freestyle?,
   :jumpsCount                  :jumps-count,
   :pool1LengthMM               :pool-length-m,
   :automatedTiming             :automated-timing?,
   :surfaceMaterialInfo         :surface-material-info,
   :adpReadiness                :adp-readiness?,
   :weightLiftingSpotsCount     :weight-lifting-spots-count,
   :euBeach                     :eu-beach?,
   :snowparkOrStreet            :snowpark-or-street?,
   :restPlacesCount             :rest-places-count,
   :playground                  :playground?,
   :longjumpPlacesCount         :longjump-places-count,
   :tatamisCount                :tatamis-count,
   :holesCount                  :holes-count,
   :halfpipeCount               :halfpipe-count,
   :beachLengthM                :beach-length-m,
   :handballFieldsCount         :handball-fields-count,
   :matchClock                  :match-clock?,
   :sprintLanesCount            :sprint-lanes-count,
   :hallLengthM                 :hall-length-m,
   :exerciseMachinesCount       :exercise-machines-count,
   :wrestlingMatsCount          :wrestling-mats-count,
   :volleyballFieldsCount       :volleyball-fields-count,
   :tennisCourtsCount           :tennis-courts-count,
   :mayBeShownInExcursionMapFi  :may-be-shown-in-excursion-map-fi?,
   :trainingSpotSurfaceMaterial :training-spot-surface-material,
   :longestSlopeM               :longest-slope-m,
   :heightM                     :height-m,
   :toilet                      :toilet?,
   :winterSwimming              :winter-swimming?,
   :shotgunShooting             :shotgun-shooting?,
   :curlingLanesCount           :curling-lanes-count,
   :climbingWallHeightM         :climbing-wall-height-m,
   :litRouteLengthKm            :lit-route-length-km,
   :throwintSportsPlaces        :throwing-sports-spots-count,
   :changingRooms               :changing-rooms?,
   :groupExerciseRoomsCount     :group-exercise-rooms-count,
   :archery                     :archery?,
   :maxVerticalDifference       :max-vertical-difference,
   :futsalFieldsCount           :futsal-fields-count,
   :automatedScoring            :automated-scoring?,
   :innerLaneLengthM            :inner-lane-length-m,
   :field1WidthM                :field-1-width-m,
   :landingPlacesCount          :landing-places-count,
   :sprintTrackLengthM          :sprint-track-length-m,
   :footballFieldsCount         :football-fields-count,
   :otherPoolsCount             :other-pools-count,
   :pool1TracksCount            :pool-tracks-count,
   :gymnasticRoutinesCount      :gymnastic-routines-count,
   :shootingPositionsCount      :shooting-positions-count,
   :freestyleSlope              :freestyle-slope?,
   :scoreboard                  :scoreboard?,
   :pool1WidthM                 :pool-width-m,
   :rifleShooting               :rifle-shooting?,
   :finishLineCamera            :finish-line-camera?,
   :runningTrackSurfaceMaterial :running-track-surface-material,
   :field1AreaM2                :field-1-area-m2,
   :horseCarriageAllowed        :horse-carriage-allowed?,
   :circularLanesCount          :circular-lanes-count,
   :standCapacityPerson         :stand-capacity-person,
   :discusThrowPlaces           :discus-throw-places,
   :pPoint                      :p-point,
   :field2LengthM               :field-2-length-m,
   :tableTennisCount            :table-tennis-count,
   :boatPlacesCount             :boat-places-count,
   :field2AreaM2                :field-2-area-m2,
   :otherPlatforms              :other-platforms?,
   :iceRinksCount               :ice-rinks-count,
   :boatLaunchingSpot           :boat-launching-spot?,
   :tobogganRun                 :toboggan-run?,
   :field2WidthM                :field-2-width-m,
   :areaM2                      :area-m2,
   :trackType                   :track-type,
   :routeLengthKm               :route-length-km,
   :loudspeakers                :loudspeakers?,
   :outdoorExerciseMachines     :outdoor-exercise-machines?,
   :slopesCount                 :slopes-count,
   :field3LengthM               :field-3-length-m,
   :fencingBasesCount           :fencing-bases-count,
   :hallWidthM                  :hall-width-m,
   :heating                     :heating?,
   :winterUsage                 :winter-usage?,
   :plasticOutrun               :plastic-outrun?,
   :highjumpPlacesCount         :highjump-places-count,
   :freeRifleShooting           :free-rifle-shooting?,
   :range                       :range?,
   :pool1MinDepthM              :pool-min-depth-m,
   :inrunsMaterial              :inruns-material,
   :skijumpHillType             :skijump-hill-type})

(def prop-mappings-reverse (set/map-invert prop-mappings))

(defn resolve-surface-material [props]
  (let [mat1 (-> props :surfaceMaterial (as-> $ (when $ (string/lower-case $))))
        info (-> props :surfaceMaterialInfo utils/trim not-empty)
        mat2 (case mat1
               ("sora")                         ["gravel"],
               ("tekonurmi")                    ["artificial-turf"],
               ("lasikuitu")                    ["fiberglass"],
               ("puu" "parketti" "joustoparketti"
                "koivu")                        ["wood"],
               ("keraaminen")                   ["ceramic"],
               ("betoni")                       ["concrete"],
               ("tiilimurska")                  ["brick-crush"],
               ("vesi")                         ["water"],
               ("tekstiili" "bolltex")          ["textile"],
               ("siistausmassa")                ["deinked-pulp"],
               ("sahanpuru" "puru" "puru yms.") ["sawdust"],
               ("kivi")                         ["stone"],
               ("maa" "suo" "maapohja")         ["soil"],
               ("nurmi")                        ["grass"],
               ("muovi / synteettinen" "muovi"
                "muovimatto" "mondo")           ["synthetic"],
               ("metalli")                      ["metal"],
               ("hiekka" "pehmeä hiekka")       ["sand"],
               ("kivituhka")                    ["rock-dust"],
               ("hake")                         ["woodchips"],
               ("puru/sora")                    ["sawdust" "gravel"]
               ("hiekka / puru")                ["sand" "sawdust"]
               ("hiekkatekonurmi")              ["sand-infilled-artificial-turf"],
               ("asfaltti")                     ["asphalt"]
               nil)]
    [mat2 (or info (when-not mat2 mat1))]))

(def surface-materials
  {"gravel"                        "Sora",
   "fiberglass"                    "Lasikuitu",
   "brick-crush"                   "Tiilimurska",
   "water"                         "Vesi",
   "concrete"                      "Betoni",
   "textile"                       "Tekstiili",
   "asphalt"                       "Asfaltti",
   "ceramic"                       "Keraaminen",
   "rock-dust"                     "Kivituhka",
   "deinked-pulp"                  "Siistausmassa",
   "stone"                         "Kivi",
   "metal"                         "Metalli",
   "soil"                          "Maa",
   "woodchips"                     "Hake",
   "grass"                         "Nurmi",
   "synthetic"                     "Muovi / synteettinen",
   "sand"                          "Hiekka",
   "artificial-turf"               "Tekonurmi",
   "wood"                          "Puu",
   "sawdust"                       "Sahanpuru",
   "sand-infilled-artificial-turf" "Hiekkatekonurmi"})

(defn- add-point-props [fs]
  (map-indexed
   (fn [idx f]
     (assoc f :properties {:pointId 123})) fs))

(defn- add-route-props [fs]
  (map-indexed
   (fn [idx f]
     (assoc f :properties {:routeCollName    "routeColl_1"
                           :routeName        "route_1"
                           :routeSegmentName (str "segment_" idx)})) fs))

(defn- add-area-props [fs]
  (map-indexed
   (fn [idx f]
     (assoc f :properties {:areaName        "area_1"
                           :areaSegmentName (str "segment_" idx)})) fs))

(defn adapt-geoms [s]
  (let [geom-type (-> s :type :type-code types/all :geometry-type)
        path      [:location :geometries :features]]
    (case geom-type
      "Point"      (update-in s path add-point-props)
      "Polygon"    (update-in s path add-area-props)
      "LineString" (update-in s path add-route-props))))
