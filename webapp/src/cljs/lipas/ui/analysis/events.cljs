(ns lipas.ui.analysis.events
  (:require
   ["ol" :as ol]
   [ajax.core :as ajax]
   [ajax.protocols :as ajaxp]
   [cemerick.url :as url]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.string :as gstring]
   [goog.string.format]
   [goog.string.path :as gpath]
   [lipas.ui.analysis.db :as db]
   [lipas.ui.map.styles :as styles]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.utils :refer [==>] :as utils]
   [lipas.utils :as cutils]
   proj4
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::calc-distances-and-travel-times
 (fn [{:keys [db]} [_ body]]
   (let [url (str (:backend-url db) "/actions/calc-distances-and-travel-times")]
     {:db (assoc-in db [:analysis :loading?] true)
      :http-xhrio
      {:method          :post
       :uri             url
       :params          body
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::calc-success]
       :on-failure      [::calc-failure]}})))

(re-frame/reg-event-db
 ::clear
 (fn [db _]
   (assoc db :analysis db/default-db)))

(re-frame/reg-event-db
 ::calc-success
 (fn [db [_ resp]]
   (-> db
       (assoc-in [:analysis :loading?] false)
       (assoc-in [:analysis :population :data] (:population resp))
       (assoc-in [:analysis :population :stats] (:population-stats resp))
       (assoc-in [:analysis :schools :data] (:schools resp))
       (assoc-in [:analysis :sports-sites :data] (:sports-sites resp)))))

(re-frame/reg-event-fx
 ::calc-failure
 (fn [{:keys [db]} [_ error]]
   (let [fatal? false
         tr     (-> db :translator)]
     {:db           (assoc-in db [:analysis :loading?] false)
      :ga/exception [(:message error) fatal?]
      :dispatch     [:lipas.ui.events/set-active-notification
                     {:message  (tr :notifications/get-failed)
                      :success? false}]})))

(defn- calc-buffered-bbox [fcoll buffer]
  (let [fs     (-> fcoll clj->js map-utils/->ol-features)
        extent (-> fs (aget 0) .getGeometry .getExtent)]
    (.forEach fs (fn [f']
                   (js/ol.extent.extend extent (-> f' .getGeometry .getExtent))))
    (js/ol.extent.buffer extent buffer)))

(defn- resolve-bbox [db]
  (let [geoms (-> db :analysis :geoms)]
    (if geoms
      (calc-buffered-bbox geoms 15000) ; 15km buffer
      (-> db :map :extent))))

(defn- resolve-filter
  ([db] (resolve-filter db "the_geom" "kilometers"))
  ([db geom-name] (resolve-filter db geom-name "kilometers"))
  ([db geom-name unit]
   (let [geoms (or (-> db :analysis :geoms map-utils/->geom-coll)
                   {:type "Feature"
                    :geometry
                    {:type        "Point"
                     :coordinates (-> db :map :center-wgs84)}})]
     (gstring/format
      "DWITHIN(%s,%s,%d,%s)"
      geom-name
      (-> geoms
          clj->js
          map-utils/->ol-feature
          map-utils/->wkt)
      (-> db :analysis :distance-km)
      unit))))

(re-frame/reg-event-fx
 ::get-population
 (fn [{:keys [db]} [_ cb]]
   (let [#_#_bbox (resolve-bbox db)
         cql      (resolve-filter db)
         base-url "/tilastokeskus/geoserver/vaestoruutu/ows?"
         params   {:service      "WFS"
                   :version      "1.0.0"
                   :request      "GetFeature"
                   :typeName     "vaestoruutu:vaki2019_1km_kp"
                   :outputFormat "application/json"
                   ;;:srsName      "EPSG:3067"
                   :srsName      "EPSG:4326"
                   :cql_filter   cql
                   #_#_:bbox     bbox
                   }]
     {:http-xhrio
      {:method          :get
       :uri             (-> base-url url/url (assoc :query params) str (subs 3))
       :response-format (ajax/raw-response-format)
       :on-success      [::get-population-success cb]
       :on-failure      [::get-population-failure]}})))

(defn any-intersects? [fs f buffer]
  (when-not (empty? fs)
    (let [extent (-> fs (aget 0) .getGeometry .getExtent)]
      (.forEach fs (fn [f']
                     (ol/extent.extend extent (-> f' .getGeometry .getExtent))))
      (let [buffered (ol/extent.buffer extent buffer)]
        (ol/extent.intersects buffered (-> f .getGeometry .getExtent))))))

;; Zones idea (within 2km,5km,10km) roughly from
;; https://www.oulu.fi/paikkatieto/Liikuntapaikkojen%20saavutettavuus.pdf
;; (pages 27-30)
(defn resolve-zone
  [geom-fs analysis-f]
  (cond
    (any-intersects? geom-fs analysis-f 2000)  [1 styles/population-zone1-fn]
    (any-intersects? geom-fs analysis-f 5000)  [2 styles/population-zone2-fn]
    (any-intersects? geom-fs analysis-f 10000) [3 styles/population-zone3-fn]))

(re-frame/reg-event-fx
 ::get-population-success
 (fn [{:keys [db]} [_ cb resp]]
   {:db         (assoc-in db [:analysis :population :data] (js/JSON.parse resp))
    :dispatch-n (if cb (cb resp) [])}))

(re-frame/reg-event-fx
 ::get-population-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :population (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-fx
 ::set-selected-population-grid
 (fn [{:keys [db]} [_ fcoll]]
   {:db (assoc-in db [:analysis :population :selected] fcoll)}))

(re-frame/reg-event-fx
 ::unselect-analysis
 (fn [{:keys [db]} _]
   {:dispatch [::hide-analysis]}))

(re-frame/reg-event-fx
 ::show-near-by-analysis
 (fn [{:keys [db]} _]
   (let [coords (-> db :map :center-wgs84)
         zoom   (-> db :map :zoom)
         f      {:type "FeatureCollection"
                 :features
                 [{:type       "Feature"
                   :properties {}
                   :geometry
                   {:type        "Point"
                    :coordinates [(:lon coords) (:lat coords)]}}]}]
     {:db       (-> db
                    (assoc-in [:analysis :geoms] f)
                    (assoc-in [:analysis :lipas-id] nil)
                    (assoc-in [:analysis :center] coords)
                    #_(cond->
                          (< zoom 7) (assoc-in [:map :zoom] 7)))
      :dispatch [::refresh-analysis]
      :ga/event ["analysis" "show-near-by-population"]})))

(re-frame/reg-event-fx
 ::show-analysis
 (fn [_ [_ lipas-id]]
   (if lipas-id
     (let [on-success [[::show-analysis* lipas-id]]]
       {:dispatch [:lipas.ui.sports-sites.events/get lipas-id on-success]})
     {})))

;; TODO resolve closest point in case of LineString / Polygon
(defn resolve-coords-js [js-fcoll]
  (let [geom (-> js-fcoll .-features (aget 0) .-geometry)]
    (case (.-type geom)
      "Point"      (-> geom .-coordinates)
      "LineString" (-> geom .-coordinates (aget 0))
      "Polygon"    (-> geom .-coordinates (aget 0) (aget 0)))))

(defn resolve-coords [fcoll]
  (let [geom (-> fcoll :features first :geometry)]
    (case (:type geom)
      "Point"      (-> geom :coordinates)
      "LineString" (-> geom :coordinates first)
      "Polygon"    (-> geom :coordinates first first))))

(re-frame/reg-event-fx
 ::show-analysis*
 (fn [{:keys [db]} [_ lipas-id]]
   (let [latest    (get-in db [:sports-sites lipas-id :latest])
         rev       (get-in db [:sports-sites lipas-id :history latest])
         geoms     (-> rev :location :geometries)
         [lon lat] (resolve-coords geoms)
         coords    {:lon lon :lat lat}]
     {:db       (-> db
                    (assoc-in [:analysis :geoms] geoms)
                    (assoc-in [:analysis :lipas-id] lipas-id)
                    (assoc-in [:analysis :center] coords)
                    (assoc-in [:analysis :site-name] (:name rev)))
      :dispatch [::refresh-analysis]
      :ga/event ["analysis" "show-analysis" lipas-id]})))

(re-frame/reg-event-db
 ::select-analysis-tab
 (fn [db [_ tab]]
   (assoc-in db [:analysis :selected-tab] tab)))

(defn calc-buffer-geom [db]
  (let [fcoll       (-> db :analysis :geoms)
        distance-km (-> db :analysis :distance-km)]
    (map-utils/calc-buffer-geom fcoll distance-km)))

(re-frame/reg-event-fx
 ::set-analysis-distance-km
 (fn [{:keys [db]} [_ v]]
   (let [buff-geom (calc-buffer-geom db)]
     {:db (-> db
              (assoc-in [:analysis :distance-km] v)
              (assoc-in [:analysis :buffer-geom] buff-geom))})))

(re-frame/reg-event-fx
 ::refresh-analysis
 (fn [{:keys [db]} _]
   (let [profiles    (-> db :analysis :travel-profiles)
         fcoll       (-> db :analysis :geoms)
         distance-km (-> db :analysis :distance-km)
         type-codes  (-> db :analysis :sports-sites :type-codes)
         zones       (-> db :analysis :zones)
         buff-geom   (calc-buffer-geom db)
         params      {:distance-km  distance-km
                      :profiles     profiles
                      :zones        zones
                      :search-fcoll fcoll
                      :buffer-fcoll buff-geom
                      :type-codes   type-codes}]
     {:db (assoc-in db [:analysis :buffer-geom] buff-geom)
      :dispatch-n
      [[::calc-distances-and-travel-times params]
       [:lipas.ui.search.events/submit-search false]]})))

(re-frame/reg-event-db
 ::select-sports-sites-view
 (fn [db [_ view]]
   (assoc-in db [:analysis :sports-sites :view] view)))

(re-frame/reg-event-db
 ::select-schools-view
 (fn [db [_ view]]
   (assoc-in db [:analysis :schools :view] view)))

(re-frame/reg-event-db
 ::select-travel-profile
 (fn [db [_ v]]
   (assoc-in db [:analysis :selected-travel-profile] v)))

(re-frame/reg-event-db
 ::select-travel-metric
 (fn [db [_ v]]
   (assoc-in db [:analysis :selected-travel-metric] v)))

(re-frame/reg-event-fx
 ::set-type-codes-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:analysis :sports-sites :type-codes] v)
    :dispatch-n [[:lipas.ui.search.events/set-type-filter v]
                 [::refresh-analysis]]}))

(re-frame/reg-event-fx
 ::set-zones
 (fn [{:keys [db]} [_ v metric]]
   (let [ranges (-> db :analysis :zones :ranges metric)
         zones  (->> v
                     (partition 2 1)
                     (map-indexed
                      (fn [idx [min-idx max-idx]]
                        {:min     (get-in ranges [min-idx :value])
                         :min-idx min-idx
                         :max     (get-in ranges [max-idx :value])
                         :max-idx max-idx
                         :id      (keyword (str "zone" (inc idx)))})))]
     {:db (assoc-in db [:analysis :zones metric] zones)
      :dispatch-n
      (let [old-max (-> db :analysis :distance-km)
            new-max (apply max v)]
        [(when (= :distance metric)
           [::set-analysis-distance-km new-max])
         (when (not= old-max new-max)
           [::refresh-analysis])])})))

(re-frame/reg-event-fx
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

(take 4 (range))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [params (:analysis db)]
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
      :db (assoc-in db [:analysis :loading?] true)})))

(re-frame/reg-event-fx
 ::report-success
 (fn [{:keys [db ]} [_ blob]]
   {:lipas.ui.effects/save-as!
    {:blob         blob
     :filename     "lipas-analysis.xlsx"
     :content-type (-> cutils/content-type :xlsx)}
    :db (assoc-in db [:analysis :loading?] false)}))

(re-frame/reg-event-fx
 ::report-failure
 (fn [{:keys [db]} [_ error]]
   (let [fatal? false
         tr     (-> db :translator)]
     {:db           (assoc-in db [:analysis :loading?] false)
      :ga/exception [(:message error) fatal?]
      :dispatch     [:lipas.ui.events/set-active-notification
                     {:message  (tr :notifications/get-failed)
                      :success? false}]})))
