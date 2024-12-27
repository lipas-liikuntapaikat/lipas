(ns lipas.schema.sports-sites
  (:require [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [lipas.data.activities :as activities]
            [lipas.data.prop-types :as prop-types]
            [lipas.schema.sports-sites.activities :as activities-schema]
            [lipas.schema.sports-sites.circumstances :as circumstances-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.schema.common :as common]
            [lipas.data.types :as types]
            [lipas.schema.core :as specs]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.util :as mu]))

(def lipas-id
  [:int {:min 0 :label "Lipas-id" :description "Unique identifier of sports facility in LIPAS system."}])

(def owner
  (into [:enum {:description "Owner entity of the sports facility."}]
        (keys owners/all)))

(def owners
  [:set {:description (-> owner second :description)}
   #'owner])

(def admin
  (into [:enum {:description "Administrative entity of the sports facility."}]
        (keys admins/all)))

(def admins
  [:set {:description (-> admin second :description)}
   #'admin])

(def name [:string {:description "The official name of the sports facility"
                    :min 2
                    :max 100}])

(def marketing-name [:string {:min 2
                              :max 100
                              :description "Marketing name or common name of the sports facility."}])

(def name-localized
  [:map {:description "The official name of the sports facility localized."}
     [:se {:optional true :description "Swedish translation of the official name of the sports facility."}
      [:string {:min 2 :max 100}]]
     [:en {:optional true :description "English translation of the official name of the sports facility."}
      [:string {:min 2 :max 100}]]])

(def email [:re {:description "Email address of the sports facility."}
            specs/email-regex])

(def www [:string {:description "Website of the sports facility."
                   :min 1
                   :max 500}])

(def reservation-link [:string {:description "Link to external booking system."
                                :min 1
                                :max 500}])

(def phone-number [:string {:description "Phone number of the sports facility"
                            :min 1
                            :max 50}])

(def comment [:string {:description "Additional information."
                       :min 1
                       :max 2048}])

(def construction-year [:int {:description "Year of construction of the sports facility"
                              :min 1800
                              :max (+ 10 utils/this-year)}])

(def renovation-years [:sequential {:description "Years of major renovation of the sports facility"}
                       [:int {:min 1800 :max (+ 10 utils/this-year)}]])

(defn make-sports-site-schema [{:keys [title
                                       type-codes
                                       description
                                       extras-schema
                                       location-schema]}]
  ;; TODO audit
  (mu/merge
   [:map
    {:title title
     :description description
     :closed false}
    [:lipas-id #'lipas-id]
    [:event-date {:description "Timestamp when this information became valid (ISO 8601, UTC time zone)"}
     #'common/iso8601-timestamp]
    [:status #'common/status]
    [:name #'name]
    [:marketing-name {:optional true} #'marketing-name]
    [:name-localized {:optional true} #'name-localized]
    [:owner #'owner]
    [:admin #'admin]
    [:email {:optional true} #'email]
    [:www {:optional true} #'www]
    [:reservations-link {:optional true} #'reservation-link]
    [:phone-number {:optional true} #'phone-number]
    [:comment {:optional true} #'comment]
    [:construction-year {:optional true} #'construction-year]
    [:renovation-years {:optional true} #'renovation-years]
    [:type
     [:map
      [:type-code (into [:enum] type-codes)]]]
    [:location location-schema]]
   extras-schema))

(def sports-site
  (into [:multi {:description "The core entity of LIPAS. Properties, geometry type and additional attributes vary based on the type of the sports facility."
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
                                          "Point" #'location-schema/point-location
                                          "LineString" #'location-schema/line-string-location
                                          "Polygon" #'location-schema/polygon-location)
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

#_(comment
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
