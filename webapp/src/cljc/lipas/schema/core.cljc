(ns lipas.schema.core
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [hiposfer.geojson.specs :as geojson]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.ice-stadiums :as ice-stadiums]
            [lipas.data.materials :as materials]
            [lipas.data.owners :as owners]
            [lipas.data.sports-sites :as sports-sites]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.data.types :as sports-site-types]))

(def this-year #?(:cljs (.getFullYear (js/Date.))
                  :clj  (.getYear (java.time.LocalDate/now))))

(defn str-btw [min max]
  (s/and string? #(<= min (count %) max)))

;; Sports site

(s/def ::name (str-btw 2 100))
(s/def ::marketing-name (str-btw 2 100))

(s/def ::owner (into #{} (keys owners/all)))
(s/def ::admin (into #{} (keys admins/all)))

(s/def ::phone-number string?)
(s/def ::www string?)

(s/def ::sports-site-type (into #{} (map :type-code) sports-site-types/all))

;; Location

(s/def ::address string?)

(def postal-code-regex #"[0-9]{5}")
(comment (re-matches postal-code-regex "00010"))

(s/def ::postal-code-type (s/and string? #(re-matches postal-code-regex %)))

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

(s/def ::postal-code (s/with-gen
                       ::postal-code-type
                       postal-code-gen))

(s/def ::postal-office (str-btw 0 50))

(s/def ::city-code (into #{} (map :city-code) cities/active))
(s/def ::neighborhood (str-btw 1 100))

(s/def ::relevant-year (s/int-in 1800 (inc this-year)))

(defn gen-str [min max]
  (gen/fmap #(apply str %)
            (gen/vector (gen/char-alpha) (+ min (rand-int max)))))

(defn email-gen []
  "Function that returns a Generator for email addresses"
  (gen/fmap
   (fn [[name host tld]]
     (str name "@" host "." tld))
   (gen/tuple
    (gen-str 1 15)
    (gen-str 1 15)
    (gen-str 2 63))))

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(comment (gen/generate (s/gen ::email)))
(comment (gen/generate (gen/vector (gen/char-alpha 10))))
(comment (gen/generate (gen-str 1 5)))
(comment (s/conform ::email-type "kissa@koira.fi"))
(s/def ::email-type (s/and string? #(re-matches email-regex %)))
(s/def ::email (s/with-gen
                 ::email-type
                 email-gen))

;;; User ;;;

(s/def ::firstname (str-btw 1 128))
(s/def ::lastname (str-btw 1 128))
(s/def ::username (str-btw 1 128))
(s/def ::password (str-btw 6 128))

(s/def ::login (s/or :username ::username
                     :email ::email))

(s/def ::user-data (s/keys :req-un [::firstname
                                    ::lastname]))
(s/def ::permissions map?)
(s/def ::user (s/keys :req-un [::email
                               ::username
                               ::password
                               ::user-data]
                      :opt-un [::permissions]))

;;; General ;;;

(comment (s/valid? ::construction-year 2018))
(s/def ::construction-year ::relevant-year)

(comment (s/valid? ::material :concrete))
(comment (s/valid? ::material :kebab))
(s/def ::material (into #{} (keys materials/all)))

;;; Building ;;;

(s/def ::main-designers string?)
(s/def ::total-surface-area-m2 (s/int-in 100 (inc 50000)))
(s/def ::total-volume-m3 (s/int-in 100 (inc 200000)))
(s/def ::pool-room-total-area-m2 (s/int-in 100 (inc 10000)))
(s/def ::total-water-area-m2 (s/int-in 100 (inc 10000)))
(s/def ::heat-sections? boolean?)
(s/def ::piled? boolean?)
(s/def ::main-construction-materials (s/coll-of ::material))
(s/def ::supporting-structures (s/coll-of ::material))
(s/def ::ceiling-structures (s/coll-of ::material))
(s/def ::staff-count (s/int-in 0 (inc 1000)))
(s/def ::seating-capacity (s/int-in 0 (inc 10000)))
(s/def ::heat-source (into #{} (keys swimming-pools/heat-sources)))
(s/def ::ventilation-units-count (s/int-in 0 (inc 100)))
(s/def ::pool-room-total-area-m2 (s/int-in 0 (inc 5000)))
(s/def ::total-water-area-m2 (s/int-in 0 (inc 5000)))

(comment (s/valid? ::main-construction-materials [:concrete :brick]))
(comment (s/valid? ::ventilation-units-count 100))

(s/def ::building (s/keys :opt-un [::construction-year
                                   ::main-designers
                                   ::total-surface-area-m2
                                   ::total-volume-m3
                                   ::pool-room-total-area-m2
                                   ::total-water-area-m2
                                   ::heat-sections?
                                   ::main-construction-materials
                                   ::piled?
                                   ::supporting-structures
                                   ::ceiling-description
                                   ::staff-count
                                   ::seating-capacity
                                   ::heat-source
                                   ::ventilation-units-count]))

(comment (s/valid? ::building {:construction-year 1995
                               :main-designer "Tipokatti"}))

;;; Renovations ;;;

(s/def ::year ::relevant-year)
(s/def ::comment string?)

(s/def ::renovation (s/keys :req-un [::year]
                            :opt-un [::comment
                                     ::main-designers]))

;;; Water treatment ;;;

(s/def ::ozonation boolean?)
(s/def ::uv-treatment boolean?)
(s/def ::activated-carbon boolean?)

(s/def ::filtering-method (into #{} (keys swimming-pools/filtering-methods)))
(comment (s/valid? ::filtering-method :coal))

;;; Pools ;;;

(comment (s/valid? ::pool-type :therapy-pool))
(s/def ::pool-type (into #{} (keys swimming-pools/pool-types)))
(s/def ::pool-temperature-c (s/int-in 0 50))
(s/def ::pool-volume-m3 (s/int-in 0 5000))
(s/def ::pool-area-m2 (s/int-in 0 2000))
(s/def ::pool-length-m (s/double-in :min 0 :max 100))
(s/def ::pool-width-m (s/double-in :min 0 :max 100))
(s/def ::pool-depth-min-m (s/double-in :min 0 :max 10))
(s/def ::pool-depth-max-m (s/double-in :min 0 :max 10))

;;; Other services ;;;

(s/def ::platforms-1m-count (s/int-in 0 100))
(s/def ::platforms-3m-count (s/int-in 0 100))
(s/def ::platforms-5m-count (s/int-in 0 100))
(s/def ::platforms-7.5m-count (s/int-in 0 100))
(s/def ::platforms-10m-count (s/int-in 0 100))
(s/def ::hydro-massage-spots-count (s/int-in 0 100))
(s/def ::hydro-neck-massage-spots-count (s/int-in 0 100))
(s/def ::kiosk? boolean?)

;;; Showers and lockers ;;;

(s/def ::showers-men-count (s/int-in 0 200))
(s/def ::showers-women-count (s/int-in 0 200))
(s/def ::lockers-men-count (s/int-in 0 1000))
(s/def ::lockers-women-count (s/int-in 0 1000))

;;; Saunas ;;;

(s/def ::sauna-type (into #{} (keys swimming-pools/sauna-types)))
(s/def ::men? boolean?)
(s/def ::women? boolean?)

;;; Slides ;;;

(s/def ::slide-structure (into #{} (keys materials/slide-structures)))
(s/def ::slide-length-m (s/int-in 0 200))

;;; Ice Rinks ;;;

(s/def ::ice-rink-category #{:small
                             :competition
                             :large})

;;; Rinks ;;;

(s/def ::rink-length-m (s/int-in 0 100))
(s/def ::rink-width-m (s/int-in 0 100))

;;; Refrigeration ;;;

(s/def ::power-kw (s/int-in 0 (inc 10000)))
(s/def ::refrigerant (into #{} (keys ice-stadiums/refrigerants)))
(s/def ::refrigerant-amount-kg (s/int-in 0 (inc 10000)))
(s/def ::refrigerant-solution (into #{} (keys ice-stadiums/refrigerant-solutions)))
(s/def ::refrigerant-solution-amount-l (s/int-in 0 (inc 30000)))

;;; Conditions ;;;

(s/def ::air-humidity-percent (s/int-in 50 (inc 70)))
(s/def ::ice-surface-temperature-c (s/int-in -6 (inc -3)))
(s/def ::skating-area-temperature-c (s/int-in 5 (inc 12)))
(s/def ::stand-temperature-c (s/int-in 0 (inc 50)))
(s/def ::daily-open-hours (s/int-in 0 (inc 24)))
(s/def ::open-months (s/int-in 0 (inc 12)))

;;; Ventilation ;;;

(s/def ::heat-recovery-thermal-efficiency-percent (s/int-in 0 (inc 100)))
(s/def ::heat-recovery-type (into #{} (keys ice-stadiums/heat-recovery-types)))
(s/def ::dryer-type (into #{} (keys ice-stadiums/dryer-types)))
(s/def ::dryer-duty-type (into #{} (keys ice-stadiums/dryer-duty-types)))
(s/def ::heat-pump-type (into #{} (keys ice-stadiums/heat-pump-types)))

;;; Ice maintenance ;;;

(s/def ::daily-maintenance-count-week-days (s/int-in 0 (inc 50)))
(s/def ::daily-maintenance-count-weekends (s/int-in 0 (inc 50)))
(s/def ::average-water-consumption-l (s/int-in 0 (inc 1000)))
(s/def ::maintenance-water-temperature-c (s/int-in 0 100))
(s/def ::ice-average-thickness-mm (s/int-in 0 (inc 150)))

;;; Energy consumption ;;;

;; Note: in cljs (type 1e7) => Number (implicitly int)
;;       in clj  (type 1e7) => Double
;;
;; So scientific notation shouln't be used because it would yield
;; non-deterministic results between platforms.
(comment (s/valid? ::electricity-mwh 1e4))
(comment (s/valid? ::electricity-mwh 0))
(comment (s/valid? ::electricity-mwh (dec 1e4))) ; works in clj but not in cljs
(comment (s/valid? ::electricity-mwh 1795))
(s/def ::electricity-mwh (s/int-in 0 10000))
(s/def ::heat-mwh (s/int-in 0 10000))
(s/def ::cold-mwh (s/int-in 0 100000)) ; TODO figure out realistic limits
(s/def ::water-m3 (s/int-in 0 100000))

;; Visitors
(s/def ::visitors-total-count (s/int-in 0 1000000))

(defn timestamp
  "Returns current timestamp in \"2018-07-11T09:38:06.370Z\" format.
  Always UTC."
  []
  #?(:cljs (.toISOString (js/Date.))
     :clj  (.toString (java.time.Instant/now))))

;; https://stackoverflow.com/questions/3143070/javascript-regex-iso-datetime
(def timestamp-regex
  #"\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+([+-][0-2]\d:[0-5]\d|Z)")

(defn zero-left-pad
  [s len]
  (let [format-fn #?(:clj format
                     :cljs gstring/format)]
    (format-fn (str "%0" len "d") s)))

(defn timestamp-gen []
  (gen/fmap
   (fn [[yyyy MM dd hh mm ss ms]]
     (let [MM (zero-left-pad MM 2)
           dd (zero-left-pad dd 2)
           hh (zero-left-pad hh 2)
           mm (zero-left-pad mm 2)
           ss (zero-left-pad ss 2)]
       (str yyyy "-" MM "-" dd "T" hh ":" mm ":" ss "." ms "Z")))
   (gen/tuple
    (s/gen (s/int-in 1900 2019))
    (s/gen (s/int-in 1 (inc 12)))
    (s/gen (s/int-in 1 (inc 28)))
    (s/gen (s/int-in 1 (inc 23)))
    (s/gen (s/int-in 0 (inc 59)))
    (s/gen (s/int-in 0 (inc 59)))
    (s/gen (s/int-in 0 (inc 999))))))

(s/def :lipas/timestamp-type (s/and string? #(re-matches timestamp-regex %)))
(s/def :lipas/timestamp (s/with-gen :lipas/timestamp-type timestamp-gen))

(s/def :lipas/email-type (s/nilable (s/and string? #(re-matches email-regex %))))
(s/def :lipas/email (s/with-gen :lipas/email-type email-gen))

;;; User ;;;

(s/def :lipas.user/firstname (str-btw 1 128))
(s/def :lipas.user/lastname (str-btw 1 128))
(s/def :lipas.user/username (str-btw 1 128))
(s/def :lipas.user/password (str-btw 6 128))
(s/def :lipas.user/email :lipas/email)

(s/def :lipas.user/login (s/or :username :lipas.user/username
                               :email :lipas.user/email))

(s/def :lipas.user/user-data (s/keys :req-un [:lipas.user/firstname
                                    :lipas.user/lastname]))

(s/def :lipas.user/permissions map?) ;; TODO

(s/def :lipas.user/user (s/keys :req-un [:lipas.user/email
                               :lipas.user/username
                               :lipas.user/password
                               :lipas.user/user-data]
                      :opt-un [:lipas.user/permissions]))

;;; Location ;;;

(s/def :lipas.location/address (str-btw 1 200))


(s/def :lipas.location/postal-code-type
  (s/and string? #(re-matches postal-code-regex %)))

(s/def :lipas.location/postal-code (s/with-gen
                       :lipas.location/postal-code-type
                       postal-code-gen))

(s/def :lipas.location/postal-office (str-btw 0 50))

(s/def :lipas.location.city/city-code (into #{} (map :city-code) cities/active))
(s/def :lipas.location.city/neighborhood (str-btw 1 100))

(s/def :lipas.location/city
  (s/keys :req-un [:lipas.location.city/city-code]
          :opt-un [:lipas.location.city/neighborhood]))

;; TODO maybe narrow down coords to Finland extent?
(s/def :lipas.location.coordinates/lat (s/double-in :min -90.0
                                                    :max 90.0
                                                    :NaN? false
                                                    :infinite? false))

(s/def :lipas.location.coordinates/lon (s/double-in :min -180.0
                                                    :max 180.0
                                                    :NaN? false
                                                    :infinite? false))

(defn lipas-point-feature-gen []
  (gen/fmap
   (fn [[lon lat]]
     {:type "FeatureCollection"
      :features
      [{:type "Feature"
        :geometry
        {:type "Point"
         :coordinates [lon lat]}}]})
   (s/gen (s/tuple :lipas.location.coordinates/lat
                   :lipas.location.coordinates/lon))))

;; NOTE: generator supports only point features atm
(s/def :lipas.location/geometries (s/with-gen
                                    ::geojson/feature-collection
                                    lipas-point-feature-gen))

(comment (s/valid?
          ::geojson/feature-collection
          (gen/generate (s/gen :lipas.location/geometries))))

;;; Sports site ;;;

(s/def :lipas.sports-site/lipas-id (s/and int? pos?))
(s/def :lipas.sports-site/status (into #{} (keys sports-sites/statuses)))
(s/def :lipas.sports-site/name (str-btw 2 100))
(s/def :lipas.sports-site/marketing-name (str-btw 2 100))

(s/def :lipas.sports-site/owner (into #{} (keys owners/all)))
(s/def :lipas.sports-site/admin (into #{} (keys admins/all)))

(s/def :lipas.sports-site/phone-number (s/nilable (str-btw 1 50)))
(s/def :lipas.sports-site/www (s/nilable (str-btw 1 200)))
(s/def :lipas.sports-site/email :lipas/email)

(s/def :lipas.sports-site.type/type-code
  (into #{} (map :type-code) sports-site-types/all))

(s/def :lipas.sports-site/construction-year
  (into #{} (range 1850 this-year)))

(s/def :lipas.sports-site/properties
  (s/map-of keyword? (s/or :string? (str-btw 1 100)
                           :number? number?
                           :boolean? boolean?)))

(s/def :lipas.sports-site/location
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

(s/def :lipas/sports-site
  (s/keys :req-un [:lipas/timestamp
                   :lipas.sports-site/lipas-id
                   :lipas.sports-site/status
                   :lipas.sports-site/type
                   :lipas.sports-site/name
                   :lipas.sports-site/owner
                   :lipas.sports-site/admin
                   :lipas.sports-site/location
                   :lipas.sports-site/type]
          :opt-un [:lipas.sports-site/marketing-name
                   :lipas.sports-site/phone-number
                   :lipas.sports-site/www
                   :lipas.sports-site/email
                   :lipas.sports-site/construction-year
                   :lipas.sports-site/properties]))

;;; Building ;;;

(s/def :lipas.building/material (into #{} (keys materials/all)))
(s/def :lipas.building/construction-year :lipas.sports-site/construction-year)
(s/def :lipas.building/main-designers (str-btw 2 100))
(s/def :lipas.building/total-surface-area-m2 (s/int-in 100 (inc 50000)))
(s/def :lipas.building/total-volume-m3 (s/int-in 100 (inc 200000)))
(s/def :lipas.building/pool-room-total-area-m2 (s/int-in 100 (inc 10000)))
(s/def :lipas.building/total-water-area-m2 (s/int-in 100 (inc 10000)))
(s/def :lipas.building/heat-sections? boolean?)
(s/def :lipas.building/piled? boolean?)
(s/def :lipas.building/staff-count (s/int-in 0 (inc 1000)))
(s/def :lipas.building/seating-capacity (s/int-in 0 (inc 10000)))
(s/def :lipas.building/heat-source (into #{} (keys swimming-pools/heat-sources)))
(s/def :lipas.building/ventilation-units-count (s/int-in 0 (inc 100)))
(s/def :lipas.building/pool-room-total-area-m2 (s/int-in 0 (inc 5000)))
(s/def :lipas.building/total-water-area-m2 (s/int-in 0 (inc 5000)))

(s/def :lipas.building/main-construction-materials
  (s/coll-of :lipas.building/material
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

(s/def :lipas.building/supporting-structures
  (s/coll-of :lipas.building/material
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

(s/def :lipas.building/ceiling-structures
  (s/coll-of :lipas.building/material
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

(comment (s/valid? :lipas.building/main-construction-materials [:concrete :brick]))
(comment (s/valid? :lipas.building/ventilation-units-count 100))

(s/def :lipas.swimming-pool/building
  (s/keys :req-un [:lipas.building/construction-year
                   :lipas.building/main-designers
                   :lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/pool-room-total-area-m2
                   :lipas.building/total-water-area-m2
                   :lipas.building/heat-sections?
                   :lipas.building/main-construction-materials
                   :lipas.building/piled?
                   :lipas.building/supporting-structures
                   :lipas.building/ceiling-structures
                   :lipas.building/staff-count
                   :lipas.building/seating-capacity
                   :lipas.building/heat-source
                   :lipas.building/ventilation-units-count]))

;;; Ice stadiums ;;;

;; Building ;;

(s/def :lipas.ice-stadium/building
  (s/keys :req-un [:lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/construction-year
                   :lipas.building/seating-capacity]))

;; Envelope structure ;;

(s/def :lipas.ice-stadium.envelope-structure/base-floor-structure
  (into #{} (keys materials/base-floor-structures)))

(s/def :lipas.ice-stadium.envelope-structure/insulated-exterior? boolean?)
(s/def :lipas.ice-stadium.envelope-structure/insulated-ceiling? boolean?)
(s/def :lipas.ice-stadium.envelope-structure/low-emissivity-coating? boolean?)

(s/def :lipas.ice-stadium/envelope-structure
  (s/keys :opt-un [:lipas.ice-stadium.envelope-structure/insulated-exterior?
                   :lipas.ice-stadium.envelope-structure/insulated-ceiling?
                   :lipas.ice-stadium.envelope-structure/low-emissivity-coating?]))

;; Rinks ;;

(s/def :lipas.ice-stadium.rink/width-m (s/int-in 0 100))
(s/def :lipas.ice-stadium.rink/length-m (s/int-in 0 100))

(s/def :lipas.ice-stadium/rink (s/keys :req-un [:lipas.ice-stadium.rink/width-m
                                                :lipas.ice-stadium.rink/length-m]))
(s/def :lipas.ice-stadium/rinks
  (s/coll-of :lipas.ice-stadium/rink
             :min-count 0
             :max-count 10
             :distinct true
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

(s/def :lipas.ice-stadium.conditions/air-humidity-min-percent (s/int-in 50 (inc 70)))
(s/def :lipas.ice-stadium.conditions/air-humidity-max-percent (s/int-in 50 (inc 70)))
(s/def :lipas.ice-stadium.conditions/ice-surface-temperature-c (s/int-in -6 (inc -3)))
(s/def :lipas.ice-stadium.conditions/skating-area-temperature-c (s/int-in 5 (inc 12)))
(s/def :lipas.ice-stadium.conditions/stand-temperature-c (s/int-in 0 (inc 50)))
(s/def :lipas.ice-stadium.conditions/daily-open-hours (s/int-in 0 (inc 24)))
(s/def :lipas.ice-stadium.conditions/open-months (s/int-in 0 (inc 12)))

(s/def :lipas.ice-stadium.conditions/daily-maintenance-count-week-days
  (s/int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/daily-maintenance-count-weekends
  (s/int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/average-water-consumption-l
  (s/int-in 0 (inc 1000)))

(s/def :lipas.ice-stadium.conditions/maintenance-water-temperature-c
  (s/int-in 0 100))

(s/def :lipas.ice-stadium.conditions/ice-average-thickness-mm
  (s/int-in 0 (inc 150)))

(s/def :lipas.ice-stadium/conditions
  (s/keys :opt-un [:lipas.ice-stadium.conditions/air-humidity-min-percent
                   :lipas.ice-stadium.conditions/air-humidity-max-percent
                   :lipas.ice-stadium.conditions/ice-surface-temperature-c
                   :lipas.ice-stadium.conditions/skating-area-temperature-c
                   :lipas.ice-stadium.conditions/stand-temperature-c
                   :lipas.ice-stadium.conditions/daily-open-hours
                   :lipas.ice-stadium.conditions/open-months
                   :lipas.ice-stadium.conditions/daily-maintenance-count-week-days
                   :lipas.ice-stadium.conditions/daily-maintenance-count-weekends
                   :lipas.ice-stadium.conditions/average-water-consumption-l
                   :lipas.ice-stadium.conditions/maintenance-water-temperature-c
                   :lipas.ice-stadium.conditions/ice-average-thickness-mm]))

;; Ventilation ;;

(s/def :lipas.ice-stadium.ventilation/heat-recovery-thermal-efficiency-percent
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
  (s/keys :opt-un
          [:lipas.ice-stadium.ventilation/heat-recovery-thermal-efficiency-percent
           :lipas.ice-stadium.ventilation/heat-recovery-type
           :lipas.ice-stadium.ventilation/dryer-type
           :lipas.ice-stadium.ventilation/dryer-duty-type
           :lipas.ice-stadium.ventilation/heat-pump-type]))

;; Energy consumption ;;

(s/def :lipas.energy-consumption/electricity-mwh (s/int-in 0 10000))
(s/def :lipas.energy-consumption/heat-mwh (s/int-in 0 10000))
;; TODO find out realistic limits for cold energy
(s/def :lipas.energy-consumption/cold-mwh (s/int-in 0 100000))
(s/def :lipas.energy-consumption/water-m3 (s/int-in 0 100000))

(s/def :lipas/energy-consumption
  (s/keys :req-un [:lipas.energy-consumption/electricity-mwh
                   :lipas.energy-consumption/heat-mwh
                   :lipas.energy-consumption/water-m3]
          :opt-un [:lipas.energy-consumption/cold-mwh]))

(s/def :lipas.ice-stadium/energy-consumption-monthly
  (s/map-of (s/int-in 1 (inc 12)) :lipas/energy-consumption))

(s/def :lipas.sports-site/ice-stadium
  (s/merge
   :lipas/sports-site
   (s/keys :req-un [:lipas.ice-stadium/building
                    :lipas.ice-stadium/rinks
                    :lipas.ice-stadium/envelope-structure
                    :lipas.ice-stadium/refrigeration
                    :lipas.ice-stadium/ventilation
                    :lipas.ice-stadium/conditions
                    :lipas/energy-consumption]
           :opt-un [:lipas.ice-stadium/energy-consumption-monthly])))

;;; Swimming pools ;;;

;; Building ;;

(s/def :lipas.swimming-pool/building
  (s/keys :req-un [:lipas.building/main-designers
                   :lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/construction-year
                   :lipas.building/seating-capacity
                   :lipas.building/total-water-area-m2
                   :lipas.building/heat-sections?
                   :lipas.building/main-construction-materials
                   :lipas.building/piled?
                   :lipas.building/supporting-structures
                   :lipas.building/ceiling-structures
                   :lipas.building/staff-count
                   :lipas.building/heat-source
                   :lipas.building/ventilation-units-count]))

;; Water treatment ;;

(s/def :lipas.swimming-pool.water-treatment/ozonation boolean?)
(s/def :lipas.swimming-pool.water-treatment/uv-treatment boolean?)
(s/def :lipas.swimming-pool.water-treatment/activated-carbon boolean?)
(s/def :lipas.swimming-pool.water-treatment/filtering-method
  (into #{} (keys swimming-pools/filtering-methods)))

(s/def :lipas.swimming-pool/water-treatment
  (s/keys :req-un [:lipas.swimming-pool.water-treatment/filtering-method
                   :lipas.swimming-pool.water-treatment/activated-carbon
                   :lipas.swimming-pool.water-treatment/uv-treatment
                   :lipas.swimming-pool.water-treatment/ozonation]))

;; Pools ;;

(s/def :lipas.swimming-pool.pool/type (into #{} (keys swimming-pools/pool-types)))
(s/def :lipas.swimming-pool.pool/temperature-c (s/int-in 0 50))
(s/def :lipas.swimming-pool.pool/volume-m3 (s/int-in 0 5000))
(s/def :lipas.swimming-pool.pool/area-m2 (s/int-in 0 2000))
(s/def :lipas.swimming-pool.pool/length-m (s/double-in :min 0 :max 100))
(s/def :lipas.swimming-pool.pool/width-m (s/double-in :min 0 :max 100))
(s/def :lipas.swimming-pool.pool/depth-min-m (s/double-in :min 0 :max 10))
(s/def :lipas.swimming-pool.pool/depth-max-m (s/double-in :min 0 :max 10))

(s/def :lipas.swimming-pool/pool
  (s/keys :req-un [:lipas.swimming-pool.pool/type
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
             :max-count 10
             :distinct false
             :into []))

;; Slides ;;

(s/def :lipas.swimming-pool.slide/length-m (s/int-in 0 200))
(s/def :lipas.swimming-pool.slide/structure
  (into #{} (keys materials/slide-structures)))

(s/def :lipas.swimming-pool/slide
  (s/keys :req-un [:lipas.swimming-pool.slide/length-m
                   :lipas.swimming-pool.slide/structure]))

(s/def :lipas.swimming-pool/slides
  (s/coll-of :lipas.swimming-pool/slide
             :min-count 0
             :max-count 10
             :distincg false
             :into []))

;; Saunas ;;

(s/def :lipas.swimming-pool.sauna/men? boolean?)
(s/def :lipas.swimming-pool.sauna/women? boolean?)
(s/def :lipas.swimming-pool.sauna/type
  (into #{} (keys swimming-pools/sauna-types)))

(s/def :lipas.swimming-pool/sauna
  (s/keys :req-un [:lipas.swimming-pool.sauna/men?
                   :lipas.swimming-pool.sauna/women?
                   :lipas.swimming-pool.sauna/type]))

(s/def :lipas.swimming-pool/saunas
  (s/coll-of :lipas.swimming-pool/sauna
             :min-count 0
             :max-count 10
             :distincg false
             :into []))

;; Other services ;;

(s/def :lipas.swimming-pool.other-services/platforms-1m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/platforms-3m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/platforms-5m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/platforms-7.5m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/platforms-10m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/hydro-massage-spots-count
  (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/hydro-neck-massage-spots-count
  (s/int-in 0 100))
(s/def :lipas.swimming-pool.other-services/kiosk? boolean?)

(s/def :lipas.swimming-pool/other-services
  (s/keys :req-un
          [:lipas.swimming-pool.other-services/platforms-1m-count
           :lipas.swimming-pool.other-services/platforms-1m-count
           :lipas.swimming-pool.other-services/platforms-3m-count
           :lipas.swimming-pool.other-services/platforms-5m-count
           :lipas.swimming-pool.other-services/platforms-7.5m-count
           :lipas.swimming-pool.other-services/platforms-10m-count
           :lipas.swimming-pool.other-services/hydro-massage-spots-count
           :lipas.swimming-pool.other-services/hydro-neck-massage-spots-count
           :lipas.swimming-pool.other-services/kiosk?]))

;; Showers and lockers ;;

(s/def :lipas.swimming-pool.facilities/showers-men-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/showers-women-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/lockers-men-count (s/int-in 0 1000))
(s/def :lipas.swimming-pool.facilities/lockers-women-count (s/int-in 0 1000))

(s/def :lipas.swimming-pool/facilities
  (s/keys :req-un [:lipas.swimming-pool.facilities/showers-men-count
                   :lipas.swimming-pool.facilities/showers-women-count
                   :lipas.swimming-pool.facilities/lockers-men-count
                   :lipas.swimming-pool.facilities/lockers-women-count]))

;; Visitors ;;

(s/def :lipas.swimming-pool.visitors/total-count (s/int-in 0 1000000))
(s/def :lipas.swimming-pool/visitors (s/keys :req-un [:lipas.visitors/total-count]))

(s/def :lipas.sports-site/swimming-pool
  (s/merge
   :lipas/sports-site
   (s/keys :req-un [:lipas.swimming-pool/water-treatment
                    :lipas.swimming-pool/facilities
                    :lipas.swimming-pool/other-services
                    :lipas.swimming-pool/building
                    :lipas.swimming-pool/pools
                    :lipas.swimming-pool/saunas
                    :lipas.swimming-pool/slides
                    :lipas.swimming-pool/visitors
                    :lipas/energy-consumption])))
