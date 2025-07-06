(ns lipas.ui.bulk-operations.events
  (:require [ajax.core :as ajax]
            [lipas.ui.bulk-operations.db :as db]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ {:keys [on-success]}]]
    (let [already-initialized? (seq (get-in db [:bulk-operations :editable-sites]))]
      (if already-initialized?
                       ;; If already initialized, don't reset the state
        {:dispatch (when on-success [on-success])}
                       ;; Otherwise, initialize fresh
        {:db (assoc-in db [:bulk-operations] db/default-db)
         :dispatch-n [[::get-editable-sites]
                      (when on-success [on-success])]}))))

(rf/reg-event-fx ::get-editable-sites
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)]
      {:db (assoc-in db [:bulk-operations :loading?] true)
       :http-xhrio
       {:method :get
        :uri (str (:backend-url db) "/actions/get-editable-sports-sites")
        :headers {:Authorization (str "Token " token)}
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::get-editable-sites-success]
        :on-failure [::get-editable-sites-failure]}})))

(rf/reg-event-db ::get-editable-sites-success
  (fn [db [_ sites]]
    (-> db
        (assoc-in [:bulk-operations :editable-sites] sites)
        (assoc-in [:bulk-operations :loading?] false))))

(rf/reg-event-db ::get-editable-sites-failure
  (fn [db [_ error]]
    (js/console.error "Failed to get editable sites:" error)
    (-> db
        (assoc-in [:bulk-operations :error] error)
        (assoc-in [:bulk-operations :loading?] false))))

(rf/reg-event-db ::toggle-site-selection
  (fn [db [_ lipas-id]]
    (update-in db [:bulk-operations :selected-sites]
               (fn [selected]
                 (if (contains? selected lipas-id)
                   (disj selected lipas-id)
                   (conj selected lipas-id))))))

(rf/reg-event-db ::select-all-sites
  (fn [db [_ site-ids]]
    (assoc-in db [:bulk-operations :selected-sites] (set site-ids))))

(rf/reg-event-db ::deselect-all-sites
  (fn [db _]
    (assoc-in db [:bulk-operations :selected-sites] #{})))

(rf/reg-event-db ::toggle-field-selection
  (fn [db [_ field]]
    (update-in db [:bulk-operations :selected-fields]
               (fn [selected]
                 (if (contains? selected field)
                   (disj selected field)
                   (conj selected field))))))

(rf/reg-event-db ::set-selected-fields
  (fn [db [_ fields]]
    (assoc-in db [:bulk-operations :selected-fields] fields)))

(rf/reg-event-db ::set-bulk-update-field
  (fn [db [_ field value]]
    (assoc-in db [:bulk-operations :update-form field] value)))

(rf/reg-event-fx ::execute-bulk-update
  (fn [{:keys [db]} [_ {:keys [on-success on-failure]}]]
    (let [token (-> db :user :login :token)
          selected-sites (get-in db [:bulk-operations :selected-sites])
          update-form (get-in db [:bulk-operations :update-form])
          selected-fields (get-in db [:bulk-operations :selected-fields])
                         ;; Remove unselected fields from the update form
          filtered-updates (reduce-kv
                             (fn [m k v]
                               (if (contains? selected-fields k)
                                 (assoc m k v)
                                 m))
                             {}
                             update-form)]
      {:db (assoc-in db [:bulk-operations :loading?] true)
       :http-xhrio
       {:method :post
        :uri (str (:backend-url db) "/actions/mass-update-sports-sites")
        :headers {:Authorization (str "Token " token)}
        :params {:lipas-ids (vec selected-sites)
                 :updates filtered-updates}
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::execute-bulk-update-success on-success]
        :on-failure [::execute-bulk-update-failure on-failure]}})))

(rf/reg-event-fx ::execute-bulk-update-success
  (fn [{:keys [db]} [_ on-success resp]]
    (js/console.log "Bulk update success:" resp)
    {:db (-> db
             (assoc-in [:bulk-operations :update-results] resp)
             (assoc-in [:bulk-operations :current-step] 2)
             (assoc-in [:bulk-operations :loading?] false))
     :dispatch (when on-success (on-success resp))}))

(rf/reg-event-fx ::execute-bulk-update-failure
  (fn [{:keys [db]} [_ on-failure error]]
    (js/console.error "Bulk update failed:" error)
    {:db (-> db
             (assoc-in [:bulk-operations :error] error)
             (assoc-in [:bulk-operations :loading?] false))
     :dispatch (when on-failure (on-failure error))}))

(rf/reg-event-db ::set-sites-filter
  (fn [db [_ filter-key value]]
    (assoc-in db [:bulk-operations :filters filter-key] value)))

(rf/reg-event-db ::reset
  (fn [db _]
    (-> db
                       ;; Reset only the selection and form state, keep the loaded data
        (assoc-in [:bulk-operations :selected-sites] #{})
        (assoc-in [:bulk-operations :selected-fields] #{})
        (assoc-in [:bulk-operations :update-form] {})
        (assoc-in [:bulk-operations :filters] {})
        (assoc-in [:bulk-operations :current-step] 0)
        (assoc-in [:bulk-operations :update-results] nil)
        (assoc-in [:bulk-operations :error] nil))))

(rf/reg-event-db ::set-current-step
  (fn [db [_ step]]
    (cond-> db
      true (assoc-in [:bulk-operations :current-step] step)
                     ;; When moving to step 2, initialize all fields as selected if none are selected yet
      (and (= step 1) (empty? (get-in db [:bulk-operations :selected-fields])))
      (assoc-in [:bulk-operations :selected-fields] #{:email :phone-number :www :reservations-link}))))
