(ns lipas.ui.analysis.heatmap.events
  (:require [ajax.core :as ajax]
            [lipas.ui.analysis.heatmap.db :as heatmap-db]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx
 ::init
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:map :mode :name] :analysis)
            (assoc-in [:map :mode :sub-mode] :heatmap))
    :dispatch-n
    [[:lipas.ui.search.events/clear-filters]
     [:lipas.ui.map.events/set-overlays
      [[:vectors false]
       [:schools false]
       [:lois false]
       [:population false]
       [:analysis false]
       [:diversity-grid false]
       [:diversity-area false]
       [:heatmap true]]]]}))

(rf/reg-event-db
 ::set-dimension
 (fn [db [_ dimension]]
   (heatmap-db/set-dimension db dimension)))

(rf/reg-event-db
 ::set-weight-by
 (fn [db [_ weight-by]]
   (heatmap-db/set-weight-by db weight-by)))

(rf/reg-event-db
 ::set-precision
 (fn [db [_ precision]]
   (heatmap-db/set-precision db precision)))

(rf/reg-event-db
 ::set-filter
 (fn [db [_ filter-key value]]
   (heatmap-db/set-filter db filter-key value)))

(rf/reg-event-db
 ::clear-filter
 (fn [db [_ filter-key]]
   (heatmap-db/clear-filter db filter-key)))

(rf/reg-event-db
 ::set-visual-param
 (fn [db [_ param value]]
   (heatmap-db/set-visual-param db param value)))

(defn get-map-bounds [db]
  (let [top-left (-> db :map :top-left-wgs84)
        bottom-right (-> db :map :bottom-right-wgs84)]
    {:min-x (first top-left)
     :max-x (first bottom-right)
     :min-y (second bottom-right)
     :max-y (second top-left)}))

(rf/reg-event-fx
 ::create-heatmap
 (fn [{:keys [db]} _]
   (let [token (-> db :user :login :token)
         params (merge
                 (select-keys (:heatmap db) [:dimension :weight-by :filters :precision])
                 {:zoom (-> db :map :zoom)
                  :bbox (get-map-bounds db)})]
     {:db (heatmap-db/set-loading db true)
      :http-xhrio {:method :post
                   :headers {:Authorization (str "Token " token)}
                   :uri (str (:backend-url db) "/actions/create-heatmap")
                   :params (utils/clean params)
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success [::heatmap-created]
                   :on-failure [::heatmap-failed]}})))

(rf/reg-event-db
 ::heatmap-created
 (fn [db [_ response]]
   (-> db
       (heatmap-db/set-loading false)
       (heatmap-db/set-error nil)
       (heatmap-db/set-heatmap-data (:data response)))))

(rf/reg-event-db
 ::heatmap-failed
 (fn [db [_ error]]
   (-> db
       (heatmap-db/set-loading false)
       (heatmap-db/set-error (str "Failed to load heatmap: " (get-in error [:response :error] "Unknown error"))))))

(rf/reg-event-fx
 ::get-facets
 (fn [{:keys [db]} _]
   (let [token (-> db :user :login :token)
         params {:bbox (get-map-bounds db)
                 :filters (select-keys (-> db :heatmap :filters) [:status-codes])}]
     {:http-xhrio {:method :post
                   :headers {:Authorization (str "Token " token)}
                   :uri (str (:backend-url db) "/actions/get-heatmap-facets")
                   :params params
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format {:keywords? true})
                   :on-success [::facets-loaded]
                   :on-failure [::facets-failed]}})))

(rf/reg-event-db
 ::facets-loaded
 (fn [db [_ facets]]
   (heatmap-db/set-facets db facets)))

(rf/reg-event-db
 ::facets-failed
 (fn [db [_ error]]
   (js/console.error "Failed to load facets:" error)
   db))

;; Combined event to update filters and refresh heatmap
(rf/reg-event-fx
 ::update-filter-and-refresh
 (fn [{:keys [db]} [_ filter-key value]]
   {:db (heatmap-db/set-filter db filter-key value)
    :dispatch [::create-heatmap]}))

 ;; Combined event to update precision and refresh heatmap
(rf/reg-event-fx
 ::update-precision-and-refresh
 (fn [{:keys [db]} [_ precision]]
   {:db (heatmap-db/set-precision db precision)
    :dispatch [::create-heatmap]}))

;; Combined event to update visual param and refresh heatmap layer
(rf/reg-event-fx
 ::update-visual-param
 (fn [{:keys [db]} [_ param value]]
   {:db (heatmap-db/set-visual-param db param value)
    :dispatch [::update-heatmap-layer]}))

;; Event to signal that the heatmap layer should be updated
(rf/reg-event-db
 ::update-heatmap-layer
 (fn [db _]
   ;; This event doesn't change the db but signals to the map view
   ;; that it should update the heatmap layer visual parameters
   db))

;; Initialize heatmap when entering heatmap mode
(rf/reg-event-fx
 ::enter-heatmap-mode
 (fn [{:keys [db]} _]
   {:db (heatmap-db/init-db db)
    :dispatch-n [[::get-facets]
                 [::create-heatmap]
                 [::init]]}))

;; Event triggered when map view changes (zoom/pan)
(rf/reg-event-fx
 ::map-view-changed
 (fn [{:keys [db]} _]
   (when (= (get-in db [:analysis :selected-tool]) "heatmap")
     {:dispatch-n [[::get-facets]
                   [::create-heatmap]]})))

;; Cleanup when leaving heatmap mode
(rf/reg-event-db
 ::leave-heatmap-mode
 (fn [db _]
   (dissoc db :heatmap)))
