(ns lipas.schema.handler
  "Route-specific schemas that don't fit elsewhere."
  (:require [lipas.data.cities :as cities]
            [lipas.data.status :as status]
            [lipas.reports :as reports]
            [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.utils :as utils]
            [malli.core :as m]))

;; Query params for sports-sites-by-type-code
(def lang-filter (m/schema [:enum "fi" "en" "se" "all"]))

(def sports-sites-query-params
  (m/schema
   [:map
    [:lang {:optional true} lang-filter]]))

;; Check sports-site name payload
(def check-sports-site-name-payload
  (m/schema
   [:map
    [:lipas-id sports-sites-schema/lipas-id]
    [:name sports-sites-schema/name]]))

;; Find fields payload
(def find-fields-payload
  (m/schema
   [:map
    [:field-types [:vector {:distinct true}
                   [:enum "football-field" "floorball-field"]]]]))

;; Create upload URL
(def create-upload-url-payload
  (m/schema
   [:map
    [:lipas-id sports-sites-schema/lipas-id]
    [:extension [:enum "png" "jpeg" "webp"]]]))

;; Report schemas
(def report-field (m/schema (into [:enum] (keys reports/fields))))

(def energy-report-req
  (m/schema
   [:map
    [:type-code types-schema/type-code-with-legacy]
    [:year [:int {:min 2000 :max (inc utils/this-year)}]]]))

(def sports-site-report-req
  (m/schema
   [:map
    [:search-query [:map]]
    [:fields [:vector {:distinct true} report-field]]
    [:locale [:enum :fi :se :en]]
    [:format {:optional true} [:enum "xlsx" "geojson" "csv"]]]))

;; Use plain :int rather than city-code enum because historical data
;; (finance reports, statistics) includes abolished municipalities.
(def city-codes
  (m/schema
   [:vector {:distinct true} [:int {:min 1}]]))

(def type-codes
  (m/schema
   [:vector {:distinct true} types-schema/type-code-with-legacy]))

(def finance-report-req
  (m/schema
   [:map
    [:city-codes city-codes]
    [:flat? {:optional true} :boolean]
    [:years {:optional true} [:vector {:distinct true}
                              [:int {:min 2000 :max utils/this-year}]]]
    [:unit {:optional true} (into [:enum] (keys reports/stats-units))]
    [:city-service {:optional true} (into [:enum] (keys reports/city-services))]]))

(def m2-per-capita-report-req
  (m/schema
   [:map
    [:city-codes {:optional true} city-codes]
    [:type-codes {:optional true} type-codes]]))

;; LOI search
(def loi-status
  (m/schema (into [:enum] (keys status/statuses))))

(def search-lois-payload
  (m/schema
   [:map
    [:loi-statuses {:optional true} [:vector {:distinct true} loi-status]]
    [:location {:optional true}
     [:map
      [:lon common/number]
      [:lat common/number]
      [:distance common/number]]]]))

;; Calculate stats
(def calculate-stats-payload
  (m/schema
   [:map
    [:year [:int {:min 2000 :max 2100}]]
    [:city-codes {:optional true} city-codes]
    [:type-codes {:optional true} type-codes]
    [:grouping {:optional true} [:enum "location.city.city-code" "type.type-code"]]]))

;; Magic link
(def email-variant (m/schema [:enum "lipas" "portal"]))

(def magic-link-login-url
  (m/schema
   [:and :string
    [:fn {:error/message "Login URL must start with a known LIPAS domain"}
     (fn [s]
       (some #(clojure.string/starts-with? s %)
             ["https://localhost"
              "https://lipas-dev.cc.jyu.fi"
              "https://uimahallit.lipas.fi"
              "https://jaahallit.lipas.fi"
              "https://liikuntapaikat.lipas.fi"
              "https://www.lipas.fi"
              "https://lipas.fi"]))]]))
