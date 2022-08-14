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
 ::calc-diversity-indices
 (fn [{:keys [db]} [_ {:keys [id] :as candidate}]]
   (let [url   (str (:backend-url db) "/actions/calc-diversity-indices")
         feat  (get-in db [:analysis :diversity :areas id])
         fcoll {:type     "FeatureCollection"
                :features [feat]}]
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
       :on-success      [::calc-success id]
       :on-failure      [::calc-failure]}
      :ga/event ["analysis" "calculate-analysis" "diversity"]

      :dispatch-n
      [(let [type-codes (->> db :analysis :diversity :settings :categories (mapcat :type-codes))]
         [:lipas.ui.search.events/set-type-filter type-codes])

       []]})))

(re-frame/reg-event-db
 ::clear
 (fn [db _]
   (assoc-in db [:analysis :diversity] db/default-db)))

(re-frame/reg-event-db
 ::calc-success
 (fn [db [_ candidate-id resp]]
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

(re-frame/reg-event-db
 ::reset-default-categories
 (fn [db _]
   (let [defaults (-> db/default-db :settings :categories)]
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
                         (map (fn [f] (update f :properties dissoc :bbox :pinta_ala)) fs)))]
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
