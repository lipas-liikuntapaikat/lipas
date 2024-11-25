(ns lipas.schema.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [hiposfer.geojson.specs :as geojson]
   [lipas.data.activities :as activities]
   [lipas.data.admins :as admins]
   [lipas.data.cities :as cities]
   [lipas.data.feedback :as feedback]
   [lipas.data.floorball :as floorball]
   [lipas.data.ice-stadiums :as ice-stadiums]
   [lipas.data.loi :as loi]
   [lipas.data.materials :as materials]
   [lipas.data.owners :as owners]
   [lipas.data.prop-types :as prop-types]
   [lipas.data.reminders :as reminders]
   [lipas.data.sports-sites :as sports-sites]
   [lipas.data.status :as status]
   [lipas.data.swimming-pools :as swimming-pools]
   [lipas.data.types :as sports-site-types]
   [lipas.reports :as reports]
   [lipas.roles :as roles]
   [lipas.utils :as utils]
   [spec-tools.core :as st]
   [spec-tools.parse :as stp]))

;;; Utils ;;;

(defn str-in [min max]
  (st/spec
   {:spec              (s/and string? #(<= min (count %) max))
    :swagger/minLength min
    :swagger/maxLength max}))

(defn number-in
  "Returns a spec that validates numbers in the range from
  min (inclusive) to max (exclusive)."
  [& {:keys [min max]}]
  (st/spec
   {:spec            (s/and number? #(<= min % (dec max)))
    ;; Generate only doubles to avoid problems with ES index type
    ;; inference and generated values being randomly int / double.
    ;; Ints are ingested fine by ES but if the initial mapping expects
    ;; all inputs to be ints it fails when it sees doubles.
    :gen             gen/double
    :swagger/type    "number"
    :swagger/minimum min
    :swagger/maximum max}))

(defn double-in
  [& {:keys [min max infinite? NaN?]}]
  (st/spec
   {:spec            (s/double-in :min min :max max :NaN? NaN? :infinite? infinite?)
    :swagger/type    "number"
    :swagger/format  "double"
    :swagger/minimum min
    :swagger/maximum max}))

(defn int-in
  "Returns a spec that validates integers in the range from
  min (inclusive) to max (exclusive)."
  [min max]
  (st/spec
   {:spec            (s/int-in min max)
    :type            :long
    :swagger/type    "number"
    :swagger/format  "int64"
    :swagger/minimum min
    :swagger/maximum max}))


;;; Regexes ;;;

(def email-regex #"^[a-zA-Z0-9åÅäÄöÖ._%+-]+@[a-zA-Z0-9åÅäÄöÖ.-]+\.[a-zA-Z]{2,63}$")
(def postal-code-regex #"[0-9]{5}")
(def two-consecutive-dots-regex #"\.{2,}")

;; https://stackoverflow.com/questions/3143070/javascript-regex-iso-datetime
(def timestamp-regex
  #"\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+([+-][0-2]\d:[0-5]\d|Z)")

(def date-regex
  #"\d{4}-[01]\d-[0-3]\d")

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

(defn email-gen
  "Function that returns a Generator for email addresses"
  []
  (gen/fmap
   (fn [[name host tld]]
     (str name "@" host "." tld))
   (gen/tuple
    (gen-str 1 15)
    (gen-str 1 15)
    (gen-str 2 5))))

(defn postal-code-gen
  "Function that returns a Generator for Finnish postal codes"
  []
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
           ss (utils/zero-left-pad ss 2)
           ms (utils/zero-left-pad ms 3)]
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
   (s/gen (s/tuple :lipas.location.coordinates/finland-lon
                   :lipas.location.coordinates/finland-lat))))

;; Specs ;;

(s/def :lipas/timestamp-type (s/and string? #(re-matches timestamp-regex %)))
(s/def :lipas/timestamp
  (st/spec
   {:spec         (s/with-gen :lipas/timestamp-type timestamp-gen)
    :swagger/type "string"
    :swagger/format "date-time"}))

(s/def :lipas/date-type
  (s/and string? #(re-matches date-regex %)))

(s/def :lipas/date
  (st/spec
   {:spec           :lipas/date-type
    :swagger/type   "string"
    :swagger/format "date"}))

(s/def :lipas/email-type (s/and string?
                                #(re-matches email-regex %)
                                #(if (re-find two-consecutive-dots-regex %) nil %)))

(s/def :lipas/email (s/with-gen :lipas/email-type email-gen))

(s/def :lipas/hours-in-day (int-in  0 (inc 24)))
(s/def :lipas/hours-in-day-with-fractions (number-in {:min 0 :max (inc 24)}))

(s/def :lipas/locale* #{:fi :se :en})
(s/def :lipas/locale
  (st/spec
   {:spec         :lipas/locale*
    :type         :keyword
    :swagger/type "enum"}))

;;; Reminder ;;;

(s/def :lipas.reminder/id uuid?)
(s/def :lipas.reminder/created-at :lipas/timestamp)
(s/def :lipas.reminder/event-date :lipas/timestamp)
(s/def :lipas.reminder/status (into #{} (keys reminders/statuses)))
(s/def :lipas.reminder.body/message (str-in 1 2048))
(s/def :lipas.reminder/body
  (s/keys :req-un [:lipas.reminder.body/message]))

(s/def :lipas/new-reminder
  (s/keys :req-un [:lipas.reminder/event-date
                   :lipas.reminder/body]))

(s/def :lipas/reminder
  (s/merge :lipas/new-reminder
           (s/keys :req-un [:lipas.reminder/id
                            :lipas.reminder/created-at
                            :lipas.reminder/status])))

;;; User ;;;

(s/def :lipas.user/id uuid?)
(s/def :lipas.user/status #{"active" "archived"})
(s/def :lipas.user/firstname (str-in 1 128))
(s/def :lipas.user/lastname (str-in 1 128))
(s/def :lipas.user/username (str-in 1 128))
(s/def :lipas.user/password (str-in 6 128))
(s/def :lipas.user/email :lipas/email)

(s/def :lipas.user/login (s/or :username :lipas.user/username
                               :email :lipas.user/email))

(s/def :lipas.user.user-data.saved-report/name (str-in 1 128))
(s/def :lipas.user.user-data.saved-report/fields (s/coll-of (str-in 1 256)))

(s/def :lipas.user.user-data/saved-report
  (s/keys :req-un [:lipas.user.user-data.saved-report/name]
          :opt-un [:lipas.user.user-data.saved-report/fields]))

(s/def :lipas.user.user-data/saved-reports
  (s/coll-of :lipas.user.user-data/saved-report
             :min-count 0
             :max-count 1000
             :into []))

(s/def :lipas.user.user-data/saved-diversity-settings
  (s/keys :req-un [:lipas.diversity.settings/category-presets]))

(s/def :lipas.user/user-data
  (s/keys :req-un [:lipas.user/firstname
                   :lipas.user/lastname]
          :opt-un [:lipas.user.user-data/saved-reports
                   :lipas.user.user-data/saved-diversity-settings]))

;; Deprecated permissions model
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

(s/def :lipas.sports-site.activity/value
  (into #{} (->> activities/by-types vals (map :value))))

(s/def :lipas.user.permissions/activities
  (s/coll-of :lipas.sports-site.activity/value
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.user/permissions
  (s/keys :opt-un [;; Old permissions
                   :lipas.user.permissions/admin?
                   :lipas.user.permissions/draft?
                   :lipas.user.permissions/sports-sites
                   :lipas.user.permissions/all-cities?
                   :lipas.user.permissions/all-types?
                   :lipas.user.permissions/cities
                   :lipas.user.permissions/types
                   :lipas.user.permissions/activities
                   ;; New roles
                   :lipas.user.permissions/roles]))

;; Role based permissions
(s/def :lipas.role/role (st/spec {:spec (set (keys roles/roles))
                                  :decode/json #(keyword %2)}))

;; Context keys
(s/def :lipas.role/type-code
  (s/coll-of :lipas.sports-site.type/type-code
             :min-count 1
             :distinct true
             :into #{}))

(s/def :lipas.role/city-code
  (s/coll-of :lipas.location.city/city-code
             :min-count 1
             :distinct true
             :into #{}))

(s/def :lipas.role/lipas-id
  (s/coll-of :lipas.sports-site/lipas-id
             :min-count 1
             :distinct true
             :into #{}))

(s/def :lipas.role/activity
  (s/coll-of :lipas.sports-site.activity/value
             :min-count 1
             :distint true
             :into #{}))

(defmulti role-type :role)

(defmethod role-type :type-manager [_]
  (s/keys :req-un [:lipas.role/role :lipas.role/type-code]
          :opt-un [:lipas.role/city-code]))

(defmethod role-type :city-manager [_]
  (s/keys :req-un [:lipas.role/role :lipas.role/city-code]
          :opt-un [:lipas.role/type-code]))

(defmethod role-type :site-manager [_]
  (s/keys :req-un [:lipas.role/role :lipas.role/lipas-id]))

(defmethod role-type :activities-manager [_]
  (s/keys :req-un [:lipas.role/role :lipas.role/activity]
          :opt-un [:lipas.role/city-code :lipas.role/type-code]))

(defmethod role-type :floorball-manager [_]
  (s/keys :req-un [:lipas.role/role]
          :opt-un [:lipas.role/type-code]))

;; :admin and others without any role-context-keys
(defmethod role-type :default [_]
  (s/keys :req-un [:lipas.role/role]))

(s/def :lipas.user.permissions.roles/role
  (s/multi-spec role-type :role))

(s/def :lipas.user.permissions/roles
  (s/coll-of :lipas.user.permissions.roles/role
             :min-count 0
             :distinct true
             :into []))

(comment
  (s/valid? :lipas.user.permissions/roles
            [{:role :admin}])
  (s/valid? :lipas.user.permissions/roles
            [{:role :city-manager
              :city-code #{837}
              :type-code #{1110}}])
  (s/valid? :lipas.user.permissions/roles
            [{:role :activities-manager
              :activity #{"fishing"}}]))

(s/def :lipas.user/permissions-request (str-in 1 200))

(s/def :lipas/new-user (s/keys :req-un [:lipas.user/email
                                        :lipas.user/username
                                        :lipas.user/user-data]
                               :opt-un [:lipas.user/password
                                        :lipas.user/permissions]))

(s/def :lipas/user (s/merge :lipas/new-user
                            (s/keys :req-un [:lipas.user/id
                                             :lipas.user/status])))

;;; Location ;;;

(s/def :lipas.location/address (str-in 1 200))
(s/def :lipas.location/postal-code-type
  (s/and string? #(re-matches postal-code-regex %)))

(s/def :lipas.location/postal-code (s/with-gen
                       :lipas.location/postal-code-type
                       postal-code-gen))

(s/def :lipas.location/postal-office (str-in 0 50))

(def city-codes (into #{} (map :city-code) cities/all))
(s/def :lipas.location.city/city-code* city-codes)
(s/def :lipas.location.city/city-code
  (st/spec
   {:spec         :lipas.location.city/city-code*
    :type         :long
    :swagger/type "number"
    :swagger/enum city-codes}))

(s/def :lipas.location.city/neighborhood (str-in 1 100))

(s/def :lipas.location/city
  (s/keys :req-un [:lipas.location.city/city-code]
          :opt-un [:lipas.location.city/neighborhood]))

(s/def :lipas.location.coordinates/lat (double-in :min -90
                                                  :max 90
                                                  :NaN? false
                                                  :infinite? false))
(s/def :lipas.location.coordinates/lon (double-in :min -180
                                                  :max 180
                                                  :NaN? false
                                                  :infinite? false))

(s/def :lipas.location.coordinates/finland-lat (double-in :min 59.846373196
                                                          :max 70.1641930203
                                                          :NaN? false
                                                          :infinite? false))
(s/def :lipas.location.coordinates/finland-lon (double-in :min 20.6455928891
                                                          :max 31.5160921567
                                                          :NaN? false
                                                          :infinite? false))

(s/def :lipas.location.coordinates/map-wgs84-bounds-lat (double-in :min 32.88
                                                                   :max 84.73
                                                                   :NaN? false
                                                                   :infinite? false))
(s/def :lipas.location.coordinates/map-wgs84-bounds-lon (double-in :min 16.1
                                                                   :max 40.18
                                                                   :NaN? false
                                                                   :infinite? false))

;; Northing
(s/def :lipas.location.coordinates/lat-euref (int-in -548576 1548576))
;; Easting
(s/def :lipas.location.coordinates/lon-euref (int-in 1548576 8388608))

;; NOTE: generator supports only point features atm
(s/def :lipas.location/geometries (s/with-gen
                                    ::geojson/feature-collection
                                    lipas-point-feature-gen))

(comment
  (s/valid?
   ::geojson/feature-collection
   (gen/generate (s/gen :lipas.location/geometries))))

;;; Sports site ;;;

(s/def :lipas.sports-site/status* (into #{} (keys sports-sites/statuses)))
(s/def :lipas.sports-site/status
  (st/spec
   {:spec :lipas.sports-site/status*
    :swagger/type "string"
    :swagger/enum (keys sports-sites/statuses)}))

(s/def :lipas.sports-site/lipas-id (int-in 0 2147483647)) ; PSQL integer max
(s/def :lipas.sports-site/hall-id (str-in 2 20))
(s/def :lipas.sports-site/name (str-in 2 100))
(s/def :lipas.sports-site/marketing-name (str-in 2 100))

(s/def :lipas.sports-site.name/fi :lipas.sports-site/name)
(s/def :lipas.sports-site.name/en :lipas.sports-site/name)
(s/def :lipas.sports-site.name/se :lipas.sports-site/name)

(s/def :lipas.sports-site/name-localized
  (s/keys :opt-un [:lipas.sports-site.name/fi
                   :lipas.sports-site.name/se
                   :lipas.sports-site.name/en]))

(s/def :lipas.sports-site/owner* (into #{} (keys owners/all)))
(s/def :lipas.sports-site/owner
  (st/spec
   {:spec         :lipas.sports-site/owner*
    :swagger/type "string"
    :swagger/enum (keys owners/all)}))

(s/def :lipas.sports-site/admin* (into #{} (keys admins/all)))
(s/def :lipas.sports-site/admin
  (st/spec
   {:spec         :lipas.sports-site/admin*
    :swagger/type "string"
    :swagger/enum (keys admins/all)}))

(s/def :lipas.sports-site/phone-number (str-in 1 50))
(s/def :lipas.sports-site/www (str-in 1 200))
(s/def :lipas.sports-site/reservations-link (str-in 1 200))
(s/def :lipas.sports-site/email :lipas/email)

(s/def :lipas.sports-site/comment (str-in 1 2048))

(def type-codes
  (keys sports-site-types/all))

(s/def :lipas.sports-site.type/type-code*
  (into #{} type-codes))

(s/def :lipas.sports-site.type/type-code
  (st/spec
   {:spec         :lipas.sports-site.type/type-code*
    :type         :long
    :swagger/type "number"
    :swagger/enum type-codes}))

(s/def :lipas.sports-site/construction-year*
  (into #{} (range 1800 (inc utils/this-year))))

(s/def :lipas.sports-site/construction-year
  (st/spec
   {:spec         :lipas.sports-site/construction-year*
    :swagger/type "number"}))

(s/def :lipas.sports-site/renovation-year*
  (int-in 1900 (inc utils/this-year)))

(s/def :lipas.sports-site/renovation-year
  (st/spec
   {:spec         :lipas.sports-site/renovation-year*
    :swagger/type "number"}))

(s/def :lipas.sports-site/renovation-years
  (s/coll-of :lipas.sports-site/renovation-year
             :distinct true :into []))

(s/def :lipas.sports-site/audit-date
  :lipas/date)

(s/def :lipas.sports-site/audit-performed-by
  (str-in 2 100))

(s/def :lipas.sports-site/audit-type
  (str-in 2 100))

(s/def :lipas.sports-site/audit
  (s/keys :req-un [:lipas.sports-site/audit-date
                   :lipas.sports-site/audit-performed-by
                   :lipas.sports-site/audit-type]))

(s/def :lipas.sports-site/audits
  (s/coll-of :lipas.sports-site/audit))

;;;; Additional properties ;;;;

(s/def :lipas.sports-site.properties/surface-material*
  (s/coll-of (into #{} (keys materials/surface-materials))
             :distinct true
             :into []))

(s/def :lipas.sports-site.properties/surface-material
  (st/spec
   {:spec :lipas.sports-site.properties/surface-material*
    :swagger/type "string"
    :swagger/enum (keys materials/surface-materials)}))

(def pos-infinity #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))
(def neg-infinity #?(:clj Double/NEGATIVE_INFINITY :cljs js/Infinity))
(def NaN #?(:clj Double/NaN :cljs js/NaN))

;; From https://github.com/SparkFund/useful-specs
(s/def ::real*
  (s/spec (fn [x] (and (number? x)
                       (not= pos-infinity x)
                       (not= neg-infinity x)
                       (not= NaN x)))
          :gen #(gen/double* {:infinite? false :NaN? false})))

(s/def ::real
  (st/spec
   {:spec         ::real*
    :swagger/type "number"}))

(s/def :lipas.sports-site.properties/height-m ::real)
(s/def :lipas.sports-site.properties/heating? boolean?)
(s/def :lipas.sports-site.properties/field-2-area-m2 ::real)
(s/def :lipas.sports-site.properties/ski-track? boolean?)
(s/def :lipas.sports-site.properties/basketball-fields-count ::real)
(s/def :lipas.sports-site.properties/surface-material-info string?)
(s/def :lipas.sports-site.properties/holes-count ::real)
(s/def :lipas.sports-site.properties/skijump-hill-type string?)
(s/def :lipas.sports-site.properties/lifts-count ::real)
(s/def :lipas.sports-site.properties/field-3-length-m ::real)
(s/def :lipas.sports-site.properties/pool-tracks-count ::real)
(s/def :lipas.sports-site.properties/field-2-length-m ::real)
(s/def :lipas.sports-site.properties/plastic-outrun? boolean?)
(s/def :lipas.sports-site.properties/automated-timing? boolean?)
(s/def :lipas.sports-site.properties/freestyle-slope? boolean?)
(s/def :lipas.sports-site.properties/kiosk? boolean?)
(s/def :lipas.sports-site.properties/summer-usage? boolean?)
(s/def :lipas.sports-site.properties/stand-capacity-person ::real)
(s/def :lipas.sports-site.properties/pier? boolean?)
(s/def :lipas.sports-site.properties/may-be-shown-in-excursion-map-fi? boolean?)
(s/def :lipas.sports-site.properties/sprint-lanes-count ::real)
(s/def :lipas.sports-site.properties/javelin-throw-places-count ::real)
(s/def :lipas.sports-site.properties/tennis-courts-count ::real)
(s/def :lipas.sports-site.properties/ski-service? boolean?)
(s/def :lipas.sports-site.properties/field-1-length-m ::real)
(s/def :lipas.sports-site.properties/finish-line-camera? boolean?)
(s/def :lipas.sports-site.properties/parking-place? boolean?)
(s/def :lipas.sports-site.properties/climbing-routes-count ::real)
(s/def :lipas.sports-site.properties/outdoor-exercise-machines? boolean?)
(s/def :lipas.sports-site.properties/automated-scoring? boolean?)
(s/def :lipas.sports-site.properties/track-width-m ::real)
(s/def :lipas.sports-site.properties/ice-climbing? boolean?)
(s/def :lipas.sports-site.properties/field-length-m ::real)
(s/def :lipas.sports-site.properties/skijump-hill-material string?)
(s/def :lipas.sports-site.properties/longest-slope-m ::real)
(s/def :lipas.sports-site.properties/circular-lanes-count ::real)
(s/def :lipas.sports-site.properties/boat-launching-spot? boolean?)
(s/def :lipas.sports-site.properties/ski-track-traditional? boolean?)
(s/def :lipas.sports-site.properties/winter-swimming? boolean?)
(s/def :lipas.sports-site.properties/altitude-difference ::real)
(s/def :lipas.sports-site.properties/climbing-wall-height-m ::real)
(s/def :lipas.sports-site.properties/route-width-m ::real)
(s/def :lipas.sports-site.properties/beach-length-m ::real)
(s/def :lipas.sports-site.properties/match-clock? boolean?)
(s/def :lipas.sports-site.properties/sprint-track-length-m ::real)
(s/def :lipas.sports-site.properties/archery? boolean?)
(s/def :lipas.sports-site.properties/throwing-sports-spots-count ::real)
(s/def :lipas.sports-site.properties/radio-and-tv-capabilities? boolean?)
(s/def :lipas.sports-site.properties/inner-lane-length-m ::real)
(s/def :lipas.sports-site.properties/discus-throw-places ::real)
(s/def :lipas.sports-site.properties/fields-count ::real)
(s/def :lipas.sports-site.properties/field-1-width-m ::real)
(s/def :lipas.sports-site.properties/field-3-width-m ::real)
(s/def :lipas.sports-site.properties/field-2-width-m ::real)
(s/def :lipas.sports-site.properties/badminton-courts-count ::real)
(s/def :lipas.sports-site.properties/hammer-throw-places-count ::real)
(s/def :lipas.sports-site.properties/horse-carriage-allowed? boolean?)
(s/def :lipas.sports-site.properties/pool-width-m ::real)
(s/def :lipas.sports-site.properties/pool-min-depth-m ::real)
(s/def :lipas.sports-site.properties/ice-rinks-count ::real)
(s/def :lipas.sports-site.properties/field-1-area-m2 ::real)
(s/def :lipas.sports-site.properties/k-point ::real)
(s/def :lipas.sports-site.properties/polevault-places-count ::real)
(s/def :lipas.sports-site.properties/group-exercise-rooms-count ::real)
(s/def :lipas.sports-site.properties/snowpark-or-street? boolean?)
(s/def :lipas.sports-site.properties/max-vertical-difference ::real)
(s/def :lipas.sports-site.properties/bowling-lanes-count ::real)
(s/def :lipas.sports-site.properties/air-gun-shooting? boolean?)
(s/def :lipas.sports-site.properties/gymnastic-routines-count ::real)
(s/def :lipas.sports-site.properties/toilet? boolean?)
(s/def :lipas.sports-site.properties/gymnastics-space? boolean?)
(s/def :lipas.sports-site.properties/show-jumping? boolean?)
(s/def :lipas.sports-site.properties/shower? boolean?)
(s/def :lipas.sports-site.properties/rest-places-count ::real)
(s/def :lipas.sports-site.properties/changing-rooms? boolean?)
(s/def :lipas.sports-site.properties/pistol-shooting? boolean?)
(s/def :lipas.sports-site.properties/halfpipe-count ::real)
(s/def :lipas.sports-site.properties/shooting-positions-count ::real)
(s/def :lipas.sports-site.properties/running-track-surface-material string?)
(s/def :lipas.sports-site.properties/tatamis-count ::real)
(s/def :lipas.sports-site.properties/lit-route-length-km ::real)
(s/def :lipas.sports-site.properties/area-m2 ::real)
(s/def :lipas.sports-site.properties/field-width-m ::real)
(s/def :lipas.sports-site.properties/cosmic-bowling? boolean?)
(s/def :lipas.sports-site.properties/wrestling-mats-count ::real)
(s/def :lipas.sports-site.properties/eu-beach? boolean?)
(s/def :lipas.sports-site.properties/hall-length-m ::real)
(s/def :lipas.sports-site.properties/rifle-shooting? boolean?)
(s/def :lipas.sports-site.properties/swimming-pool-count ::real)
(s/def :lipas.sports-site.properties/pool-water-area-m2 ::real)
(s/def :lipas.sports-site.properties/curling-lanes-count ::real)
(s/def :lipas.sports-site.properties/climbing-wall-width-m ::real)
(s/def :lipas.sports-site.properties/area-km2 ::real)
(s/def :lipas.sports-site.properties/scoreboard? boolean?)
(s/def :lipas.sports-site.properties/futsal-fields-count ::real)
(s/def :lipas.sports-site.properties/training-wall? boolean?)
(s/def :lipas.sports-site.properties/shotput-count ::real)
(s/def :lipas.sports-site.properties/longjump-places-count ::real)
(s/def :lipas.sports-site.properties/football-fields-count ::real)
(s/def :lipas.sports-site.properties/floorball-fields-count ::real)
(s/def :lipas.sports-site.properties/equipment-rental? boolean?)
(s/def :lipas.sports-site.properties/slopes-count ::real)
(s/def :lipas.sports-site.properties/old-lipas-typecode int?)
(s/def :lipas.sports-site.properties/other-pools-count ::real)
(s/def :lipas.sports-site.properties/shortest-slope-m ::real)
(s/def :lipas.sports-site.properties/adp-readiness? boolean?)
(s/def :lipas.sports-site.properties/squash-courts-count ::real)
(s/def :lipas.sports-site.properties/boxing-rings-count ::real)
(s/def :lipas.sports-site.properties/ice-reduction? boolean?)
(s/def :lipas.sports-site.properties/fencing-bases-count ::real)
(s/def :lipas.sports-site.properties/classified-route string?)
(s/def :lipas.sports-site.properties/weight-lifting-spots-count ::real)
(s/def :lipas.sports-site.properties/landing-places-count ::real)
(s/def :lipas.sports-site.properties/toboggan-run? boolean?)
(s/def :lipas.sports-site.properties/sauna? boolean?)
(s/def :lipas.sports-site.properties/jumps-count ::real)
(s/def :lipas.sports-site.properties/table-tennis-count ::real)
(s/def :lipas.sports-site.properties/pool-max-depth-m ::real)
(s/def :lipas.sports-site.properties/loudspeakers? boolean?)
(s/def :lipas.sports-site.properties/shotgun-shooting? boolean?)
(s/def :lipas.sports-site.properties/lit-slopes-count ::real)
(s/def :lipas.sports-site.properties/green? boolean?)
(s/def :lipas.sports-site.properties/free-rifle-shooting? boolean?)
(s/def :lipas.sports-site.properties/winter-usage? boolean?)
(s/def :lipas.sports-site.properties/ligthing? boolean?)
(s/def :lipas.sports-site.properties/field-3-area-m2 ::real)
(s/def :lipas.sports-site.properties/accessibility-info string?)
(s/def :lipas.sports-site.properties/covered-stand-person-count ::real)
(s/def :lipas.sports-site.properties/playground? boolean?)
(s/def :lipas.sports-site.properties/handball-fields-count ::real)
(s/def :lipas.sports-site.properties/p-point ::real)
(s/def :lipas.sports-site.properties/inruns-material string?)
(s/def :lipas.sports-site.properties/basketball-field-type string?)
(s/def :lipas.sports-site.properties/volleyball-fields-count ::real)
(s/def :lipas.sports-site.properties/boat-places-count ::real)
(s/def :lipas.sports-site.properties/pool-temperature-c ::real)
(s/def :lipas.sports-site.properties/hall-width-m ::real)
(s/def :lipas.sports-site.properties/climbing-wall? boolean?)
(s/def :lipas.sports-site.properties/ski-track-freestyle? boolean?)
(s/def :lipas.sports-site.properties/spinning-hall? boolean?)
(s/def :lipas.sports-site.properties/other-platforms? boolean?)
(s/def :lipas.sports-site.properties/highjump-places-count ::real)
(s/def :lipas.sports-site.properties/light-roof? boolean?)
(s/def :lipas.sports-site.properties/pool-length-m ::real)
(s/def :lipas.sports-site.properties/route-length-km ::real)
(s/def :lipas.sports-site.properties/exercise-machines-count ::real)
(s/def :lipas.sports-site.properties/track-type string?)
(s/def :lipas.sports-site.properties/training-spot-surface-material string?)
(s/def :lipas.sports-site.properties/range? boolean?)
(s/def :lipas.sports-site.properties/track-length-m ::real)
(s/def :lipas.sports-site.properties/school-use? boolean?)
(s/def :lipas.sports-site.properties/free-use? boolean?)
(s/def :lipas.sports-site.properties/may-be-shown-in-harrastuspassi-fi? boolean?)
(s/def :lipas.sports-site.properties/padel-courts-count ::real)
(s/def :lipas.sports-site.properties/rapid-canoeing-centre? boolean?)
(s/def :lipas.sports-site.properties/canoeing-club? boolean?)
(s/def :lipas.sports-site.properties/activity-service-company? boolean?)
(s/def :lipas.sports-site.properties/boating-service-class string?)
(s/def :lipas.sports-site.properties/water-point
  (into #{} (keys (get-in prop-types/all [:water-point :opts]))))
(s/def :lipas.sports-site.properties/customer-service-point? boolean?)
(s/def :lipas.sports-site.properties/height-of-basket-or-net-adjustable? boolean?)
(s/def :lipas.sports-site.properties/changing-rooms-m2 ::real)
(s/def :lipas.sports-site.properties/ligthing-info string?)
(s/def :lipas.sports-site.properties/highest-obstacle-m ::real)
(s/def :lipas.sports-site.properties/fitness-stairs-length-m ::real)
(s/def :lipas.sports-site.properties/free-customer-use? boolean?)
(s/def :lipas.sports-site.properties/space-divisible ::real)
(s/def :lipas.sports-site.properties/auxiliary-training-area? boolean?)
(s/def :lipas.sports-site.properties/sport-specification string?)
(s/def :lipas.sports-site.properties/active-space-width-m ::real)
(s/def :lipas.sports-site.properties/active-space-length-m ::real)
(s/def :lipas.sports-site.properties/mirror-wall? boolean?)
(s/def :lipas.sports-site.properties/parkour-hall-equipment-and-structures
  (s/coll-of
   (into #{} (keys (get-in prop-types/all [:parkour-hall-equipment-and-structures :opts])))
   :distinct true))
(s/def :lipas.sports-site.properties/ringette-boundary-markings? boolean?)
(s/def :lipas.sports-site.properties/field-1-flexible-rink? boolean?)
(s/def :lipas.sports-site.properties/field-2-flexible-rink? boolean?)
(s/def :lipas.sports-site.properties/field-3-flexible-rink? boolean?)
(s/def :lipas.sports-site.properties/pool-tables-count ::real)
(s/def :lipas.sports-site.properties/snooker-tables-count ::real)
(s/def :lipas.sports-site.properties/kaisa-tables-count ::real)
(s/def :lipas.sports-site.properties/pyramid-tables-count ::real)
(s/def :lipas.sports-site.properties/carom-tables-count ::real)
(s/def :lipas.sports-site.properties/total-billiard-tables-count ::real)
(s/def :lipas.sports-site.properties/boating-service-class
  (s/coll-of
   (into #{} (keys (get-in prop-types/all [:boating-service-class :opts])))
   :distinct true))
(s/def :lipas.sports-site.properties/travel-modes
  (s/coll-of
   (into #{} (keys (get-in prop-types/all [:travel-modes :opts])))
   :distinct true))
(s/def :lipas.sports-site.properties/travel-mode-info string?)
(s/def :lipas.sports-site.properties/sledding-hill? boolean?)
(s/def :lipas.sports-site.properties/mobile-orienteering? boolean?)
(s/def :lipas.sports-site.properties/ski-orienteering? boolean?)
(s/def :lipas.sports-site.properties/bike-orienteering? boolean?)
(s/def :lipas.sports-site.properties/hs-point ::real)
(s/def :lipas.sports-site.properties/lighting-info string?)
(s/def :lipas.sports-site.properties/year-round-use? boolean?)

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
                   :lipas.sports-site.properties/track-length-m
                   :lipas.sports-site.properties/may-be-shown-in-harrastuspassi-fi?
                   :lipas.sports-site.properties/padel-courts-count
                   :lipas.sports-site.properties/activity-service-company?
                   :lipas.sports-site.properties/canoeing-club?
                   :lipas.sports-site.properties/rapid-canoeing-centre?
                   :lipas.sports-site.properties/fitness-stairs-length-m
                   :lipas.sports-site.properties/highest-obstacle-m
                   :lipas.sports-site.properties/ligthing-info
                   :lipas.sports-site.properties/changing-rooms-m2
                   :lipas.sports-site.properties/height-of-basket-or-net-adjustable?
                   :lipas.sports-site.properties/customer-service-point?
                   :lipas.sports-site.properties/water-point
                   :lipas.sports-site.properties/boating-service-class
                   :lipas.sports-site.properties/free-customer-use?
                   :lipas.sports-site.properties/space-divisible
                   :lipas.sports-site.properties/auxiliary-training-area?
                   :lipas.sports-site.properties/sport-specification
                   :lipas.sports-site.properties/active-space-width-m
                   :lipas.sports-site.properties/active-space-length-m
                   :lipas.sports-site.properties/mirror-wall?
                   :lipas.sports-site.properties/parkour-hall-equipment-and-structures
                   :lipas.sports-site.properties/ringette-boundary-markings?
                   :lipas.sports-site.properties/field-1-flexible-rink?
                   :lipas.sports-site.properties/field-2-flexible-rink?
                   :lipas.sports-site.properties/field-3-flexible-rink?
                   :lipas.sports-site.properties/pool-tables-count
                   :lipas.sports-site.properties/snooker-tables-count
                   :lipas.sports-site.properties/kaisa-tables-count
                   :lipas.sports-site.properties/pyramid-tables-count
                   :lipas.sports-site.properties/carom-tables-count
                   :lipas.sports-site.properties/total-billiard-tables-count
                   :lipas.sports-site.properties/boating-service-class
                   :lipas.sports-site.properties/free-use?
                   :lipas.sports-site.properties/school-use?
                   :lipas.sports-site.properties/year-round-use?
                   :lipas.sports-site.properties/lighting-info
                   :lipas.sports-site.properties/hs-point
                   :lipas.sports-site.properties/bike-orienteering?
                   :lipas.sports-site.properties/ski-orienteering?
                   :lipas.sports-site.properties/mobile-orienteering?
                   :lipas.sports-site.properties/sledding-hill?
                   :lipas.sports-site.properties/travel-mode-info
                   :lipas.sports-site.properties/travel-modes]))

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
  (into #{} (keys sports-site-types/all)))

(s/def :lipas.sports-site.type/size-category*
  (into #{} (keys ice-stadiums/size-categories)))

(s/def :lipas.sports-site.type/size-category
  (st/spec
   {:spec         :lipas.sports-site.type/size-category*
    :swagger/type "string"
    :swagger/enum (keys ice-stadiums/size-categories)}))

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
(s/def :lipas.energy-consumption/comment :lipas.sports-site/comment)

(s/def :lipas/energy-consumption
  (s/keys :opt-un [:lipas.energy-consumption/electricity-mwh
                   :lipas.energy-consumption/cold-mwh
                   :lipas.energy-consumption/heat-mwh
                   :lipas.energy-consumption/water-m3
                   :lipas.energy-consumption/contains-other-buildings?
                   :lipas.energy-consumption/operating-hours
                   :lipas.energy-consumption/comment]))

(s/def :lipas.energy-consumption/jan :lipas/energy-consumption)
(s/def :lipas.energy-consumption/feb :lipas/energy-consumption)
(s/def :lipas.energy-consumption/mar :lipas/energy-consumption)
(s/def :lipas.energy-consumption/apr :lipas/energy-consumption)
(s/def :lipas.energy-consumption/may :lipas/energy-consumption)
(s/def :lipas.energy-consumption/jun :lipas/energy-consumption)
(s/def :lipas.energy-consumption/jul :lipas/energy-consumption)
(s/def :lipas.energy-consumption/aug :lipas/energy-consumption)
(s/def :lipas.energy-consumption/sep :lipas/energy-consumption)
(s/def :lipas.energy-consumption/oct :lipas/energy-consumption)
(s/def :lipas.energy-consumption/nov :lipas/energy-consumption)
(s/def :lipas.energy-consumption/dec :lipas/energy-consumption)

(s/def :lipas/energy-consumption-monthly
  (s/keys :opt-un [:lipas.energy-consumption/jan
                   :lipas.energy-consumption/feb
                   :lipas.energy-consumption/mar
                   :lipas.energy-consumption/apr
                   :lipas.energy-consumption/may
                   :lipas.energy-consumption/jun
                   :lipas.energy-consumption/jul
                   :lipas.energy-consumption/aug
                   :lipas.energy-consumption/sep
                   :lipas.energy-consumption/oct
                   :lipas.energy-consumption/nov
                   :lipas.energy-consumption/dec]))

;; Visitors ;;

(s/def :lipas.visitors/total-count (int-in 0 1000000))      ; Users
(s/def :lipas.visitors/spectators-count (int-in 0 1000000)) ; Spectators

(s/def :lipas/visitors
  (s/keys :opt-un [:lipas.visitors/total-count
                   :lipas.visitors/spectators-count]))

(s/def :lipas.visitors/jan :lipas/visitors)
(s/def :lipas.visitors/feb :lipas/visitors)
(s/def :lipas.visitors/mar :lipas/visitors)
(s/def :lipas.visitors/apr :lipas/visitors)
(s/def :lipas.visitors/may :lipas/visitors)
(s/def :lipas.visitors/jun :lipas/visitors)
(s/def :lipas.visitors/jul :lipas/visitors)
(s/def :lipas.visitors/aug :lipas/visitors)
(s/def :lipas.visitors/sep :lipas/visitors)
(s/def :lipas.visitors/oct :lipas/visitors)
(s/def :lipas.visitors/nov :lipas/visitors)
(s/def :lipas.visitors/dec :lipas/visitors)

(s/def :lipas/visitors-monthly
  (s/keys :opt-un [:lipas.visitors/jan
                   :lipas.visitors/feb
                   :lipas.visitors/mar
                   :lipas.visitors/apr
                   :lipas.visitors/may
                   :lipas.visitors/jun
                   :lipas.visitors/jul
                   :lipas.visitors/aug
                   :lipas.visitors/sep
                   :lipas.visitors/oct
                   :lipas.visitors/nov
                   :lipas.visitors/dec]))

(s/def :lipas.sports-site.fields.field/type
  #{"football-field" "floorball-field"})

;; UUID just doesn't work with complex spec coercions
(s/def :lipas.sports-site.fields.field/field-id (str-in 36 36))
(s/def :lipas.sports-site.fields.field/name (str-in 2 100))
(s/def :lipas.sports-site.fields.field/length-m (number-in :min 0 :max (inc 200)))
(s/def :lipas.sports-site.fields.field/width-m (number-in :min 0 :max (inc 200)))
(s/def :lipas.sports-site.fields.field/height-m (number-in :mina 0 :max (inc 100)))
(s/def :lipas.sports-site.fields.field/surface-area-m2 (number-in :min 0 :max (inc 20000)))

(s/def :lipas.sports-site.fields/field
  (s/keys :req-un [:lipas.sports-site.fields.field/type
                   :lipas.sports-site.fields.field/field-id]
          :opt-un [:lipas.sports-site.fields.field/name
                   :lipas.sports-site.fields.field/length-m
                   :lipas.sports-site.fields.field/width-m
                   :lipas.sports-site.fields.field/height-m
                   :lipas.sports-site.fields.field/surface-area-m2]))

(s/def :lipas.sports-site.fields.floorball/type
  #{"floorball-field"})

(s/def :lipas.sports-site.fields.floorball/minimum-height-m ::real)

(s/def :lipas.sports-site.fields.floorball/surface-material
  (into #{} (keys materials/field-surface-materials)))

(s/def :lipas.sports-site.fields.floorball/surface-material-product
  (str-in 2 100))

(s/def :lipas.sports-site.fields.floorball/surface-material-color
  (str-in 2 100))

(s/def :lipas.sports-site.fields.floorball/floor-elasticity
  (into #{} (keys floorball/floor-elasticity)))

(s/def :lipas.properties/lighting-lux
  (s/int-in 0 3000))

(s/def :lipas.sports-site.fields.floorball/lighting-corner-1-1-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-corner-1-2-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-corner-2-1-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-corner-2-2-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-goal-1-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-goal-2-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-center-point-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/lighting-average-lux
  :lipas.properties/lighting-lux)

(s/def :lipas.sports-site.fields.floorball/rink-product
  (str-in 2 100))

(s/def :lipas.sports-site.fields.floorball/rink-color
  (str-in 2 100))

(s/def :lipas.sports-site.fields.floorball/scoreboard-visible-to-benches?
  boolean?)

(s/def :lipas.sports-site.fields.floorball/scoreboard-visible-to-officials?
  boolean?)

(s/def :lipas.sports-site.fields.floorball/stands-total-capacity-person
  (s/int-in 0 100000))

(s/def :lipas.sports-site.fields.floorball/seating-area-capacity-person
  (s/int-in 0 100000))

(s/def :lipas.sports-site.fields.floorball/standing-area-capacity-person
  (s/int-in 0 100000))

(s/def :lipas.sports-site.fields.floorball/accessible-seating-capacity-person
  (s/int-in 0 100000))

(s/def :lipas.sports-site.fields.floorball/audience-stand-access
  (into #{} (keys floorball/audience-stand-access)))

(s/def :lipas.sports-site.fields.floorball/field-accessible-without-strairs?
  boolean?)

(s/def :lipas.sports-site.fields.floorball/safety-area-end-1-m
  (number-in {:min 0 :max 10}))

(s/def :lipas.sports-site.fields.floorball/safety-area-end-2-m
  (number-in {:min 0 :max 10}))

(s/def :lipas.sports-site.fields.floorball/safety-area-side-1-m
  (number-in {:min 0 :max 10}))

(s/def :lipas.sports-site.fields.floorball/safety-area-side-2-m
  (number-in {:min 0 :max 10}))

(s/def :lipas.sports-site.fields/floorball
  (s/merge :lipas.sports-site.fields/field
           (s/keys
            :req-un [:lipas.sports-site.fields.floorball/type]
            :opt-un [:lipas.sports-site.fields.floorball/minimum-height-m
                     :lipas.sports-site.fields.floorball/surface-material
                     :lipas.sports-site.fields.floorball/floor-elasticity
                     :lipas.sports-site.fields.floorball/surface-material-product
                     :lipas.sports-site.fields.floorball/surface-material-color
                     :lipas.sports-site.fields.floorball/rink-product
                     :lipas.sports-site.fields.floorball/rink-color
                     :lipas.sports-site.fields.floorball/lighting-corner-1-1-lux
                     :lipas.sports-site.fields.floorball/lighting-corner-1-2-lux
                     :lipas.sports-site.fields.floorball/lighting-corner-2-1-lux
                     :lipas.sports-site.fields.floorball/lighting-corner-2-2-lux
                     :lipas.sports-site.fields.floorball/lighting-goal-1-lux
                     :lipas.sports-site.fields.floorball/lighting-goal-2-lux
                     :lipas.sports-site.fields.floorball/lighting-center-point-lux
                     :lipas.sports-site.fields.floorball/lighting-average-lux
                     :lipas.sports-site.fields.floorball/safety-area-end-1-m
                     :lipas.sports-site.fields.floorball/safety-area-end-2-m
                     :lipas.sports-site.fields.floorball/safety-area-side-1-m
                     :lipas.sports-site.fields.floorball/safety-area-side-2-m
                     :lipas.sports-site.fields.floorball/scoreboard-visible-to-benches?
                     :lipas.sports-site.fields.floorball/scoreboard-visible-to-officials?
                     :lipas.sports-site.fields.floorball/stands-total-capacity-person
                     :lipas.sports-site.fields.floorball/seating-area-capacity-person
                     :lipas.sports-site.fields.floorball/standing-area-capacity-person
                     :lipas.sports-site.fields.floorball/accessible-seating-capacity-person
                     :lipas.sports-site.fields.floorball/audience-stand-access
                     :lipas.sports-site.fields.floorball/field-accessible-without-strairs?])))

(s/def :lipas.sports-site.circumstances.floorball/available-goals-count
  (s/int-in 0 200))

(s/def :lipas.sports-site.circumstances.floorball/iff-certification-stickers-in-goals?
  boolean?)

(s/def :lipas.sports-site.circumstances.floorball/iff-certified-rink?
  boolean?)

(s/def :lipas.sports-site.circumstances.floorball/goal-shrinking-elements-count
  (s/int-in 0 100))

(s/def :lipas.sports-site.circumstances.floorball/corner-pieces-count
  (s/int-in 0 100))

(s/def :lipas.sports-site.circumstances/general-information
  (str-in 2 1000))

(s/def :lipas.sports-site.circumstances/locker-rooms-count
  (s/int-in 0 1000))

(s/def :lipas.sports-site.circumstances/teams-using
  (str-in 0 1024))

(s/def :lipas.sports-site.circumstances/storage-capacity-comment
  (str-in 0 2048))

(s/def :lipas.sports-site.circumstances/open-floor-space-width-m
  (number-in :min 0 :max (inc 200)))

(s/def :lipas.sports-site.circumstances/open-floor-space-length-m
  (number-in :min 0 :max (inc 200)))

(s/def :lipas.sports-site.circumstances/open-floor-space-area-m2
  (number-in :min 0 :max (inc 10000)))

(s/def :lipas.sports-site.locker-room/surface-area-m2
  (number-in :min 0 :max (inc 2000)))

(s/def :lipas.sports-site.locker-room/showers-count
  (s/int-in 0 100))

(s/def :lipas.sports-site.locker-room/toilets-count
  (s/int-in 0 100))

(s/def :lipas.sports-site/locker-room
  (s/keys :req-un []
          :opt-un [:lipas.sports-site.locker-room/surface-area-m2
                   :lipas.sports-site.locker-room/showers-count
                   :lipas.sports-site.locker-room/toilets-count]))

(s/def :lipas.sports-site/locker-rooms
  (s/coll-of :lipas.sports-site/locker-room
             :into []))

(s/def :lipas.sports-site.circumstances/saunas-count
  (s/int-in 0 100))

(s/def :lipas.sports-site.circumstances/separate-referee-locker-room?
  boolean?)

(s/def :lipas.sports-site.circumstances/doping-test-facilities?
  boolean?)

(s/def :lipas.sports-site.circumstances/locker-room-quality-comment
  (str-in 2 2048))

(s/def :lipas.sports-site.circumstances/defilbrillator?
  boolean?)

(s/def :lipas.sports-site.circumstances/stretcher?
  boolean?)

(s/def :lipas.sports-site.circumstances/first-aid-comment
  (str-in 2 2048))

(s/def :lipas.sports-site.circumstances/scoreboard-count
  (s/int-in 0 100))

(s/def :lipas.sports-site.circumstances/player-enterance
  (into #{} (keys floorball/player-entrance)))

(s/def :lipas.sports-site.circumstances/side-training-space?
  boolean?)

(s/def :lipas.sports-site.circumstances/gym?
  boolean?)

(s/def :lipas.sports-site.circumstances/audience-toilets-count
  (s/int-in 0 1000))

(s/def :lipas.sports-site.circumstances/vip-area?
  boolean?)

(s/def :lipas.sports-site.circumstances/vip-area-comment
  (str-in 2 2048))

(s/def :lipas.sports-site.circumstances/field-level-loading-doors?
  boolean?)

(s/def :lipas.sports-site.circumstances/loading-equipment-available?
  boolean?)

(s/def :lipas.sports-site.circumstances/detached-chairs-quantity
  (s/int-in 0 1000))

(s/def :lipas.sports-site.circumstances/detached-tables-quantity
  (s/int-in 0 1000))

(s/def :lipas.sports-site.circumstances/cafeteria-and-restaurant-capacity-person
  (s/int-in 0 10000))

(s/def :lipas.sports-site.circumstances/restaurateur-contact-info
  (str-in 2 2048))

(s/def :lipas.sports-site.circumstances/cafe-or-restaurant-has-exclusive-rights-for-products?
  boolean?)

(s/def :lipas.sports-site.circumstances/conference-space-quantity
  (s/int-in 0 100))

(s/def :lipas.sports-site.circumstances/conference-space-total-capacity-person
  (s/int-in 0 10000))

(s/def :lipas.sports-site.circumstances/press-conference-space?
  boolean?)

(s/def :lipas.sports-site.circumstances/ticket-sales-operator
  (str-in 2 100))

(s/def :lipas.sports-site.circumstances/bus-parking-capacity
  (number-in :min 0 :max (inc 1000)))

(s/def :lipas.sports-site.circumstances/car-parking-capacity
  (number-in :min 0 :max 10000))

(s/def :lipas.sports-site.circumstances/car-parking-economics-model
  (into #{} (keys floorball/car-parking-economics-model)))

(s/def :lipas.sports-site.circumstances/roof-trusses?
  boolean?)

(s/def :lipas.sports-site.circumstances/roof-trusses-capacity-kg
  (s/int-in 0 10000))

(s/def :lipas.sports-site.circumstances/roof-trusses-operation-model
  (into #{} (keys floorball/roof-trussess-operation-model)))

(s/def :lipas.sports-site.circumstances/speakers-aligned-towards-stands?
  boolean?)

(s/def :lipas.sports-site.circumstances/audio-mixer-available?
  boolean?)

(s/def :lipas.sports-site.circumstances/wireless-microphone-quantity
  (s/int-in 0 200))

(s/def :lipas.sports-site.circumstances/wired-microphone-quantity
  (s/int-in 0 200))

(s/def :lipas.sports-site.circumstances/camera-stands?
  boolean?)

(s/def :lipas.sports-site.circumstances/fixed-cameras?
  boolean?)

(s/def :lipas.sports-site.circumstances/broadcast-car-park?
  boolean?)

(s/def :lipas.sports-site.circumstances/wifi-available?
  boolean?)

(s/def :lipas.sports-site.circumstances/wifi-capacity-sufficient-for-streaming?
  boolean?)

(s/def :lipas.sports-site.circumstances/electrical-plan-available?
  boolean?)

(s/def :lipas.sports-site.circumstances/three-phase-electric-power?
  boolean?)

(s/def :lipas.sports-site.circumstances/led-screens-or-surfaces-for-ads?
  boolean?)

(s/def :lipas.sports-site.circumstances/audit-date
  :lipas/date)

(s/def :lipas.sports-site.circumstances/floorball
  (s/keys :req-un []
          :opt-un [:lipas.sports-site.circumstances/teams-using
                   :lipas.sports-site.circumstances/storage-capacity-comment
                   :lipas.sports-site.circumstances/open-floor-space-width-m
                   :lipas.sports-site.circumstances/open-floor-space-length-m
                   :lipas.sports-site.circumstances/open-floor-space-area-m2
                   :lipas.sports-site.circumstances.floorball/available-goals-count
                   :lipas.sports-site.circumstances.floorball/iff-certification-stickers-in-goals?
                   :lipas.sports-site.circumstances.floorball/iff-certified-rink?
                   :lipas.sports-site.circumstances.floorball/goal-shrinking-elements-count
                   :lipas.sports-site.circumstances.floorball/corner-pieces-count
                   :lipas.sports-site.circumstances/saunas-count
                   :lipas.sports-site.circumstances/separate-referee-locker-room?
                   :lipas.sports-site.circumstances/doping-test-facilities?
                   :lipas.sports-site.circumstances/locker-room-quality-comment
                   :lipas.sports-site.circumstances/defilbrillator?
                   :lipas.sports-site.circumstances/stretcher?
                   :lipas.sports-site.circumstances/first-aid-comment
                   :lipas.sports-site.circumstances/scoreboard-count
                   :lipas.sports-site.circumstances/player-enterance
                   :lipas.sports-site.circumstances/side-training-space?
                   :lipas.sports-site.circumstances/gym?
                   :lipas.sports-site.circumstances/audience-toilets-count
                   :lipas.sports-site.circumstances/vip-area?
                   :lipas.sports-site.circumstances/vip-area-comment
                   :lipas.sports-site.circumstances/field-level-loading-doors?
                   :lipas.sports-site.circumstances/loading-equipment-available?
                   :lipas.sports-site.circumstances/detached-chairs-quantity
                   :lipas.sports-site.circumstances/detached-tables-quantity
                   :lipas.sports-site.circumstances/cafeteria-and-restaurant-capacity-person
                   :lipas.sports-site.circumstances/restaurateur-contact-info
                   :lipas.sports-site.circumstances/cafe-or-restaurant-has-exclusive-rights-for-products?
                   :lipas.sports-site.circumstances/conference-space-quantity
                   :lipas.sports-site.circumstances/conference-space-total-capacity-person
                   :lipas.sports-site.circumstances/press-conference-space?
                   :lipas.sports-site.circumstances/ticket-sales-operator
                   :lipas.sports-site.circumstances/bus-parking-capacity
                   :lipas.sports-site.circumstances/car-parking-capacity
                   :lipas.sports-site.circumstances/car-parking-economics-model
                   :lipas.sports-site.circumstances/roof-trusses?
                   :lipas.sports-site.circumstances/roof-trusses-capacity-kg
                   :lipas.sports-site.circumstances/roof-trusses-operation-model
                   :lipas.sports-site.circumstances/speakers-aligned-towards-stands?
                   :lipas.sports-site.circumstances/audio-mixer-available?
                   :lipas.sports-site.circumstances/wireless-microphone-quantity
                   :lipas.sports-site.circumstances/wired-microphone-quantity
                   :lipas.sports-site.circumstances/camera-stands?
                   :lipas.sports-site.circumstances/fixed-cameras?
                   :lipas.sports-site.circumstances/broadcast-car-park?
                   :lipas.sports-site.circumstances/wifi-available?
                   :lipas.sports-site.circumstances/wifi-capacity-sufficient-for-streaming?
                   :lipas.sports-site.circumstances/electrical-plan-available?
                   :lipas.sports-site.circumstances/three-phase-electric-power?
                   :lipas.sports-site.circumstances/led-screens-or-surfaces-for-ads?
                   :lipas.sports-site.circumstances/audit-date
                   :lipas.sports-site.circumstances/general-information
                   :lipas.sports-site.circumstances/locker-rooms-count
                   :lipas.sports-site/audits]))

(s/def :lipas.sports-site/circumstances
  (s/or :circumstances/floorball :lipas.sports-site.circumstances/floorball))

(s/def :lipas.sports-site/fields
  (s/coll-of (s/or :field/generic :lipas.sports-site.fields/field
                   :field/floorball :lipas.sports-site.fields/floorball)
             :into []))

(s/def :lipas.sports-site.ptv/last-sync string?)
(s/def :lipas.sports-site.ptv/org-id string?)
(s/def :lipas.sports-site.ptv/sync-enabled boolean?)
(s/def :lipas.sports-site.ptv/delete-existing boolean?)
(s/def :lipas.sports-site.ptv/source-id string?)
(s/def :lipas.sports-site.ptv/publishing-status string?)
(s/def :lipas.sports-site.ptv/previous-type-code int?)

(s/def :lipas.ptv.summary/fi (str-in 0 150))
(s/def :lipas.ptv.summary/se (str-in 0 150))
(s/def :lipas.ptv.summary/en (str-in 0 150))
(s/def :lipas.sports-site.ptv/summary
  (s/keys :opt-un [:lipas.ptv.summary/fi
                   :lipas.ptv.summary/se
                   :lipas.ptv.summary/en]))

(s/def :lipas.ptv.description/fi string?)
(s/def :lipas.ptv.description/se string?)
(s/def :lipas.ptv.description/en string?)
(s/def :lipas.sports-site.ptv/description
  (s/keys :opt-un [:lipas.ptv.description/fi
                   :lipas.ptv.description/se
                   :lipas.ptv.description/en]))

(s/def :lipas.sports-site.ptv/service-channel-integration #{"lipas-managed" "manual"})
(s/def :lipas.sports-site.ptv/service-integration #{"lipas-managed" "manual"})
(s/def :lipas.sports-site.ptv/descriptions-integration #{"lipas-managed-ptv-fields" "lipas-managed-comment-field" "ptv-managed"})

(s/def :lipas.sports-site.ptv/service-ids (s/coll-of string?))
(s/def :lipas.sports-site.ptv/service-channel-ids (s/coll-of string?))
(s/def :lipas.sports-site.ptv/languages (s/coll-of string?))

(s/def :lipas.ptv.error/message string?)
(s/def :lipas.ptv.error/data (s/? map?))
(s/def :lipas.sports-site.ptv/error
  (s/keys :req-un [:lipas.ptv.error/message
                   :lipas.ptv.error/data]))

(s/def :lipas.sports-site/ptv
  (s/keys :req-un [:lipas.sports-site.ptv/org-id
                   :lipas.sports-site.ptv/sync-enabled

                   :lipas.sports-site.ptv/summary
                   :lipas.sports-site.ptv/description]
          :opt-un [;; Added on first successful sync
                   :lipas.sports-site.ptv/last-sync
                   :lipas.sports-site.ptv/previous-type-code
                   :lipas.sports-site.ptv/publishing-status
                   :lipas.sports-site.ptv/service-ids
                   :lipas.sports-site.ptv/languages

                   :lipas.sports-site.ptv/delete-existing

                   ;; Added on sync - removed when archived
                   :lipas.sports-site.ptv/source-id
                   :lipas.sports-site.ptv/service-channel-ids

                   ;; Previously used keys, not used now
                   :lipas.sports-site.ptv/service-channel-integration
                   :lipas.sports-site.ptv/service-integration
                   :lipas.sports-site.ptv/descriptions-integration

                   ;; Only added on sync error - removed when success
                   :lipas.sports-site.ptv/error]))

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
                   :lipas.sports-site/name-localized
                   :lipas.sports-site/marketing-name
                   :lipas.sports-site/phone-number
                   :lipas.sports-site/www
                   :lipas.sports-site/reservations-link
                   :lipas.sports-site/email
                   :lipas.sports-site/construction-year
                   :lipas.sports-site/renovation-years
                   :lipas.sports-site/comment
                   :lipas/energy-consumption-monthly
                   :lipas/energy-consumption
                   :lipas/visitors
                   :lipas/visitors-monthly
                   :lipas.sports-site/properties
                   :lipas.sports-site/fields
                   :lipas.sports-site/locker-rooms
                   :lipas.sports-site/circumstances
                   :lipas.sports-site/ptv]))

(s/def :lipas/sports-site
  (s/merge
   :lipas/new-sports-site
   (s/keys :req-un [:lipas.sports-site/lipas-id])))

(s/def :lipas/new-or-existing-sports-site
  (s/merge
   :lipas/new-sports-site
   (s/keys :opt-un [:lipas.sports-site/lipas-id])))

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
(s/def :lipas.building/staff-count (int-in 0 (inc 1000)))
(s/def :lipas.building/seating-capacity (int-in 0 (inc 50000)))
(s/def :lipas.building/heat-source (into #{} (keys swimming-pools/heat-sources)))
(s/def :lipas.building/heat-sources
  (s/coll-of :lipas.building/heat-source
             :min-count 0
             :max-count (count swimming-pools/heat-sources)
             :distinct true
             :into []))

(s/def :lipas.building/ventilation-units-count (int-in 0 (inc 100)))

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
(s/def :lipas.ice-stadium.rink/area-m2 (number-in :min 0 :max 5000))

(s/def :lipas.ice-stadium/rink
  (s/keys :req-un []
          :opt-un [:lipas.ice-stadium.rink/width-m
                   :lipas.ice-stadium.rink/length-m
                   :lipas.ice-stadium.rink/area-m2]))

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
  (int-in 0 (inc 10000)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant
  (into #{} (keys ice-stadiums/refrigerants)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-amount-kg
  (int-in 0 (inc 10000)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-solution
  (into #{} (keys ice-stadiums/refrigerant-solutions)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l
  (int-in 0 (inc 30000)))

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

(s/def :lipas.ice-stadium.conditions/air-humidity-min (int-in 0 (inc 100)))
(s/def :lipas.ice-stadium.conditions/air-humidity-max (int-in 0 (inc 100)))
(s/def :lipas.ice-stadium.conditions/stand-temperature-c (int-in -10 (inc 50)))
(s/def :lipas.ice-stadium.conditions/daily-open-hours :lipas/hours-in-day-with-fractions)
(s/def :lipas.ice-stadium.conditions/open-months (int-in 0 (inc 12)))

(s/def :lipas.ice-stadium.conditions/ice-surface-temperature-c
  (int-in -10 (inc 0)))

(s/def :lipas.ice-stadium.conditions/skating-area-temperature-c
  (int-in -15 (inc 20)))

(s/def :lipas.ice-stadium.conditions/daily-maintenances-week-days
  (int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/daily-maintenances-weekends
  (int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/weekly-maintenances (int-in 0 (inc 100)))

(s/def :lipas.ice-stadium.conditions/average-water-consumption-l
  (int-in 0 (inc 1000)))

(s/def :lipas.ice-stadium.conditions/maintenance-water-temperature-c
  (int-in 0 100))

(s/def :lipas.ice-stadium.conditions/ice-resurfacer-fuel
  (into #{} (keys ice-stadiums/ice-resurfacer-fuels)))

(s/def :lipas.ice-stadium.conditions/ice-average-thickness-mm
  (int-in 0 (inc 150)))

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
  (int-in 0 (inc 100)))

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

(s/def :lipas.swimming-pool.pool/accessibility-feature
  (into #{} (keys swimming-pools/accessibility)))

(s/def :lipas.swimming-pool.pool/accessibility
  (s/coll-of :lipas.swimming-pool.pool/accessibility-feature
             :min-count 0
             :max-count (count swimming-pools/accessibility)
             :distinct true
             :into []))

(s/def :lipas.swimming-pool/pool
  (s/keys :opt-un [:lipas.swimming-pool.pool/type
                   :lipas.swimming-pool.pool/outdoor-pool?
                   :lipas.swimming-pool.pool/temperature-c
                   :lipas.swimming-pool.pool/volume-m3
                   :lipas.swimming-pool.pool/area-m2
                   :lipas.swimming-pool.pool/length-m
                   :lipas.swimming-pool.pool/width-m
                   :lipas.swimming-pool.pool/depth-min-m
                   :lipas.swimming-pool.pool/depth-max-m
                   :lipas.swimming-pool.pool/accessibility]))

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

(s/def :lipas.swimming-pool.facilities/platforms-1m-count (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-3m-count (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-5m-count (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-7.5m-count (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-10m-count (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/hydro-massage-spots-count
  (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count
  (int-in 0 100))
(s/def :lipas.swimming-pool.facilities/kiosk? boolean?)
(s/def :lipas.swimming-pool.facilities/gym? boolean?)

;; Showers and lockers ;;
(s/def :lipas.swimming-pool.facilities/showers-men-count (int-in 0 200))
(s/def :lipas.swimming-pool.facilities/showers-women-count (int-in 0 200))
(s/def :lipas.swimming-pool.facilities/showers-unisex-count (int-in 0 200))
(s/def :lipas.swimming-pool.facilities/lockers-men-count (int-in 0 1000))
(s/def :lipas.swimming-pool.facilities/lockers-women-count (int-in 0 1000))
(s/def :lipas.swimming-pool.facilities/lockers-unisex-count (int-in 0 1000))

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

(s/def :lipas.swimming-pool.conditions/open-days-in-year (int-in 0 (inc 365)))
(s/def :lipas.swimming-pool.conditions/daily-open-hours :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-mon :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-tue :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-wed :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-thu :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-fri :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-sat :lipas/hours-in-day-with-fractions)
(s/def :lipas.swimming-pool.conditions/open-hours-sun :lipas/hours-in-day-with-fractions)

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

(s/def :lipas.sports-site-like/type
  (s/keys :req-un [:lipas.sport-site.type/type-code]))

(s/def :lipas.sports-site-like/location
  (s/keys :req-un [:lipas.location/city]))

(s/def :lipas/sports-site-like
  (s/keys :req-un [:lipas.sports-site-like/type
                   :lipas.sports-site-like/location]
          :opt-un [:lipas.sports-site/lipas-id]))

;;; Floorball ;;;

(s/def :lipas.floorball.type/type-code #{2240})

(s/def :lipas.floorball/type
  (s/merge
   :lipas.sports-site/type
   (s/keys :req-un [:lipas.floorball.type/type-code])))

;;; Football ;;;

(s/def :lipas.football.type/type-code #{2240})

(s/def :lipas.football/type
  (s/merge
   :lipas.sports-site/type
   (s/keys :req-un [:lipas.football.type/type-code])))

;;; Legacy API ;;;

(s/def :lipas.legacy.api/closeToLon (int-in -180 180)#_:lipas.location.coordinates/lon)
(s/def :lipas.legacy.api/closeToLat (int-in -180 180)#_:lipas.location.coordinates/lat)
(s/def :lipas.legacy.api/modifiedAfter :lipas/timestamp)
(def legacy-fields #{"properties"
                     "schoolUse"
                     "email"
                     "type.name"
                     "reservationsLink"
                     "location.sportsPlaces"
                     "renovationYears"
                     "admin"
                     "location.coordinates.tm35fin"
                     "www"
                     "location.geometries"
                     "name"
                     "type.typeCode"
                     "location.locationId"
                     "constructionYear"
                     "freeUse"
                     "location.city.name"
                     "lastModified"
                     "marketingName"
                     "location.postalCode"
                     "location.postalOffice"
                     "location.city.cityCode"
                     "phoneNumber"
                     "location.neighborhood"
                     "owner"
                     "location.coordinates.wgs84"
                     "location.address"})
(s/def :lipas.legacy.api/field* legacy-fields)
(s/def :lipas.legacy.api/field 
  (st/spec
   {:spec         :lipas.legacy.api/field*
    :swagger/type "string"
    :swagger/enum legacy-fields}))
(s/def :lipas.legacy.api/fields
  (s/coll-of :lipas.legacy.api/field
             :min-count 0
             :distinct true
             :into []))
(s/def :lipas.legacy.api/retkikartta boolean?)
(s/def :lipas.legacy.api/closeToMatch #{"start-point" "any-point"})
(s/def :lipas.legacy.api/page (int-in 0 999999))
(s/def :lipas.legacy.api/closeToDistanceKm (number-in {:min 0 :max 99999}))
(s/def :lipas.legacy.api/harrastuspassi boolean?)
(s/def :lipas.legacy.api/pageSize (int-in 0 1000))
(s/def :lipas.legacy.api/typeCodes (s/coll-of :lipas.sports-site.type/type-code
                                              :distinct true
                                              :min-count 0
                                              :into []))
(s/def :lipas.legacy.api/cityCodes [:lipas.location.city/city-code])
(s/def :lipas.legacy.api/searchString (str-in 3 100))
(s/def :lipas.legacy.api/since :lipas/timestamp)
(s/def :lipas.legacy.api/search-params
  (s/keys :opt-un [:lipas.legacy.api/closeToLon
                   :lipas.legacy.api/closeToLat
                   :lipas.api/lang
                   :lipas.legacy.api/modifiedAfter
                   :lipas.legacy.api/fields
                   :lipas.legacy.api/retkikartta
                   :lipas.legacy.api/closeToMatch
                   :lipas.legacy.api/page
                   :lipas.legacy.api/closeToDistanceKm
                   :lipas.legacy.api/harrastuspassi
                   :lipas.legacy.api/pageSize
                   :lipas.legacy.api/typeCodes
                   :lipas.legacy.api/cityCodes
                   :lipas.legacy.api/searchString]))

;;; HTTP-API ;;;

(s/def :lipas.api/revs #{"latest" "yearly"})
(s/def :lipas.api/lang #{"fi" "en" "se"})
(s/def :lipas.api/draft boolean?)
(s/def :lipas.api/query-params
  (s/keys :opt-un [:lipas.api/revs
                   :lipas.api/lang]))

(s/def :lipas.api.get-sports-sites-by-type-code/lang #{"fi" "en" "se" "all"})
(s/def :lipas.api.get-sports-sites-by-type-code/query-params
  (s/keys :opt-un [:lipas.api.get-sports-sites-by-type-code/lang]))

(s/def :lipas.api.get-sports-site/query-params
  :lipas.api.get-sports-sites-by-type-code/query-params)

(s/def :lipas.report/field (into #{} (keys reports/fields)))

(s/def :lipas.api.sports-site-report.req/search-query map?)
(s/def :lipas.api.sports-site-report.req/fields
  (s/coll-of :lipas.report/field
             :distinct true
             :min-count 0
             :max-count (count (keys reports/fields))
             :into []))

(s/def :lipas.api.energy-report.req/year
  (int-in 2000 (inc utils/this-year)))

(s/def :lipas.api.energy-report/req
  (s/keys :req-un [:lipas.sports-site.type/type-code
                   :lipas.api.energy-report.req/year]))

(s/def :lipas.api.sports-sites-report/format #{"xlsx" "geojson" "csv"})

(s/def :lipas.api.sports-site-report/req
  (s/keys :req-un [:lipas.api.sports-site-report.req/search-query
                   :lipas.api.sports-site-report.req/fields
                   :lipas/locale]
          :opt-un [:lipas.api.sports-sites-report/format]))

(s/def :lipas.api.report.req/city-codes
  (s/coll-of :lipas.location.city/city-code
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.api.report.req/type-codes
  (s/coll-of :lipas.sports-site.type/type-code
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.api.finance-report.req/flat? boolean?)
(s/def :lipas.api.finance-report.req/unit (keys reports/stats-units))
(s/def :lipas.api.finance-report.req/city-service (keys reports/city-services))
(s/def :lipas.api.finance-report.req/years
  (s/coll-of (int-in 2000 utils/this-year) :distinct true :into []))

(s/def :lipas.api.finance-report/req
  (s/keys :req-un [:lipas.api.report.req/city-codes]
          :opt-un [:lipas.api.finance-report.req/flat?
                   :lipas.api.finance-report.req/years
                   :lipas.api.finance-report.req/unit
                   :lipas.api.finance-report.req/city-service]))

(s/def :lipas.api.m2-per-capita-report/req
  (s/keys :opt-un [:lipas.api.report.req/city-codes
                   :lipas.api.report.req/type-codes]))

(s/def :lipas.magic-link/email-variant #{"lipas" "portal"})
(s/def :lipas.magic-link/login-url
  (s/or :local #(str/starts-with? % "https://localhost")
        :dev   #(str/starts-with? % "https://lipas-dev.cc.jyu.fi")
        :prod1 #(str/starts-with? % "https://uimahallit.lipas.fi")
        :prod2 #(str/starts-with? % "https://jaahallit.lipas.fi")
        :prod3 #(str/starts-with? % "https://liikuntapaikat.lipas.fi")
        :prod4 #(str/starts-with? % "https://www.lipas.fi")
        :prod5 #(str/starts-with? % "https://lipas.fi")))


;;; Diversity index calculation

(s/def :lipas.diversity.settings.category/name (str-in 1 128))
(s/def :lipas.diversity.settings.category/factor ::real)
(s/def :lipas.diversity.settings.category/type-codes
  (s/coll-of :lipas.sports-site.type/type-code
             :min-count 1
             :distinct true
             :into []))

(s/def :lipas.diversity.settings/category
  (s/keys :req-un [:lipas.diversity.settings.category/name
                   :lipas.diversity.settings.category/factor
                   :lipas.diversity.settings.category/type-codes]))

(s/def :lipas.diversity.settings.categories/name (str-in 1 128))
(s/def :lipas.diversity.settings.categories/categories
  (s/coll-of :lipas.diversity.settings/category
             :min-count 1
             :distinct true
             :into []))

(s/def :lipas.diversity.settings/category-preset
  (s/keys :req-un [:lipas.diversity.settings.categories/name
                   :lipas.diversity.settings.categories/categories]))

(s/def :lipas.diversity.settings/category-presets
  (s/coll-of :lipas.diversity.settings/category-preset))

(s/def :lipas.diversity.settings/analysis-area-fcoll ::geojson/feature-collection)
(s/def :lipas.diversity.settings/analysis-radius-km ::real)
(s/def :lipas.diversity.settings/max-distance-m ::real)
(s/def :lipas.diversity.settings/distance-mode #{"euclid" "route"})

(s/def :lipas.api.diversity-indices/req
  (s/keys :req-un [:lipas.diversity.settings.categories/categories
                   :lipas.diversity.settings/analysis-area-fcoll]
          :opt-un [:lipas.diversity.settings/analysis-radius-km
                   :lipas.diversity.settings/max-distance-m
                   :lipas.diversity.settings/distance-mode]))

;;; Feedback

(s/def :lipas.feedback/type (into #{} (keys feedback/types)))
(s/def :lipas.feedback/text (str-in 2 10000))
(s/def :lipas.feedback/sender :lipas/email)

(s/def :lipas.feedback/payload
  (s/keys :req [:lipas.feedback/type
                :lipas.feedback/text]
          :opt [:lipas.feedback/sender]))

;;; Check sports-site name
(s/def :lipas.api.check-sports-site-name/payload
  (s/keys :req-un [:lipas.sports-site/lipas-id
                   :lipas.sports-site/name]))

(s/def :lipas.api.find-fields/field-types
  (s/coll-of :lipas.sports-site.fields.field/type
             :min-count 0
             :max-count 100
             :distinct true
             :into []))

;; Find fields
(s/def :lipas.api.find-fields/payload
  (s/keys :req-un [:lipas.api.find-fields/field-types]))

;; create-upload-url
(s/def :lipas.api.create-upload-url/content-type*
  #{"png" "jpeg" "webp"})

(s/def :lipas.api.create-upload-url/extension
  (st/spec
   {:spec         :lipas.api.create-upload-url/content-type*
    :type         :string
    :swagger/type "enum"}))

(s/def :lipas.api.create-upload-url/payload
  (s/keys :req-un [:lipas.sports-site/lipas-id
                   :lipas.api.create-upload-url/extension]))

;;; Location of Interest (loi) ;;;

(defn uuid-gen []
  (gen/fmap
   (fn [uuid]
     (str uuid))
   (gen/uuid)))

(s/def :lipas.loi/id
  (st/spec {:spec (str-in 36 36)
            :gen uuid-gen
            :swagger/type "string"}))

#_(s/def :lipas.loi/created-at :lipas/timestamp)
(s/def :lipas.loi/event-date :lipas/timestamp)

(s/def :lipas.loi/status
  (st/spec {:spec         (into #{} (keys status/statuses))
            :swagger/type "string"
            :swagger/enum (keys status/statuses)}))

(s/def :lipas.loi/loi-category
  (st/spec {:spec         (into #{} (keys loi/categories))
            :swagger/type "string"
            :swagger/enum (keys loi/categories)}))

(let [vs (->> loi/types vals (map :value))]
  (s/def :lipas.loi/loi-type
    (st/spec {:spec (into #{} vs)
              :swagger/type "string"
              :swagger/enum vs})))

;; NOTE: generator supports only point features atm
(s/def :lipas.loi/geometries (s/with-gen
                               ::geojson/feature-collection
                               lipas-point-feature-gen))

(s/def :lipas.loi/document
  (s/keys :req-un [:lipas.loi/event-date
                   :lipas.loi/status
                   :lipas.loi/loi-category
                   :lipas.loi/loi-type
                   :lipas.loi/geometries]
          :opt-un [:lipas.loi/id]))

(s/def :lipas.loi/documents
  (s/coll-of :lipas.loi/document))


;; LOI search API
(s/def :lipas.api.search-lois.payload/distance ::real)

(s/def :lipas.api.search-lois.payload/loi-statuses
  (s/coll-of :lipas.loi/status
             :distinct true
             :into []))

(s/def :lipas.api.search-lois.payload/location
  (s/keys :req-un [:lipas.location.coordinates/lon
                   :lipas.location.coordinates/lat
                   :lipas.api.search-lois.payload/distance]))

(s/def :lipas.api.search-lois/payload
  (s/keys :opt-un [:lipas.api.search-lois.payload/loi-statuses
                   :lipas.api.search-lois.payload/location]))


;;; Calc stats API

(s/def :lipas.city.population/year (into #{} (range 2000 (inc 2022))))
(s/def :lipas.stats.sports-sites/grouping #{"location.city.city-code"
                                            "type.type-code"})


(s/def :lipas.api.calculate-stats/payload
  (s/keys :req-un [:lipas.city.population/year]
          :opt-un [:lipas.api.report.req/city-codes
                   :lipas.api.report.req/type-codes
                   :lipas.stats.sports-sites/grouping]))


(comment
  (s/valid? :lipas.api.search-lois/payload {:loi-statuses ["active" "planned"]
                                            :location {:lon 25.48347583491476
                                                       :lat 62.0546268484493
                                                       :distance 100}})
  )


(comment
  (stp/parse-spec :lipas.loi/document)
  (stp/parse-spec :lipas/timestamp)
  (stp/parse-spec :lipas.sports-site/lipas-id)
  (stp/parse-spec :lipas.user/permissions)
  (stp/parse-spec :lipas.user.permissions/roles))
