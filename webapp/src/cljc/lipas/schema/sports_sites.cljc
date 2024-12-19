(ns lipas.schema.sports-sites
  (:require [lipas.data.activities :as activities]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.owners :as owners]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.sports-sites :as sports-sites]
            [lipas.data.types :as types]
            [lipas.schema.core :as specs]
            [lipas.utils :as utils]
            [malli.json-schema :as json-schema]
            [malli.util :as mu]))

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

;; https://github.com/metosin/malli/issues/670
(def number-schema number?)

(defn make-location-schema [geometry-schema]
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
       [:map
        [:type [:enum "Feature"]]
        [:id {:optional true} :string]
        [:geometry geometry-schema]]]]]]])

(def point-geometry
  [:map
   {:title "Point"}
   [:type [:enum "Point"]]
   [:coordinates [:vector {:min 2 :max 3} number-schema]]])

(def line-string-geometry
  [:map
   {:title "LineString"}
   [:type [:enum "LineString"]]
   [:coordinates [:vector [:vector {:min 2 :max 3} number-schema]]]
   [:properties {:optional true}
    [:map
     [:name {:optional true} :string]
     [:lipas-id {:optional true} :int]
     [:type-code {:optional true} :int]
     [:route-part-difficulty {:optional true} :string]
     [:travel-direction {:optional true} :string]]]])


(def polygon-geometry
  [:map
   {:title "Polygon"}
   [:type [:enum "Polygon"]]
   [:coordinates [:vector [:vector [:vector {:min 2 :max 3} number-schema]]]]])

(def point-location (make-location-schema point-geometry))
(def line-string-location (make-location-schema line-string-geometry))
(def polygon-location (make-location-schema polygon-geometry))

(def sports-site-base
  [:map
   {:title "Shared Properties"}
   [:lipas-id [:int]]
   [:status (into [:enum] (keys sports-sites/statuses))]
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
    [:sequential [:int {:min 1800 :max (+ 10 utils/this-year)}]]]])

(defn make-sports-site-schema [{:keys [title
                                       type-codes
                                       description
                                       extras-schema
                                       location-schema]}]
  ;; TODO audit
  [:and
   #'sports-site-base
   (mu/merge
     [:map
      {:title title
       :description description}
      [:type
       [:map
        [:type-code (into [:enum] type-codes)]]]
      [:location location-schema]]
     extras-schema)])

(def sports-site
  (into [:multi {:title "SportsSite"
                 :dispatch (fn [x]
                             (-> x :type :type-code))}]
        (for [[type-code {:keys [geometry-type props] :as x}] (sort-by key types/all)
              :let [activity (get activities/by-type-code type-code)
                    activity-key (some-> activity :value keyword)
                    floorball? (= 2240 type-code)]]
          [type-code (make-sports-site-schema
                       {:title (str type-code " - " (:fi (:name x)))
                        :type-codes #{type-code}
                        :location-schema (case geometry-type
                                           "Point" #'point-location
                                           "LineString" #'line-string-location
                                           "Polygon" #'polygon-location)
                        :extras-schema (cond-> [:map]
                                         (seq props)
                                         (mu/assoc :properties
                                                   (into [:map]
                                                         (for [[k schema] (select-keys prop-types/schemas (keys props))]
                                                           [k {:optional true} schema]))
                                                   {:optional true})

                                         floorball?
                                         (mu/assoc :circumstances
                                                   circumstances-schema
                                                   {:optional true
                                                    :description "Floorball information"})

                                         activity
                                         (mu/assoc :activities
                                                   [:map
                                                    [activity-key
                                                     {:optional true}
                                                     (case activity-key
                                                       :outdoor-recreation-areas #'activities/outdoor-recreation-areas-schema
                                                       :outdoor-recreation-facilities #'activities/outdoor-recreation-facilities-schema
                                                       :outdoor-recreation-routes #'activities/outdoor-recreation-routes-schema
                                                       :cycling #'activities/cycling-schema
                                                       :paddling #'activities/paddling-schema
                                                       :birdwatching #'activities/birdwatching-schema
                                                       :fishing #'activities/fishing-schema)]]
                                                   {:optional true}))})])))


