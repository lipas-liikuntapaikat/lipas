(ns lipas.backend.analysis.reachability
  (:require [clojure.string :as str]
            [lipas.backend.analysis.common :as common]
            [lipas.backend.gis :as gis]
            [lipas.backend.osrm :as osrm]
            [lipas.data.types :as types]
            [lipas.utils :as utils]))

(defn calc-distances
  [source-fcoll populations]
  (let [geom-jts (-> source-fcoll gis/->jts-geom gis/transform-crs)]
    (->> populations
         (reduce-kv
          (fn [res id {:keys [coords] :as m}]
            (let [g (gis/wgs84wkt->tm35fin-geom coords)]
              (assoc res id (assoc m :distance-m (gis/shortest-distance geom-jts g)))))
          {}))))

(defn calc-travel-times
  [search-fcoll populations profiles id-fn]
  (let [destinations (gis/->coord-pair-strs search-fcoll)

        sources (->> populations
                     (map (comp gis/wkt-point->coords :coords :_source))
                     (map #(str/join "," %)))

        result (if (and (seq sources) (seq destinations))
                 (osrm/get-distances-and-travel-times
                    {:profiles     profiles
                     :sources      sources
                     :destinations destinations})
                   [])]

    (reduce-kv
     (fn [m k {:keys [distances durations]}]
       (assoc m k
              (into {}
                    (map-indexed
                     (fn [idx pop]
                       [(id-fn pop)
                        {:distance-m  (apply min (nth distances idx))
                         :duration-s  (apply min (nth durations idx))}]))
                    populations)))
     {}
     result)))

(defn resolve-zone
  [zones m profile metric]
  (if (= :travel-time metric)

    ;; travel time
    (when-let [time-s (-> m :route profile :duration-s)]
      (let [time-min (/ time-s 60)]
        (->> zones
             :travel-time
             (some
              (fn [zone]
                (when (>= (:max zone) time-min (:min zone))
                  (:id zone)))))))

    ;; distance
    (let [distance-km (-> m :route profile :distance-m (/ 1000))]
      (->> zones
           :distance
           (some
            (fn [zone]
              (when (>= (:max zone) distance-km (:min zone))
                (:id zone))))))))

(defn- anonymize-all [profiles zones m]
  (reduce
   (fn [m ks]
     (update-in m ks common/anonymize))
   m
   (for [k1 [:distance :travel-time]
         k2 profiles
         k3 (map :id ((if (= :distance k1) :distance :travel-time) zones))
         k4 [:ika_65_ :ika_15_64 :ika_0_14 :vaesto]]
     [k1 k2 k3 k4])))

(defn- resolve-zones
  [zones profiles pop-data pop-distances pop-travel-times]
  (->> pop-data
       :hits
       (map :_source)

        ;; Combine distance and travel-time calculations to demographics
       (map
        (fn [{:keys [id_nro] :as m}]
          (let [ika_65_   (utils/->int (:ika_65_ m))
                ika_15_64 (utils/->int (:ika_15_64 m))
                ika_0_14  (utils/->int (:ika_0_14 m))
                vaesto    (utils/->int (:vaesto m))]
            (-> m
                (select-keys [:id_nro :ika_65_ :ika_15_64 :ika_0_14 :kunta
                              #_:naiset #_:miehet :vaesto :coords])
                (assoc :ika_65_ ika_65_)
                (assoc :ika_15_64 ika_15_64)
                (assoc :ika_0_14 ika_0_14)
                (assoc :anonymisoitu (- (max vaesto 0)
                                        (+ (max ika_0_14 0)
                                           (max ika_15_64 0)
                                           (max ika_65_ 0))))
                (update :kunta utils/->int)
                #_(update :naiset utils/->int)
                #_(update :miehet utils/->int)
                (update :vaesto utils/->int)
                (assoc :route
                       (into {}
                             (for [p profiles]
                               [p (get-in pop-travel-times [p id_nro])])))
                (assoc-in [:route :direct :distance-m]
                          (double
                           (Math/round
                            (get-in pop-distances [id_nro :distance-m]))))))))

        ;; Resolve zones
       (map
        (fn [m]
          (reduce
           (fn [m1 profile]
             (let [d-zone  (resolve-zone zones m1 profile :distance)
                   tt-zone (resolve-zone zones m1 profile :travel-time)]
               (cond-> m1
                 d-zone  (assoc-in [:zone profile :distance] d-zone)
                 tt-zone (assoc-in [:zone profile :travel-time] tt-zone))))
           m
           (conj profiles :direct))))))

(defn- combine
  [sports-site-data sports-site-distances sports-site-travel-times
   pop-data pop-distances pop-travel-times
   school-data school-distances school-travel-times
   profiles zones]

  (let [pop-data-with-zones (resolve-zones zones
                                           profiles
                                           pop-data
                                           pop-distances
                                           pop-travel-times)]

    {:population
     (->> pop-data-with-zones
          (map
           (fn [{:keys [_id_nro] :as m}]
             (-> m
                 (select-keys [:vaesto :coords :zone])
                 (update :vaesto common/anonymize)))))

     :population-stats
     (->> pop-data-with-zones
          ;; Sum by metric, profile, zone and demography
          (reduce
           (fn [res m]
             (reduce
              (fn [res2 profile]
                (let [d-zone  (get-in m [:zone profile :distance])
                      tt-zone (get-in m [:zone profile :travel-time])]
                  (cond-> res2

                    d-zone
                    (->
                     (update-in [:distance profile d-zone :ika_65_] #(+ (or % 0) (:ika_65_ m)))
                     (update-in [:distance profile d-zone :ika_15_64] #(+ (or % 0) (:ika_15_64 m)))
                     (update-in [:distance profile d-zone :ika_0_14] #(+ (or % 0) (:ika_0_14 m)))
                     (update-in [:distance profile d-zone :anonymisoitu] #(+ (or % 0) (:anonymisoitu m)))
                     (update-in [:distance profile d-zone :vaesto] #(+ (or % 0) (:vaesto m))))

                    tt-zone
                    (->
                     (update-in [:travel-time profile tt-zone :ika_65_] #(+ (or % 0) (:ika_65_ m)))
                     (update-in [:travel-time profile tt-zone :ika_15_64] #(+ (or % 0) (:ika_15_64 m)))
                     (update-in [:travel-time profile tt-zone :ika_0_14] #(+ (or % 0) (:ika_0_14 m)))
                     (update-in [:travel-time profile tt-zone :anonymisoitu] #(+ (or % 0) (:anonymisoitu m)))
                     (update-in [:travel-time profile tt-zone :vaesto] #(+ (or % 0) (:vaesto m)))))))
              res
              (conj profiles :direct)))
           {})

          ;; Anonymize possibly "too small" population values
          (anonymize-all profiles zones))

     :schools
     (->> school-data
          :hits
          (map (juxt :_id :_source))
          (map
           (fn [[id m]]
             (-> m
                 (assoc :route
                        (into {}
                              (for [p profiles]
                                [p (get-in school-travel-times [p id])])))
                 (assoc-in [:route :direct :distance-m]
                           (double
                            (Math/round
                             (get-in school-distances [id :distance-m]))))))))

     :sports-sites
     (->> sports-site-data
          :hits
          (map (juxt :_id :_source))
          (map
           (fn [[id m]]
             (-> m
                 (select-keys [:type :name])
                 (assoc :route
                        (into {}
                              (for [p profiles]
                                [p (get-in sports-site-travel-times [p id])])))
                 (assoc-in [:route :direct :distance-m]
                           (double
                            (Math/round
                             (get-in sports-site-distances [id :distance-m]))))))))}))

(defn get-sports-site-data
  [search buffer-fcoll distance-km type-codes]
  (let [g1 (gis/->jts-geom buffer-fcoll)]
    (-> (common/get-sports-site-data search buffer-fcoll distance-km type-codes)
        (update :hits #(map
                        (fn [hit]
                          (let [closest (-> hit
                                            :_source
                                            :search-meta
                                            :location
                                            :simple-geoms
                                            gis/->flat-coords
                                            not-empty
                                            (some->
                                             gis/->jts-multi-point
                                             (gis/nearest-points g1)
                                             first
                                             gis/->wkt))]
                            (when closest
                              (assoc-in hit [:_source :coords] closest)))
                          ) %))
        (update :hits #(remove nil? %)))))

(defn calc-distances-and-travel-times
  [search {:keys [search-fcoll buffer-fcoll distance-km profiles type-codes zones]}]
  (let [pop-data (future (common/get-population-data search buffer-fcoll distance-km))

        school-data (future (common/get-school-data search buffer-fcoll distance-km))

        sports-site-data (future
                           (get-sports-site-data search buffer-fcoll distance-km type-codes))

        profiles [:car :foot :bicycle]

        pop-distances (future
                        (calc-distances
                         search-fcoll
                         (->> @pop-data
                              :hits
                              (reduce
                               (fn [res m]
                                 (let [id     (-> m :_source :id_nro)
                                       coords (-> m :_source :coords)]
                                   (assoc res id {:coords coords})))
                               {}))))

        school-distances (future
                           (calc-distances
                            search-fcoll
                            (->> @school-data
                                 :hits
                                 (reduce
                                  (fn [res m]
                                    (let [id     (-> m :_id)
                                          coords (-> m :_source :coords)]
                                      (assoc res id {:coords coords})))
                                  {}))))

        sports-site-distances (future
                                (calc-distances
                                 search-fcoll
                                 (->> @sports-site-data
                                      :hits
                                      (reduce
                                       (fn [res m]
                                         (let [id     (-> m :_id)
                                               coords (-> m :_source :coords)]
                                           (assoc res id {:coords coords})))
                                       {}))))

        pop-travel-times (future
                           (calc-travel-times search-fcoll
                                              (:hits @pop-data)
                                              profiles
                                              (comp :id_nro :_source)))

        school-travel-times (future
                              (calc-travel-times search-fcoll
                                                 (:hits @school-data)
                                                 profiles
                                                 :_id))

        sports-site-travel-times (future
                                   (calc-travel-times search-fcoll
                                                      (:hits @sports-site-data)
                                                      profiles
                                                      :_id))]

    (combine @sports-site-data
             @sports-site-distances
             @sports-site-travel-times
             @pop-data
             @pop-distances
             @pop-travel-times
             @school-data
             @school-distances
             @school-travel-times
             profiles
             zones)))

;;; Report generation ;;;;

(def metrics
  {:travel-time {:fi "Matka-aika" :en "Travel time"}
   :distance    {:fi "Etäisyys" :en "Distance"}})

(def profiles
  {:car     {:en "By car" :fi "Autolla"}
   :bicycle {:en "By bicycle" :fi "Polkupyörällä"}
   :foot    {:en "By foot" :fi "Kävellen"}})

(def population-fields
  {:ika_65_      {:en "Age 65-" :fi "Ikä 65-"}
   :ika_15_64    {:en "Age 15-64" :fi "Ikä 15-64"}
   :ika_0_14     {:en "Age 0-14" :fi "Ikä 0-14"}
   :anonymisoitu {:en "Age anonymized" :fi "Ikä anonymisoitu"}
   :vaesto       {:en "People total" :fi "Kokonaisväestö"}})

(def school-fields
  {:vaka        "Varhaiskasvatusyksikkö"
   :lukiot      "Lukiot"
   :peruskoulut "Peruskoulut"
   :perus+lukio "Perus- ja lukioasteen koulut"
   :erityis     "Peruskouluasteen erityiskoulut"})

(def units
  {:travel-time "min"
   :distance    "km"})

(defn make-zone-name [zone suffix]
  (str (:min zone) "-" (:max zone) suffix))

(def zone-sorter (juxt :zone1 :zone2 :zone3 :zone4 :zone5 :zone6 :zone7 :zone8 :zone9))
(defn create-population-sheet
  [{:keys [zones] :as data}]
  (let [locale :fi
        headers (into ["Suure" "Kulkutapa" "Etäisyys" "Väestöryhmä"]
                      (->> data :runs vals (map :site-name)))]
    (into [headers]
          (for [metric    [:travel-time :distance]
                :let      [zones-by-id (->> zones metric (utils/index-by :id))]
                profile   [:car :foot :bicycle]
                zone-id   (->> zones metric (map :id) (sort-by zone-sorter))
                pop-group [:ika_0_14 :ika_15_64 :ika_65_ :anonymisoitu :vaesto]]
            (into
             [(get-in metrics [metric locale])
              (get-in profiles [profile locale])
              (make-zone-name (zones-by-id zone-id) (units metric))
              (get-in population-fields [pop-group locale])]
             (map (fn [m] (get-in m [metric profile zone-id pop-group]))
                  (->> data :runs vals (map (comp :stats :population)))))))))

(defn create-schools-sheet
  [{:keys [zones] :as data}]
  (let [locale  :fi
        headers (into ["Suure" "Kulkutapa" "Etäisyys" "Oppilaitostyyppi"]
                      (->> data :runs vals (map :site-name)))]
    (into [headers]
          (for [metric      [:travel-time :distance]
                :let        [zones-by-id (->> zones metric (utils/index-by :id))]
                profile     [:car :foot :bicycle]
                zone-id     (->> zones metric (map :id) (sort-by zone-sorter))
                school-type (vals school-fields)]
            (into
             [(get-in metrics [metric locale])
              (get-in profiles [profile locale])
              (make-zone-name (zones-by-id zone-id) (units metric))
              school-type]
             (map (fn [lipas-id]
                    (let [schools (get-in data [:runs lipas-id :schools :data])]
                      (->> schools
                           (filter
                            (fn [m]
                              (and
                               (= school-type (:type m))
                               (= zone-id (resolve-zone zones m profile metric)))))
                           count)))
                  (->> data :runs keys)))))))

(def type-code->name
  (into {}
        (map (juxt first (comp :fi :name second)))
        types/all))

(def type-code->sub-cat
  (into {}
        (map (juxt first (comp :fi :name types/sub-categories :sub-category second)))
        types/all))

(def type-code->main-cat
  (into {}
        (map (juxt first (comp :fi :name types/main-categories :main-category second)))
        types/all))

(defn create-sports-sites-sheet
  [{:keys [zones] :as data}]
  (let [locale  :fi
        headers (into ["Suure" "Kulkutapa" "Etäisyys" "Pääryhmä" "Alaryhmä"
                       "Liikuntapaikkatyyppi"]
                      (->> data :runs vals (map :site-name)))]
    (into [headers]
          (for [metric    [:travel-time :distance]
                :let      [zones-by-id (->> zones metric (utils/index-by :id))]
                profile   [:car :foot :bicycle]
                zone-id   (->> zones metric (map :id) (sort-by zone-sorter))
                type-code (->> (keys types/all) sort)]
            (into
             [(get-in metrics [metric locale])
              (get-in profiles [profile locale])
              (make-zone-name (zones-by-id zone-id) (units metric))
              (type-code->main-cat type-code)
              (type-code->sub-cat type-code)
              (type-code->name type-code)]
             (map (fn [lipas-id]
                    (let [sports-sites (get-in data [:runs lipas-id :sports-sites :data])]
                      (->> sports-sites
                           (filter
                            (fn [m]
                              (and
                               (= type-code (-> m :type :type-code))
                               (= zone-id (resolve-zone zones m profile metric)))))
                           count)))
                  (->> data :runs keys)))))))

(defn create-report [data]
  ["Väestö" (create-population-sheet data)
   "Koulut" (create-schools-sheet data)
   "Liikuntapaikat" (create-sports-sites-sheet data)])

(comment
  (def data (read-string (slurp "/Users/tipo/kana.edn")))
  (-> data :zones :travel-time)
  (require '[clojure.java.io :as io])
  (require '[cognitect.transit :as transit])

  (def in (io/input-stream "/tmp/cc.transit"))
  (def reader (transit/reader in :json))

  (spit "/tmp/cc-output.edn" (transit/read reader))

  (def data2 (read-string (slurp "/Users/tipo/lipas/reachability/data.edn")))
  (keys data2)
  (->> data2 :runs keys)
  (get-in data2 [:runs 502918])
  (get-in data2 [:runs 502918 :schools])
  (get-in data2 [:runs 502918 :population :stats])

  (def zone-sorter (juxt :zone1 :zone2 :zone3 :zone4 :zone5 :zone6 :zone7 :zone8 :zone9))
  (->> data2 :zones :travel-time (map :id) (sort-by zone-sorter))
  (:zones data2)
  (create-population-sheet data2)
  (:zones data2)
  (->> data2 :zones :travel-time (some (comp #{:zone1} :id)))
  (create-report data2)

  (->> data2 :runs vals (mapcat (comp :data :schools)))
  (create-schools-sheet data2)

  (->> data2 :runs vals first :sports-sites)
  (create-sports-sites-sheet data2)
  )
