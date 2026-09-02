(ns lipas.schema.handler
  "Route-specific schemas that don't fit elsewhere."
  (:require [clojure.string :as str]
            [lipas.data.status :as status]
            [lipas.reports :as reports]
            [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.types :as types-schema]
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
     [:search-query [:map {:closed false}]]
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

(def magic-link-hosts
  "Hosts allowed to receive a magic-link / password-reset URL. That URL carries
  a login token — a full credential — so this set is the line between \"we
  emailed you a link\" and account takeover."
  #{"localhost"
    "lipas-dev.cc.jyu.fi"
    "uimahallit.lipas.fi"
    "jaahallit.lipas.fi"
    "liikuntapaikat.lipas.fi"
    "www.lipas.fi"
    "lipas.fi"})

(defn magic-link-url?
  "True when `s` is an https URL whose HOST is one of `magic-link-hosts`.

  Parses the URL rather than prefix-matching it, on purpose. The previous
  `str/starts-with?` check accepted any attacker host that merely BEGAN with an
  allowed origin — `https://lipas.fi.attacker.example/` and
  `https://localhost.attacker.example/` both passed it, which made the
  whitelist decorative.

  Userinfo is rejected outright: `https://lipas.fi@attacker.example/` has host
  `attacker.example` but reads as ours to a human skimming the email."
  [s]
  (boolean
    (when (string? s)
      #?(:clj (try
                (let [uri (java.net.URI. ^String s)]
                  (and (= "https" (.getScheme uri))
                       (nil? (.getUserInfo uri))
                       (contains? magic-link-hosts
                                  (some-> (.getHost uri) str/lower-case))))
                (catch Exception _ false))
         :cljs (try
                 (let [u (js/URL. s)]
                   (and (= "https:" (.-protocol u))
                        (str/blank? (.-username u))
                        (str/blank? (.-password u))
                        (contains? magic-link-hosts
                                   (str/lower-case (.-hostname u)))))
                 (catch :default _ false))))))

(def magic-link-login-url
  (m/schema
    [:and :string
     [:fn {:error/message "Login URL must point to a known LIPAS host"}
      magic-link-url?]]))

;; Reverse geocoding

(def reverse-geocode-query-params
  "`common/lat` and `common/lon` are Finland with room to spare, and that
  bound is what keeps this route honest: it rejects nonsense before it
  becomes an outbound Pelias request and a PostGIS point-in-polygon scan —
  the endpoint is public and unauthenticated, and every one of our postal
  sources only knows about Finland anyway. The response echoes the point
  through the same two schemas."
  (m/schema
    [:map
     [:lat common/lat]
     [:lon common/lon]]))

(def localized-name
  "A proper name from Posti, Tilastokeskus or `lipas.data.cities`. Swedish is
  keyed `:se`, LIPAS' locale keyword everywhere else — the source rows call
  it `sv`, and the translation happens at this boundary, not in the UI."
  (m/schema [:maybe [:map [:fi [:maybe :string]] [:se [:maybe :string]]]]))

(def municipality
  (m/schema
    [:maybe
     [:map
      [:code :string]
      [:name {:optional true} localized-name]]]))

(def reverse-geocode-response
  (m/schema
    [:map
     [:point [:map [:lat common/lat] [:lon common/lon]]]
     [:area [:maybe
             [:map
              [:postal-code :string]
              [:name localized-name]
              [:postal-office localized-name]
              [:municipality municipality]]]]
     [:addresses
      [:sequential
       [:map
        [:street localized-name]
        [:number [:maybe :string]]
        [:label [:maybe :string]]
        [:distance-m [:maybe :int]]
        [:municipality municipality]
        [:pelias-postal-code [:maybe :string]]
        [:posti [:maybe
                 [:map
                  [:postal-code :string]
                  [:postal-office localized-name]
                  [:exact :boolean]]]]]]]
     [:summary
      [:map
       [:postal-code [:maybe :string]]
       [:postal-code-source [:maybe [:enum :posti :paavo :pelias]]]
       [:postal-office localized-name]
       [:municipality municipality]
       [:region localized-name]
       [:address [:maybe :string]]
       [:address-distance-m [:maybe :int]]
       [:alternative-postal-code [:maybe :string]]
       [:sources [:map
                  [:pelias [:enum :ok :empty :error]]
                  [:paavo [:enum :ok :empty]]]]]]]))
