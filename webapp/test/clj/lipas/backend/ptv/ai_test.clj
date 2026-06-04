(ns lipas.backend.ptv.ai-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.ptv.ai :as ai]))

(def ^:private raw-sports-site
  "Realistic sample mirroring what `sports_sites_current` returns."
  {:lipas-id 83622
   :name "Pattasten hiekkakenttä"
   :marketing-name nil
   :comment "Kenttä on yleisesti pesäpallokäytössä."
   :status "active"
   :event-date "2024-06-01T12:00:00.000Z"
   :construction-year 1985
   :renovation-years [2010 2020]
   :email "liikuntapaikat@example.fi"
   :phone-number "0406313928"
   :www "https://example.fi"
   :reservations-link "https://tilavaraus.example.fi/"
   :admin "city-sports"
   :owner "city"
   :type {:type-code 1340}
   :location {:city {:city-code 678 :neighborhood "Pattanen"}
              :address "Urheilijantie 10"
              :postal-code "92140"
              :postal-office "Pattijoki"
              :geometries {:type "FeatureCollection" :features []}}
   :properties {:surface-material ["sand"]
                :free-use? true
                :school-use? true
                :toilet? true
                :changing-rooms? true
                :field-length-m 85
                :field-width-m 40
                :area-m2 3400
                :stand-capacity-person 80
                :may-be-shown-in-harrastuspassi-fi? true
                :loudspeakers? false}
   :search-meta {:name "pattasten hiekkakenttä"
                 :admin {:name {:fi "Kunta / liikuntatoimi"}}
                 :owner {:name {:fi "Kunta"}}
                 :audits {:latest-audit-date nil}
                 :location {:wgs84-point [24.56 64.69]
                            :wgs84-center [24.56 64.69]
                            :city {:name {:fi "Raahe" :se "Brahestad" :en "Raahe"}}
                            :province {:name {:fi "Pohjois-Pohjanmaa"}}
                            :avi-area {:name {:fi "Pohjois-Suomen AVI"}}
                            :simple-geoms {:type "FeatureCollection"}}
                 :type {:name {:fi "Pallokenttä" :se "Bollplan" :en "Ball field"}
                        :tags {:fi []}
                        :main-category {:name {:fi "Ulkokentät ja liikuntapuistot"}}
                        :sub-category {:name {:fi "Pallokentät" :se "Bollplaner" :en "Ball games courts"}}}
                 :fields {:field-types []}
                 :activities nil}})

(deftest ->prompt-doc-keeps-citizen-relevant-top-level-fields
  (let [doc (ai/->prompt-doc raw-sports-site)]
    (is (= "Pattasten hiekkakenttä" (:name doc)))
    (is (= "Kenttä on yleisesti pesäpallokäytössä." (:comment doc)))
    (is (= "active" (:status doc)))
    (is (= "https://tilavaraus.example.fi/" (:reservations-link doc)))
    (is (= 83622 (:lipas-id doc)))))

(deftest ->prompt-doc-drops-contact-and-administrative-fields
  (let [doc (ai/->prompt-doc raw-sports-site)]
    (testing "contact info removed per no-contact-info policy"
      (is (not (contains? doc :email)))
      (is (not (contains? doc :phone-number)))
      (is (not (contains? doc :www))))
    (testing "year fields removed to avoid drive-by year mentions"
      (is (not (contains? doc :construction-year)))
      (is (not (contains? doc :renovation-years))))
    (testing "admin/owner duplicated in PTV structured fields"
      (is (not (contains? doc :admin)))
      (is (not (contains? doc :owner))))
    (testing "internal metadata excluded"
      (is (not (contains? doc :event-date)))
      (is (not (contains? doc :type))
          "raw :type is only a numeric code; resolved name lives in :search-meta"))))

(deftest ->prompt-doc-trims-location-to-neighborhood
  (let [doc (ai/->prompt-doc raw-sports-site)]
    (is (= {:neighborhood "Pattanen"} (:location doc)))
    (testing "street address and postal info dropped"
      (is (not (contains? (:location doc) :address)))
      (is (not (contains? (:location doc) :postal-code)))
      (is (not (contains? (:location doc) :postal-office))))))

(deftest ->prompt-doc-drops-location-entirely-when-no-neighborhood
  (let [site (update-in raw-sports-site [:location :city] dissoc :neighborhood)
        doc (ai/->prompt-doc site)]
    (is (not (contains? doc :location))
        "nothing to say about location beyond what search-meta already carries")))

(deftest ->prompt-doc-trims-search-meta-to-type-and-city-names
  (let [doc (ai/->prompt-doc raw-sports-site)]
    (is (= {:fi "Pallokenttä" :se "Bollplan" :en "Ball field"}
           (get-in doc [:search-meta :type :name])))
    (is (= {:fi "Pallokentät" :se "Bollplaner" :en "Ball games courts"}
           (get-in doc [:search-meta :type :sub-category])))
    (is (= {:fi "Raahe" :se "Brahestad" :en "Raahe"}
           (get-in doc [:search-meta :location :city :name])))
    (testing "coordinates and administrative hierarchy dropped"
      (is (not (contains? (get-in doc [:search-meta :location]) :wgs84-point)))
      (is (not (contains? (get-in doc [:search-meta :location]) :province)))
      (is (not (contains? (get-in doc [:search-meta :location]) :avi-area))))
    (testing "admin/owner/audits/fields dropped"
      (is (not (contains? (:search-meta doc) :admin)))
      (is (not (contains? (:search-meta doc) :owner)))
      (is (not (contains? (:search-meta doc) :audits)))
      (is (not (contains? (:search-meta doc) :fields))))))

(deftest ->prompt-doc-properties-allowlist-keeps-citizen-relevant
  (let [props (:properties (ai/->prompt-doc raw-sports-site))]
    (is (= ["sand"] (:surface-material props)))
    (is (true? (:free-use? props)))
    (is (true? (:school-use? props)))
    (is (true? (:toilet? props)))
    (is (true? (:changing-rooms? props)))))

(deftest ->prompt-doc-properties-drops-noisy-dimensions
  (let [props (:properties (ai/->prompt-doc raw-sports-site))]
    (is (not (contains? props :field-length-m)))
    (is (not (contains? props :field-width-m)))
    (is (not (contains? props :area-m2)))
    (testing "admin/internal flags dropped"
      (is (not (contains? props :may-be-shown-in-harrastuspassi-fi?)))
      (is (not (contains? props :loudspeakers?))))))

(deftest ->prompt-doc-encodes-stand-capacity-into-bucket
  (testing "buckets: <100 :small, <500 :medium, >=500 :large"
    (is (= :small  (-> (assoc-in raw-sports-site [:properties :stand-capacity-person]  50) ai/->prompt-doc :properties :stand-capacity-person)))
    (is (= :small  (-> (assoc-in raw-sports-site [:properties :stand-capacity-person]  99) ai/->prompt-doc :properties :stand-capacity-person)))
    (is (= :medium (-> (assoc-in raw-sports-site [:properties :stand-capacity-person] 100) ai/->prompt-doc :properties :stand-capacity-person)))
    (is (= :medium (-> (assoc-in raw-sports-site [:properties :stand-capacity-person] 499) ai/->prompt-doc :properties :stand-capacity-person)))
    (is (= :large  (-> (assoc-in raw-sports-site [:properties :stand-capacity-person] 500) ai/->prompt-doc :properties :stand-capacity-person)))))

(deftest ->prompt-doc-keeps-citizen-meaningful-linear-dimensions
  (testing "route and pool lengths stay as raw numbers (citizen-actionable)"
    (let [site  (assoc raw-sports-site
                       :properties {:route-length-km 8.5
                                    :lit-route-length-km 3.0
                                    :pool-length-m 25
                                    :track-length-m 400
                                    :beach-length-m 120})
          props (:properties (ai/->prompt-doc site))]
      (is (= 8.5 (:route-length-km props)))
      (is (= 3.0 (:lit-route-length-km props)))
      (is (= 25 (:pool-length-m props)))
      (is (= 400 (:track-length-m props)))
      (is (= 120 (:beach-length-m props))))))

(deftest ->prompt-doc-keeps-activity-indicator-counts
  (let [site  (assoc raw-sports-site
                     :properties {:basketball-fields-count 2
                                  :volleyball-fields-count 1
                                  :floorball-fields-count 1
                                  :holes-count 18})
        props (:properties (ai/->prompt-doc site))]
    (is (= 2 (:basketball-fields-count props)))
    (is (= 1 (:volleyball-fields-count props)))
    (is (= 1 (:floorball-fields-count props)))
    (is (= 18 (:holes-count props)))))

(deftest ->prompt-doc-drops-blank-fields
  (testing "nil, empty string, and empty collections are not emitted"
    (let [site  (-> raw-sports-site
                    (assoc :comment "")
                    (assoc :marketing-name nil)
                    (assoc-in [:properties :surface-material] []))
          doc   (ai/->prompt-doc site)
          props (:properties doc)]
      (is (not (contains? doc :comment)))
      (is (not (contains? doc :marketing-name)))
      (is (not (contains? props :surface-material))))))

(deftest ->prompt-doc-omits-properties-key-when-all-filtered-out
  (let [site (assoc raw-sports-site
                    :properties {:field-length-m 85
                                 :field-width-m 40
                                 :area-m2 3400})
        doc (ai/->prompt-doc site)]
    (is (not (contains? doc :properties))
        "don't emit an empty :properties map")))

(deftest ->prompt-doc-top-level-keys-are-strictly-the-allowlist
  (testing "no unexpected keys slip through for a fully populated site"
    (let [doc (ai/->prompt-doc raw-sports-site)
          expected #{:name :comment :status :reservations-link :lipas-id
                     :location :search-meta :properties}]
      (is (= expected (set (keys doc)))))))
