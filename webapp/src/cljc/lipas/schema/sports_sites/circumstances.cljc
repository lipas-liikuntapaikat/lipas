(ns lipas.schema.sports-sites.circumstances
  (:require [lipas.data.floorball :as floorball]
            [malli.util :as mu]
            [malli.core :as m]))

;; TODO check fields that are exposed via public API before release
(def floorball
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
   [:open-floor-space-length-m {:optional true} number?]
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
   [:open-floor-space-width-m {:optional true} number?]
   [:cafeteria-and-restaurant-capacity-person {:optional true} :int]
   [:speakers-aligned-towards-stands? {:optional true} :boolean]
   [:conference-space-quantity {:optional true} :int]
   [:three-phase-electric-power? {:optional true} :boolean]
   [:roof-trusses-capacity-kg {:optional true} :int]
   [:open-floor-space-area-m2 {:optional true} number?]
   [:detached-tables-quantity {:optional true} :int]
   [:available-goals-count {:optional true} :int]
   [:player-entrance {:optional true}
    (into [:enum] (keys floorball/player-entrance))]
   [:floor-elasticity {:optional true}
    (into [:enum] (keys floorball/floor-elasticity))]
   [:audience-stand-access {:optional true}
    (into [:enum] (keys floorball/audience-stand-access))]])

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
