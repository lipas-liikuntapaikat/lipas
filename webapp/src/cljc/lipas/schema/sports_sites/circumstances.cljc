(ns lipas.schema.sports-sites.circumstances
  (:require [lipas.data.floorball :as floorball]
            [lipas.schema.common :as common]
            [malli.util :as mu]
            [malli.core :as m]
            #?(:clj [clojure.test.check.generators])))

;; Standalone schemas for frontend form validation
;; Constraints match original clojure.spec definitions from schema/core.cljc

;; Locker room fields — (number-in :min 0 :max (inc 2000)), (s/int-in 0 100)
(def locker-room-surface-area-m2 (m/schema [:and common/number [:fn #(<= 0 % 2000)]]))
(def showers-count (m/schema [:int {:min 0 :max 99}]))
(def toilets-count (m/schema [:int {:min 0 :max 99}]))

;; String fields — (str-in min max), both inclusive ✓
(def teams-using (m/schema [:string {:min 0 :max 1024}]))
(def general-information (m/schema [:string {:min 2 :max 1000}]))
(def storage-capacity-comment (m/schema [:string {:min 0 :max 2048}]))
(def locker-room-quality-comment (m/schema [:string {:min 2 :max 2048}]))
(def first-aid-comment (m/schema [:string {:min 2 :max 2048}]))
(def vip-area-comment (m/schema [:string {:min 2 :max 2048}]))
(def restaurateur-contact-info (m/schema [:string {:min 2 :max 2048}]))
(def ticket-sales-operator (m/schema [:string {:min 2 :max 100}]))

;; Int fields — (s/int-in 0 N) exclusive upper bound → :max (dec N)
(def locker-rooms-count (m/schema [:int {:min 0 :max 999}]))
(def scoreboard-count (m/schema [:int {:min 0 :max 99}]))
(def audience-toilets-count (m/schema [:int {:min 0 :max 999}]))
(def detached-chair-quantity (m/schema [:int {:min 0 :max 999}]))
(def detached-tables-quantity (m/schema [:int {:min 0 :max 999}]))
(def cafeteria-and-restaurant-capacity-person (m/schema [:int {:min 0 :max 9999}]))
(def conference-space-quantity (m/schema [:int {:min 0 :max 99}]))
(def conference-space-total-capacity-person (m/schema [:int {:min 0 :max 9999}]))
(def roof-trusses-capacity-kg (m/schema [:int {:min 0 :max 9999}]))
(def microfone-quantity (m/schema [:int {:min 0 :max 199}]))
(def saunas-count (m/schema [:int {:min 0 :max 99}]))

;; Floorball-specific int fields — (s/int-in 0 N)
(def available-goals-count (m/schema [:int {:min 0 :max 199}]))
(def goal-shrinking-elements-count (m/schema [:int {:min 0 :max 99}]))
(def corner-pieces-count (m/schema [:int {:min 0 :max 99}]))

;; Number fields — (number-in :min M :max N) uses (<= M % (dec N))
;; Those with (inc N) in original cancel out: (<= 0 % (dec (inc N))) = (<= 0 % N)
(def car-parking-capacity (m/schema [:and common/number [:fn #(<= 0 % 9999)]]))    ;; (number-in :min 0 :max 10000)
(def bus-park-capacity (m/schema [:and common/number [:fn #(<= 0 % 1000)]]))       ;; (number-in :min 0 :max (inc 1000))
(def open-floor-space-length-m (m/schema [:and common/number [:fn #(<= 0 % 200)]]));; (number-in :min 0 :max (inc 200))
(def open-floor-space-width-m (m/schema [:and common/number [:fn #(<= 0 % 200)]])) ;; (number-in :min 0 :max (inc 200))
(def open-floor-space-area-m2 (m/schema [:and common/number [:fn #(<= 0 % 10000)]]));; (number-in :min 0 :max (inc 10000))

;; Locker room schema
(def locker-room
  (m/schema
   [:map
    {:description "A locker room in the facility"}
    [:surface-area-m2 {:optional true :description "Surface area of the locker room in square meters"}
     #'common/number]
    [:showers-count {:optional true :description "Number of showers"}
     [:int {:min 0 :max 100}]]
    [:toilets-count {:optional true :description "Number of toilets"}
     [:int {:min 0 :max 100}]]]))

(def locker-rooms
  (m/schema
   [:vector {:description "Collection of locker rooms in the facility"}
    #'locker-room]))

;; Audit schema
(def audit
  (m/schema
   [:map
    {:description "Floorball facility audit record"}
    [:audit-date {:description "Date of the audit"}
     [:and
      {:gen/gen #?(:clj (clojure.test.check.generators/fmap
                         (fn [millis]
                           (let [instant (java.time.Instant/ofEpochMilli millis)
                                 formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")]
                             (.format (.atZone instant (java.time.ZoneId/of "UTC")) formatter)))
                         (clojure.test.check.generators/choose
                          (.getTime #inst "2000-01-01")
                          (.getTime #inst "2030-12-31")))
                   :cljs nil)}
      :string
      [:re #"^\d{4}-\d{2}-\d{2}$"]]]
    [:audit-type {:description "Type of the audit"}
     [:enum "floorball-circumstances-audit"]]
    [:audit-performed-by {:description "ID of the person who performed the audit"}
     :string]]))

(def audits
  (m/schema
   [:vector {:description "Collection of audits for the facility"}
    #'audit]))

;; TODO check fields that are exposed via public API before release
(def floorball
  (m/schema
   [:map {:description "Enriched floorball facility information"}
    [:storage-capacity {:optional true} :string]
    [:roof-trusses-operation-model {:optional true}
     (into [:enum] (keys floorball/roof-trussess-operation-model))]
    [:general-information {:optional true} :string]
    [:corner-pieces-count {:optional true} :int]
    [:audience-toilets-count {:optional true} :int]
    [:bus-park-capacity {:optional true} :int]
    [:wifi-capacity-sufficient-for-streaming? {:optional true} :boolean]
    [:first-aid-comment {:optional true} :string]
    [:fixed-cameras? {:optional true} :boolean]
    [:stretcher? {:optional true} :boolean]
    [:field-level-loading-doors? {:optional true} :boolean]
    [:car-parking-economics-model {:optional true}
     (into [:enum] (keys floorball/car-parking-economics-model))]
    [:vip-area? {:optional true} :boolean]
    [:separate-referee-locker-room? {:optional true} :boolean]
    [:audit-date {:optional true} :string]
    [:led-screens-or-surfaces-for-ads? {:optional true} :boolean]
    [:saunas-count {:optional true} :int]
    [:camera-stands? {:optional true} :boolean]
    [:wired-microfone-quantity {:optional true} :int]
    [:locker-rooms-count {:optional true} :int]
    [:car-parking-capacity {:optional true} :int]
    [:broadcast-car-park? {:optional true} :boolean]
    [:press-conference-space? {:optional true} :boolean]
    [:open-floor-space-length-m {:optional true} #'common/number]
    [:wifi-available? {:optional true} :boolean]
    [:goal-shrinking-elements-count {:optional true} :int]
    [:scoreboard-count {:optional true} :int]
    [:restaurateur-contact-info {:optional true} :string]
    [:vip-area-comment {:optional true} :string]
    [:cafe-or-restaurant-has-exclusive-rights-for-products?
     {:optional true}
     :boolean]
    [:gym? {:optional true} :boolean]
    [:ticket-sales-operator {:optional true} :string]
    [:side-training-space? {:optional true} :boolean]
    [:locker-room-quality-comment {:optional true} :string]
    [:roof-trusses? {:optional true} :boolean]
    [:detached-chair-quantity {:optional true} :int]
    [:conference-space-total-capacity-person {:optional true} :int]
    [:iff-certification-stickers-in-goals? {:optional true} :boolean]
    [:electrical-plan-available? {:optional true} :boolean]
    [:audio-mixer-available? {:optional true} :boolean]
    [:iff-certified-rink? {:optional true} :boolean]
    [:teams-using {:optional true} :string]
    [:loading-equipment-available? {:optional true} :boolean]
    [:doping-test-facilities? {:optional true} :boolean]
    [:wireless-microfone-quantity {:optional true} :int]
    [:defibrillator? {:optional true} :boolean]
    [:open-floor-space-width-m {:optional true} #'common/number]
    [:cafeteria-and-restaurant-capacity-person {:optional true} :int]
    [:speakers-aligned-towards-stands? {:optional true} :boolean]
    [:conference-space-quantity {:optional true} :int]
    [:three-phase-electric-power? {:optional true} :boolean]
    [:roof-trusses-capacity-kg {:optional true} :int]
    [:open-floor-space-area-m2 {:optional true} #'common/number]
    [:detached-tables-quantity {:optional true} :int]
    [:available-goals-count {:optional true} :int]
    [:player-entrance {:optional true}
     (into [:enum] (keys floorball/player-entrance))]
    [:floor-elasticity {:optional true}
     (into [:enum] (keys floorball/floor-elasticity))]
    [:audience-stand-access {:optional true}
     (into [:enum] (keys floorball/audience-stand-access))]]))

(def csv-headers
  ["Tietue"
   "Tietotyyppi"
   ;; TODO: these are still hardcoded in views
   #_"Nimi fi"
   #_"Nimi se"
   #_"Nimi en"])

(defn schema->csv-data []
  (let [ast (m/ast floorball)]
    (into [csv-headers]
          (for [k (sort (mu/keys floorball))]
            [(name k)
             (name (get-in ast [:keys k :value :type]))]))))
