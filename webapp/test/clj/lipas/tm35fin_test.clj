(ns lipas.tm35fin-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.gis :as gis]
            [lipas.tm35fin :as sut]))

(defspec agrees-with-proj4-over-finland 500
  ;; Finland and a margin around it
  (prop/for-all [lon (gen/double* {:min 19.0 :max 32.0 :NaN? false :infinite? false})
                 lat (gen/double* {:min 59.0 :max 70.5 :NaN? false :infinite? false})]
                (let [[e n] (sut/from-wgs84 [lon lat])
                      [pe pn] (gis/wgs84->tm35fin-no-wrap [lon lat])]
                  (and (< (Math/abs (- e pe)) 0.001)
                       (< (Math/abs (- n pn)) 0.001)))))

(deftest known-points
  (is (= [500000.0 6874180.147715377] (sut/from-wgs84 [27.0 62.0]))
      "on the central meridian")
  (is (nil? (sut/from-wgs84 nil)))
  (is (nil? (sut/from-wgs84 [nil 60.0]))))
