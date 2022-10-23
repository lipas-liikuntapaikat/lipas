(ns lipas.ui.analysis.diversity.events
  (:require
   [ajax.core :as ajax]
   [goog.string.format]
   [lipas.ui.analysis.diversity.db :as db]
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

(re-frame/reg-event-db
 ::set-analysis-area-fcoll
 (fn [db [_ fcoll]]
   (assoc-in db [:analysis :diversity :settings :analysis-area-fcoll] fcoll)))

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
     {:dispatch [::calc-all-diversity-indices* ids]})))

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
       {:db       (assoc-in db [:analysis :diversity :loading?] true)
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
        :ga/event ["analysis" "calculate-analysis" "diversity"]

        :dispatch-n
        [(when-not skip-search
           (let [type-codes (->> db :analysis :diversity :settings :categories (mapcat :type-codes))]
            [:lipas.ui.search.events/set-type-filter type-codes]))]}))))

(re-frame/reg-event-db
 ::clear
 (fn [db _]
   (assoc-in db [:analysis :diversity] db/default-db)))

(re-frame/reg-event-db
 ::calc-success
 (fn [db [_ candidate-id cb resp]]
   (when cb (cb))
   (-> db
       (assoc-in [:analysis :diversity :loading?] false)
       (assoc-in [:analysis :diversity :results candidate-id] resp))))

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
                       (let [id (gensym)]
                         (assoc res id (assoc-in f [:properties :id] id))))
                     {})
                    (merge (map-utils/normalize-geom-colls fcoll geom-type)
                           (map-utils/normalize-multi-geoms fcoll geom-type)))]
     (-> db
         (assoc-in [:analysis :diversity :areas] fs)
         (assoc-in [:analysis :diversity :import :batch-id] (gensym))))))

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
         categories    (-> db
                           (get-in [:analysis :diversity :category-presets preset-kw :categories])
                           (->seasonal seasonalities))]
     (-> db
         (assoc-in [:analysis :diversity :settings :categories] categories)
         (assoc-in [:analysis :diversity :selected-category-preset] preset-kw)))))

(re-frame/reg-event-db
 ::reset-default-categories
 (fn [db _]
   (let [defaults (-> db/default-db :categories :default :categories)]
     (assoc-in db [:analysis :diversity :settings :categories] defaults))))

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
   (let [category {:name "" :type-codes []}]
     (update-in db [:analysis :diversity :settings :categories] #(into [category] %)))))

(re-frame/reg-event-db
 ::delete-category
 (fn [db [_ idx]]
   (update-in db [:analysis :diversity :settings :categories]
              #(into (subvec % 0 idx) (subvec % (inc idx))))))

(re-frame/reg-event-db
 ::set-max-distance-m
 (fn [db [_ n]]
   (assoc-in db [:analysis :diversity :settings :max-distance-m] n)))

(re-frame/reg-event-fx
 ::export-aggs
 (fn [{:keys [db]} [_ fmt]]
   (let [results (-> db :analysis :diversity :results)
         feats   (-> db :analysis :diversity :areas)
         fcoll   {:type     "FeatureCollection"
                  :features (for [[id f] feats
                                  :let   [aggs (get-in results [id :aggs])]
                                  :when  aggs]
                              (update f :properties merge aggs))}]
     {:lipas.ui.effects/save-as!
      {:blob     (js/Blob. #js[(js/JSON.stringify (clj->js fcoll))])
       :filename (str "monipuolisuus_alueet" "." fmt)}})))

(re-frame/reg-event-fx
 ::export-grid
 (fn [{:keys [db]} [_ fmt]]
   (let [fcolls (-> db :analysis :diversity :results (->> (map (comp :grid second))))
         fcoll   {:type     "FeatureCollection"
                  :features (mapcat :features fcolls)}]
     {:lipas.ui.effects/save-as!
      {:blob     (js/Blob. #js[(js/JSON.stringify (clj->js fcoll))])
       :filename (str "monipuolisuus_ruudukko" "." fmt)}})))

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

(comment
  (re-frame/dispatch [:lipas.ui.analysis.diversity.events/fetch-postal-code-areas 992]))
