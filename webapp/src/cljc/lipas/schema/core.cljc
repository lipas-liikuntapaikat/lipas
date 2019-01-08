(ns lipas.schema.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [hiposfer.geojson.specs :as geojson]
   [lipas.data.admins :as admins]
   [lipas.data.cities :as cities]
   [lipas.data.ice-stadiums :as ice-stadiums]
   [lipas.data.materials :as materials]
   [lipas.data.owners :as owners]
   [lipas.data.sports-sites :as sports-sites]
   [lipas.data.swimming-pools :as swimming-pools]
   [lipas.data.types :as sports-site-types]
   [lipas.reports :as reports]
   [lipas.utils :as utils]))

;;; Utils ;;;

(defn str-in [min max]
  (s/and string? #(<= min (count %) max)))

(defn number-in
  "Returns a spec that validates numbers in the range from
  min (inclusive) to max (exclusive)."
  [& {:keys [min max]}]
  (s/and number? #(<= min % (dec max))))

;;; Regexes ;;;

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(def postal-code-regex #"[0-9]{5}")

;; https://stackoverflow.com/questions/3143070/javascript-regex-iso-datetime
(def timestamp-regex
  #"\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+([+-][0-2]\d:[0-5]\d|Z)")

;;; Generators ;;;

(def default-chars
  (str "abcdefghijklmnopqrstuvwxyz"
       "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
       "0123456789"))

(defn gen-str
  ([min max]
   (gen-str min max default-chars))
  ([min max chars]
   (gen/fmap (fn [n]
               (apply str (repeatedly n #(rand-nth chars))))
             (s/gen (s/int-in min max)))))

(defn email-gen []
  "Function that returns a Generator for email addresses"
  (gen/fmap
   (fn [[name host tld]]
     (str name "@" host "." tld))
   (gen/tuple
    (gen-str 1 15)
    (gen-str 1 15)
    (gen-str 2 5))))

(defn postal-code-gen []
  "Function that returns a Generator for Finnish postal codes"
  (gen/fmap
   (partial reduce str)
   (s/gen
    (s/tuple
     (s/int-in 0 10)
     (s/int-in 0 10)
     (s/int-in 0 10)
     (s/int-in 0 10)
     (s/int-in 0 10)))))

(defn timestamp-gen []
  (gen/fmap
   (fn [[yyyy MM dd hh mm ss ms]]
     (let [MM (utils/zero-left-pad MM 2)
           dd (utils/zero-left-pad dd 2)
           hh (utils/zero-left-pad hh 2)
           mm (utils/zero-left-pad mm 2)
           ss (utils/zero-left-pad ss 2)]
       (str yyyy "-" MM "-" dd "T" hh ":" mm ":" ss "." ms "Z")))
   (gen/tuple
    (s/gen (s/int-in 1900 (inc utils/this-year)))
    (s/gen (s/int-in 1 (inc 12)))
    (s/gen (s/int-in 1 (inc 28)))
    (s/gen (s/int-in 1 (inc 23)))
    (s/gen (s/int-in 0 (inc 59)))
    (s/gen (s/int-in 0 (inc 59)))
    (s/gen (s/int-in 0 (inc 999))))))

(defn lipas-point-feature-gen []
  (gen/fmap
   (fn [[lon lat]]
     {:type "FeatureCollection"
      :features
      [{:type "Feature"
        :geometry
        {:type "Point"
         :coordinates [lon lat]}}]})
   (s/gen (s/tuple :lipas.location.coordinates/lon
                   :lipas.location.coordinates/lat))))

;; Specs ;;

(s/def :lipas/timestamp-type (s/and string? #(re-matches timestamp-regex %)))
(s/def :lipas/timestamp (s/with-gen :lipas/timestamp-type timestamp-gen))

(s/def :lipas/email-type (s/and string? #(re-matches email-regex %)))
(s/def :lipas/email (s/with-gen :lipas/email-type email-gen))

(s/def :lipas/hours-in-day (number-in :min 0 :max (inc 24)))

;;; User ;;;

(s/def :lipas.user/id uuid?)
(s/def :lipas.user/firstname (str-in 1 128))
(s/def :lipas.user/lastname (str-in 1 128))
(s/def :lipas.user/username (str-in 1 128))
(s/def :lipas.user/password (str-in 6 128))
(s/def :lipas.user/email :lipas/email)

(s/def :lipas.user/login (s/or :username :lipas.user/username
                               :email :lipas.user/email))

(s/def :lipas.user/user-data (s/keys :req-un [:lipas.user/firstname
                                              :lipas.user/lastname]))

(s/def :lipas.user.permissions/sports-sites
  (s/coll-of :lipas.sports-site/lipas-id
             :min-count 0
             :max-count 1000
             :distinct true
             :into []))

(s/def :lipas.user.permissions/cities
  (s/coll-of :lipas.location.city/city-code
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.user.permissions/types
  (s/coll-of :lipas.sports-site.type/type-code
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.user.permissions/admin? boolean?)
(s/def :lipas.user.permissions/draft? boolean?)
(s/def :lipas.user.permissions/all-cities? boolean?)
(s/def :lipas.user.permissions/all-types? boolean?)

(s/def :lipas.user/permissions
  (s/keys :opt-un [:lipas.user.permissions/admin?
                   :lipas.user.permissions/draft?
                   :lipas.user.permissions/sports-sites
                   :lipas.user.permissions/all-cities?
                   :lipas.user.permissions/all-types?
                   :lipas.user.permissions/cities
                   :lipas.user.permissions/types]))

(s/def :lipas.user/permissions-request (str-in 1 200))

(s/def :lipas/new-user (s/keys :req-un [:lipas.user/email
                                        :lipas.user/username
                                        :lipas.user/user-data]
                               :opt-un [:lipas.user/password
                                        :lipas.user/permissions]))

(s/def :lipas/user (s/merge :lipas/new-user
                            (s/keys :req-un [:lipas.user/id])))

;;; Location ;;;

(s/def :lipas.location/address (str-in 1 200))
(s/def :lipas.location/postal-code-type
  (s/and string? #(re-matches postal-code-regex %)))

(s/def :lipas.location/postal-code (s/with-gen
                       :lipas.location/postal-code-type
                       postal-code-gen))

(s/def :lipas.location/postal-office (str-in 0 50))
(s/def :lipas.location.city/city-code (into #{} (map :city-code) cities/active))
(s/def :lipas.location.city/neighborhood (str-in 1 100))

(s/def :lipas.location/city
  (s/keys :req-un [:lipas.location.city/city-code]
          :opt-un [:lipas.location.city/neighborhood]))

(s/def :lipas.location.coordinates/lat (s/double-in :min 59.846373196
                                                    :max 70.1641930203
                                                    :NaN? false
                                                    :infinite? false))

(s/def :lipas.location.coordinates/lon (s/double-in :min 20.6455928891
                                                    :max 31.5160921567
                                                    :NaN? false
                                                    :infinite? false))

;; NOTE: generator supports only point features atm
(s/def :lipas.location/geometries (s/with-gen
                                    ::geojson/feature-collection
                                    lipas-point-feature-gen))

(comment (s/valid?
          ::geojson/feature-collection
          (gen/generate (s/gen :lipas.location/geometries))))

;;; Sports site ;;;

(s/def :lipas.sports-site/lipas-id (s/int-in 0 2147483647)) ; PSQL integer max
(s/def :lipas.sports-site/hall-id (str-in 2 20))
(s/def :lipas.sports-site/status (into #{} (keys sports-sites/statuses)))
(s/def :lipas.sports-site/name (str-in 2 100))
(s/def :lipas.sports-site/marketing-name (str-in 2 100))

(s/def :lipas.sports-site/owner (into #{} (keys owners/all)))
(s/def :lipas.sports-site/admin (into #{} (keys admins/all)))

(s/def :lipas.sports-site/phone-number (str-in 1 50))
(s/def :lipas.sports-site/www (str-in 1 200))
(s/def :lipas.sports-site/email :lipas/email)

(s/def :lipas.sports-site/comment (str-in 1 2048))

(s/def :lipas.sports-site.type/type-code
  (into #{} (keys sports-site-types/all)))

(s/def :lipas.sports-site/construction-year
  (into #{} (range 1850 (inc utils/this-year))))

(s/def :lipas.sports-site/renovation-years
  (s/coll-of (s/int-in 1900 (inc utils/this-year))
             :distinct true :into []))

;;;; Additional properties ;;;;

(s/def :lipas.sports-site.properties/surface-material
  (s/coll-of (into #{} (keys materials/surface-materials))
             :min-count 0
             :max-count (count materials/surface-materials)
             :distinct true
             :into []))

(s/def :lipas.sports-site.properties/height-m number?)
(s/def :lipas.sports-site.properties/heating? boolean?)
(s/def :lipas.sports-site.properties/field-2-area-m2 number?)
(s/def :lipas.sports-site.properties/ski-track? boolean?)
(s/def :lipas.sports-site.properties/basketball-fields-count number?)
(s/def :lipas.sports-site.properties/surface-material-info string?)
(s/def :lipas.sports-site.properties/holes-count number?)
(s/def :lipas.sports-site.properties/skijump-hill-type string?)
(s/def :lipas.sports-site.properties/lifts-count number?)
(s/def :lipas.sports-site.properties/field-3-length-m number?)
(s/def :lipas.sports-site.properties/pool-tracks-count number?)
(s/def :lipas.sports-site.properties/field-2-length-m number?)
(s/def :lipas.sports-site.properties/plastic-outrun? boolean?)
(s/def :lipas.sports-site.properties/automated-timing? boolean?)
(s/def :lipas.sports-site.properties/freestyle-slope? boolean?)
(s/def :lipas.sports-site.properties/kiosk? boolean?)
(s/def :lipas.sports-site.properties/summer-usage? boolean?)
(s/def :lipas.sports-site.properties/stand-capacity-person number?)
(s/def :lipas.sports-site.properties/pier? boolean?)
(s/def :lipas.sports-site.properties/may-be-shown-in-excursion-map-fi? boolean?)
(s/def :lipas.sports-site.properties/sprint-lanes-count number?)
(s/def :lipas.sports-site.properties/javelin-throw-places-count number?)
(s/def :lipas.sports-site.properties/tennis-courts-count number?)
(s/def :lipas.sports-site.properties/ski-service? boolean?)
(s/def :lipas.sports-site.properties/field-1-length-m number?)
(s/def :lipas.sports-site.properties/finish-line-camera? boolean?)
(s/def :lipas.sports-site.properties/parking-place? boolean?)
(s/def :lipas.sports-site.properties/climbing-routes-count number?)
(s/def :lipas.sports-site.properties/outdoor-exercise-machines? boolean?)
(s/def :lipas.sports-site.properties/automated-scoring? boolean?)
(s/def :lipas.sports-site.properties/track-width-m number?)
(s/def :lipas.sports-site.properties/ice-climbing? boolean?)
(s/def :lipas.sports-site.properties/field-length-m number?)
(s/def :lipas.sports-site.properties/skijump-hill-material string?)
(s/def :lipas.sports-site.properties/longest-slope-m number?)
(s/def :lipas.sports-site.properties/circular-lanes-count number?)
(s/def :lipas.sports-site.properties/boat-launching-spot? boolean?)
(s/def :lipas.sports-site.properties/ski-track-traditional? boolean?)
(s/def :lipas.sports-site.properties/winter-swimming? boolean?)
(s/def :lipas.sports-site.properties/altitude-difference number?)
(s/def :lipas.sports-site.properties/climbing-wall-height-m number?)
(s/def :lipas.sports-site.properties/route-width-m number?)
(s/def :lipas.sports-site.properties/beach-length-m number?)
(s/def :lipas.sports-site.properties/match-clock? boolean?)
(s/def :lipas.sports-site.properties/sprint-track-length-m number?)
(s/def :lipas.sports-site.properties/archery? boolean?)
(s/def :lipas.sports-site.properties/throwing-sports-spots-count number?)
(s/def :lipas.sports-site.properties/radio-and-tv-capabilities? boolean?)
(s/def :lipas.sports-site.properties/inner-lane-length-m number?)
(s/def :lipas.sports-site.properties/discus-throw-places number?)
(s/def :lipas.sports-site.properties/fields-count number?)
(s/def :lipas.sports-site.properties/field-1-width-m number?)
(s/def :lipas.sports-site.properties/field-3-width-m number?)
(s/def :lipas.sports-site.properties/field-2-width-m number?)
(s/def :lipas.sports-site.properties/badminton-courts-count number?)
(s/def :lipas.sports-site.properties/hammer-throw-places-count number?)
(s/def :lipas.sports-site.properties/horse-carriage-allowed? boolean?)
(s/def :lipas.sports-site.properties/pool-width-m number?)
(s/def :lipas.sports-site.properties/pool-min-depth-m number?)
(s/def :lipas.sports-site.properties/ice-rinks-count number?)
(s/def :lipas.sports-site.properties/field-1-area-m2 number?)
(s/def :lipas.sports-site.properties/k-point number?)
(s/def :lipas.sports-site.properties/polevault-places-count number?)
(s/def :lipas.sports-site.properties/group-exercise-rooms-count number?)
(s/def :lipas.sports-site.properties/snowpark-or-street? boolean?)
(s/def :lipas.sports-site.properties/max-vertical-difference number?)
(s/def :lipas.sports-site.properties/bowling-lanes-count number?)
(s/def :lipas.sports-site.properties/air-gun-shooting? boolean?)
(s/def :lipas.sports-site.properties/gymnastic-routines-count number?)
(s/def :lipas.sports-site.properties/toilet? boolean?)
(s/def :lipas.sports-site.properties/gymnastics-space? boolean?)
(s/def :lipas.sports-site.properties/show-jumping? boolean?)
(s/def :lipas.sports-site.properties/shower? boolean?)
(s/def :lipas.sports-site.properties/rest-places-count number?)
(s/def :lipas.sports-site.properties/changing-rooms? boolean?)
(s/def :lipas.sports-site.properties/pistol-shooting? boolean?)
(s/def :lipas.sports-site.properties/halfpipe-count number?)
(s/def :lipas.sports-site.properties/shooting-positions-count number?)
(s/def :lipas.sports-site.properties/running-track-surface-material string?)
(s/def :lipas.sports-site.properties/tatamis-count number?)
(s/def :lipas.sports-site.properties/lit-route-length-km number?)
(s/def :lipas.sports-site.properties/area-m2 number?)
(s/def :lipas.sports-site.properties/field-width-m number?)
(s/def :lipas.sports-site.properties/cosmic-bowling? boolean?)
(s/def :lipas.sports-site.properties/wrestling-mats-count number?)
(s/def :lipas.sports-site.properties/eu-beach? boolean?)
(s/def :lipas.sports-site.properties/hall-length-m number?)
(s/def :lipas.sports-site.properties/rifle-shooting? boolean?)
(s/def :lipas.sports-site.properties/swimming-pool-count number?)
(s/def :lipas.sports-site.properties/pool-water-area-m2 number?)
(s/def :lipas.sports-site.properties/curling-lanes-count number?)
(s/def :lipas.sports-site.properties/climbing-wall-width-m number?)
(s/def :lipas.sports-site.properties/area-km2 number?)
(s/def :lipas.sports-site.properties/scoreboard? boolean?)
(s/def :lipas.sports-site.properties/futsal-fields-count number?)
(s/def :lipas.sports-site.properties/training-wall? boolean?)
(s/def :lipas.sports-site.properties/shotput-count number?)
(s/def :lipas.sports-site.properties/longjump-places-count number?)
(s/def :lipas.sports-site.properties/football-fields-count number?)
(s/def :lipas.sports-site.properties/floorball-fields-count number?)
(s/def :lipas.sports-site.properties/equipment-rental? boolean?)
(s/def :lipas.sports-site.properties/slopes-count number?)
(s/def :lipas.sports-site.properties/old-lipas-typecode int?)
(s/def :lipas.sports-site.properties/other-pools-count number?)
(s/def :lipas.sports-site.properties/shortest-slope-m number?)
(s/def :lipas.sports-site.properties/adp-readiness? boolean?)
(s/def :lipas.sports-site.properties/squash-courts-count number?)
(s/def :lipas.sports-site.properties/boxing-rings-count number?)
(s/def :lipas.sports-site.properties/ice-reduction? boolean?)
(s/def :lipas.sports-site.properties/fencing-bases-count number?)
(s/def :lipas.sports-site.properties/classified-route string?)
(s/def :lipas.sports-site.properties/weight-lifting-spots-count number?)
(s/def :lipas.sports-site.properties/landing-places-count number?)
(s/def :lipas.sports-site.properties/toboggan-run? boolean?)
(s/def :lipas.sports-site.properties/sauna? boolean?)
(s/def :lipas.sports-site.properties/jumps-count number?)
(s/def :lipas.sports-site.properties/table-tennis-count number?)
(s/def :lipas.sports-site.properties/pool-max-depth-m number?)
(s/def :lipas.sports-site.properties/loudspeakers? boolean?)
(s/def :lipas.sports-site.properties/shotgun-shooting? boolean?)
(s/def :lipas.sports-site.properties/lit-slopes-count number?)
(s/def :lipas.sports-site.properties/green? boolean?)
(s/def :lipas.sports-site.properties/free-rifle-shooting? boolean?)
(s/def :lipas.sports-site.properties/winter-usage? boolean?)
(s/def :lipas.sports-site.properties/ligthing? boolean?)
(s/def :lipas.sports-site.properties/field-3-area-m2 number?)
(s/def :lipas.sports-site.properties/accessibility-info string?)
(s/def :lipas.sports-site.properties/covered-stand-person-count number?)
(s/def :lipas.sports-site.properties/playground? boolean?)
(s/def :lipas.sports-site.properties/handball-fields-count number?)
(s/def :lipas.sports-site.properties/p-point number?)
(s/def :lipas.sports-site.properties/inruns-material string?)
(s/def :lipas.sports-site.properties/basketball-field-type string?)
(s/def :lipas.sports-site.properties/volleyball-fields-count number?)
(s/def :lipas.sports-site.properties/boat-places-count number?)
(s/def :lipas.sports-site.properties/pool-temperature-c number?)
(s/def :lipas.sports-site.properties/hall-width-m number?)
(s/def :lipas.sports-site.properties/climbing-wall? boolean?)
(s/def :lipas.sports-site.properties/ski-track-freestyle? boolean?)
(s/def :lipas.sports-site.properties/spinning-hall? boolean?)
(s/def :lipas.sports-site.properties/other-platforms? boolean?)
(s/def :lipas.sports-site.properties/highjump-places-count number?)
(s/def :lipas.sports-site.properties/light-roof? boolean?)
(s/def :lipas.sports-site.properties/pool-length-m number?)
(s/def :lipas.sports-site.properties/route-length-km number?)
(s/def :lipas.sports-site.properties/exercise-machines-count number?)
(s/def :lipas.sports-site.properties/track-type string?)
(s/def :lipas.sports-site.properties/training-spot-surface-material string?)
(s/def :lipas.sports-site.properties/range? boolean?)
(s/def :lipas.sports-site.properties/track-length-m number?)
(s/def :lipas.sports-site.properties/school-use? boolean?)
(s/def :lipas.sports-site.properties/free-use? boolean?)

(s/def :lipas.sports-site/properties
  (s/keys :opt-un [:lipas.sports-site.properties/height-m
                   :lipas.sports-site.properties/heating?
                   :lipas.sports-site.properties/field-2-area-m2
                   :lipas.sports-site.properties/surface-material
                   :lipas.sports-site.properties/ski-track?
                   :lipas.sports-site.properties/basketball-fields-count
                   :lipas.sports-site.properties/surface-material-info
                   :lipas.sports-site.properties/holes-count
                   :lipas.sports-site.properties/skijump-hill-type
                   :lipas.sports-site.properties/lifts-count
                   :lipas.sports-site.properties/field-3-length-m
                   :lipas.sports-site.properties/pool-tracks-count
                   :lipas.sports-site.properties/field-2-length-m
                   :lipas.sports-site.properties/plastic-outrun?
                   :lipas.sports-site.properties/automated-timing?
                   :lipas.sports-site.properties/freestyle-slope?
                   :lipas.sports-site.properties/kiosk?
                   :lipas.sports-site.properties/summer-usage?
                   :lipas.sports-site.properties/stand-capacity-person
                   :lipas.sports-site.properties/pier?
                   :lipas.sports-site.properties/may-be-shown-in-excursion-map-fi?
                   :lipas.sports-site.properties/sprint-lanes-count
                   :lipas.sports-site.properties/javelin-throw-places-count
                   :lipas.sports-site.properties/tennis-courts-count
                   :lipas.sports-site.properties/ski-service?
                   :lipas.sports-site.properties/field-1-length-m
                   :lipas.sports-site.properties/finish-line-camera?
                   :lipas.sports-site.properties/parking-place?
                   :lipas.sports-site.properties/climbing-routes-count
                   :lipas.sports-site.properties/outdoor-exercise-machines?
                   :lipas.sports-site.properties/automated-scoring?
                   :lipas.sports-site.properties/track-width-m
                   :lipas.sports-site.properties/ice-climbing?
                   :lipas.sports-site.properties/field-length-m
                   :lipas.sports-site.properties/skijump-hill-material
                   :lipas.sports-site.properties/longest-slope-m
                   :lipas.sports-site.properties/circular-lanes-count
                   :lipas.sports-site.properties/boat-launching-spot?
                   :lipas.sports-site.properties/ski-track-traditional?
                   :lipas.sports-site.properties/winter-swimming?
                   :lipas.sports-site.properties/altitude-difference
                   :lipas.sports-site.properties/climbing-wall-height-m
                   :lipas.sports-site.properties/route-width-m
                   :lipas.sports-site.properties/beach-length-m
                   :lipas.sports-site.properties/match-clock?
                   :lipas.sports-site.properties/sprint-track-length-m
                   :lipas.sports-site.properties/archery?
                   :lipas.sports-site.properties/throwing-sports-spots-count
                   :lipas.sports-site.properties/radio-and-tv-capabilities?
                   :lipas.sports-site.properties/inner-lane-length-m
                   :lipas.sports-site.properties/discus-throw-places
                   :lipas.sports-site.properties/fields-count
                   :lipas.sports-site.properties/field-1-width-m
                   :lipas.sports-site.properties/field-3-width-m
                   :lipas.sports-site.properties/field-2-width-m
                   :lipas.sports-site.properties/badminton-courts-count
                   :lipas.sports-site.properties/hammer-throw-places-count
                   :lipas.sports-site.properties/horse-carriage-allowed?
                   :lipas.sports-site.properties/pool-width-m
                   :lipas.sports-site.properties/pool-min-depth-m
                   :lipas.sports-site.properties/ice-rinks-count
                   :lipas.sports-site.properties/field-1-area-m2
                   :lipas.sports-site.properties/k-point
                   :lipas.sports-site.properties/polevault-places-count
                   :lipas.sports-site.properties/group-exercise-rooms-count
                   :lipas.sports-site.properties/snowpark-or-street?
                   :lipas.sports-site.properties/max-vertical-difference
                   :lipas.sports-site.properties/bowling-lanes-count
                   :lipas.sports-site.properties/air-gun-shooting?
                   :lipas.sports-site.properties/gymnastic-routines-count
                   :lipas.sports-site.properties/toilet?
                   :lipas.sports-site.properties/gymnastics-space?
                   :lipas.sports-site.properties/show-jumping?
                   :lipas.sports-site.properties/shower?
                   :lipas.sports-site.properties/rest-places-count
                   :lipas.sports-site.properties/changing-rooms?
                   :lipas.sports-site.properties/pistol-shooting?
                   :lipas.sports-site.properties/halfpipe-count
                   :lipas.sports-site.properties/shooting-positions-count
                   :lipas.sports-site.properties/running-track-surface-material
                   :lipas.sports-site.properties/tatamis-count
                   :lipas.sports-site.properties/lit-route-length-km
                   :lipas.sports-site.properties/area-m2
                   :lipas.sports-site.properties/field-width-m
                   :lipas.sports-site.properties/cosmic-bowling?
                   :lipas.sports-site.properties/wrestling-mats-count
                   :lipas.sports-site.properties/eu-beach?
                   :lipas.sports-site.properties/hall-length-m
                   :lipas.sports-site.properties/rifle-shooting?
                   :lipas.sports-site.properties/swimming-pool-count
                   :lipas.sports-site.properties/pool-water-area-m2
                   :lipas.sports-site.properties/curling-lanes-count
                   :lipas.sports-site.properties/climbing-wall-width-m
                   :lipas.sports-site.properties/area-km2
                   :lipas.sports-site.properties/scoreboard?
                   :lipas.sports-site.properties/futsal-fields-count
                   :lipas.sports-site.properties/training-wall?
                   :lipas.sports-site.properties/shotput-count
                   :lipas.sports-site.properties/longjump-places-count
                   :lipas.sports-site.properties/football-fields-count
                   :lipas.sports-site.properties/floorball-fields-count
                   :lipas.sports-site.properties/equipment-rental?
                   :lipas.sports-site.properties/slopes-count
                   :lipas.sports-site.properties/old-lipas-typecode
                   :lipas.sports-site.properties/pool-length-m
                   :lipas.sports-site.properties/other-pools-count
                   :lipas.sports-site.properties/shortest-slope-m
                   :lipas.sports-site.properties/adp-readiness?
                   :lipas.sports-site.properties/squash-courts-count
                   :lipas.sports-site.properties/boxing-rings-count
                   :lipas.sports-site.properties/ice-reduction?
                   :lipas.sports-site.properties/fencing-bases-count
                   :lipas.sports-site.properties/classified-route
                   :lipas.sports-site.properties/weight-lifting-spots-count
                   :lipas.sports-site.properties/landing-places-count
                   :lipas.sports-site.properties/toboggan-run?
                   :lipas.sports-site.properties/sauna?
                   :lipas.sports-site.properties/jumps-count
                   :lipas.sports-site.properties/table-tennis-count
                   :lipas.sports-site.properties/pool-max-depth-m
                   :lipas.sports-site.properties/loudspeakers?
                   :lipas.sports-site.properties/shotgun-shooting?
                   :lipas.sports-site.properties/lit-slopes-count
                   :lipas.sports-site.properties/green?
                   :lipas.sports-site.properties/free-rifle-shooting?
                   :lipas.sports-site.properties/winter-usage?
                   :lipas.sports-site.properties/ligthing?
                   :lipas.sports-site.properties/field-3-area-m2
                   :lipas.sports-site.properties/accessibility-info
                   :lipas.sports-site.properties/covered-stand-person-count
                   :lipas.sports-site.properties/playground?
                   :lipas.sports-site.properties/handball-fields-count
                   :lipas.sports-site.properties/p-point
                   :lipas.sports-site.properties/inruns-material
                   :lipas.sports-site.properties/basketball-field-type
                   :lipas.sports-site.properties/volleyball-fields-count
                   :lipas.sports-site.properties/boat-places-count
                   :lipas.sports-site.properties/pool-temperature-c
                   :lipas.sports-site.properties/hall-width-m
                   :lipas.sports-site.properties/climbing-wall?
                   :lipas.sports-site.properties/ski-track-freestyle?
                   :lipas.sports-site.properties/spinning-hall?
                   :lipas.sports-site.properties/other-platforms?
                   :lipas.sports-site.properties/highjump-places-count
                   :lipas.sports-site.properties/light-roof?
                   :lipas.sports-site.properties/route-length-km
                   :lipas.sports-site.properties/exercise-machines-count
                   :lipas.sports-site.properties/track-type
                   :lipas.sports-site.properties/training-spot-surface-material
                   :lipas.sports-site.properties/range?
                   :lipas.sports-site.properties/track-length-m]))

(s/def :lipas.sports-site/properties-old
  (s/map-of keyword? (s/or :string? (str-in 1 100)
                           :number? number?
                           :boolean? boolean?)))

(s/def :lipas/location
  (s/keys :req-un [:lipas.location/address
                   :lipas.location/postal-code
                   :lipas.location/geometries
                   :lipas.location/city]
          :opt-un [:lipas.location/postal-office]))

(s/def :lipas.sport-site.type/type-code
  (into #{} (map :type-code) sports-site-types/all))

(s/def :lipas.sports-site.type/size-category
  (into #{} (keys ice-stadiums/size-categories)))

(s/def :lipas.sports-site/type
  (s/keys :req-un [:lipas.sports-site.type/type-code]
          :opt-un [:lipas.sports-site.type/size-category]))

;; When was the *document* created
(s/def :lipas.sports-site/created-at :lipas/timestamp)

;; What date/time does the document describe
(s/def :lipas.sports-site/event-date :lipas/timestamp)

;; Energy consumption ;;

(s/def :lipas.energy-consumption/electricity-mwh (number-in :min 0 :max 10000))
(s/def :lipas.energy-consumption/heat-mwh (number-in :min 0 :max 10000))
;; TODO find out realistic limits for cold energy
(s/def :lipas.energy-consumption/cold-mwh (number-in :min 0 :max 100000))
(s/def :lipas.energy-consumption/water-m3 (number-in :min 0 :max 500000))
(s/def :lipas.energy-consumption/contains-other-buildings? boolean?)
(s/def :lipas.energy-consumption/operating-hours (number-in :min 0 :max (* 24 7 365)))

(s/def :lipas/energy-consumption
  (s/keys :opt-un [:lipas.energy-consumption/electricity-mwh
                   :lipas.energy-consumption/cold-mwh
                   :lipas.energy-consumption/heat-mwh
                   :lipas.energy-consumption/water-m3
                   :lipas.energy-consumption/contains-other-buildings?
                   :lipas.energy-consumption/operating-hours]))

(def months #{:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec})

(s/def :lipas/energy-consumption-monthly
  (s/map-of months :lipas/energy-consumption))

;; Visitors ;;

(s/def :lipas.visitors/total-count (s/int-in 0 1000000))      ; Users
(s/def :lipas.visitors/spectators-count (s/int-in 0 1000000)) ; Spectators

(s/def :lipas/visitors
  (s/keys :opt-un [:lipas.visitors/total-count
                   :lipas.visitors/spectators-count]))

(s/def :lipas/visitors-monthly
  (s/map-of months :lipas/visitors))

(s/def :lipas/new-sports-site
  (s/keys :req-un [:lipas.sports-site/event-date
                   :lipas.sports-site/status
                   :lipas.sports-site/type
                   :lipas.sports-site/name
                   :lipas.sports-site/owner
                   :lipas.sports-site/admin
                   :lipas.sports-site/type
                   :lipas/location]
          :opt-un [:lipas.sports-site/created-at
                   :lipas.sports-site/marketing-name
                   :lipas.sports-site/phone-number
                   :lipas.sports-site/www
                   :lipas.sports-site/email
                   :lipas.sports-site/construction-year
                   :lipas.sports-site/renovation-years
                   :lipas.sports-site/comment
                   :lipas/energy-consumption-monthly
                   :lipas/energy-consumption
                   :lipas/visitors
                   :lipas/visitors-monthly
                   ;; :lipas.sports-site/properties
                   ]))

(s/def :lipas/sports-site
  (s/merge
   :lipas/new-sports-site
   (s/keys :req-un [:lipas.sports-site/lipas-id])))

(s/def :lipas/sports-sites (s/coll-of :lipas/sports-site :distinct true :into []))

;;; Building ;;;

(s/def :lipas.building/main-construction-material
  (into #{} (keys materials/building-materials)))
(s/def :lipas.building/supporting-structure
  (into #{} (keys materials/supporting-structures)))
(s/def :lipas.building/ceiling-structure
  (into #{} (keys materials/ceiling-structures)))

(s/def :lipas.building/construction-year :lipas.sports-site/construction-year)
(s/def :lipas.building/main-designers (str-in 2 100))
(s/def :lipas.building/total-surface-area-m2 (number-in :min 100 :max (inc 50000)))
(s/def :lipas.building/total-volume-m3 (number-in :min 100 :max (inc 400000)))
(s/def :lipas.building/total-pool-room-area-m2 (number-in :min 100 :max (inc 10000)))
(s/def :lipas.building/total-ice-area-m2 (number-in :min 100 :max (inc 10000)))
(s/def :lipas.building/total-water-area-m2 (number-in :min 10 :max (inc 10000)))
(s/def :lipas.building/heat-sections? boolean?)
(s/def :lipas.building/piled? boolean?)
(s/def :lipas.building/staff-count (s/int-in 0 (inc 1000)))
(s/def :lipas.building/seating-capacity (s/int-in 0 (inc 50000)))
(s/def :lipas.building/heat-source (into #{} (keys swimming-pools/heat-sources)))
(s/def :lipas.building/heat-sources
  (s/coll-of :lipas.building/heat-source
             :min-count 0
             :max-count (count swimming-pools/heat-sources)
             :distinct true
             :into []))

(s/def :lipas.building/ventilation-units-count (s/int-in 0 (inc 100)))

(s/def :lipas.building/main-construction-materials
  (s/coll-of :lipas.building/main-construction-material
             :min-count 0
             :max-count (count materials/building-materials)
             :distinct true
             :into []))

(s/def :lipas.building/supporting-structures
  (s/coll-of :lipas.building/supporting-structure
             :min-count 0
             :max-count (count materials/supporting-structures)
             :distinct true
             :into []))

(s/def :lipas.building/ceiling-structures
  (s/coll-of :lipas.building/ceiling-structure
             :min-count 0
             :max-count (count materials/ceiling-structures)
             :distinct true
             :into []))

;;; Ice stadiums ;;;

;; Building ;;

(s/def :lipas.ice-stadium/building
  (s/keys :opt-un [:lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/total-ice-area-m2
                   :lipas.building/construction-year
                   :lipas.building/seating-capacity]))

;; Envelope structure ;;

(s/def :lipas.ice-stadium.envelope/base-floor-structure
  (into #{} (keys materials/base-floor-structures)))

(s/def :lipas.ice-stadium.envelope/insulated-exterior? boolean?)
(s/def :lipas.ice-stadium.envelope/insulated-ceiling? boolean?)
(s/def :lipas.ice-stadium.envelope/low-emissivity-coating? boolean?)

(s/def :lipas.ice-stadium/envelope
  (s/keys :opt-un [:lipas.ice-stadium.envelope/insulated-exterior?
                   :lipas.ice-stadium.envelope/insulated-ceiling?
                   :lipas.ice-stadium.envelope/low-emissivity-coating?]))

;; Rinks ;;

(s/def :lipas.ice-stadium.rink/width-m (number-in :min 0 :max 100))
(s/def :lipas.ice-stadium.rink/length-m (number-in :min 0 :max 100))

(s/def :lipas.ice-stadium/rink (s/keys :req-un [:lipas.ice-stadium.rink/width-m
                                                :lipas.ice-stadium.rink/length-m]))
(s/def :lipas.ice-stadium/rinks
  (s/coll-of :lipas.ice-stadium/rink
             :min-count 0
             :max-count 20
             :distinct false
             :into []))

;; Refrigeration ;;

(s/def :lipas.ice-stadium.refrigeration/original? boolean?)
(s/def :lipas.ice-stadium.refrigeration/individual-metering? boolean?)
(s/def :lipas.ice-stadium.refrigeration/condensate-energy-recycling? boolean?)

(s/def :lipas.ice-stadium.refrigeration/condensate-energy-main-target
  (into #{} (keys ice-stadiums/condensate-energy-targets)))

(s/def :lipas.ice-stadium.refrigeration/condensate-energy-main-targets
  (s/coll-of :lipas.ice-stadium.refrigeration/condensate-energy-main-target
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

(s/def :lipas.ice-stadium.refrigeration/power-kw
  (s/int-in 0 (inc 10000)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant
  (into #{} (keys ice-stadiums/refrigerants)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-amount-kg
  (s/int-in 0 (inc 10000)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-solution
  (into #{} (keys ice-stadiums/refrigerant-solutions)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l
  (s/int-in 0 (inc 30000)))

(s/def :lipas.ice-stadium/refrigeration
  (s/keys :opt-un [:lipas.ice-stadium.refrigeration/original?
                   :lipas.ice-stadium.refrigeration/individual-metering?
                   :lipas.ice-stadium.refrigeration/condensate-energy-recycling?
                   :lipas.ice-stadium.refrigeration/condensate-energy-main-targets
                   :lipas.ice-stadium.refrigeration/power-kw
                   :lipas.ice-stadium.refrigeration/refrigerant
                   :lipas.ice-stadium.refrigeration/refrigerant-amount-kg
                   :lipas.ice-stadium.refrigeration/refrigerant-solution
                   :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l]))

;; Conditions ;;

(s/def :lipas.ice-stadium.conditions/air-humidity-min (s/int-in 0 (inc 100)))
(s/def :lipas.ice-stadium.conditions/air-humidity-max (s/int-in 0 (inc 100)))
(s/def :lipas.ice-stadium.conditions/stand-temperature-c (s/int-in -10 (inc 50)))
(s/def :lipas.ice-stadium.conditions/daily-open-hours :lipas/hours-in-day)
(s/def :lipas.ice-stadium.conditions/open-months (s/int-in 0 (inc 12)))

(s/def :lipas.ice-stadium.conditions/ice-surface-temperature-c
  (s/int-in -10 (inc 0)))

(s/def :lipas.ice-stadium.conditions/skating-area-temperature-c
  (s/int-in -15 (inc 20)))

(s/def :lipas.ice-stadium.conditions/daily-maintenances-week-days
  (s/int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/daily-maintenances-weekends
  (s/int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/weekly-maintenances (s/int-in 0 (inc 100)))

(s/def :lipas.ice-stadium.conditions/average-water-consumption-l
  (s/int-in 0 (inc 1000)))

(s/def :lipas.ice-stadium.conditions/maintenance-water-temperature-c
  (s/int-in 0 100))

(s/def :lipas.ice-stadium.conditions/ice-resurfacer-fuel
  (into #{} (keys ice-stadiums/ice-resurfacer-fuels)))

(s/def :lipas.ice-stadium.conditions/ice-average-thickness-mm
  (s/int-in 0 (inc 150)))

(s/def :lipas.ice-stadium/conditions
  (s/keys :opt-un [:lipas.ice-stadium.conditions/air-humidity-min
                   :lipas.ice-stadium.conditions/air-humidity-max
                   :lipas.ice-stadium.conditions/ice-surface-temperature-c
                   :lipas.ice-stadium.conditions/skating-area-temperature-c
                   :lipas.ice-stadium.conditions/stand-temperature-c
                   :lipas.ice-stadium.conditions/daily-open-hours
                   :lipas.ice-stadium.conditions/open-months
                   ;; :lipas.ice-stadium.conditions/daily-maintenances-week-days
                   ;; :lipas.ice-stadium.conditions/daily-maintenances-weekends
                   :lipas.ice-stadium.conditions/weekly-maintenances
                   :lipas.ice-stadium.conditions/average-water-consumption-l
                   :lipas.ice-stadium.conditions/maintenance-water-temperature-c
                   :lipas.ice-stadium.conditions/ice-resurfacer-fuel
                   :lipas.ice-stadium.conditions/ice-average-thickness-mm]))

;; Ventilation ;;

(s/def :lipas.ice-stadium.ventilation/heat-recovery-efficiency
  (s/int-in 0 (inc 100)))

(s/def :lipas.ice-stadium.ventilation/heat-recovery-type
  (into #{} (keys ice-stadiums/heat-recovery-types)))

(s/def :lipas.ice-stadium.ventilation/dryer-type
  (into #{} (keys ice-stadiums/dryer-types)))

(s/def :lipas.ice-stadium.ventilation/dryer-duty-type
  (into #{} (keys ice-stadiums/dryer-duty-types)))

(s/def :lipas.ice-stadium.ventilation/heat-pump-type
  (into #{} (keys ice-stadiums/heat-pump-types)))

(s/def :lipas.ice-stadium/ventilation
  (s/keys :opt-un [:lipas.ice-stadium.ventilation/heat-recovery-efficiency
                   :lipas.ice-stadium.ventilation/heat-recovery-type
                   :lipas.ice-stadium.ventilation/dryer-type
                   :lipas.ice-stadium.ventilation/dryer-duty-type
                   :lipas.ice-stadium.ventilation/heat-pump-type]))

(s/def :lipas.ice-stadium.type/type-code #{2510 2520})
(s/def :lipas.ice-stadium/type
  (s/merge
   :lipas.sports-site/type
   (s/keys :req-un [:lipas.ice-stadium.type/type-code])))

(s/def :lipas.sports-site/ice-stadium
  (s/merge
   :lipas/sports-site
   (s/keys :req-un [:lipas.ice-stadium/type]
           :opt-un [:lipas.sports-site/hall-id
                    :lipas.ice-stadium/building
                    :lipas.ice-stadium/rinks
                    :lipas.ice-stadium/envelope
                    :lipas.ice-stadium/refrigeration
                    :lipas.ice-stadium/ventilation
                    :lipas.ice-stadium/conditions])))

;;; Swimming pools ;;;

;; Building ;;

(s/def :lipas.swimming-pool/building
  (s/keys :opt-un [:lipas.building/main-designers
                   :lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/construction-year
                   :lipas.building/seating-capacity
                   :lipas.building/total-water-area-m2
                   :lipas.building/total-pool-room-area-m2
                   :lipas.building/heat-sections?
                   :lipas.building/main-construction-materials
                   :lipas.building/piled?
                   :lipas.building/supporting-structures
                   :lipas.building/ceiling-structures
                   :lipas.building/staff-count
                   :lipas.building/heat-source
                   :lipas.building/heat-sources
                   :lipas.building/ventilation-units-count]))

;; Water treatment ;;

(s/def :lipas.swimming-pool.water-treatment/ozonation boolean?)
(s/def :lipas.swimming-pool.water-treatment/uv-treatment boolean?)
(s/def :lipas.swimming-pool.water-treatment/activated-carbon boolean?)
(s/def :lipas.swimming-pool.water-treatment/filtering-method
  (into #{} (keys swimming-pools/filtering-methods)))

(s/def :lipas.swimming-pool.water-treatment/filtering-methods
  (s/coll-of :lipas.swimming-pool.water-treatment/filtering-method
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

;; TODO maybe get rid of this?
(s/def :lipas.swimming-pool.water-treatment/comment (str-in 1 2048))

(s/def :lipas.swimming-pool/water-treatment
  (s/keys :opt-un [:lipas.swimming-pool.water-treatment/filtering-methods
                   :lipas.swimming-pool.water-treatment/activated-carbon
                   :lipas.swimming-pool.water-treatment/uv-treatment
                   :lipas.swimming-pool.water-treatment/ozonation
                   :lipas.swimming-pool.water-treatment/comment]))

;; Pools ;;

(s/def :lipas.swimming-pool.pool/temperature-c (number-in :min 0 :max 50))
(s/def :lipas.swimming-pool.pool/volume-m3 (number-in :min 0 :max 5000))
(s/def :lipas.swimming-pool.pool/area-m2 (number-in :min 0 :max 2000))
(s/def :lipas.swimming-pool.pool/length-m (number-in :min 0 :max 200))
(s/def :lipas.swimming-pool.pool/width-m (number-in :min 0 :max 100))
(s/def :lipas.swimming-pool.pool/depth-min-m (number-in :min 0 :max 10))
(s/def :lipas.swimming-pool.pool/depth-max-m (number-in :min 0 :max 10))
(s/def :lipas.swimming-pool.pool/type (into #{} (keys swimming-pools/pool-types)))
(s/def :lipas.swimming-pool.pool/outdoor-pool? boolean?)

(s/def :lipas.swimming-pool/pool
  (s/keys :opt-un [:lipas.swimming-pool.pool/type
                   :lipas.swimming-pool.pool/outdoor-pool?
                   :lipas.swimming-pool.pool/temperature-c
                   :lipas.swimming-pool.pool/volume-m3
                   :lipas.swimming-pool.pool/area-m2
                   :lipas.swimming-pool.pool/length-m
                   :lipas.swimming-pool.pool/width-m
                   :lipas.swimming-pool.pool/depth-min-m
                   :lipas.swimming-pool.pool/depth-max-m]))

(s/def :lipas.swimming-pool/pools
  (s/coll-of :lipas.swimming-pool/pool
             :min-count 0
             :max-count 50
             :distinct false
             :into []))

;; Slides ;;

(s/def :lipas.swimming-pool.slide/length-m (number-in :min 0 :max 200))
(s/def :lipas.swimming-pool.slide/structure
  (into #{} (keys materials/slide-structures)))

(s/def :lipas.swimming-pool/slide
  (s/keys :req-un [:lipas.swimming-pool.slide/length-m]
          :opt-un [:lipas.swimming-pool.slide/structure]))

(s/def :lipas.swimming-pool/slides
  (s/coll-of :lipas.swimming-pool/slide
             :min-count 0
             :max-count 10
             :distinct false
             :into []))

;; Saunas ;;

(s/def :lipas.swimming-pool.sauna/men? boolean?)
(s/def :lipas.swimming-pool.sauna/women? boolean?)
(s/def :lipas.swimming-pool.sauna/accessible? boolean?)
(s/def :lipas.swimming-pool.sauna/type
  (into #{} (keys swimming-pools/sauna-types)))

(s/def :lipas.swimming-pool/sauna
  (s/keys :req-un [:lipas.swimming-pool.sauna/type]
          :opt-un [:lipas.swimming-pool.sauna/men?
                   :lipas.swimming-pool.sauna/women?
                   :lipas.swimming-pool.sauna/accessible?]))

(s/def :lipas.swimming-pool/saunas
  (s/coll-of :lipas.swimming-pool/sauna
             :min-count 0
             :max-count 50
             :distinct false
             :into []))

;; Other facilities ;;

(s/def :lipas.swimming-pool.facilities/platforms-1m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-3m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-5m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-7.5m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-10m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/hydro-massage-spots-count
  (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count
  (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/kiosk? boolean?)
(s/def :lipas.swimming-pool.facilities/gym? boolean?)

;; Showers and lockers ;;
(s/def :lipas.swimming-pool.facilities/showers-men-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/showers-women-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/showers-unisex-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/lockers-men-count (s/int-in 0 1000))
(s/def :lipas.swimming-pool.facilities/lockers-women-count (s/int-in 0 1000))
(s/def :lipas.swimming-pool.facilities/lockers-unisex-count (s/int-in 0 1000))

(s/def :lipas.swimming-pool/facilities
  (s/keys :opt-un [:lipas.swimming-pool.facilities/platforms-1m-count
                   :lipas.swimming-pool.facilities/platforms-3m-count
                   :lipas.swimming-pool.facilities/platforms-5m-count
                   :lipas.swimming-pool.facilities/platforms-7.5m-count
                   :lipas.swimming-pool.facilities/platforms-10m-count
                   :lipas.swimming-pool.facilities/hydro-massage-spots-count
                   :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count
                   :lipas.swimming-pool.facilities/kiosk?
                   :lipas.swimming-pool.facilities/gym?
                   :lipas.swimming-pool.facilities/showers-men-count
                   :lipas.swimming-pool.facilities/showers-women-count
                   :lipas.swimming-pool.facilities/lockers-men-count
                   :lipas.swimming-pool.facilities/lockers-women-count]))

;; Conditions ;;

(s/def :lipas.swimming-pool.conditions/open-days-in-year (s/int-in 0 (inc 365)))
(s/def :lipas.swimming-pool.conditions/daily-open-hours :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-mon :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-tue :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-wed :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-thu :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-fri :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-sat :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-sun :lipas/hours-in-day)

(s/def :lipas.swimming-pool/conditions
  (s/keys :opt-un [:lipas.swimming-pool.conditions/open-days-in-year
                   :lipas.swimming-pool.conditions/open-hours-in-day
                   :lipas.swimming-pool.conditions/open-hours-mon
                   :lipas.swimming-pool.conditions/open-hours-tue
                   :lipas.swimming-pool.conditions/open-hours-wed
                   :lipas.swimming-pool.conditions/open-hours-thu
                   :lipas.swimming-pool.conditions/open-hours-fri
                   :lipas.swimming-pool.conditions/open-hours-sat
                   :lipas.swimming-pool.conditions/open-hours-sun
                   :lipas.swimming-pool.conditions/total-visitors-count]))

(s/def :lipas.swimming-pool.type/type-code #{3110 3120 3130})
(s/def :lipas.swimming-pool/type
  (s/merge
   :lipas.sports-site/type
   (s/keys :req-un [:lipas.swimming-pool.type/type-code])))

;; Energy saving ;;
(s/def :lipas.swimming-pool.energy-saving/shower-water-heat-recovery?
  boolean?)

(s/def :lipas.swimming-pool.energy-saving/filter-rinse-water-recovery?
  boolean?)

(s/def :lipas.swimming-pool/energy-saving
  (s/keys :opt-un
          [:lipas.swimming-pool.energy-saving/shower-water-heat-recovery?
           :lipas.swimming-pool.energy-saving/filter-rinse-water-recovery?]))

(s/def :lipas.sports-site/swimming-pool
  (s/merge
   :lipas/sports-site
   (s/keys :req-un [:lipas.swimming-pool/type]
           :opt-un [:lipas.sports-site/hall-id ; Legacy portal id
                    :lipas.swimming-pool/water-treatment
                    :lipas.swimming-pool/facilities
                    :lipas.swimming-pool/building
                    :lipas.swimming-pool/pools
                    :lipas.swimming-pool/saunas
                    :lipas.swimming-pool/slides
                    :lipas.swimming-pool/conditions
                    :lipas.swimming-pool/energy-saving])))

(s/def :lipas.sports-site/swimming-pools
  (s/coll-of :lipas.sports-site/swimming-pool
             :distinct true
             :into []))

;;; HTTP-API ;;;

(s/def :lipas.api/revs #{"latest" "yearly"})
(s/def :lipas.api/lang #{"fi" "en" "se"})
(s/def :lipas.api/draft #{"true" "false"})
(s/def :lipas.api/query-params
  (s/keys :opt-un [:lipas.api/revs
                   :lipas.api/lang
                   :lipas.api/draft]))

(s/def :lipas.report/field (into #{} (keys reports/fields)))

(s/def :lipas.api.sports-site-report.req/search-query map?)
(s/def :lipas.api.sports-site-report.req/fields
  (s/coll-of :lipas.report/field
             :distinct true
             :min-count 0
             :max-count (count (keys reports/fields))
             :into []))

(s/def :lipas.api.sports-site-report/req
  (s/keys :req-un [:lipas.api.sports-site-report.req/search-query
                   :lipas.api.sports-site-report.req/fields]))
