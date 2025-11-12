(ns lipas.schema.sports-sites-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.schema.sports-sites :as sports-sites]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.generator :as mg]))

;; Basic field schemas

(deftest lipas-id-test
  (testing "Valid lipas-id values"
    (is (m/validate sports-sites/lipas-id 0))
    (is (m/validate sports-sites/lipas-id 1))
    (is (m/validate sports-sites/lipas-id 12345))
    (is (m/validate sports-sites/lipas-id 999999)))

  (testing "Invalid lipas-id values"
    (is (not (m/validate sports-sites/lipas-id -1)))
    (is (not (m/validate sports-sites/lipas-id -100)))
    (is (not (m/validate sports-sites/lipas-id 3.14)))
    (is (not (m/validate sports-sites/lipas-id "123")))
    (is (not (m/validate sports-sites/lipas-id nil)))))

(deftest name-test
  (testing "Valid names"
    (is (m/validate sports-sites/name "AB"))
    (is (m/validate sports-sites/name "Olympic Stadium"))
    (is (m/validate sports-sites/name "Olympiastadion"))
    (is (m/validate sports-sites/name (apply str (repeat 100 "a")))))

  (testing "Invalid names"
    (is (not (m/validate sports-sites/name "A"))) ; too short
    (is (not (m/validate sports-sites/name (apply str (repeat 101 "a"))))) ; too long
    (is (not (m/validate sports-sites/name "")))
    (is (not (m/validate sports-sites/name 123)))
    (is (not (m/validate sports-sites/name nil))))

  (testing "Unicode in names"
    (is (m/validate sports-sites/name "Töölön Kisahalli"))
    (is (m/validate sports-sites/name "Hämeenlinnan uimahalli"))))

(deftest marketing-name-test
  (testing "Valid marketing names"
    (is (m/validate sports-sites/marketing-name "AB"))
    (is (m/validate sports-sites/marketing-name "The Arena"))
    (is (m/validate sports-sites/marketing-name (apply str (repeat 100 "a")))))

  (testing "Invalid marketing names"
    (is (not (m/validate sports-sites/marketing-name "A")))
    (is (not (m/validate sports-sites/marketing-name (apply str (repeat 101 "a")))))
    (is (not (m/validate sports-sites/marketing-name "")))
    (is (not (m/validate sports-sites/marketing-name nil)))))

(deftest name-localized-test
  (testing "Valid localized names"
    (is (m/validate sports-sites/name-localized {})) ; both optional
    (is (m/validate sports-sites/name-localized {:se "Svenska namn"}))
    (is (m/validate sports-sites/name-localized {:en "English name"}))
    (is (m/validate sports-sites/name-localized {:se "Svenska namn" :en "English name"})))

  (testing "Invalid localized names"
    (is (not (m/validate sports-sites/name-localized {:se "A"}))) ; too short
    (is (not (m/validate sports-sites/name-localized {:en "B"}))) ; too short
    (is (not (m/validate sports-sites/name-localized {:se (apply str (repeat 101 "a"))}))) ; too long
    (is (not (m/validate sports-sites/name-localized {:se 123})))
    (is (not (m/validate sports-sites/name-localized "not a map")))
    (is (not (m/validate sports-sites/name-localized nil)))))

(deftest email-test
  (testing "Valid emails"
    (is (m/validate sports-sites/email "info@example.com"))
    (is (m/validate sports-sites/email "sports.facility@city.fi"))
    (is (m/validate sports-sites/email "contact+info@domain.org")))

  (testing "Invalid emails"
    (is (not (m/validate sports-sites/email "not-an-email")))
    (is (not (m/validate sports-sites/email "@example.com")))
    (is (not (m/validate sports-sites/email "user@")))
    (is (not (m/validate sports-sites/email "")))
    (is (not (m/validate sports-sites/email nil)))))

(deftest www-test
  (testing "Valid website URLs"
    (is (m/validate sports-sites/www "https://example.com"))
    (is (m/validate sports-sites/www "http://sports.city.fi"))
    (is (m/validate sports-sites/www "a")) ; min 1 char
    (is (m/validate sports-sites/www (apply str (repeat 500 "a")))))

  (testing "Invalid website URLs"
    (is (not (m/validate sports-sites/www ""))) ; too short
    (is (not (m/validate sports-sites/www (apply str (repeat 501 "a"))))) ; too long
    (is (not (m/validate sports-sites/www 123)))
    (is (not (m/validate sports-sites/www nil)))))

(deftest reservations-link-test
  (testing "Valid reservations links"
    (is (m/validate sports-sites/reservations-link "https://booking.example.com"))
    (is (m/validate sports-sites/reservations-link "a"))
    (is (m/validate sports-sites/reservations-link (apply str (repeat 500 "a")))))

  (testing "Invalid reservations links"
    (is (not (m/validate sports-sites/reservations-link "")))
    (is (not (m/validate sports-sites/reservations-link (apply str (repeat 501 "a")))))
    (is (not (m/validate sports-sites/reservations-link nil)))))

(deftest phone-number-test
  (testing "Valid phone numbers"
    (is (m/validate sports-sites/phone-number "+358 40 1234567"))
    (is (m/validate sports-sites/phone-number "040-1234567"))
    (is (m/validate sports-sites/phone-number "09-12345"))
    (is (m/validate sports-sites/phone-number "a"))
    (is (m/validate sports-sites/phone-number (apply str (repeat 50 "1")))))

  (testing "Invalid phone numbers"
    (is (not (m/validate sports-sites/phone-number "")))
    (is (not (m/validate sports-sites/phone-number (apply str (repeat 51 "1")))))
    (is (not (m/validate sports-sites/phone-number nil)))))

(deftest comment-test
  (testing "Valid comments"
    (is (m/validate sports-sites/comment "Additional info"))
    (is (m/validate sports-sites/comment "a"))
    (is (m/validate sports-sites/comment (apply str (repeat 2048 "a")))))

  (testing "Invalid comments"
    (is (not (m/validate sports-sites/comment "")))
    (is (not (m/validate sports-sites/comment (apply str (repeat 2049 "a")))))
    (is (not (m/validate sports-sites/comment nil))))

  (testing "Multiline comments"
    (is (m/validate sports-sites/comment "Line 1\nLine 2\nLine 3"))
    (is (m/validate sports-sites/comment "Bullet points:\n- Point 1\n- Point 2"))))

(deftest construction-year-test
  (testing "Valid construction years"
    (is (m/validate sports-sites/construction-year 1800))
    (is (m/validate sports-sites/construction-year 1900))
    (is (m/validate sports-sites/construction-year 2024))
    (is (m/validate sports-sites/construction-year utils/this-year))
    (is (m/validate sports-sites/construction-year (+ utils/this-year 10))))

  (testing "Invalid construction years"
    (is (not (m/validate sports-sites/construction-year 1799))) ; before min
    (is (not (m/validate sports-sites/construction-year (+ utils/this-year 11)))) ; after max
    (is (not (m/validate sports-sites/construction-year 2024.5)))
    (is (not (m/validate sports-sites/construction-year "2024")))
    (is (not (m/validate sports-sites/construction-year nil))))

  (testing "Property: construction year is always in valid range"
    (let [samples (repeatedly 50 #(+ 1800 (rand-int (- (+ utils/this-year 11) 1800))))]
      (is (every? #(m/validate sports-sites/construction-year %) samples)))))

(deftest renovation-years-test
  (testing "Valid renovation years"
    (is (m/validate sports-sites/renovation-years []))
    (is (m/validate sports-sites/renovation-years [2020]))
    (is (m/validate sports-sites/renovation-years [1990 2000 2010]))
    (is (m/validate sports-sites/renovation-years [1800 (+ utils/this-year 10)])))

  (testing "Invalid renovation years"
    (is (not (m/validate sports-sites/renovation-years [1799])))
    (is (not (m/validate sports-sites/renovation-years [(+ utils/this-year 11)])))
    (is (not (m/validate sports-sites/renovation-years [2024.5])))
    (is (not (m/validate sports-sites/renovation-years ["2024"])))
    (is (not (m/validate sports-sites/renovation-years #{2020}))) ; set, not sequential
    (is (not (m/validate sports-sites/renovation-years nil)))))

(deftest owner-test
  (testing "Valid owner values"
    (is (m/validate sports-sites/owner "city"))
    (is (m/validate sports-sites/owner "company-ltd"))
    (is (m/validate sports-sites/owner "foundation"))
    (is (m/validate sports-sites/owner "state"))
    (is (m/validate sports-sites/owner "registered-association")))

  (testing "Invalid owner values"
    (is (not (m/validate sports-sites/owner "invalid-owner")))
    (is (not (m/validate sports-sites/owner :city))) ; keyword not accepted
    (is (not (m/validate sports-sites/owner "")))
    (is (not (m/validate sports-sites/owner nil)))))

(deftest owners-test
  (testing "Valid owner sets"
    (is (m/validate sports-sites/owners #{"city"}))
    (is (m/validate sports-sites/owners #{"city" "state"}))
    (is (m/validate sports-sites/owners #{}))) ; empty set valid

  (testing "Invalid owner sets"
    (is (not (m/validate sports-sites/owners #{"invalid-owner"})))
    (is (not (m/validate sports-sites/owners #{"city" "invalid"})))
    (is (not (m/validate sports-sites/owners ["city"]))) ; vector not set
    (is (not (m/validate sports-sites/owners nil)))))

(deftest admin-test
  (testing "Valid admin values"
    (is (m/validate sports-sites/admin "city-sports"))
    (is (m/validate sports-sites/admin "city-technical-services"))
    (is (m/validate sports-sites/admin "city-education"))
    (is (m/validate sports-sites/admin "private-company"))
    (is (m/validate sports-sites/admin "state")))

  (testing "Invalid admin values"
    (is (not (m/validate sports-sites/admin "invalid-admin")))
    (is (not (m/validate sports-sites/admin :city-sports)))
    (is (not (m/validate sports-sites/admin "")))
    (is (not (m/validate sports-sites/admin nil)))))

(deftest admins-test
  (testing "Valid admin sets"
    (is (m/validate sports-sites/admins #{"city-sports"}))
    (is (m/validate sports-sites/admins #{"city-sports" "state"}))
    (is (m/validate sports-sites/admins #{})))

  (testing "Invalid admin sets"
    (is (not (m/validate sports-sites/admins #{"invalid-admin"})))
    (is (not (m/validate sports-sites/admins #{"city-sports" "invalid"})))
    (is (not (m/validate sports-sites/admins ["city-sports"])))
    (is (not (m/validate sports-sites/admins nil)))))

;; Edge cases and property tests

(deftest string-fields-edge-cases-test
  (testing "Unicode characters in string fields"
    (is (m/validate sports-sites/name "Töölön pallokenttä"))
    (is (m/validate sports-sites/marketing-name "Käpylän jäähalli"))
    (is (m/validate sports-sites/comment "Huom! Åland-erikoisuus"))
    (is (m/validate sports-sites/phone-number "+358 (0)40 123 4567")))

  (testing "Special characters in string fields"
    (is (m/validate sports-sites/name "St. John's Stadium"))
    (is (m/validate sports-sites/www "https://example.com/path?query=value&foo=bar"))
    (is (m/validate sports-sites/reservations-link "https://booking.com/facility#section"))
    (is (m/validate sports-sites/comment "Contact: email@example.com or call +358-40-1234567")))

  (testing "Boundary length values"
    ;; Name: 2-100 chars
    (is (m/validate sports-sites/name (apply str (repeat 99 "a"))))
    (is (m/validate sports-sites/name (apply str (repeat 100 "a"))))
    (is (not (m/validate sports-sites/name (apply str (repeat 101 "a")))))

    ;; WWW: 1-500 chars
    (is (m/validate sports-sites/www (apply str (repeat 499 "a"))))
    (is (m/validate sports-sites/www (apply str (repeat 500 "a"))))
    (is (not (m/validate sports-sites/www (apply str (repeat 501 "a")))))

    ;; Phone: 1-50 chars
    (is (m/validate sports-sites/phone-number (apply str (repeat 49 "1"))))
    (is (m/validate sports-sites/phone-number (apply str (repeat 50 "1"))))
    (is (not (m/validate sports-sites/phone-number (apply str (repeat 51 "1")))))

    ;; Comment: 1-2048 chars
    (is (m/validate sports-sites/comment (apply str (repeat 2047 "a"))))
    (is (m/validate sports-sites/comment (apply str (repeat 2048 "a"))))
    (is (not (m/validate sports-sites/comment (apply str (repeat 2049 "a")))))))

(deftest year-fields-properties-test
  (testing "Property: construction year is always within valid range"
    (let [min-year 1800
          max-year (+ utils/this-year 10)
          samples (repeatedly 100 #(+ min-year (rand-int (- max-year min-year))))]
      (is (every? int? samples))
      (is (every? #(<= min-year % max-year) samples))
      (is (every? #(m/validate sports-sites/construction-year %) samples))))

  (testing "Property: renovation years array elements are all valid"
    (let [valid-years [1950 1975 2000 2015 2024]
          invalid-years [1799 (+ utils/this-year 11)]]
      (is (m/validate sports-sites/renovation-years valid-years))
      (is (not (m/validate sports-sites/renovation-years (conj valid-years 1799))))
      (is (not (m/validate sports-sites/renovation-years (conj valid-years (+ utils/this-year 11))))))))

;; Schema building function tests

(deftest make-sports-site-schema-basic-structure-test
  (testing "Basic schema structure for Point geometry"
    (let [schema (sports-sites/make-sports-site-schema
                  {:title "Test Sports Site"
                   :description "Test description"
                   :type-codes #{1234}
                   :location-schema [:map [:test :string]]
                   :extras-schema [:map]})
          form (m/form schema)]

      (is (= :map (first form)))
      (is (map? (second form)))

      ;; Check required fields are present
      (let [fields (into #{} (map first (drop 2 form)))]
        (is (contains? fields :lipas-id))
        (is (contains? fields :event-date))
        (is (contains? fields :status))
        (is (contains? fields :name))
        (is (contains? fields :owner))
        (is (contains? fields :admin))
        (is (contains? fields :type))
        (is (contains? fields :location)))

      ;; Check metadata
      (is (= "Test Sports Site" (:title (second form))))
      (is (= "Test description" (:description (second form))))
      (is (= false (:closed (second form))))))

  (testing "Optional fields are marked as optional"
    (let [schema (sports-sites/make-sports-site-schema
                  {:title "Test"
                   :type-codes #{1234}
                   :location-schema [:map]
                   :extras-schema [:map]})
          form (m/form schema)
          optional-fields #{:marketing-name :name-localized :email :www
                            :reservations-link :phone-number :comment
                            :construction-year :renovation-years}]
      (doseq [field (drop 2 form)]
        (when (optional-fields (first field))
          (is (true? (-> field second :optional))
              (str "Field " (first field) " should be optional")))))))

(deftest make-sports-site-schema-type-code-structure-test
  (testing "Type code is nested in type map"
    (let [schema (sports-sites/make-sports-site-schema
                  {:title "Test"
                   :type-codes #{1530 2000}
                   :location-schema [:map]
                   :extras-schema [:map]})
          form (m/form schema)
          type-field (first (filter #(= :type (first %)) (drop 2 form)))
          type-map (second type-field)
          type-code-field (second type-map)] ; [:type-code [:enum 1530 2000]]

      (is (= :map (first type-map)))
      (is (= :type-code (first type-code-field)))
      (is (= :enum (first (second type-code-field))))
      (is (= #{1530 2000} (set (drop 1 (second type-code-field))))))))

(deftest make-sports-site-schema-compat-type-code-structure-test
  (testing "Compat version has :encode/json identity in type-code enum"
    (let [schema (sports-sites/make-sports-site-schema
                  {:title "Test"
                   :type-codes #{1530 2000}
                   :location-schema [:map]
                   :extras-schema [:map]}
                  true) ; compat? = true
          form (m/form schema)
          type-field (first (filter #(= :type (first %)) (drop 2 form)))
          type-map (second type-field)
          type-code-field (second type-map) ; [:type-code [:enum {...} 1530 2000]]
          enum-spec (second type-code-field)]

      (is (= :enum (first enum-spec)))
      (is (map? (second enum-spec)))
      (is (= identity (get (second enum-spec) :encode/json)))
      (is (= #{1530 2000} (set (drop 2 enum-spec)))))))

(deftest location-schema-city-code-difference-test
  (testing "Compat version uses location schemas with :encode/json identity in city-code"
    (let [normal-schema (sports-sites/make-sports-site-schema
                         {:title "Test"
                          :type-codes #{1530}
                          :location-schema [:map
                                            [:city [:map
                                                    [:city-code [:enum 91 92]]]]
                                            [:address :string]]
                          :extras-schema [:map]})
          compat-schema (sports-sites/make-sports-site-schema
                         {:title "Test"
                          :type-codes #{1530}
                          :location-schema [:map
                                            [:city [:map
                                                    [:city-code [:enum {:encode/json identity} 91 92]]]]
                                            [:address :string]]
                          :extras-schema [:map]}
                         true)
          normal-form (m/form normal-schema)
          compat-form (m/form compat-schema)

          ;; Navigate to city-code in normal schema
          normal-location (first (filter #(= :location (first %)) (drop 2 normal-form)))
          normal-location-map (second normal-location)
          normal-city (second normal-location-map)
          normal-city-map (second normal-city)
          normal-city-code (second normal-city-map)
          normal-enum (second normal-city-code)

          ;; Navigate to city-code in compat schema
          compat-location (first (filter #(= :location (first %)) (drop 2 compat-form)))
          compat-location-map (second compat-location)
          compat-city (second compat-location-map)
          compat-city-map (second compat-city)
          compat-city-code (second compat-city-map)
          compat-enum (second compat-city-code)]

      ;; Normal version has no :encode/json
      (is (= :enum (first normal-enum)))
      (is (or (not (map? (second normal-enum)))
              (nil? (:encode/json (second normal-enum)))))

      ;; Compat version has :encode/json identity
      (is (= :enum (first compat-enum)))
      (is (map? (second compat-enum)))
      (is (= identity (:encode/json (second compat-enum))))))

  (testing "sports-site and sports-site-compat use different location schemas"
    ;; Verify that type code 1530 uses point-location vs point-location-compat
    (let [normal-form (m/form sports-sites/sports-site)
          compat-form (m/form sports-sites/sports-site-compat)

          ;; Get schema for type code 1530
          type-1530-normal (nth (drop 2 normal-form)
                                (.indexOf (map first (drop 2 normal-form)) 1530))
          type-1530-compat (nth (drop 2 compat-form)
                                (.indexOf (map first (drop 2 compat-form)) 1530))

          normal-schema-map (second type-1530-normal)
          compat-schema-map (second type-1530-compat)

          ;; Get location field
          normal-location (first (filter #(= :location (first %)) (drop 2 normal-schema-map)))
          compat-location (first (filter #(= :location (first %)) (drop 2 compat-schema-map)))]

      ;; Both should have location field
      (is (not (nil? normal-location)))
      (is (not (nil? compat-location)))

      ;; The location schemas are different vars
      (is (not= (second normal-location) (second compat-location))))))

(deftest schema-functions-produce-equivalent-validation-test
  (testing "Both schema functions validate the same valid data"
    (let [valid-site {:lipas-id 12345
                      :event-date "2025-01-01T00:00:00.000Z"
                      :status "active"
                      :name "Test Facility"
                      :owner "city"
                      :admin "city-sports"
                      :type {:type-code 1530}
                      :location {:city {:city-code 91}
                                 :address "Test Street 1"
                                 :postal-code "00100"
                                 :geometries {:type "FeatureCollection"
                                              :features [{:type "Feature"
                                                          :geometry {:type "Point"
                                                                     :coordinates [25.0 60.0]}}]}}}
          normal-schema (sports-sites/make-sports-site-schema
                         {:title "Test"
                          :type-codes #{1530}
                          :location-schema [:map
                                            [:city [:map [:city-code :int]]]
                                            [:address :string]
                                            [:postal-code :string]
                                            [:geometries :any]]
                          :extras-schema [:map]})
          compat-schema (sports-sites/make-sports-site-schema
                         {:title "Test"
                          :type-codes #{1530}
                          :location-schema [:map
                                            [:city [:map [:city-code :int]]]
                                            [:address :string]
                                            [:postal-code :string]
                                            [:geometries :any]]
                          :extras-schema [:map]}
                         true)]

      (is (m/validate normal-schema valid-site))
      (is (m/validate compat-schema valid-site))))

  (testing "Both schema functions reject the same invalid data"
    (let [invalid-sites [{:lipas-id -1 ; negative lipas-id
                          :event-date "2025-01-01T00:00:00.000Z"
                          :status "active"
                          :name "Test"
                          :owner "city"
                          :admin "city-sports"
                          :type {:type-code 1530}
                          :location {:city {:city-code 91}
                                     :address "Test"
                                     :postal-code "00100"
                                     :geometries {}}}
                         {:lipas-id 123
                          :event-date "2025-01-01T00:00:00.000Z"
                          :status "active"
                          :name "X" ; too short
                          :owner "city"
                          :admin "city-sports"
                          :type {:type-code 1530}
                          :location {:city {:city-code 91}
                                     :address "Test"
                                     :postal-code "00100"
                                     :geometries {}}}
                         {:lipas-id 123
                          :event-date "2025-01-01T00:00:00.000Z"
                          :status "active"
                          :name "Test"
                          :owner "invalid-owner" ; invalid enum
                          :admin "city-sports"
                          :type {:type-code 1530}
                          :location {:city {:city-code 91}
                                     :address "Test"
                                     :postal-code "00100"
                                     :geometries {}}}]
          normal-schema (sports-sites/make-sports-site-schema
                         {:title "Test"
                          :type-codes #{1530}
                          :location-schema [:map
                                            [:city [:map [:city-code :int]]]
                                            [:address :string]
                                            [:postal-code :string]
                                            [:geometries :any]]
                          :extras-schema [:map]})
          compat-schema (sports-sites/make-sports-site-schema
                         {:title "Test"
                          :type-codes #{1530}
                          :location-schema [:map
                                            [:city [:map [:city-code :int]]]
                                            [:address :string]
                                            [:postal-code :string]
                                            [:geometries :any]]
                          :extras-schema [:map]}
                         true)]

      (doseq [invalid-site invalid-sites]
        (is (not (m/validate normal-schema invalid-site)))
        (is (not (m/validate compat-schema invalid-site)))))))

(deftest extras-schema-merging-test
  (testing "Extras schema is properly merged into base schema"
    (let [extras [:map
                  [:custom-field {:optional true} :string]
                  [:another-field {:optional true} :int]]
          schema (sports-sites/make-sports-site-schema
                  {:title "Test"
                   :type-codes #{1234}
                   :location-schema [:map]
                   :extras-schema extras})
          form (m/form schema)
          fields (into #{} (map first (drop 2 form)))]

      (is (contains? fields :custom-field))
      (is (contains? fields :another-field))))

  (testing "Empty extras schema works"
    (let [schema (sports-sites/make-sports-site-schema
                  {:title "Test"
                   :type-codes #{1234}
                   :location-schema [:map]
                   :extras-schema [:map]})]
      (is (m/validate schema {:lipas-id 1
                              :event-date "2025-01-01T00:00:00.000Z"
                              :status "active"
                              :name "Test"
                              :owner "city"
                              :admin "city-sports"
                              :type {:type-code 1234}
                              :location {}}))))

  (testing "Extras with properties field"
    (let [extras [:map
                  [:properties {:optional true}
                   [:map
                    [:field-length-m {:optional true} :int]
                    [:surface-material {:optional true} :string]]]]
          schema (sports-sites/make-sports-site-schema
                  {:title "Test"
                   :type-codes #{1234}
                   :location-schema [:map]
                   :extras-schema extras})]

      (is (m/validate schema {:lipas-id 1
                              :event-date "2025-01-01T00:00:00.000Z"
                              :status "active"
                              :name "Test"
                              :owner "city"
                              :admin "city-sports"
                              :type {:type-code 1234}
                              :location {}
                              :properties {:field-length-m 100
                                           :surface-material "artificial-turf"}})))))

;; Multi-schema tests for sports-site and sports-site-compat

(deftest sports-site-multi-schema-structure-test
  (testing "sports-site is a multi-schema with dispatch on type-code"
    (is (= :multi (first sports-sites/sports-site)))
    (let [opts (second sports-sites/sports-site)]
      (is (map? opts))
      (is (fn? (:dispatch opts)))
      (is (string? (:description opts)))))

  (testing "sports-site-compat is a multi-schema with same structure"
    (is (= :multi (first sports-sites/sports-site-compat)))
    (let [opts (second sports-sites/sports-site-compat)]
      (is (map? opts))
      (is (fn? (:dispatch opts)))
      (is (string? (:description opts)))))

  (testing "Both multi-schemas have the same type codes"
    (let [normal-codes (set (map first (drop 2 sports-sites/sports-site)))
          compat-codes (set (map first (drop 2 sports-sites/sports-site-compat)))]
      (is (= normal-codes compat-codes))
      (is (> (count normal-codes) 0)))))

(deftest sports-site-dispatch-test
  (testing "Dispatch function works correctly"
    (let [dispatch-fn (-> sports-sites/sports-site second :dispatch)]
      (is (= 1530 (dispatch-fn {:type {:type-code 1530}})))
      (is (= 2000 (dispatch-fn {:type {:type-code 2000}})))
      (is (= 101 (dispatch-fn {:type {:type-code 101}}))))))

(deftest sports-site-validates-known-type-codes-test
  (testing "Validates a swimming pool (type-code 3110)"
    (let [swimming-pool {:lipas-id 12345
                         :event-date "2025-01-01T00:00:00.000Z"
                         :status "active"
                         :name "Test Swimming Pool"
                         :owner "city"
                         :admin "city-sports"
                         :type {:type-code 3110}
                         :location {:city {:city-code 91}
                                    :address "Pool Street 1"
                                    :postal-code "00100"
                                    :geometries {:type "FeatureCollection"
                                                 :features [{:type "Feature"
                                                             :geometry {:type "Point"
                                                                        :coordinates [25.0 60.0]}}]}}}]
      (is (m/validate sports-sites/sports-site swimming-pool))
      (is (m/validate sports-sites/sports-site-compat swimming-pool))))

  (testing "Validates an outdoor recreation route (type-code 4405)"
    (let [route {:lipas-id 12345
                 :event-date "2025-01-01T00:00:00.000Z"
                 :status "active"
                 :name "Test Route"
                 :owner "city"
                 :admin "city-sports"
                 :type {:type-code 4405}
                 :location {:city {:city-code 91}
                            :address "Route Start"
                            :postal-code "00100"
                            :geometries {:type "FeatureCollection"
                                         :features [{:type "Feature"
                                                     :geometry {:type "LineString"
                                                                :coordinates [[25.0 60.0] [25.1 60.1]]}}]}}}]
      (is (m/validate sports-sites/sports-site route))
      (is (m/validate sports-sites/sports-site-compat route))))

  (testing "Validates a sports field (type-code 101)"
    (let [field {:lipas-id 12345
                 :event-date "2025-01-01T00:00:00.000Z"
                 :status "active"
                 :name "Test Field"
                 :owner "city"
                 :admin "city-sports"
                 :type {:type-code 101}
                 :location {:city {:city-code 91}
                            :address "Field Street 1"
                            :postal-code "00100"
                            :geometries {:type "FeatureCollection"
                                         :features [{:type "Feature"
                                                     :geometry {:type "Polygon"
                                                                :coordinates [[[25.0 60.0] [25.1 60.0] [25.1 60.1] [25.0 60.1] [25.0 60.0]]]}}]}}}]
      (is (m/validate sports-sites/sports-site field))
      (is (m/validate sports-sites/sports-site-compat field)))))

(deftest sports-site-with-properties-test
  (testing "Type codes with properties validate correctly"
    ;; Using a type code that has properties defined
    (let [site-with-props {:lipas-id 12345
                           :event-date "2025-01-01T00:00:00.000Z"
                           :status "active"
                           :name "Test Facility"
                           :owner "city"
                           :admin "city-sports"
                           :type {:type-code 2510} ; Ice rink
                           :location {:city {:city-code 91}
                                      :address "Ice Street 1"
                                      :postal-code "00100"
                                      :geometries {:type "FeatureCollection"
                                                   :features [{:type "Feature"
                                                               :geometry {:type "Point"
                                                                          :coordinates [25.0 60.0]}}]}}
                           :properties {:field-length-m 60
                                        :field-width-m 30}}]
      (is (m/validate sports-sites/sports-site site-with-props))
      (is (m/validate sports-sites/sports-site-compat site-with-props)))))

(deftest floorball-type-codes-test
  (testing "Floorball type codes (2240, 2150, 2210, 2220) have extra fields"
    (let [floorball-codes [2240 2150 2210 2220]
          multi-form (m/form sports-sites/sports-site)]
      (doseq [type-code floorball-codes]
        (let [normal-schema (nth (drop 2 multi-form)
                                 (.indexOf (map first (drop 2 multi-form)) type-code))
              schema-map (second normal-schema)
              fields (into #{} (map first (drop 2 schema-map)))]

          (is (contains? fields :fields)
              (str "Type code " type-code " should have :fields"))
          (is (contains? fields :locker-rooms)
              (str "Type code " type-code " should have :locker-rooms"))
          (is (contains? fields :audits)
              (str "Type code " type-code " should have :audits"))
          (is (contains? fields :circumstances)
              (str "Type code " type-code " should have :circumstances"))))))

  (testing "Non-floorball type codes don't have floorball fields"
    (let [non-floorball-codes [101 3110 1530]
          multi-form (m/form sports-sites/sports-site)]
      (doseq [type-code non-floorball-codes]
        (let [normal-schema (nth (drop 2 multi-form)
                                 (.indexOf (map first (drop 2 multi-form)) type-code))
              schema-map (second normal-schema)
              fields (into #{} (map first (drop 2 schema-map)))]

          (is (not (contains? fields :fields))
              (str "Type code " type-code " should not have :fields"))
          (is (not (contains? fields :locker-rooms))
              (str "Type code " type-code " should not have :locker-rooms")))))))

(deftest new-sports-site-schema-test
  (testing "new-sports-site doesn't require lipas-id"
    (let [new-site {:event-date "2025-01-01T00:00:00.000Z"
                    :status "active"
                    :name "New Facility"
                    :owner "city"
                    :admin "city-sports"
                    :type {:type-code 1530}
                    :location {:city {:city-code 91}
                               :address "New Street 1"
                               :postal-code "00100"
                               :geometries {:type "FeatureCollection"
                                            :features [{:type "Feature"
                                                        :geometry {:type "Point"
                                                                   :coordinates [25.0 60.0]}}]}}}]
      (is (m/validate sports-sites/new-sports-site new-site))
      (is (not (m/validate sports-sites/sports-site new-site)))))

  (testing "new-sports-site accepts lipas-id if provided"
    (let [site-with-id {:lipas-id 12345
                        :event-date "2025-01-01T00:00:00.000Z"
                        :status "active"
                        :name "Facility"
                        :owner "city"
                        :admin "city-sports"
                        :type {:type-code 1530}
                        :location {:city {:city-code 91}
                                   :address "Street 1"
                                   :postal-code "00100"
                                   :geometries {:type "FeatureCollection"
                                                :features [{:type "Feature"
                                                            :geometry {:type "Point"
                                                                       :coordinates [25.0 60.0]}}]}}}]
      (is (m/validate sports-sites/new-sports-site site-with-id))
      (is (m/validate sports-sites/sports-site site-with-id)))))

;; Property-based tests for schema consistency

(deftest schema-consistency-property-test
  (testing "Property: All type codes in sports-site are in sports-site-compat"
    (let [normal-form (m/form sports-sites/sports-site)
          compat-form (m/form sports-sites/sports-site-compat)
          normal-codes (set (map first (drop 2 normal-form)))
          compat-codes (set (map first (drop 2 compat-form)))]
      (is (= normal-codes compat-codes))))

  (testing "Property: All schemas have required base fields"
    (let [required-fields #{:lipas-id :event-date :status :name :owner :admin :type :location}
          multi-form (m/form sports-sites/sports-site)
          type-codes (take 10 (map first (drop 2 multi-form)))]
      (doseq [type-code type-codes]
        (let [schema (nth (drop 2 multi-form)
                          (.indexOf (map first (drop 2 multi-form)) type-code))
              schema-map (second schema)
              fields (set (map first (drop 2 schema-map)))]
          (is (clojure.set/subset? required-fields fields)
              (str "Type code " type-code " missing required fields: "
                   (clojure.set/difference required-fields fields)))))))

  (testing "Property: Optional fields are consistently marked across type codes"
    (let [optional-fields #{:marketing-name :name-localized :email :www
                            :reservations-link :phone-number :comment
                            :construction-year :renovation-years}
          multi-form (m/form sports-sites/sports-site)
          type-codes (take 10 (map first (drop 2 multi-form)))]
      (doseq [type-code type-codes]
        (let [schema (nth (drop 2 multi-form)
                          (.indexOf (map first (drop 2 multi-form)) type-code))
              schema-map (second schema)]
          (doseq [field (drop 2 schema-map)]
            (when (optional-fields (first field))
              (is (true? (-> field second :optional))
                  (str "Type code " type-code " field " (first field) " should be optional")))))))))

(comment
  (require '[clojure.test :as t])
  (t/run-tests 'lipas.schema.sports-sites-test)

  ;; Run specific test groups
  (t/test-var #'make-sports-site-schema-basic-structure-test)
  (t/test-var #'make-sports-site-schema-compat-type-code-structure-test)
  (t/test-var #'schema-functions-produce-equivalent-validation-test)
  (t/test-var #'sports-site-multi-schema-structure-test)
  (t/test-var #'floorball-type-codes-test)
  (t/test-var #'schema-consistency-property-test))
