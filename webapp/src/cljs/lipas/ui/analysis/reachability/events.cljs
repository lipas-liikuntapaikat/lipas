(ns lipas.ui.analysis.reachability.events
  (:require [ajax.core :as ajax]
            [ajax.protocols :as ajaxp]
            [lipas.ui.analysis.reachability.db :as db]
            [lipas.ui.map.utils :as map-utils]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-event-fx
 ::calc-distances-and-travel-times
 (fn [{:keys [db]} [_ lipas-id body]]
   (let [url (str (:backend-url db) "/actions/calc-distances-and-travel-times")]
     {:db             (assoc-in db [:analysis :reachability :runs lipas-id :loading?] true)
      :http-xhrio
      {:method          :post
       :uri             url
       :params          body
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::calc-success lipas-id]
       :on-failure      [::calc-failure lipas-id]}
      :tracker/event! ["analysis" "calculate-reachability" "lipas-id" lipas-id]})))

(rf/reg-event-db
 ::select-sports-site
 (fn [db [_ lipas-id]]
   (assoc-in db [:analysis :reachability :selected-sports-site] lipas-id)))

(rf/reg-event-db
 ::clear
 (fn [db _]
   (assoc-in db [:analysis :reachability] db/default-db)))

(rf/reg-event-db
 ::calc-success
 (fn [db [_ lipas-id resp]]
   (-> db
       (assoc-in [:analysis :reachability :runs lipas-id :loading?] false)
       (assoc-in [:analysis :reachability :runs lipas-id :population :data] (:population resp))
       (assoc-in [:analysis :reachability :runs lipas-id :population :stats] (:population-stats resp))
       (assoc-in [:analysis :reachability :runs lipas-id :schools :data] (:schools resp))
       (assoc-in [:analysis :reachability :runs lipas-id :sports-sites :data] (:sports-sites resp))
       (assoc-in [:analysis :reachability :selected-sports-site] lipas-id))))

(rf/reg-event-fx
 ::calc-failure
 (fn [{:keys [db]} [_ lipas-id _error]]
   (let [tr     (-> db :translator)]
     {:db             (assoc-in db [:analysis :reachability :runs lipas-id :loading?] false)
      :tracker/event! ["error" "calculate-reachability-failure"]
      :dispatch       [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/get-failed)
                        :success? false}]})))

(rf/reg-event-fx
 ::show-analysis
 (fn [_ [_ lipas-id]]
   (if lipas-id
     (let [on-success [[::show-analysis* lipas-id]]]
       {:dispatch [:lipas.ui.sports-sites.events/get lipas-id on-success]})
     {})))

(defn resolve-coords [fcoll]
  (let [geom (-> fcoll :features first :geometry)]
    (case (:type geom)
      "Point"      (-> geom :coordinates)
      "LineString" (-> geom :coordinates first)
      "Polygon"    (-> geom :coordinates first first))))

(rf/reg-event-fx
 ::show-analysis*
 (fn [{:keys [db]} [_ lipas-id]]
   (let [latest    (get-in db [:sports-sites lipas-id :latest])
         rev       (get-in db [:sports-sites lipas-id :history latest])
         geoms     (-> rev :location :geometries)
         [lon lat] (resolve-coords geoms)
         coords    {:lon lon :lat lat}]
     {:db             (-> db
                    (assoc-in [:analysis :reachability :runs lipas-id :geoms] geoms)
                    (assoc-in [:analysis :reachability :runs lipas-id :lipas-id] lipas-id)
                    (assoc-in [:analysis :reachability :runs lipas-id :center] coords)
                    (assoc-in [:analysis :reachability :runs lipas-id :site-name] (:name rev)))
      :dispatch-n
      [[:lipas.ui.search.events/clear-filters]
       [:lipas.ui.map.events/show-analysis*]
       [::refresh-analysis]]
      :tracker/event! ["analysis" "show-analysis" "lipas-id" lipas-id]})))

(rf/reg-event-db
 ::select-analysis-tab
 (fn [db [_ tab]]
   (assoc-in db [:analysis :reachability :selected-tab] tab)))

(defn calc-buffer-geom [db lipas-id]
  (let [fcoll       (get-in db [:analysis :reachability :runs lipas-id :geoms])
        distance-km (-> db :analysis :reachability :distance-km)]
    (map-utils/calc-buffer-geom fcoll distance-km)))

(rf/reg-event-fx
 ::set-analysis-distance-km
 (fn [{:keys [db]} [_ v]]
   (let [lipas-ids (-> db :analysis :reachability :runs keys)]
     {:db (reduce
           (fn [db lipas-id]
             (let [buff-geom (calc-buffer-geom db lipas-id)]
               (assoc-in db [:analysis :reachability :runs lipas-id :buffer-geom] buff-geom)))
           (assoc-in db [:analysis :reachability :distance-km] v)
           lipas-ids)})))

(rf/reg-event-fx
 ::refresh-analysis
 (fn [{:keys [db]} _]
   (let [lipas-ids (-> db :analysis :reachability :runs keys)
         profiles    (-> db :analysis :reachability :travel-profiles)
         distance-km (-> db :analysis :reachability :distance-km)
         type-codes  (-> db :analysis :reachability :sports-sites :type-codes)
         zones       (-> db :analysis :reachability :zones)
         buff-geoms (->> lipas-ids
                         (map (fn [lipas-id] [lipas-id (calc-buffer-geom db lipas-id)]))
                         (into {}))]
     {:db (reduce
           (fn [db lipas-id]
             (let [path [:analysis :reachability :runs lipas-id :buffer-geom]]
               (assoc-in db path (buff-geoms lipas-id))))
           db
           lipas-ids)
      :dispatch-n
      (into [[:lipas.ui.search.events/submit-search false]]
            (for [lipas-id lipas-ids]
             (let [fcoll  (get-in db [:analysis :reachability :runs lipas-id :geoms])
                   params {:distance-km  distance-km
                           :profiles     profiles
                           :zones        zones
                           :search-fcoll fcoll
                           :buffer-fcoll (calc-buffer-geom db lipas-id)
                           :type-codes   type-codes}]
               [::calc-distances-and-travel-times lipas-id params])))})))

(rf/reg-event-db
 ::select-sports-sites-view
 (fn [db [_ view]]
   (assoc-in db [:analysis :reachability :sports-sites :view] view)))

(rf/reg-event-db
 ::select-schools-view
 (fn [db [_ view]]
   (assoc-in db [:analysis :reachability :schools :view] view)))

(rf/reg-event-db
 ::select-travel-profile
 (fn [db [_ v]]
   (assoc-in db [:analysis :reachability :selected-travel-profile] v)))

(rf/reg-event-db
 ::select-travel-metric
 (fn [db [_ v]]
   (assoc-in db [:analysis :reachability :selected-travel-metric] v)))

(rf/reg-event-fx
 ::set-type-codes-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:analysis :reachability :sports-sites :type-codes] v)
    :dispatch-n [[:lipas.ui.search.events/set-type-filter v]
                 [::refresh-analysis]]}))

(rf/reg-event-fx
 ::set-zones
 (fn [{:keys [db]} [_ v metric]]
   (let [ranges (-> db :analysis :reachability :zones :ranges metric)
         zones  (->> v
                     (partition 2 1)
                     (map-indexed
                      (fn [idx [min-idx max-idx]]
                        {:min     (get-in ranges [min-idx :value])
                         :min-idx min-idx
                         :max     (get-in ranges [max-idx :value])
                         :max-idx max-idx
                         :id      (keyword (str "zone" (inc idx)))})))]
     {:db (assoc-in db [:analysis :reachability :zones metric] zones)
      :dispatch-n
      (let [old-max        (-> db :analysis :reachability :distance-km)
            new-max        (apply max (map :max zones))
            old-zone-count (count (get-in db [:analysis :reachability :zones metric]))]
        [(when (= :distance metric)
           [::set-analysis-distance-km new-max])
         (when (or
                (< old-zone-count (count zones))
                (and (= :distance metric) (not= old-max new-max)))
           [::refresh-analysis])])})))

(rf/reg-event-fx
 ::set-zones-count
 (fn [_ [_ n metric current-zones hacky-atom-ref]]
   (let [v (cond
             (< n (count current-zones))
             (take (inc n) current-zones)

             (>= n (count current-zones))
             (take (inc n) (range))

             :else current-zones)
         _ (reset! hacky-atom-ref v)]
     {:dispatch [::set-zones v metric]})))

(rf/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [params    (-> db :analysis :reachability)
         lipas-ids (-> db :analysis :reachability :runs keys)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/create-analysis-report")
       :params          params
       :format          (ajax/transit-request-format)
       :response-format {:type         :blob
                         :content-type (-> cutils/content-type :xlsx)
                         :description  (-> cutils/content-type :xlsx)
                         :read         ajaxp/-body}
       :on-success      [::report-success]
       :on-failure      [::report-failure]}
      :db             (assoc-in db [:analysis :reachability :loading?] true)
      :tracker/event! ["analysis" "download-reachability-report"]})))

(rf/reg-event-fx
 ::report-success
 (fn [{:keys [db ]} [_ blob]]
   {:lipas.ui.effects/save-as!
    {:blob         blob
     :filename     "lipas-analysis.xlsx"
     :content-type (-> cutils/content-type :xlsx)}
    :db (assoc-in db [:analysis :reachability :loading?] false)}))

(rf/reg-event-fx
 ::report-failure
 (fn [{:keys [db]} [_ _error]]
   (let [tr (-> db :translator)]
     {:db             (assoc-in db [:analysis :reachability :loading?] false)
      :tracker/event! ["error" "reachability-report-failure"]
      :dispatch       [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/get-failed)
                        :success? false}]})))

(rf/reg-event-db
 ::set-population-chart-mode
 (fn [db [_ v]]
   (assoc-in db [:analysis :reachability :population :chart-mode]
             (if v "cumulative" "non-cumulative"))))

(rf/reg-event-db
 ::set-schools-chart-mode
 (fn [db [_ v]]
   (assoc-in db [:analysis :reachability :schools :chart-mode]
             (if v "cumulative" "non-cumulative"))))
