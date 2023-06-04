(ns lipas.ui.sports-sites.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.interceptors :as interceptors]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ lipas-id]]
   (let [site (get-in db [:sports-sites lipas-id])
         rev  (-> (utils/make-revision site (utils/timestamp))
                  (utils/make-editable))]
     (-> db
         (assoc-in [:sports-sites lipas-id :editing] rev)
         (assoc-in [:sports-sites :name-check] {})))))

(defmulti calc-derived-fields (comp :type-code :type))
(defmethod calc-derived-fields :default [sports-site] sports-site)

(re-frame/reg-event-db
 ::calc-derived-fields
 (fn [db [_ lipas-id sports-site]]
   (assoc-in db [:sports-sites lipas-id :editing] (calc-derived-fields sports-site))))

(re-frame/reg-event-fx
 ::edit-field
 (fn [{:keys [db]} [_ lipas-id path value]]
   (let [new-db (utils/set-field db (into [:sports-sites lipas-id :editing] path) value)]
     {:db       new-db
      :dispatch [::calc-derived-fields lipas-id (get-in new-db [:sports-sites lipas-id :editing])]})))

(re-frame/reg-event-db
 ::discard-edits
 (fn [db [_ lipas-id]]
   (assoc-in db [:sports-sites lipas-id :editing] nil)))

;; Save revision (data). Triggers REST-api call
(re-frame/reg-event-fx
 ::commit-rev
 [interceptors/check-token]
 (fn [{:keys [db]} [_ rev draft? on-success on-failure]]
   (let [token  (-> db :user :login :token)
         params (when draft? "?draft=true")]
     {:db (assoc-in db [:sports-sites :save-in-progress?] true)
      :http-xhrio
      {:method          :post
       :headers         {:Authorization (str "Token " token)}
       :uri             (str (:backend-url db) (str "/sports-sites" params))
       :params          rev
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-success on-success]
       :on-failure      [::save-failure on-failure]}})))

;; Save by lipas-id. Sets :event-date to current-timestamp.
(re-frame/reg-event-fx
 ::save-edits
 (fn [{:keys [db]} [_ lipas-id on-success on-failure]]
   (let [rev    (get-in db [:sports-sites lipas-id :editing])
         type   (get-in db [:sports-sites :types (-> rev :type :type-code)])
         rev    (-> rev
                    utils/make-saveable
                    (update :properties #(select-keys % (-> type :props keys)))
                    (assoc :event-date (utils/timestamp)))
         draft? false]
     {:dispatch [::commit-rev rev draft? on-success on-failure]})))

(re-frame/reg-event-fx
 ::save-success
 (fn [{:keys [db]} [_ on-success result]]
   ;; `on-success` is a function that returns vector of events to be
   ;; dispatched.
   (let [tr              (-> db :translator)
         #_#_status      (-> result :status)
         type            (-> result :type :type-code)
         lipas-id        (-> result :lipas-id)
         year            (dec utils/this-year)
         dispatch-extras (when on-success (on-success result))]
     {:db             (-> db
                          (utils/add-to-db result)
                          (assoc-in [:sports-sites lipas-id :editing] nil)
                          (assoc-in [:sports-sites :save-in-progress?] false))
      :dispatch-n     (into
                       [[:lipas.ui.events/set-active-notification
                         {:message  (tr :notifications/save-success)
                          :success? true}]
                        (when (#{2510 2520 3110 3130} type)
                          [:lipas.ui.energy.events/fetch-energy-report year type])]
                       dispatch-extras)
      :tracker/event! ["sports-site" "save" "lipas-id" lipas-id]})))

(re-frame/reg-event-fx
 ::save-failure
 (fn [{:keys [db]} [_ on-failure error]]
   (let [tr     (:translator db)]
     {:db           (-> db
                        (assoc-in [:sports-sites :errors (utils/timestamp)] error)
                        (assoc-in [:sports-sites :save-in-progress?] false))
      :dispatch-n   (into
                     [[:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/save-failed)
                        :success? false}]]
                     (when on-failure (on-failure error)))
      :tracker/event! ["error" "save-sports-site-failure"]})))

(re-frame/reg-event-fx
 ::get-success
 (fn [{:keys [db]} [_ sites]]
   {:db (reduce utils/add-to-db db sites)}))

(re-frame/reg-event-fx
 ::get-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :sports-sites (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-fx
 ::get-by-type-code
 (fn [{:keys [db]} [_ type-code]]
   {:http-xhrio
    {:method          :get
     :uri             (str (:backend-url db) "/sports-sites/type/" type-code)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-success]
     :on-failure      [::get-failure]}}))

(re-frame/reg-event-fx
 ::get-success-single
 (fn [{:keys [db]} [_ on-success site]]
   {:db         (utils/add-to-db db site)
    :dispatch-n (or on-success [])}))

(re-frame/reg-event-fx
 ::get
 (fn [{:keys [db]} [_ lipas-id on-success]]
   (let [latest (get-in db [:sports-sites lipas-id :latest])]
     (if latest
       {:dispatch-n (or on-success [])} ;; No need for get if we already have the data
       {:http-xhrio
        {:method          :get
         :uri             (str (:backend-url db) "/sports-sites/" lipas-id)
         :response-format (ajax/json-response-format {:keywords? true})
         :on-success      [::get-success-single on-success]
         :on-failure      [::get-failure]}}))))

(re-frame/reg-event-fx
 ::get-history
 (fn [{:keys [db]} [_ lipas-id]]
   {:http-xhrio
    {:method          :get
     :uri             (str (:backend-url db) "/sports-sites/history/" lipas-id)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-success]
     :on-failure      [::get-failure]}}))

(re-frame/reg-event-fx
 ::download-contacts-report
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :reports/contacts)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config})))

(re-frame/reg-event-fx
 ::start-adding-new-site
 (fn [{:keys [db]} [_ template]]
   {:db       (-> db
                  (assoc-in [:new-sports-site :adding?] true)
                  (assoc-in [:new-sports-site :template] template))
    :dispatch-n [[:lipas.ui.search.events/clear-filters]
                 [:lipas.ui.search.events/set-results-view :list]
                 [::clear-name-check]]}))

(re-frame/reg-event-db
 ::discard-new-site
 (fn [db [_]]
   (-> db
       (assoc-in [:new-sports-site :adding?] false)
       (assoc-in [:new-sports-site :type] nil)
       (assoc-in [:new-sports-site :data] nil)
       (assoc-in [:new-sports-site :template] nil))))

(re-frame/reg-event-db
 ::select-new-site-type
 (fn [db [_ type-code]]
   (assoc-in db [:new-sports-site :type] type-code)))

(re-frame/reg-event-fx
 ::init-new-site
 (fn [{:keys [db]} [_ type-code geoms]]
   ;; If city-code is not set yet, We guess it based on user
   ;; permissions.  TODO maybe use geolocation for better guesses
   (let [template  (or (-> db :new-sports-site :template) {})
         city-code (or
                    (-> db :new-sports-site :data :location :city :city-code)
                    (-> db :user :login :permissions :cities first))
         data      (cutils/deep-merge
                    {:status     "active"
                     :event-date (utils/timestamp)
                     :type       {:type-code type-code}
                     :location   {:geometries geoms
                                  :city       {:city-code city-code}}}
                    template)]
     {:db (-> db
              (update-in [:new-sports-site :data] cutils/deep-merge data)
              (update :new-sports-site dissoc :template))})))

(re-frame/reg-event-db
 ::edit-new-site-field
 (fn [db [_ path value]]
   (utils/set-field db (into [:new-sports-site :data] path) value)))

(re-frame/reg-event-db
 ::toggle-delete-dialog
 (fn [db _]
   (update-in db [:sports-sites :delete-dialog :open?] not)))

(re-frame/reg-event-db
 ::select-delete-status
 (fn [db [_ status]]
   (assoc-in db [:sports-sites :delete-dialog :selected-status] status)))

(re-frame/reg-event-db
 ::select-delete-year
 (fn [db [_ year]]
   (assoc-in db [:sports-sites :delete-dialog :selected-year] year)))

(re-frame/reg-event-fx
 ::delete
 (fn [db [_ data status year draft?]]
   (let [event-date (if (utils/this-year? year)
                      (utils/timestamp)
                      (utils/->end-of-year year))
         data       (-> data
                        (assoc :event-date event-date :status status)
                        (utils/clean))
         on-success (fn [] [[:lipas.ui.map.events/show-sports-site nil]
                            [:lipas.ui.search.events/submit-search]
                            [::select-delete-status nil]
                            [::select-delete-year utils/this-year]])]
     {:dispatch [::commit-rev data draft? on-success]})))

(re-frame/reg-event-fx
 ::duplicate
 (fn [{:keys [db]} [_ rev]]
   (let [data (merge
               (dissoc rev :lipas-id)
               {:event-date (utils/timestamp)
                :status     "active"})]
     {:db (-> db
              (assoc-in [:new-sports-site :data] data))})))

(re-frame/reg-event-fx
 ::resurrect
 (fn [{:keys [db]} [_ lipas-id on-success on-failure]]
   (let [site  (get-in db [:sports-sites lipas-id])
         rev   (-> (utils/make-revision site (utils/timestamp))
                   (utils/make-editable)
                   (assoc :status "active"))
         draft false]
     {:dispatch [::commit-rev rev draft on-success on-failure]})))

(re-frame/reg-event-fx
 ::check-sports-site-name
 [interceptors/check-token]
 (fn [{:keys [db]} [_ lipas-id name]]
   (let [params {:lipas-id lipas-id :name name}]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) (str "/actions/check-sports-site-name"))
       :params          params
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::check-sports-site-name-success]
       :on-failure      [::check-sports-site-name-failure]}})))

(re-frame/reg-event-db
 ::check-sports-site-name-success
 (fn [db [_ resp]]
   (assoc-in db [:sports-sites :name-check :response] resp)))

(re-frame/reg-event-db
 ::check-sports-site-name-failure
 (fn [db [_ resp]]
   (assoc-in db [:sports-sites :name-check :error] resp)))

(re-frame/reg-event-db
 ::clear-name-check
 (fn [db [_ resp]]
   (assoc-in db [:sports-sites :name-check] {})))
