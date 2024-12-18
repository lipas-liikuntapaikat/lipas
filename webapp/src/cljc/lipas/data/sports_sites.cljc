(ns lipas.data.sports-sites
  (:require [lipas.data.activities :as activities]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.owners :as owners]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.status :as status]
            [lipas.data.types :as types]
            [lipas.schema.core :as specs]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.util :as mu]))

(def document-statuses
  {"draft"
   {:fi "Ehdotus"
    :se nil
    :en "Draft"}
   "published"
   {:fi "Julkaistu"
    :se nil
    :en "Published"}})

(def statuses status/statuses)

(def field-types
  {"floorball-field"
   {:fi "Salibandykenttä"
    :en "Floorball field"
    :se "Innebandyplan"}})

(def circumstances-schema
  [:map
   [:storage-capacity {:optional true} :string]
   [:roof-trusses-operation-model {:optional true} :string]
   [:general-information {:optional true} :string]
   [:corner-pieces-count {:optional true} :int]
   [:audience-toilets-count {:optional true} :int]
   [:bus-park-capacity {:optional true} :int]
   [:wifi-capacity-sufficient-for-streaming? {:optional true} :boolean]
   [:first-aid-comment {:optional true} :string]
   [:fixed-cameras? {:optional true} :boolean]
   [:stretcher? {:optional true} :boolean]
   [:field-level-loading-doors? {:optional true} :boolean]
   [:car-parking-economics-model {:optional true} :string]
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
   [:player-entrance {:optional true} :string]])

(def number-schema [:or :double :int])

(def geom-type->schema
  {"Point"      [:map
                 [:type [:enum "Feature"]]
                 [:id {:optional true} :string]
                 [:geometry
                  [:map
                   [:type [:enum "Point"]]
                   [:coordinates [:vector {:min 2 :max 3} number-schema]]]]]
   "LineString" [:map
                 [:type [:enum "Feature"]]
                 [:id {:optional true} :string]
                 [:geometry
                  [:map
                   [:type [:enum "LineString"]]
                   [:coordinates [:vector [:vector {:min 2 :max 3} number-schema]]]
                   [:properties {:optional true}
                    [:map
                     [:name {:optional true} :string]
                     [:lipas-id {:optional true} :int]
                     [:type-code {:optional true} :int]
                     [:route-part-difficulty {:optional true} :string]
                     [:travel-direction {:optional true} :string]]]]]]
   "Polygon"    [:map
                 [:type [:enum "Feature"]]
                 [:geometry
                  [:map
                   [:type [:enum "Polygon"]]
                   [:coordinates [:vector [:vector [:vector {:min 2 :max 3} number-schema]]]]]]]})

(def base-schema
  ;; TODO audit
  [:map
   [:lipas-id [:int]]
   [:status (into [:enum] (keys statuses))]
   [:name [:string {:min 2 :max 100}]]
   [:marketing-name {:optional true}
    [:string {:min 2 :max 100}]]
   [:name-localized {:optional true}
    [:map
     [:se {:optional true}
      [:string {:min 2 :max 100}]]
     [:en {:optional true}
      [:string {:min 2 :max 100}]]]]
   [:owner (into [:enum] (keys owners/all))]
   [:admin (into [:enum] (keys admins/all))]
   [:email {:optional true}
    [:re specs/email-regex]]
   [:www {:optional true}
    [:string {:min 1 :max 500}]]
   [:reservations-link {:optional true}
    [:string {:min 1 :max 500}]]
   [:phone-number {:optional true}
    [:string {:min 1 :max 50}]]
   [:comment {:optional true}
    [:string {:min 1 :max 2048}]]
   [:construction-year {:optional true}
    [:int {:min 1800 :max (+ 10 utils/this-year)}]]
   [:renovation-years {:optional true}
    [:sequential [:int {:min 1800 :max (+ 10 utils/this-year)}]]]
   [:location
    [:map
     [:city
      [:map
       [:city-code (into [:enum] (keys cities/by-city-code))]
       [:neighborhood {:optional true}
        [:string {:min 1 :max 100}]]]]
     [:address [:string {:min 1 :max 200}]]
     [:postal-code [:re specs/postal-code-regex]]
     [:postal-office {:optional true}
      [:string {:min 1 :max 100}]]
     [:geometries
      [:map
       [:type [:enum "FeatureCollection"]]
       [:features
        [:vector
         (into [:or] (vals geom-type->schema))]]]]]]
   [:circumstances {:optional true} circumstances-schema]
   [:activities {:optional true} activities/activities-schema]
   [:properties {:optional true}
    (into [:map] (for [[k schema] prop-types/schemas]
                   [k {:optional true} schema]))]])
