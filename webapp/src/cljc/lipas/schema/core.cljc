(ns lipas.schema.core
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [lipas.data.cities :as cities]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [lipas.data.materials :as materials]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.data.types :as sports-place-types]))

(def this-year #?(:cljs (.getFullYear (js/Date.))
                  :clj  (.getYear (java.time.LocalDate/now))))

;; Sports-place

(s/def ::name (s/and string? #(<= 2 (count %))))

(s/def ::owner owners/all)

(s/def ::admin admins/all)

(s/def ::phone-number string?)
(s/def ::www string?)

;; Location

(s/def ::address string?)

(def postal-code-regex #"[0-9]{5}")
(comment (re-matches postal-code-regex "00010"))

(s/def ::postal-code (s/and string? #(re-matches postal-code-regex %)))
(s/def ::postal-office string?)

(s/def ::city-code (into #{} (map :city-code) cities/active))
(s/def ::sports-place-type (into #{} (map :type-code) sports-place-types/all))

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

(s/def ::firstname (s/and string? #(<= 1 (count %) 128)))
(s/def ::lastname (s/and string? #(<= 1 (count %) 128)))
(s/def ::username (s/and string? #(<= 1 (count %) 128)))
(s/def ::password (s/and string? #(<= 6 (count %) 128)))

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
(s/def ::pool-length-m (s/int-in 0 (inc 100)))
(s/def ::pool-width-m (s/int-in 0 (inc 100)))
(s/def ::pool-depth-min-m (s/double-in :min 0 :max 10))
(s/def ::pool-depth-max-m (s/double-in :min 0 :max 10))
(s/valid? ::pool-depth-max-m 0.1)

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
(s/def ::refrigerant-amount-kg (s/int-in 0 (inc 10000)))
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
;; So `int-in` can't be used because it would yield non-deterministic
;; results between platforms.
(comment (s/valid? ::electricity-mwh 1e4))
(comment (s/valid? ::electricity-mwh 0))
(comment (s/valid? ::electricity-mwh (inc 1e4)))
(comment (s/valid? ::electricity-mwh 1795))
(s/def ::electricity-mwh #(<= 0 % (dec 1e4)))
(s/def ::heat-mwh #(<= 0 % (dec 1e4)))
(s/def ::water-m3 #(<= 0 % (dec 1e5)))
