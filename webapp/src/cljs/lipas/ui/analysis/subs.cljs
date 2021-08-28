(ns lipas.ui.analysis.subs
  (:require
   [clojure.string :as string]
   [goog.date.duration :as gduration]
   [goog.object :as gobj]
   [goog.color :as gcolor]
   [lipas.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::analysis
 (fn [db _]
   (-> db :analysis)))

(re-frame/reg-sub
 ::loading?
 :<- [::analysis]
 (fn [analysis _]
   (:loading? analysis)))

(re-frame/reg-sub
 ::population-data
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :population :data)))

(re-frame/reg-sub
 ::population-stats
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :population :stats)))

(re-frame/reg-sub
 ::population-chart-mode
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :population :chart-mode)))

(re-frame/reg-sub
 ::schools-data
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :schools :data)))

(re-frame/reg-sub
 ::schools-chart-mode
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :schools :chart-mode)))

(re-frame/reg-sub
 ::selected-analysis-center
 :<- [::analysis]
 (fn [analysis _]
   (:site-name analysis)))

(re-frame/reg-sub
 ::analytics-center-fcoll
 :<- [::analysis]
 (fn [analysis _]
   (:geoms analysis)))

(re-frame/reg-sub
 ::analytics-center-point
 :<- [::analytics-center-fcoll]
 (fn [fcoll _]
   (let [geom (-> fcoll :features first :geometry)]
     (when geom
      (case (:type geom)
        "Point"      (-> geom :coordinates)
        "LineString" (-> geom :coordinates first)
        "Polygon"    (-> geom :coordinates first first))))))

(re-frame/reg-sub
 ::population-labels
 :<- [:lipas.ui.subs/translator]
 :<- [::selected-travel-metric]
 :<- [::zones]
 (fn [[tr metric zones] _]
   (merge
    {:age-0-14    (str "0-14" (tr :duration/years-short))
     :age-15-64   (str "15-64" (tr :duration/years-short))
     :age-65-     (str "65" (tr :duration/years-short) "-")
     :men         (tr :general/men)
     :women       (tr :general/women)
     :total       (tr :general/total-short)
     :vaka        (tr :analysis/daycare)
     :lukiot      (tr :analysis/high-school)
     :peruskoulut (tr :analysis/elementary-school)
     :perus+lukio (tr :analysis/elementary-and-high-school)
     :erityis     (tr :analysis/special-school)}
    (into {}
          (map
           (fn [zone]
             [(:id zone) (str (:max zone) (if (= metric :distance) "km" "min"))]))
          (get zones metric)))))

(re-frame/reg-sub
 ::selected-travel-profile
 :<- [::analysis]
 (fn [analysis]
   (:selected-travel-profile analysis)))

(re-frame/reg-sub
 ::selected-travel-metric
 :<- [::analysis]
 (fn [analysis]
   (:selected-travel-metric analysis)))

(defn resolve-zone-v3
  [zones m profile metric]
  (if (= :travel-time metric)

    ;; travel time
    (let [time-min (-> m :route profile :duration-s (/ 60))]
      (->> zones
           :travel-time
           (some
            (fn [zone]
              (when (>= (:max zone) time-min (:min zone))
                (:id zone))))))

    ;; distance
    (let [distance-km (-> m :route profile :distance-m (/ 1000))]
      (->> zones
           :distance
           (some
            (fn [zone]
              (when (>= (:max zone) distance-km (:min zone))
                (:id zone))))))))

(re-frame/reg-sub
 ::population-chart-data-v3
 :<- [::population-stats]
 :<- [::selected-travel-profile]
 :<- [::selected-travel-metric]
 :<- [::population-labels]
 :<- [::population-chart-mode]
 (fn [[stats profile metric labels chart-mode] _]
   (let [res (->> (get-in stats [metric profile])
                  (reduce
                   (fn [res [zone m]]
                     (conj res
                           {:zone      (labels zone)
                            :zone*     zone
                            :age-0-14  (:ika_0_14 m)
                            :age-15-64 (:ika_15_64 m)
                            :age-65-   (:ika_65_ m)
                            :vaesto    (:vaesto m)})) [])

                  (sort-by :zone*))]

     (if (= "non-cumulative" chart-mode)
       res
       ;; Calc cumulative results
       (first
        (reduce
         (fn [[res prev-zone] zone]
           (let [zone (if prev-zone
                        (-> zone
                            (update :age-0-14 #(+ % (:age-0-14 prev-zone)))
                            (update :age-15-64 #(+ % (:age-15-64 prev-zone)))
                            (update :age-65- #(+ % (:age-65- prev-zone)))
                            (update :vaesto #(+ % (:vaesto prev-zone))))
                        zone)]
             [(conj res zone) zone]))
         [[]]
         res))))))

(re-frame/reg-sub
 ::schools-data-v2
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :schools :data)))

(re-frame/reg-sub
 ::schools-chart-data-v2
 :<- [::schools-data-v2]
 :<- [::selected-travel-profile]
 :<- [::selected-travel-metric]
 :<- [::population-labels]
 :<- [::zones]
 :<- [::schools-chart-mode]
 (fn [[data profile metric labels zones chart-mode] _]

   (let [res (->> data
                  (map
                   (fn [m]
                     (assoc m :zone (resolve-zone-v3 zones m profile metric))))

                  (remove (comp nil? :zone))
                  (group-by :zone)

                  (reduce
                   (fn [res [zone ms]]
                     (conj res
                           {:zone        (labels zone)
                            :zone*       zone
                            :vaka        (->> ms
                                              (filter #(= "Varhaiskasvatusyksikkö" (:type %)))
                                              count)
                            :lukiot      (->> ms
                                              (filter #(= "Lukiot" (:type %)))
                                              count)
                            :peruskoulut (->> ms
                                              (filter #(= "Peruskoulut" (:type %)))
                                              count)
                            :perus+lukio (->> ms
                                              (filter #(= "Perus- ja lukioasteen koulut" (:type %)))
                                              count)
                            :erityis     (->> ms
                                              (filter #(= "Peruskouluasteen erityiskoulut" (:type %)))
                                              count)})) [])

                  (sort-by :zone*))]

     (if (= "non-cumulative" chart-mode)
       res

       ;; Calculate cumulative results
       (first
        (reduce
         (fn [[res prev-zone] zone]
           (let [zone (if prev-zone
                        (-> zone
                            (update :vaka #(+ % (:vaka prev-zone)))
                            (update :lukiot #(+ % (:lukiot prev-zone)))
                            (update :peruskoulut #(+ % (:peruskoulut prev-zone)))
                            (update :perus+lukio #(+ % (:perus+lukio prev-zone)))
                            (update :erityis #(+ % (:erityis prev-zone))))
                        zone)]
             [(conj res zone) zone]))
         [[]]
         res))))))

(re-frame/reg-sub
 ::analysis
 (fn [db _]
   (:analysis db)))

(re-frame/reg-sub
 ::selected-analysis-tab
 :<- [::analysis]
 (fn [analysis _]
   (:selected-tab analysis)))

(re-frame/reg-sub
 ::analysis-distance-km
 :<- [::analysis]
 (fn [analysis _]
   (:distance-km analysis)))

(re-frame/reg-sub
 ::sports-sites-data
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :sports-sites :data)))

(re-frame/reg-sub
 ::sports-sites-list
 :<- [::sports-sites-data]
 :<- [::selected-travel-metric]
 :<- [::selected-travel-profile]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [:lipas.ui.subs/locale]
 (fn [[data metric profile types locale] _]
   (letfn [(get-metric [m]
             (get-in m [:route profile (if (= :distance metric)
                                         :distance-m
                                         :duration-s)]))

           (get-text [v]
             (when v
               (if (= metric :distance)
                 (str (.toFixed (/ v 1000) 2) "km")
                 (str (gduration/format (* 1000 v))))))]

     (->> data
          (map (fn [m]
                 (let [v (get-metric m)
                       t (str (get-in types [(-> m :type :type-code) :name locale])
                              " | "
                              (get-text v))]
                   (-> m
                       (assoc :metric v)
                       (assoc :display-val t)))))
          (sort-by :metric)))))

(re-frame/reg-sub
 ::sports-site-distances
 :<- [:lipas.ui.search.subs/search-results-fast]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [:lipas.ui.subs/locale]
 (fn [[search-results types locale] _]
   (let [results (gobj/getValueByKeys search-results "hits" "hits")]
     (->> results
          (map
           (fn [result]
             (let [doc       (gobj/get result "_source")
                   type-code (gobj/getValueByKeys doc "type" "type-code")]
               {:name      (gobj/get doc "name")
                :type-code type-code
                :type      (get-in types [type-code :name locale])
                :distance
                (/
                 (js/Math.round
                  (min
                   (js/Number (gobj/getValueByKeys result "fields" "distance-start-m"))
                   (js/Number (gobj/getValueByKeys result "fields" "distance-center-m"))
                   (js/Number (gobj/getValueByKeys result "fields" "distance-end-m"))))
                   1000)})))))))

(re-frame/reg-sub
 ::sports-sites-view
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :sports-sites :view)))

(re-frame/reg-sub
 ::zones
 :<- [::analysis]
 (fn [analysis _]
   (:zones analysis)))

(def zone-sorter (juxt :zone1 :zone2 :zone3 :zone4 :zone5 :zone6 :zone7 :zone8 :zone9))

(re-frame/reg-sub
 ::sports-sites-chart-data-v2
 :<- [::sports-sites-data]
 :<- [::selected-travel-profile]
 :<- [::selected-travel-metric]
 :<- [::population-labels]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [:lipas.ui.subs/locale]
 :<- [::zones]
 (fn [[data profile metric labels types locale zones] _]
   (->> data
        (map
         (fn [m]
           (assoc m :zone (resolve-zone-v3 zones m profile metric))))

        (group-by (comp :type-code :type))

        (reduce
         (fn [res [type-code vs]]
           (let [by-zone (group-by :zone vs)]
             (conj res
                   (merge
                    {:type (get-in types [type-code :name locale])}
                    (into {}
                          (map (fn [zone]
                                 (let [zone-id (:id zone)]
                                   [zone-id (-> zone-id by-zone count)])))
                          (get zones metric)))))
           ) [])

        (sort-by zone-sorter utils/reverse-cmp))))

(re-frame/reg-sub
 ::school-distances
 :<- [::schools-data]
 :<- [::selected-travel-profile]
 (fn [[schools-data profile] _]
   (->> schools-data
        (map (fn [{:keys [distance-m] :as m}]
               (assoc m :distance (when distance-m (/ distance-m 1000)))))
        (sort-by :distance))))

(re-frame/reg-sub
 ::schools-list
 :<- [::schools-data]
 :<- [::selected-travel-metric]
 :<- [::selected-travel-profile]
 (fn [[schools-data metric profile] _]
   (letfn [(get-metric [m]
             (get-in m [:route profile (if (= :distance metric)
                                         :distance-m
                                         :duration-s)]))

           (get-text [v]
             (when v
               (if (= metric :distance)
                 (str (.toFixed (/ v 1000) 2) "km")
                 (str (gduration/format (* 1000 v))))))]

     (->> schools-data
          (map (fn [m]
                 (let [v (get-metric m)
                       t (str (:type m)
                              " | "
                              (get-text v))]
                   (-> m
                       (assoc :metric v)
                       (assoc :display-val t)))))
          (sort-by :metric)))))

(re-frame/reg-sub
 ::schools-view
 :<- [::analysis]
 (fn [analysis _]
   (-> analysis :schools :view)))

(re-frame/reg-sub
 ::zones
 :<- [::analysis]
 (fn [analysis _]
   (:zones analysis)))

(re-frame/reg-sub
 ::zones-by-selected-metric
 :<- [::zones]
 :<- [::selected-travel-metric]
 (fn [[zones metric] _]
   (get zones metric)))

(re-frame/reg-sub
 ::zones-count
 :<- [::zones]
 (fn [zones [_ metric]]
   (count (get zones metric))))

(re-frame/reg-sub
 ::zones-count-max
 :<- [::zones]
 (fn [zones [_ metric]]
   #_(let [range (get-in zones [:ranges metric])]
       (count range))
   (condp = metric
     :travel-time 7
     :distance 10
     nil)))

#_(def base-color "#0073e6")

(def from-color (gcolor/hexToRgb "#C8D4D9"))
(def to-color (gcolor/hexToRgb "#006190"))

(re-frame/reg-sub
 ::zone-colors
 :<- [::zones]
 (fn [zones [_ metric]]
   (into {}
         (for [n (range 1 (inc (count (get zones metric))))]
           [(keyword (str "zone" n))
            (-> (gcolor/blend from-color to-color (/ n (count (get zones metric))))
                gcolor/rgbArrayToHex)]))))

(re-frame/reg-sub
 ::zones-selector-value
 :<- [::zones]
 (fn [zones [_ metric]]
   (into [] (comp (mapcat (juxt :min-idx :max-idx)) (distinct)) (get zones metric))))

(re-frame/reg-sub
 ::zones-selector-marks
 :<- [::zones]
 (fn [zones [_ metric]]
   (into {}
         (map (juxt first (comp :label second)))
         (get-in zones [:ranges metric]))))

(re-frame/reg-sub
 ::zones-selector-max
 (fn [[_ metric] _]
   (re-frame/subscribe [::zones-selector-marks metric]))
 (fn [marks _]
   (apply max (keys marks))))

(re-frame/reg-sub
 ::zones-selector-colors
 (fn [[_ metric] _]
   (re-frame/subscribe [::zone-colors metric]))
 (fn [colors _]
   (->> colors
        (sort-by first)
        (map second)
        (map (fn [color] {:backgroundColor color})))))

(re-frame/reg-sub
 ::schools-chart-data
 :<- [::school-distances]
 :<- [::zones-by-selected-metric]
 (fn [[data zones] _]
   (->> data
        (map
         (fn [{:keys [distance] :as m}]
           (assoc m :zone (some #(and (<= distance (:max %))
                                      (:id %))
                                zones))))
        (remove (comp nil? :zone))
        (group-by :type)
        (map
         (fn [[type vs]]
           (let [by-zone (group-by :zone vs)]
             {:type type
              :zone1 (-> :zone1 by-zone count)
              :zone2 (-> :zone2 by-zone count)
              :zone3 (-> :zone3 by-zone count)
              :zone4 (-> :zone4 by-zone count)})))
        (sort-by (juxt :zone1 :zone2 :zone3 :zone4) utils/reverse-cmp))))

(comment
  (def zones [{:min 0 :max 2 :id 1}
              {:min 2 :max 5 :id 2}
              {:min 5 :max 10 :id 3}])
  (map (juxt :min :max) zones)
  (into [] (comp (mapcat (juxt :min :max)) (distinct)) zones)

  cons
  (type (cons "asdf" [1 2 3]))
  frequencies

  (map vector [0 2 5 10] (rest [0 2 5 10]))

  (->> [0 2 5 10]
       (partition 2 1)
       (map-indexed
        (fn [idx [min max]]
          {:min min :max max :id (keyword (str "zone" (inc idx)))})))

  (partition 2 1 [0 2 5 10])
  (partition 2 1 [0 2 5 10 15])

  (def base-color "#0073e6")
  "#1a81e9"
  "#338feb"
  (gcolor/hexToRgb base-color)
  (gcolor/lighten base-color 0.1)

  (-> base-color
      gcolor/hexToRgb
      (gcolor/lighten 0.2)
      gcolor/rgbArrayToHex)

  (some #(and (<= 4.25 (:max %)) (:id %)) zones))
