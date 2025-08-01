(ns legacy-api.transform-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [legacy-api.transform :as transform]
   [legacy-api.sports-place :as legacy-sports-place]
   [clojure.set :as set]))

(def old-lipas-sports-place-example
  {:properties
   {:infoFi "Pannakenttä, lasten leikkivälineitä, pieni jalkapallokenttä, ulkosäbäkaukalo. Osa suurempaa aluetta."
    :playground true
    :ligthing true
    :fieldsCount 1
    :iceRinksCount 1}
   :schoolUse true
   :email "email@dot.com"
   :admin "Kunta / tekninen toimi"
   :www "http://www.keuruu.fi/asukkaat/liikunta-ja-vapaa-aika"
   :name "Keuruun Koulukeskuksen lähiliikunta-alue /ala-aste"
   :type {:name "Lähiliikuntapaikka"
          :typeCode 1120}
   :constructionYear 2011
   :lastModified "2019-08-29 15:55:30.259"
   :sportsPlaceId 72269
   :phoneNumber "+358123456789"
   :location
   {:sportsPlaces [72269]
    :coordinates
    {:tm35fin {:lon 380848.639900476 :lat 6905404.53185308}
     :wgs84 {:lon 24.7051081551397 :lat 62.2613211721703}}
    :geometries
    {:type "FeatureCollection"
     :features
     [{:type "Feature"
       :geometry {:type "Point"
                  :coordinates [24.7051081551397 62.2613211721703]}
       :properties {:pointId 579183}}]}
    :locationId 584784
    :city {:name "Keuruu" :cityCode 249}
    :postalCode "42700"
    :postalOffice "Keuruu"
    :address "Keuruuntie 18"}
   :owner "Kunta"})

(def new-lipas-sports-place-example
  {:properties
   {:ligthing? true
    :playground? true
    :school-use? true
    :fields-count 1
    :ice-rinks-count 1}
   :email "email@dot.com"
   :phone-number "+358123456789"
   :admin "city-technical-services"
   :www "http://www.keuruu.fi/asukkaat/liikunta-ja-vapaa-aika"
   :name "Keuruun Koulukeskuksen lähiliikunta-alue /ala-aste"
   :construction-year 2011
   :type {:type-code 1120}
   :lipas-id 72269
   :status "active"
   :comment "Pannakenttä, lasten leikkivälineitä, pieni jalkapallokenttä, ulkosäbäkaukalo. Osa suurempaa aluetta."
   :event-date "2019-08-29T12:55:30.259Z"
   :location
   {:city {:city-code "249"}
    :address "Keuruuntie 18"
    :geometries
    {:type "FeatureCollection"
     :features
     [{:type "Feature"
       :geometry
       {:type "Point"
        :coordinates [24.7051081551397 62.2613211721703]}}]}
    :postal-code "42700"
    :postal-office "Keuruu"}
   :owner "city"})

(deftest transform-new-to-old-lipas-test
  (testing "Transform new LIPAS sports site to old sports place format"
    (let [result (transform/->old-lipas-sports-site new-lipas-sports-place-example)]

      (testing "Basic fields transformation"
        (is (= (:name result)
               {:fi "Keuruun Koulukeskuksen lähiliikunta-alue /ala-aste"}))
        (is (= (:email result) "email@dot.com"))
        (is (= (:phoneNumber result) "+358123456789"))
        (is (= (:www result) "http://www.keuruu.fi/asukkaat/liikunta-ja-vapaa-aika"))
        (is (= (:constructionYear result) 2011)))

      (testing "Admin and owner transformation"
        (is (= (:admin result) "city-technical-services"))
        (is (= (:owner result) "city")))

      (testing "Type transformation"
        (is (= (-> result :type :typeCode) 1120)))

      (testing "School use transformation"
        (is (= (:schoolUse result) true)))

      (testing "Properties transformation"
        (let [props (:properties result)]
          (is (= (:playground props) true))
          (is (= (:ligthing props) true))
          (is (= (:fieldsCount props) 1))
          (is (= (:iceRinksCount props) 1))
          (is (= (:infoFi props) "Pannakenttä, lasten leikkivälineitä, pieni jalkapallokenttä, ulkosäbäkaukalo. Osa suurempaa aluetta."))))

      (testing "Location transformation"
        (let [location (:location result)]
          (is (= (-> location :city :cityCode) "249"))
          (is (= (:address location) "Keuruuntie 18"))
          (is (= (:postalCode location) "42700"))
          (is (= (:postalOffice location) "Keuruu"))))

      (testing "Date transformation"
        (is (= (:lastModified result) "2019-08-29 15:55:30.259")))
      
      (testing "Type transformation"
        (is (= (-> result :type :typeCode) 1120))))))


(deftest prop-mappings-test
  (testing "Property mappings are consistent"
    (let [old-props (keys (:properties old-lipas-sports-place-example))
          new-props (keys (:properties new-lipas-sports-place-example))]

      (testing "Old properties can be mapped to new format"
        (doseq [old-prop old-props]
          (when-not (= old-prop :infoFi)
            (is (contains? legacy-sports-place/prop-mappings old-prop)
                (str "Missing mapping for old property: " old-prop)))))

      (testing "New properties can be mapped to old format"
        (doseq [new-prop new-props]
          (when-not (= new-prop :school-use?)
            (is (contains? legacy-sports-place/prop-mappings-reverse new-prop)
                (str "Missing reverse mapping for new property: " new-prop))))))))

(deftest prop-mappings-test
  (testing "Property mappings are consistent"
    (let [old-props (keys (:properties old-lipas-sports-place-example))
          new-props (keys (:properties new-lipas-sports-place-example))]

      (testing "Old properties can be mapped to new format"
        (doseq [old-prop old-props]
          (when-not (= old-prop :infoFi)
            (is (contains? legacy-sports-place/prop-mappings old-prop)
                (str "Missing mapping for old property: " old-prop)))))

      (testing "New properties can be mapped to old format"
        (doseq [new-prop new-props]
          (when-not (= new-prop :school-use?)
            (is (contains? legacy-sports-place/prop-mappings-reverse new-prop)
                (str "Missing reverse mapping for new property: " new-prop))))))))
