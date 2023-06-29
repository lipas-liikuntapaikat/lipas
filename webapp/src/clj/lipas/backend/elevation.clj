(ns lipas.backend.elevation
  (:require
   [clj-http.client :as client]
   [lipas.backend.config :as config]
   [clojure.string :as str]
   [lipas.backend.gis :as gis]
   [taoensso.timbre :as log]))

;; MML elevation coverage model consists of 2x2m squares that contain
;; the elevation information as meters above/below sea level. We
;; calculate an envelope for our geometries and request the relevant
;; tiles (squares) as ESRI Ascii Grid text files from MML api. Then we
;; resolve the elevation value for each point in the geometry from the
;; elevation tiles.

;; https://www.maanmittauslaitos.fi/ortokuvien-ja-korkeusmallien-kyselypalvelu/tekninen-kuvaus
;; https://en.wikipedia.org/wiki/Esri_grid

(def mml-api-key (get-in config/default-config [:app :mml-api :api-key] ))
(def mml-coverage-url (get-in config/default-config [:app :mml-api :coverage-url]))

(def default-query-params
  {"service"     "WCS",
   "version"     "2.0.1",
   "request"     "GetCoverage",
   "format"      "text/plain"
   "CoverageID"  "korkeusmalli_2m"
   "SCALEFACTOR" "1",
   "SUBSET"      ["N(7181000,7181200)" "E(496000,496200)"]})

(def coverage-info
  "Acquired from `describe-coverage` output."
  {:cell-size 2
   :envelope
   {:min-x 44000
    :max-x 740000
    :min-y 6594000
    :max-y 7782000}})

(def query-envelope-size-m
  "250x250m was selected as the grid size because of good perf/coverage
  ratio with MML api."
  250)

(defn describe-coverage
  []
  (let [opts {:basic-auth   (str mml-api-key ":")
              :query-params (-> default-query-params
                                (dissoc "SCALEFACTOR" "SUBSET" "format")
                                (assoc "request" "DescribeCoverage"))}]
    (-> (client/get mml-coverage-url opts)
        :body)))

(defn fit-to-coverage
  "Returns an envelope that contains given envelope and 'matches' MML
  2x2m coverage grid. Ensures that the returned envelope is within the
  bounds of the coverage's envelope."
  [{:keys [min-x min-y max-x max-y]} {:keys [envelope]}]
  {:min-x (max (:min-x envelope) (let [n (Math/round min-x)] (if (odd? n) (dec n) n)))
   :min-y (max (:min-y envelope) (let [n (Math/round min-y)] (if (odd? n) (dec n) n)))
   :max-x (min (:max-x envelope) (let [n (Math/round max-x)] (if (odd? n) (dec n) n)))
   :max-y (min (:max-y envelope) (let [n (Math/round max-y)] (if (odd? n) (dec n) n)))})

(defn resolve-elevation
  [coords elevations]
  (let [[lon lat]      (gis/wgs84->tm35fin-no-wrap coords)
        ;; Find the correct grid
        {:keys [headers data]} (->> elevations
                                    (some
                                     (fn [{:keys [headers] :as elevation}]
                                       (let [min-x (:xllcorner headers)
                                             min-y (:yllcorner headers)
                                             max-x (+ min-x (* (:ncols headers)
                                                               (:cellsize headers)))
                                             max-y (+ min-y (* (:nrows headers)
                                                               (:cellsize headers)))]
                                         (and (>= max-x lon min-x)
                                              (>= max-y lat min-y)
                                              elevation)))))

        ;; Resolve col and row for the coords relative to the lower
        ;; left corner of the grid
        col (long (Math/floor
                   (/ (- lon (:xllcorner headers))
                      (:cellsize headers))))
        row (long (Math/floor
                   (/ (- lat (:yllcorner headers))
                      (:cellsize headers))))]

    ;; Reverse the data (rows) so the lower left corner comes first
    ;; and we can lookup in 'natural' order.
    (-> data rseq (nth row) (nth col))))

(defn append-elevations
  [fcoll elevations]
  (update fcoll :features
          (fn [fs]
            (map
             (fn [f]
               (update-in f [:geometry :coordinates]
                          (fn [coords]
                            (condp = (-> f :geometry :type)
                              "Point"      (let [[x y] coords]
                                             [x y (resolve-elevation coords elevations)])
                              "LineString" (mapv
                                            (fn [coords]
                                              (let [[x y] coords]
                                                [x y (resolve-elevation coords elevations)]))
                                            coords)
                              "Polygon"    (mapv
                                            (fn [coords]
                                              (mapv
                                               (fn [coords]
                                                 (let [[x y] coords]
                                                   [x y (resolve-elevation coords elevations)]))
                                               coords))
                                            coords)
                              (throw (ex-info "Encountered unexpected geometry type" f))))))
             fs))))

(defn parse-ascii-grid-headers
    [lines]
  (into {}
        (for [s lines]
          (let [[_ k v] (re-find #"(\w+)\s*(-?\d+\.?\d*)" s)
                k       (str/lower-case k)]
            [(keyword k)
             (if (str/starts-with? k "n")
               (parse-long v)
               (parse-double v))]))))

(defn parse-ascii-grid-data
  [lines]
  (into []
        (for [s     lines
              :when (not-empty s)]
          (->> (re-seq #"-?\d+\.?\d*" s)
               (map parse-double)))))

(defn parse-ascii-grid
  [s]
  (let [lines        (str/split-lines s)
        header-lines (take-while #(re-matches #"^[A-Za-z].*" %) lines)
        data-lines   (drop (count header-lines) lines)]
    {:headers (parse-ascii-grid-headers header-lines)
     :data    (parse-ascii-grid-data data-lines)}))

(defn make-query-params
  [{:keys [min-x max-x min-y max-y]}]
  (assoc default-query-params "SUBSET"
         [(format "N(%s,%s)"  min-y max-y)
          (format "E(%s,%s)"  min-x max-x)]))

(defn get-elevation-coverage
  [envelope]
  (let [opts {:query-params (make-query-params envelope)
              :basic-auth   (str mml-api-key ":")
              #_#_:as       :stream}]
    (log/info "Getting coverage with envelope" envelope)
    (-> (client/get mml-coverage-url opts)
        :body
        (parse-ascii-grid))))

(defn enrich-elevation
  [fcoll]
  (let [;; Add some tolerance because otherwise grid queries might
        ;; break near the edges. 4 meters was found via experimentation.
        buff-m    4
        fcoll-jts (-> fcoll gis/->jts-geom (gis/transform-crs gis/srid gis/tm35fin-srid))
        envelopes (-> (gis/get-envelope fcoll-jts buff-m)
                      (fit-to-coverage coverage-info)
                      (gis/chunk-envelope query-envelope-size-m)
                      (->> (filter #(gis/intersects-envelope? % fcoll-jts))))]
    (->> envelopes
         (map (fn [envelope] (future (get-elevation-coverage envelope))))
         (map deref)
         (append-elevations fcoll))))

(comment
  (->> (describe-coverage)
       (spit "/tmp/lol.xml"))

  (def test-point
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [25.720539797408946,
                      62.62057217751676]}}]})

  (enrich-elevation test-point)

  (-> test-point
      gis/->flat-coords
      (->> (map gis/wgs84->tm35fin-no-wrap)))
  ;; => ([434355.5312499977 6943966.504886635])

  (-> test-point
      gis/->tm35fin-envelope
      make-query-params)

  {"service"     "WCS",
   "version"     "2.0.1",
   "request"     "GetCoverage",
   "format"      "text/plain",
   "CoverageID"  "korkeusmalli_2m",
   "SCALEFACTOR" "1",
   "SUBSET"      ["N(6943964,6943968)" "E(434353,434357)"]}

  (def lol
    {:headers
     {:ncols     2,
      :nrows     2,
      :xllcorner 434353.0,
      :yllcorner 6943964.0,
      :cellsize  2.0},
     :data (list (list 152.55 152.34) (list 152.39 152.3))})

  (resolve-elevation [25.720539797408946,
              62.62057217751676] lol)
  ;; => 152.34

  (-> (list (list 152.55 152.34) (list 152.39 152.3)) (nth 1) (nth 1))

  (def test-envelopes
    [[:m10    {:min-x 400000 :max-x 400010 :min-y 6900000 :max-y 6900010}]
     [:m25    {:min-x 400000 :max-x 400025 :min-y 6900000 :max-y 6900025}]
     [:m50    {:min-x 400000 :max-x 400050 :min-y 6900000 :max-y 6900050}]
     [:m100   {:min-x 400000 :max-x 400100 :min-y 6900000 :max-y 6900100}]
     [:m250   {:min-x 400000 :max-x 400250 :min-y 6900000 :max-y 6900250}]
     [:m500   {:min-x 400000 :max-x 400500 :min-y 6900000 :max-y 6900500}]
     [:m1000  {:min-x 400000 :max-x 401000 :min-y 6900000 :max-y 6901000}]
     [:m2500  {:min-x 400000 :max-x 402500 :min-y 6900000 :max-y 6902500}]
     [:m5000  {:min-x 400000 :max-x 405000 :min-y 6900000 :max-y 6905000}]
     [:m10000 {:min-x 400000 :max-x 410000 :min-y 6900000 :max-y 6910000}]])

  (doseq [[k v] test-envelopes]
    (println k)
    (let [output (with-out-str (time (get-elevation-coverage v)))]
      (println output)))

  ;; :m10
  ;; "Elapsed time: 1951.641453 msecs"  <--- cold start?
  ;; :m25
  ;; "Elapsed time: 244.095314 msecs"
  ;; :m50
  ;; "Elapsed time: 256.81999 msecs"
  ;; :m100
  ;; "Elapsed time: 241.35635 msecs"
  ;; :m250
  ;; "Elapsed time: 255.206125 msecs"   <--- sweet spot?
  ;; :m500
  ;; "Elapsed time: 436.237267 msecs"
  ;; :m1000
  ;; "Elapsed time: 740.046107 msecs"
  ;; :m2500
  ;; "Elapsed time: 2919.371703 msecs"
  ;; :m5000
  ;; "Elapsed time: 16044.512852 msecs"
  ;; :m10000
  ;; "Elapsed time: 62406.48823 msecs"

  (def dada "NCOLS 1\r\nNROWS 1\r\nXLLCORNER 434354.531249997672\r\nYLLCORNER 6943965.504886634648\r\nCELLSIZE 1.000000000000\r\nNODATA_VALUE -9999\r\n")

  (parse-ascii-grid-headers (str/split-lines dada))

  (re-find #"(\w+)\s*(-?\d+\.?\d*)" "kissa    -9999.0")

  (def test-route
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436753445903, 63.9531598143881],
         [26.4505514903968, 63.9127506671744]]}},
      {:type "Feature"
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436550567509, 63.9531552213109],
         [25.7583312263512, 63.9746827436437]]}}]})

  (-> (gis/->tm35fin-envelope test-route 2)
      (fit-to-coverage coverage-info)
      (gis/chunk-envelope 10000))
  ;; => [{:min-x 439212, :max-x 449212, :min-y 7087406, :max-y 7094786}
  ;;     {:min-x 449212, :max-x 459212, :min-y 7087406, :max-y 7094786}
  ;;     {:min-x 459212, :max-x 469212, :min-y 7087406, :max-y 7094786}
  ;;     {:min-x 469212, :max-x 473044, :min-y 7087406, :max-y 7094786}]

  (-> (gis/->tm35fin-envelope test-route 2)
      (fit-to-coverage coverage-info)
      (gis/chunk-envelope 250)
      (->> (filter #(gis/intersects-envelope? % test-route))))

  ;; Without filter
  (count *1)
  ;; => 4110

  ;; with filter
  (def lolz *1)
  (count lolz)
  ;; => 165

  (* 250 165)

  (def big-data
    (get-elevation-coverage {:min-x 439212, :max-x 449212, :min-y 7087406, :max-y 7094786}))

  big-data

  (:headers big-data)
  ;; => {:ncols 5000,
  ;;     :nrows 3690,
  ;;     :xllcorner 439212.0,
  ;;     :yllcorner 7087406.0,
  ;;     :cellsize 2.0}

  (enrich-elevation test-route)

  (+ 1 1)

  (def lolx *1)

  (- (:max-x lolx) (:min-x lolx))

  (get-elevation-coverage test-route)
  (enrich-elevation test-route)


  )
