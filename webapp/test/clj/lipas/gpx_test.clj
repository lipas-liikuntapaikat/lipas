(ns lipas.gpx-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.xml :as xml]
            [lipas.gpx :as gpx])
  (:import [java.io ByteArrayInputStream]))

(defn- parse [^String s]
  (xml/parse (ByteArrayInputStream. (.getBytes s "UTF-8"))))

(defn- children [node tag]
  (filter #(= tag (:tag %)) (:content node)))

(defn- child [node tag]
  (first (children node tag)))

(defn- text [node]
  (apply str (:content node)))

(def route-fcoll
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :properties {:name "Ähtärin \"kunto\" & <polku>"
                  :type "Kuntorata"
                  :city "Ähtäri"}
     :geometry {:type "LineString"
                :coordinates [[25.1 62.5 101.2]
                              [25.2 62.6 103.0]]}}]})

(deftest linestring-test
  (let [s (gpx/geojson->gpx route-fcoll)
        doc (parse s)]
    (testing "document root"
      (is (.startsWith ^String s "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
      (is (= :gpx (:tag doc)))
      (is (= "1.1" (-> doc :attrs :version)))
      (is (= "LIPAS" (-> doc :attrs :creator)))
      (is (= "http://www.topografix.com/GPX/1/1" (-> doc :attrs :xmlns))))
    (testing "track structure"
      (let [trk (child doc :trk)
            seg (child trk :trkseg)
            pts (children seg :trkpt)]
        (is (some? trk))
        (is (= 1 (count (children trk :trkseg))))
        (is (= 2 (count pts)))
        (testing "GeoJSON [lon lat ele] maps to lat/lon attrs + ele child"
          (is (= "62.5" (-> pts first :attrs :lat)))
          (is (= "25.1" (-> pts first :attrs :lon)))
          (is (= "101.2" (text (child (first pts) :ele)))))))
    (testing "name is escaped and round-trips"
      (is (= "Ähtärin \"kunto\" & <polku>"
             (text (child (child doc :trk) :name)))))
    (testing "desc concatenates scalar props as k=v lines"
      (is (= "name=Ähtärin \"kunto\" & <polku>\ntype=Kuntorata\ncity=Ähtäri"
             (text (child (child doc :trk) :desc)))))))

(deftest point-test
  (let [doc (parse (gpx/geojson->gpx
                     {:type "Feature"
                      :properties {:name "Kenttä"}
                      :geometry {:type "Point"
                                 :coordinates [24.94 60.17 8.0]}}))
        wpt (child doc :wpt)]
    (is (some? wpt))
    (is (= "60.17" (-> wpt :attrs :lat)))
    (is (= "24.94" (-> wpt :attrs :lon)))
    (testing "ele comes before name per GPX 1.1 schema order"
      (is (= [:ele :name :desc] (mapv :tag (:content wpt)))))
    (is (= "8.0" (text (child wpt :ele))))
    (is (= "Kenttä" (text (child wpt :name))))))

(deftest polygon-test
  (testing "each ring becomes its own trkseg"
    (let [doc (parse (gpx/geojson->gpx
                       {:type "Feature"
                        :properties {}
                        :geometry {:type "Polygon"
                                   :coordinates
                                   [[[25.0 62.0] [25.1 62.0] [25.1 62.1] [25.0 62.0]]
                                    [[25.02 62.02] [25.04 62.02] [25.04 62.04] [25.02 62.02]]]}}))
          trk (child doc :trk)]
      (is (= 1 (count (children doc :trk))))
      (is (= 2 (count (children trk :trkseg))))
      (testing "no name/desc elements when props are empty"
        (is (empty? (children trk :name)))
        (is (empty? (children trk :desc)))))))

(deftest multi-geometry-test
  (testing "MultiLineString becomes one trk with a trkseg per line"
    (let [doc (parse (gpx/geojson->gpx
                       {:type "MultiLineString"
                        :coordinates [[[25.0 62.0] [25.1 62.1]]
                                      [[26.0 63.0] [26.1 63.1]]]}))]
      (is (= 1 (count (children doc :trk))))
      (is (= 2 (count (children (child doc :trk) :trkseg))))))
  (testing "GeometryCollection emits both wpts and trks"
    (let [doc (parse (gpx/geojson->gpx
                       {:type "Feature"
                        :properties {:name "Monigeometria"}
                        :geometry {:type "GeometryCollection"
                                   :geometries
                                   [{:type "Point" :coordinates [25.0 62.0]}
                                    {:type "LineString"
                                     :coordinates [[25.0 62.0] [25.1 62.1]]}]}}))]
      (is (= 1 (count (children doc :wpt))))
      (is (= 1 (count (children doc :trk)))))))

(deftest options-test
  (testing "creator option"
    (is (= "Custom" (-> (parse (gpx/geojson->gpx
                                 {:type "Point" :coordinates [25.0 62.0]}
                                 {:creator "Custom"}))
                        :attrs :creator))))
  (testing "nil and collection props are dropped from desc"
    (let [doc (parse (gpx/geojson->gpx
                       {:type "Feature"
                        :properties {:name "X" :type nil :tags ["a" "b"]}
                        :geometry {:type "Point" :coordinates [25.0 62.0]}}))]
      (is (= "name=X" (text (child (child doc :wpt) :desc)))))))
