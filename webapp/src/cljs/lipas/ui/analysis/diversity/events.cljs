(ns lipas.ui.analysis.diversity.events
  (:require
   [ajax.core :as ajax]
   [clojure.string :as str]
   [goog.string.format]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.utils :refer [==>] :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::init
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:map :mode :name] :analysis)
            (assoc-in [:map :mode :sub-mode] :diversity))
    :dispatch-n
    [[:lipas.ui.search.events/clear-filters]
     [:lipas.ui.map.events/set-overlays
      [[:vectors true]
       [:schools false]
       [:population false]
       [:analysis false]
       [:diversity-grid true]
       [:diversity-area true]]]]}))

(re-frame/reg-event-db
 ::select-analysis-tab
 (fn [db [_ tab]]
   (assoc-in db [:analysis :diversity :selected-tab] tab)))

(re-frame/reg-event-fx
 ::calc-all-diversity-indices*
 (fn [_ [_ ids]]
   (if (seq ids)
     {:dispatch [::calc-diversity-indices
                 {:id (first ids)}
                 #(==> [::calc-all-diversity-indices* (rest ids)])
                 :skip-search]}
     {})))

(re-frame/reg-event-fx
 ::calc-all-diversity-indices
 (fn [{:keys [db]}]
   (let [ids (keys (get-in db [:analysis :diversity :areas]))]
     {:dispatch-n
      [[::calc-all-diversity-indices* ids]
       ;; Disable grid because otherwise the map will flicker
       [:lipas.ui.map.events/set-overlay false :diversity-grid]]})))

(re-frame/reg-event-fx
 ::calc-diversity-indices
 (fn [{:keys [db]} [_ {:keys [id] :as candidate} cb skip-search]]
   (let [url   (str (:backend-url db) "/actions/calc-diversity-indices")
         feat  (get-in db [:analysis :diversity :areas id])
         fcoll {:type     "FeatureCollection"
                :features [feat]}]

     ;; Flood prevention
     (if (-> db :analysis :diversity :loading?)
       {}
       {:db             (assoc-in db [:analysis :diversity :loading?] true)
        :http-xhrio
        {:method          :post
         :uri             url
         :params          (-> db
                              :analysis
                              :diversity
                              :settings
                              (assoc :analysis-area-fcoll fcoll))
         :format          (ajax/transit-request-format)
         :response-format (ajax/transit-response-format)
         :on-success      [::calc-success id cb]
         :on-failure      [::calc-failure]}
        :ga/event       ["analysis" "calculate-analysis" "diversity"]
        :tracker/event! ["analysis" "calculate-analysis" "diversity"]

        :dispatch-n
        [(when-not skip-search
           (let [type-codes (->> db :analysis :diversity :settings :categories (mapcat :type-codes))]
             [:lipas.ui.search.events/set-type-filter type-codes]))]}))))

(re-frame/reg-event-db
 ::calc-success
 (fn [db [_ candidate-id cb resp]]
   (when cb (cb))
   (-> db
       (assoc-in [:analysis :diversity :loading?] false)
       (assoc-in [:analysis :diversity :results candidate-id] resp)
       (update-in [:analysis :diversity :selected-result-areas] (comp set conj) candidate-id))))

(re-frame/reg-event-db
 ::clear-results
 (fn [db _]
   (assoc-in db [:analysis :diversity :results] {})))

(re-frame/reg-event-fx
 ::calc-failure
 (fn [{:keys [db]} [_ error]]
   (let [fatal? false
         tr     (-> db :translator)]
     {:db           (assoc-in db [:analysis :diversity :loading?] false)
      :ga/exception [(:message error) fatal?]
      :dispatch     [:lipas.ui.events/set-active-notification
                     {:message  (tr :notifications/get-failed)
                      :success? false}]})))


(re-frame/reg-event-fx
 ::load-geoms-from-file
 (fn [{:keys [db]} [_ files geom-type]]
   (let [file   (aget files 0)
         params {:enc  "utf-8"
                 :file file
                 :ext  (map-utils/parse-ext file)
                 :cb   (fn [data]
                         (==> [::clear-results])
                         (==> [::set-analysis-candidates data geom-type]))}]

     (if-let [ext (:unknown (map-utils/file->geoJSON params))]
       {:dispatch-n [(let [tr (-> db :translator)]
                       [:lipas.ui.events/set-active-notification
                        {:message  (tr :map.import/unknown-format ext)
                         :success? false}])]}
       {}))))

(re-frame/reg-event-db
 ::set-analysis-candidates
 (fn [db [_ geoJSON geom-type]]
   (let [fcoll (js->clj geoJSON :keywordize-keys true)
         fs    (->> fcoll
                    :features
                    (filter (comp #{geom-type} :type :geometry))
                    (reduce
                     (fn [res f]
                       (let [id (str (gensym))]
                         (assoc res id (assoc-in f [:properties :id] id))))
                     {})
                    (merge (map-utils/normalize-geom-colls fcoll geom-type)
                           (map-utils/normalize-multi-geoms fcoll geom-type)))]
     (-> db
         (assoc-in [:analysis :diversity :areas] fs)
         (assoc-in [:analysis :diversity :import :batch-id] (str (gensym)))))))

(def seasonalities
  {1530 "winter",
   1520 "winter",
   2320 "all-year",
   6130 "summer",
   1395 "summer",
   6210 "summer",
   1370 "summer",
   1360 "summer",
   110  "all-year",
   2360 "all-year",
   5310 "summer",
   1560 "winter",
   205  "summer",
   2150 "all-year",
   2210 "all-year",
   101  "all-year",
   102  "all-year",
   1110 "all-year",
   6220 "all-year",
   4530 "all-year",
   4720 "summer",
   1330 "summer",
   206  "all-year",
   4830 "all-year",
   1180 "summer",
   4422 "winter",
   4430 "all-year",
   204  "all-year",
   4610 "winter",
   2610 "all-year",
   2110 "all-year",
   104  "all-year",
   2330 "all-year",
   2280 "all-year",
   2140 "all-year",
   4220 "all-year",
   2230 "all-year",
   1350 "summer",
   4840 "summer",
   1510 "winter",
   5350 "summer",
   4440 "winter",
   2520 "all-year",
   4710 "summer",
   304  "all-year",
   4412 "all-year",
   4820 "all-year",
   1170 "summer",
   4404 "all-year",
   108  "all-year",
   4401 "all-year",
   2350 "all-year",
   2340 "all-year",
   2120 "all-year",
   109  "all-year",
   5160 "all-year",
   1550 "winter",
   3230 "summer",
   5130 "summer",
   5110 "summer",
   3240 "winter",
   4510 "all-year",
   4240 "all-year",
   2270 "all-year",
   4210 "all-year",
   301  "all-year",
   111  "all-year",
   4630 "winter",
   4810 "summer",
   1540 "winter",
   5320 "summer",
   3210 "summer",
   4640 "winter",
   1150 "summer",
   2310 "all-year",
   5210 "all-year",
   2380 "all-year",
   103  "all-year",
   201  "all-year",
   1220 "summer",
   4411 "all-year",
   1140 "summer",
   4520 "all-year",
   6110 "summer",
   1120 "summer",
   1390 "summer",
   5340 "summer",
   302  "all-year",
   4405 "all-year",
   6120 "all-year",
   1310 "summer",
   202  "all-year",
   1620 "summer",
   2250 "all-year",
   2530 "all-year",
   112  "all-year",
   2130 "all-year",
   3220 "summer",
   5330 "summer",
   4230 "all-year",
   4320 "all-year",
   3130 "all-year",
   3110 "all-year",
   203  "summer",
   4402 "winter",
   4620 "winter",
   5360 "summer",
   2290 "all-year",
   2260 "all-year",
   1160 "summer",
   1210 "summer",
   5140 "summer",
   4310 "all-year",
   1130 "summer",
   5120 "summer",
   4110 "winter",
   4452 "summer",
   5370 "winter",
   2240 "all-year",
   2510 "all-year",
   1640 "summer",
   1380 "summer",
   4451 "summer",
   4403 "all-year",
   5150 "summer",
   1630 "all-year",
   2295 "all-year",
   2370 "all-year",
   1340 "summer",
   1610 "summer",
   4421 "winter",
   2220 "all-year",
   1320 "summer"})

(defn ->seasonal
  [categories seasons-pred]
  (->> categories
       (map (fn [c] (update c :type-codes #(filter (comp seasons-pred seasonalities) %))))
       (remove (fn [c] (empty? (:type-codes c))))
       vec))

(re-frame/reg-event-fx
 ::toggle-seasonality
 (fn [{:keys [db]} [_ s enable?]]
   (let [op (if enable? conj disj)]
     {:db (update-in db [:analysis :diversity :selected-seasonalities] op s)
      :dispatch
      [::select-category-preset (get-in db [:analysis :diversity :selected-category-preset])]})))

(re-frame/reg-event-db
 ::select-category-preset
 (fn [db [_ preset-kw]]
   (let [seasonalities (get-in db [:analysis :diversity :selected-seasonalities])
         presets       (merge (-> db :analysis :diversity :category-presets)
                              (-> db :analysis :diversity :user-category-presets))
         categories    (-> presets
                           (get-in [preset-kw :categories])
                           (->seasonal seasonalities))]
     (-> db
         (assoc-in [:analysis :diversity :settings :categories] categories)
         (assoc-in [:analysis :diversity :selected-category-preset] preset-kw)))))

(re-frame/reg-event-db
 ::set-category-name
 (fn [db [_ idx name]]
   (assoc-in db [:analysis :diversity :settings :categories idx :name] name)))

(re-frame/reg-event-db
 ::set-category-factor
 (fn [db [_ idx factor]]
   (assoc-in db [:analysis :diversity :settings :categories idx :factor] factor)))

(re-frame/reg-event-db
 ::set-category-type-codes
 (fn [db [_ idx type-codes]]
   (assoc-in db [:analysis :diversity :settings :categories idx :type-codes] type-codes)))

(re-frame/reg-event-db
 ::add-new-category
 (fn [db _]
   (let [category {:name "" :type-codes [] :factor 1}]
     (update-in db [:analysis :diversity :settings :categories] #(into [category] %)))))

(re-frame/reg-event-db
 ::delete-category
 (fn [db [_ idx]]
   (update-in db [:analysis :diversity :settings :categories]
              #(into (subvec % 0 idx) (subvec % (inc idx))))))

(re-frame/reg-event-fx
 ::save-category-preset
 (fn [{:keys [db]} [_ name]]
   (let [new-preset {:name       name
                     :categories (-> db :analysis :diversity :settings :categories)}
         presets    (-> (get-in db [:analysis :diversity :user-category-presets])
                        (assoc name new-preset)
                        vals)
         user-data  (-> db
                        :user
                        :login
                        :user-data
                        (assoc-in [:saved-diversity-settings :category-presets] presets))]
     {:dispatch-n
      [[:lipas.ui.user.events/update-user-data user-data]
       [::toggle-category-save-dialog]]})))

(re-frame/reg-event-db
 ::set-max-distance-m
 (fn [db [_ n]]
   (assoc-in db [:analysis :diversity :settings :max-distance-m] n)))

(re-frame/reg-event-db
 ::select-export-format
 (fn [db [_ s]]
   (assoc-in db [:analysis :diversity :selected-export-format] s)))

(defn ->areas-excel-data
  [{:keys [areas results]}]
  (for [[id m] areas
        :let [aggs (get-in results [id :aggs])]
        :when aggs]
    (merge
     (:properties m)
     (dissoc aggs :diversity-idx-median :diversity-idx-mode))))

(defn- export-aggs-excel
  [db fmt]
  (let [data    (->areas-excel-data (get-in db [:analysis :diversity]))
        headers (-> data first keys (->> (map (juxt identity name)) (sort-by second)))
        config  {:filename "diversity_report_areas"
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
    {:lipas.ui.effects/download-excel! config}))

(defn- export-aggs-geojson
  [db fmt]
  (let [results (-> db :analysis :diversity :results)
        feats   (-> db :analysis :diversity :areas)
        fcoll   {:type     "FeatureCollection"
                 :features (for [[id f] feats
                                 :let   [aggs (get-in results [id :aggs])]
                                 :when  aggs]
                             (update f :properties merge (dissoc aggs
                                                                 :diversity-idx-mode
                                                                 :diversity-idx-median)))}]
    {:lipas.ui.effects/save-as!
     {:blob     (js/Blob. #js[(js/JSON.stringify (clj->js fcoll))])
      :filename (str "diversity_report_areas" "." fmt)}}))

(re-frame/reg-event-fx
 ::export-aggs
 (fn [{:keys [db]} [_ fmt]]
   (condp = fmt
     "geojson" (export-aggs-geojson db fmt)
     "excel" (export-aggs-excel db fmt))))

(defn- export-grid-excel
  [db _fmt]
  (let [fcolls (-> db :analysis :diversity :results (->> (map (comp :grid second))))
        data   (->> fcolls
                  (mapcat :features)
                  (map (fn [f] (-> f :properties (dissoc :population)))))
        headers (-> data first keys (->> (map (juxt identity name)) (sort-by second)))
        config  {:filename "diversity_report_grid"
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
    {:lipas.ui.effects/download-excel! config}))

(defn- export-grid-geojson
  [db fmt]
  (let [fcolls (-> db :analysis :diversity :results (->> (map (comp :grid second))))
        fcoll   {:type     "FeatureCollection"
                 :features (->> fcolls
                                (mapcat :features)
                                (map (fn [f] (update f :properties dissoc :population))))}]
    {:lipas.ui.effects/save-as!
     {:blob     (js/Blob. #js[(js/JSON.stringify (clj->js fcoll))])
      :filename (str "diversity_report_grid" "." fmt)}}))

(re-frame/reg-event-fx
 ::export-grid
 (fn [{:keys [db]} [_ fmt]]
   (condp = fmt
     "geojson" (export-grid-geojson db fmt)
     "excel" (export-grid-excel db fmt))))

(defn export-categories-excel
  [db _fmt]
  (let [data    (-> db :analysis :diversity :settings :categories
                    (->> (map (fn [m] (update m :type-codes #(str/join "," %))))))
        headers (-> data first keys (->> (map (juxt identity name)) (sort-by second)))
        config  {:filename "diversity_report_categories"
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
    {:lipas.ui.effects/download-excel! config}))

(defn export-categories-json
  [db _fmt]
  (let [categories (-> db :analysis :diversity :settings :categories)]
    {:lipas.ui.effects/save-as!
     {:blob     (js/Blob. #js[(js/JSON.stringify (clj->js categories))])
      :filename (str "diversity_report_categories" ".json")}}))

(re-frame/reg-event-fx
 ::export-categories
 (fn [{:keys [db]} [_ fmt]]
   (condp = fmt
     "geojson" (export-categories-json db fmt)
     "excel" (export-categories-excel db fmt))))

(defn export-settings-excel
  [db _fmt]
  (let [data    (-> db :analysis :diversity :settings
                    (select-keys [:max-distance-m :analysis-radius-km :distance-mode]))
        headers (-> data keys (->> (map (juxt identity name)) (sort-by second)))
        config  {:filename "diversity_report_parameters"
                 :sheet
                 {:data (utils/->excel-data headers [data])}}]
    {:lipas.ui.effects/download-excel! config}))

(defn export-settings-json
  [db _fmt]
  (let [data (-> db :analysis :diversity :settings
                 (select-keys [:max-distance-m
                               :analysis-radius-km
                               :distance-mode]))]
    {:lipas.ui.effects/save-as!
     {:blob     (js/Blob. #js[(js/JSON.stringify (clj->js data))])
      :filename (str "diversity_report_parameters" ".json")}}))

(re-frame/reg-event-fx
 ::export-settings
 (fn [{:keys [db]} [_ fmt]]
   (condp = fmt
     "geojson" (export-settings-json db fmt)
     "excel" (export-settings-excel db fmt))))

;; https://lipas.fi/tilastokeskus/geoserver/postialue/wfs\?service\=wfs\&version\=2.0.0\&request\=GetFeature\&typeNames\=postialue:pno_2022\&cql_filter\=kuntanro\=992\&outputFormat\=json

(re-frame/reg-event-fx
 ::fetch-postal-code-areas
 (fn [{:keys [db]} [_ city-code]]
   (let [url "https://lipas.fi/tilastokeskus/geoserver/postialue/wfs"]
     {:db       (assoc-in db [:analysis :diversity :loading?] true)
      :http-xhrio
      {:method          :get
       :uri             url
       :params          {:service      "wfs"
                         :version      "2.0.0"
                         :request      "GetFeature"
                         :srsName      "EPSG:4326"
                         :typeNames    "postialue:pno_2022"
                         :cql_filter   (str "kuntanro=" city-code)
                         :outputFormat "json"}
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::fetch-postal-code-areas-success]
       :on-failure      [::fetch-postal-code-areas-failure]}})))

(re-frame/reg-event-fx
 ::fetch-postal-code-areas-success
 (fn [{:keys [db]} [_ resp]]
   (let [fcoll (update resp :features
                       (fn [fs]
                         (map
                          (fn [f]
                            (update f :properties dissoc
                                    :bbox
                                    :pinta_ala
                                    :namn
                                    :kunta
                                    :kuntanro
                                    :vuosi
                                    :objectid))
                          fs)))]
     {:db         (assoc-in db [:analysis :diversity :loading?] false)
      :dispatch-n [[::set-analysis-candidates fcoll "Polygon"]]})))

(re-frame/reg-event-fx
 ::fetch-postal-code-areas-failure
 (fn [{:keys [db]} [_ error]]
   (let [fatal? false
         tr     (-> db :translator)]
     {:db           (assoc-in db [:analysis :diversity :loading?] false)
      :ga/exception [(:message error) fatal?]
      :dispatch     [:lipas.ui.events/set-active-notification
                     {:message  (tr :notifications/get-failed)
                      :success? false}]})))

(re-frame/reg-event-db
 ::select-analysis-chart-areas
 (fn [db [_ v]]
   (assoc-in db [:analysis :diversity :selected-result-areas] v)))

(re-frame/reg-event-db
 ::select-analysis-chart-tab
 (fn [db [_ v]]
   (assoc-in db [:analysis :diversity :selected-chart-tab] v)))

(re-frame/reg-event-db
 ::toggle-category-save-dialog
 (fn [db [_ _]]
   (update-in db [:analysis :diversity :category-save-dialog-open?] not)))

(re-frame/reg-event-db
 ::set-new-preset-name
 (fn [db [_ s]]
   (assoc-in db [:analysis :diversity :new-preset-name] s)))

(comment
  (re-frame/dispatch [:lipas.ui.analysis.diversity.events/fetch-postal-code-areas 992]))
