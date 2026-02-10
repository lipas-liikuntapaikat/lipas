(ns lipas.schema.sports-sites
  (:refer-clojure :exclude [name comment])
  (:require [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [lipas.data.activities :as activities]
            [lipas.data.prop-types :as prop-types]
            [lipas.schema.sports-sites.activities :as activities-schema]
            [lipas.schema.sports-sites.circumstances :as circumstances-schema]
            [lipas.schema.sports-sites.fields :as fields-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.schema.common :as common]
            [lipas.data.types :as types]
            [lipas.utils :as utils]
            [malli.core :as m]))

(def lipas-id
  (m/schema [:int {:min 0 :label "Lipas-id" :description "Unique identifier of sports facility in LIPAS system."}]))

(def owner
  (m/schema (into [:enum {:description "Owner entity of the sports facility."}]
                  (keys owners/all))))

(def owners
  (m/schema [:set {:description (:description (m/properties owner))}
             #'owner]))

(def admin
  (m/schema (into [:enum {:description "Administrative entity of the sports facility."}]
                  (keys admins/all))))

(def admins
  (m/schema [:set {:description (:description (m/properties admin))}
             #'admin]))

(def name (m/schema [:string {:description "The official name of the sports facility"
                              :min 2
                              :max 100}]))

(def marketing-name (m/schema [:string {:min 2
                                        :max 100
                                        :description "Marketing name or common name of the sports facility."}]))

(def name-localized
  (m/schema [:map {:description "The official name of the sports facility localized."}
             [:se {:optional true :description "Swedish translation of the official name of the sports facility."}
              [:string {:min 2 :max 100}]]
             [:en {:optional true :description "English translation of the official name of the sports facility."}
              [:string {:min 2 :max 100}]]]))

(def email (m/schema [:re {:description "Email address of the sports facility."}
                      common/email-regex]))

(def www (m/schema [:string {:description "Website of the sports facility."
                             :min 1
                             :max 500}]))

(def reservations-link (m/schema [:string {:description "Link to external booking system."
                                           :min 1
                                           :max 500}]))

(def phone-number (m/schema [:string {:description "Phone number of the sports facility"
                                      :min 1
                                      :max 50}]))

(def comment (m/schema [:string {:description "Additional information."
                                 :min 1
                                 :max 2048}]))

(def construction-year (m/schema [:int {:description "Year of construction of the sports facility"
                                        :min 1800
                                        :max (+ 10 utils/this-year)}]))

(def renovation-years (m/schema [:sequential {:description "Years of major renovation of the sports facility"
                                              :distinct true}
                                 [:int {:min 1800 :max (+ 10 utils/this-year)}]]))

(defn make-sports-site-schema
  "Creates a sports site schema. When compat is true, adds :encode/json identity
   to type-code enum to prevent json-transformer from converting integers to strings.
   When include-lipas-id? is false, omits :lipas-id (for new sports site creation)."
  ([{:keys [title type-codes description extras-schema location-schema]}]
   (make-sports-site-schema {:title title
                             :type-codes type-codes
                             :description description
                             :extras-schema extras-schema
                             :location-schema location-schema}
                            false true))
  ([opts compat?]
   (make-sports-site-schema opts compat? true))
  ([{:keys [title type-codes description extras-schema location-schema]} compat? include-lipas-id?]
   ;; Build the schema as pure data vectors (no mu/merge) to avoid
   ;; StackOverflow when malli compiles the outer :multi schema.
   ;; extras-schema is always [:map & entries], so we extract and concat entries.
   (let [extras-entries (when (and extras-schema (> (count extras-schema) 1))
                          (rest extras-schema))]
     (into [:map
            {:title title
             :description description
             :closed false}]
           (concat
            (when include-lipas-id?
              [[:lipas-id #'lipas-id]])
            [[:event-date {:description "Timestamp when this information became valid (ISO 8601, UTC time zone)"}
              #'common/iso8601-timestamp]
             [:status #'common/status]
             [:name #'name]
             [:marketing-name {:optional true} #'marketing-name]
             [:name-localized {:optional true} #'name-localized]
             [:owner #'owner]
             [:admin #'admin]
             [:email {:optional true} #'email]
             [:www {:optional true} #'www]
             [:reservations-link {:optional true} #'reservations-link]
             [:phone-number {:optional true} #'phone-number]
             [:comment {:optional true} #'comment]
             [:construction-year {:optional true} #'construction-year]
             [:renovation-years {:optional true} #'renovation-years]
             [:type
              [:map
               ;; Add :encode/json identity in compat mode to prevent conversion to string
               [:type-code (if compat?
                             (into [:enum {:encode/json identity}] type-codes)
                             (into [:enum] type-codes))]]]
             [:location location-schema]]
            extras-entries)))))

(defn- make-sports-site-multi-schema
  "Helper function to generate sports-site multi schema.
   When compat? is true, uses compat location schemas and adds :encode/json identity.
   When include-lipas-id? is false, omits :lipas-id from child schemas."
  ([compat?]
   (make-sports-site-multi-schema compat? true))
  ([compat? include-lipas-id?]
   (into [:multi {:description "The core entity of LIPAS. Properties, geometry type and additional attributes vary based on the type of the sports facility."
                  :dispatch (fn [x]
                              (-> x :type :type-code))}]
         (for [[type-code {:keys [geometry-type props] :as x}] (sort-by key types/all)
               :let [activity (get activities/by-type-code type-code)
                     activity-key (some-> activity :value keyword)
                     ;; Type-codes that support floorball fields feature
                     floorball-type-codes #{2240 2150 2210 2220}
                     floorball? (contains? floorball-type-codes type-code)
                     ;; Select appropriate location schema based on compat mode
                     location-schema (case geometry-type
                                       "Point" (if compat?
                                                 #'location-schema/point-location-compat
                                                 #'location-schema/point-location)
                                       "LineString" (if compat?
                                                      #'location-schema/line-string-location-compat
                                                      #'location-schema/line-string-location)
                                       "Polygon" (if compat?
                                                   #'location-schema/polygon-location-compat
                                                   #'location-schema/polygon-location))]]
           ;; make-sports-site-schema now returns pure data (no mu/merge),
           ;; so no m/form conversion needed.
           [type-code (make-sports-site-schema
                       {:title (str type-code " - " (:en (:name x)))
                        :description (get-in x [:description :en])
                        :type-codes #{type-code}
                        :location-schema location-schema
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
                                         (conj [:fields
                                                {:optional true
                                                 :description "Collection of playing fields in the facility"}
                                                #'fields-schema/fields]
                                               [:locker-rooms
                                                {:optional true
                                                 :description "Collection of locker rooms in the facility"}
                                                #'circumstances-schema/locker-rooms]
                                               [:audits
                                                {:optional true
                                                 :description "Collection of facility audits"}
                                                #'circumstances-schema/audits]
                                               [:circumstances
                                                {:optional true
                                                 :description "Floorball facility information"}
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
                                                    :fishing #'activities-schema/fishing)]]]))}
                       compat?
                       include-lipas-id?)]))))

(def sports-site
  (m/schema (make-sports-site-multi-schema false)))

(def new-sports-site
  (m/schema (make-sports-site-multi-schema false false)))

(def sports-site-compat
  "Sports site schema with json-transform compatibility.
  Prevents json-transformer from converting enum integers to strings.
  Use this for API response coercion."
  (m/schema (make-sports-site-multi-schema true)))

(def new-or-existing-sports-site
  (m/schema [:or sports-site new-sports-site]))

#_(comment
    (mu/get sports-site 101)

    (require '[malli.error :as me])
    (me/humanize
     (m/explain new-or-existing-sports-site
                {:status "active"
               ;;:lipas-id 1
                 :event-date "2025-01-01T00:00:00.000Z"
                 :name "foo"
                 :owner "city"
                 :ptv {:kissa "koira"}
                 :admin "city-sports"
                 :location {:city {:city-code 5}
                            :address "foo"
                            :postal-code "00100"
                            :postal-office "foo"
                            :geometries {:type "FeatureCollection"
                                         :features [{:type "Feature"
                                                     :geometry {:type "Point"
                                                                :coordinates [0.0 0.0]}}]}}
                 :type {:type-code 1530}})))

(def prop-types prop-types/schemas)
