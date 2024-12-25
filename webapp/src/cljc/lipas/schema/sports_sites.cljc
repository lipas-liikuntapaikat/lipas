(ns lipas.schema.sports-sites
  (:require [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.owners :as owners]
            [lipas.data.activities :as activities]
            [lipas.data.prop-types :as prop-types]
            [lipas.schema.sports-sites.activities :as activities-schema]
            [lipas.schema.sports-sites.circumstances :as circumstances-schema]
            [lipas.schema.common :as common]
            [lipas.data.types :as types]
            [lipas.schema.core :as specs]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.util :as mu]))

(def city-code
  (into [:enum {:title "CityCode"
                :description "Official municipality identifier https://stat.fi/fi/luokitukset/kunta/kunta_1_20240101"}]
        (sort (keys cities/by-city-code))))

(def city-codes
  [:set {:title "CityCodes"
         :description (-> city-code second :description)}
   city-code])

(def type-code
  (into [:enum {:title "TypeCode"
                :description "Sports facility type according to LIPAS classification https://www.jyu.fi/fi/liikunta/yhteistyo/lipas-liikunnan-paikkatietojarjestelma"}]
        (sort (keys types/all))))

(def type-codes
  [:set {:title "TypeCodes"
         :description (-> type-code second :description)}
   type-code])

(defn make-location-schema [feature-schema]
  [:map
   [:city
    [:map
     [:city-code #'city-code]
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
      [:vector feature-schema]]]]])

(def line-string-feature-props
  [:map
   [:name {:optional true} :string]
   [:lipas-id {:optional true} :int]
   [:type-code {:optional true} :int]
   [:route-part-difficulty {:optional true} :string]
   [:travel-direction {:optional true} :string]])

(def line-string-feature
  (mu/assoc common/line-string-feature :properties line-string-feature-props))

(def point-location (make-location-schema common/point-feature))
(def line-string-location (make-location-schema line-string-feature))
(def polygon-location (make-location-schema common/polygon-feature))

(def owner
  (into [:enum {:title "Onwer"
                :description "Owner entity of the sports facility."}]
        (keys owners/all)))

(def owners
  [:set {:title "Admins"
         :description (-> owner second :description)}
   #'owner])

(def admin
  (into [:enum {:title "Admin"
                :description "Administrative entity of the sports facility."}]
        (keys admins/all)))

(def admins
  [:set {:title "Admins"
         :description (-> admin second :description)}
   #'admin])

(def sports-site-base
  [:map
   {:title "Shared Properties"
    ;; Because this is used from :and, both branches need to be open for Malli to work.
    :closed false}
   [:lipas-id [:int]]
   [:status #'common/status]
   [:name {:description "The official name of the sports facility"}
    [:string {:min 2 :max 100}]]
   [:marketing-name {:optional true :description "Marketing name or common name of the sports facility."}
    [:string {:min 2 :max 100}]]
   [:name-localized {:optional true :description "The official name of the sports facility localized."}
    [:map
     [:se {:optional true :description "Swedish translation of the official name of the sports facility."}
      [:string {:min 2 :max 100}]]
     [:en {:optional true :description "English translation of the official name of the sports facility."}
      [:string {:min 2 :max 100}]]]]
   [:owner #'owner]
   [:admin #'admin]
   [:email {:optional true :description "Email address of the sports facility."}
    [:re specs/email-regex]]
   [:www {:optional true :description "Website of the sports facility."}
    [:string {:min 1 :max 500}]]
   [:reservations-link {:optional true :description "Link to external booking system."}
    [:string {:min 1 :max 500}]]
   [:phone-number {:optional true :description "Phone number of the sports facility"}
    [:string {:min 1 :max 50}]]
   [:comment {:optional true :description "Additional information."}
    [:string {:min 1 :max 2048}]]
   [:construction-year {:optional true :description "Year of construction of the sports facility"}
    [:int {:min 1800 :max (+ 10 utils/this-year)}]]
   [:renovation-years {:optional true :description "Years of major renovation of the sports facility"}
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
       :description description
       :closed false}
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
                      {:title (str type-code " - " (:en (:name x)))
                       :description (get-in x [:description :en])
                       :type-codes #{type-code}
                       :location-schema (case geometry-type
                                          "Point" #'point-location
                                          "LineString" #'line-string-location
                                          "Polygon" #'polygon-location)
                       :extras-schema (cond-> [:map]
                                        (seq props)
                                        (conj [:properties
                                               {:optional true}
                                               (into [:map]
                                                     (for [[k schema] (select-keys prop-types/schemas (keys props))]
                                                       [k {:optional true
                                                           :description (get-in prop-types/all [k :description :en])}
                                                        schema]))])

                                        floorball?
                                        (conj [:circumstances
                                               {:optional true
                                                :description "Floorball information"}
                                               #'circumstances-schema/floorball])

                                        activity
                                        (conj [:activities
                                               {:optional true
                                                :description "Enriched content for Luontoon.fi service."}
                                               [:map
                                                [activity-key
                                                 {:optional true}
                                                 (case activity-key
                                                   :outdoor-recreation-areas #'activities-schema/outdoor-recreation-areas
                                                   :outdoor-recreation-facilities #'activities-schema/outdoor-recreation-facilities
                                                   :outdoor-recreation-routes #'activities-schema/outdoor-recreation-routes
                                                   :cycling #'activities-schema/cycling
                                                   :paddling #'activities-schema/paddling
                                                   :birdwatching #'activities-schema/birdwatching
                                                   :fishing #'activities-schema/fishing)]]]))})])))

(comment
  (mu/get sports-site 101)

  (m/validate [:vector sports-site]
              [{:lipas-id 1
                :status "active"
                :name "foo"
                :owner "city"
                :admin "city-sports"
                :location {:city {:city-code 5}
                           :address "foo"
                           :postal-code "00100"
                           :postal-office "foo"
                           :geometries {:type "FeatureCollection"
                                        :features [{:type "Feature"
                                                    :geometry {:type "Point"
                                                               :coordinates [0.0 0.0]}}]}}
                :type {:type-code 1530}}]))
