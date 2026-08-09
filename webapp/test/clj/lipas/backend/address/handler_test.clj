(ns lipas.backend.address.handler-test
  "Integration tests for GET /actions/reverse-geocode.

  Everything except Digitransit is real here: the route, its coercion, the
  Posti tables imported from the sample PCF/BAF files and a Paavo polygon in
  PostGIS. Pelias is the one thing that cannot be real — it is a paid third
  party over the network — so the app is built with a `:reverse-fn` in its
  `:pelias` config, which is a plain function and needs no mocking library.

  The polygon sits over central Helsinki and carries postal code 00250 while
  the BAF street there resolves to 00100, so the two halves of the answer
  disagree on purpose: that is what exercises the summary's precedence and
  the `:alternative-postal-code` caveat in one go."
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.address.db :as address-db]
    [lipas.backend.address.posti :as posti]
    [lipas.backend.email :as email]
    [lipas.backend.handler :as handler]
    [lipas.test-utils :as test-utils]
    [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))

(defn- app
  "The real handler, with `pelias-fn` standing in for Digitransit."
  [pelias-fn]
  (handler/create-app {:db (test-db)
                       :search (:lipas/search @test-system)
                       :emailer (email/->TestEmailer)
                       :pelias {:reverse-fn pelias-fn}}))

(defn- GET [pelias-fn query-string]
  (let [resp ((app pelias-fn) (mock/request :get (str "/api/actions/reverse-geocode" query-string)))]
    (assoc resp :body (test-utils/<-json (:body resp)))))

(defn- GET-transit
  "The same request the map panel makes. Transit is what carries the keyword
  and locale-keyed shape the UI reads; JSON flattens both."
  [pelias-fn query-string]
  (let [resp ((app pelias-fn) (-> (mock/request :get (str "/api/actions/reverse-geocode" query-string))
                                  (mock/header "Accept" "application/transit+json")))]
    (assoc resp :body (test-utils/<-transit (:body resp)))))

;;; Fixture data ;;;

(def ^:private helsinki-lat 60.17)
(def ^:private helsinki-lon 24.94)

(defn- extra-postal-code
  "The sample PCF file covers six codes, none of which is one of the sample
  BAF streets' — the two fixtures were sampled independently. These fill the
  gap so the postitoimipaikka and region joins have something to find."
  [code name-fi name-sv]
  {:postal-code code
   :name-fi name-fi
   :name-sv name-sv
   :type :normal
   :municipality-code "091"
   :municipality-name-fi "Helsinki"
   :municipality-name-sv "Helsingfors"
   :region-code "FI1B1"
   :region-name-fi "Helsinki-Uusimaa"
   :region-name-sv "Helsingfors-Nyland"
   :valid-from "2026-01-01"})

(defn- helsinki-square
  "A Paavo feature covering a tenth of a degree around the test point."
  [postal-code]
  {:type "Feature"
   :properties {:posti_alue postal-code
                :nimi "Taka-Töölö"
                :namn "Bortre Tölö"
                :kunta "091"
                :vuosi 2026}
   :geometry {:type "Polygon"
              :coordinates [[[24.9 60.1] [25.0 60.1] [25.0 60.2] [24.9 60.2] [24.9 60.1]]]}})

(defn- seed! []
  (let [db (test-db)
        pcf (:records (posti/parse-pcf-file (io/resource "posti/pcf-sample.dat")))
        baf (:records (posti/parse-baf-file (io/resource "posti/baf-sample.dat")))]
    (address-db/replace-postal-codes! db
                                      (conj (vec pcf)
                                            (extra-postal-code "00100" "HELSINKI" "HELSINGFORS")
                                            (extra-postal-code "00250" "HELSINKI" "HELSINGFORS"))
                                      "2026-08-07")
    (address-db/replace-street-segments! db baf "2026-08-01")
    (address-db/replace-paavo-areas! db [(helsinki-square "00250")] 2026)))

(defn- pelias-stub
  "Digitransit's answer for the test point: Mannerheimintie 5 next door, and
  a street BAF has never heard of a little further out."
  [_lat _lon]
  [{:street "Mannerheimintie"
    :housenumber "5"
    :name "Mannerheimintie 5"
    :label "Mannerheimintie 5, Helsinki"
    :localadmin "Helsinki"
    :postalcode "00100"
    :distance 0.041}
   {:street "Kuvitteellinenkatu"
    :housenumber "1"
    :name "Kuvitteellinenkatu 1"
    :label "Kuvitteellinenkatu 1, Helsinki"
    :localadmin "Helsinki"
    :postalcode "00100"
    :distance 0.2}])

(defn- boom [_lat _lon]
  (throw (ex-info "Digitransit is down" {})))

(def ^:private query
  (str "?lat=" helsinki-lat "&lon=" helsinki-lon))

;;; Tests ;;;

(deftest reverse-geocode-test
  (seed!)
  (let [{:keys [status body]} (GET pelias-stub query)
        {:keys [point area addresses summary]} body]

    (testing "the endpoint is public — no token, no 401"
      (is (= 200 status)))

    (testing "the clicked point comes back for the coordinate row"
      (is (= {:lat helsinki-lat :lon helsinki-lon} point)))

    (testing "the Paavo polygon containing the point, joined to Posti's names"
      (is (= {:postal-code "00250"
              :name {:fi "Taka-Töölö" :se "Bortre Tölö"}
              :postal-office {:fi "HELSINKI" :se "HELSINGFORS"}
              :municipality {:code "091" :name {:fi "Helsinki" :se "Helsingfors"}}}
             area)))

    (testing "addresses come back nearest first, checked against BAF"
      (is (= 2 (count addresses)))
      (is (= ["Mannerheimintie 5, Helsinki" "Kuvitteellinenkatu 1, Helsinki"]
             (mapv :label addresses)))
      (is (= [41 200] (mapv :distance-m addresses)))
      (is (= {:postal-code "00100"
              :postal-office {:fi "HELSINKI" :se "HELSINGFORS"}
              :exact true}
             (:posti (first addresses))))
      (is (nil? (:posti (second addresses)))
          "a street that is not in BAF gets no Posti verdict"))

    (testing "an address 41 m away outranks the polygon it disagrees with"
      (is (= "00100" (:postal-code summary)))
      (is (= "posti" (:postal-code-source summary))
          "keyword-valued fields arrive as strings over JSON")
      (is (= "00250" (:alternative-postal-code summary)))
      (is (= "Mannerheimintie 5" (:address summary)))
      (is (= 41 (:address-distance-m summary)))
      (is (= {:fi "HELSINKI" :se "HELSINGFORS"} (:postal-office summary)))
      (is (= {:fi "Helsinki-Uusimaa" :se "Helsingfors-Nyland"} (:region summary))))

    (testing "the municipality comes from the polygon, which contains the point"
      (is (= {:code "091" :name {:fi "Helsinki" :se "Helsingfors"}} (:municipality summary))))

    (testing "both sources answered"
      (is (= {:pelias "ok" :paavo "ok"} (:sources summary))))))

(deftest transit-negotiation-test
  (seed!)
  (let [{:keys [status body]} (GET-transit pelias-stub query)
        {:keys [summary]} body]

    (testing "the route content-negotiates transit"
      (is (= 200 status)))

    (testing "keyword-valued fields stay keywords, unlike over JSON"
      (is (= :posti (:postal-code-source summary)))
      (is (= {:pelias :ok :paavo :ok} (:sources summary))))

    (testing "Swedish proper names are keyed :se, LIPAS' locale keyword"
      (is (= {:fi "HELSINKI" :se "HELSINGFORS"} (:postal-office summary)))
      (is (= {:code "091" :name {:fi "Helsinki" :se "Helsingfors"}}
             (:municipality summary))))))

(deftest pelias-failure-still-answers-test
  (seed!)
  (let [{:keys [status body]} (GET boom query)
        {:keys [summary addresses]} body]

    (testing "a Digitransit outage is not a failed request"
      (is (= 200 status))
      (is (empty? addresses)))

    (testing "the Paavo half of the answer still stands"
      (is (= "00250" (:postal-code summary)))
      (is (= "paavo" (:postal-code-source summary)))
      (is (= {:fi "HELSINKI" :se "HELSINGFORS"} (:postal-office summary)))
      (is (= {:code "091" :name {:fi "Helsinki" :se "Helsingfors"}} (:municipality summary))))

    (testing "and the response says which half is missing"
      (is (= {:pelias "error" :paavo "ok"} (:sources summary))))))

(deftest empty-tables-test
  (testing "with nothing imported the endpoint still answers, emptily"
    (let [{:keys [status body]} (GET (fn [_ _] []) query)]
      (is (= 200 status))
      (is (nil? (:area body)))
      (is (nil? (get-in body [:summary :postal-code])))
      (is (= {:pelias "empty" :paavo "empty"} (get-in body [:summary :sources]))))))

(deftest coordinate-validation-test
  (let [called (atom 0)
        counting-stub (fn [lat lon] (swap! called inc) (pelias-stub lat lon))]

    (testing "coordinates outside Finland are rejected"
      (doseq [[lat lon what] [[10.0 24.94 "latitude far south"]
                              [80.0 24.94 "latitude far north"]
                              [60.17 0.0 "longitude far west"]
                              [60.17 40.0 "longitude far east"]]]
        (is (= 400 (:status (GET counting-stub (str "?lat=" lat "&lon=" lon)))) what)))

    (testing "so are non-numeric and missing coordinates"
      (is (= 400 (:status (GET counting-stub "?lat=abc&lon=24.94"))))
      (is (= 400 (:status (GET counting-stub "?lat=60.17"))))
      (is (= 400 (:status (GET counting-stub "")))))

    (testing "and none of them reached Digitransit"
      (is (zero? @called)))

    (testing "the corners of the accepted box (lipas.schema.common) are accepted"
      (doseq [[lat lon] [[59.0 18.0] [71.0 33.0]]]
        (is (= 200 (:status (GET counting-stub (str "?lat=" lat "&lon=" lon))))))
      (is (= 2 @called)))))
