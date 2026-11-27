(ns lipas.ui.site-notes.events
  "Org-private site notes: fetched per site (sports-site panel) and per org (the
  org page's notes tab). Notes are append-only on the backend — the only
  changes are new notes and status changes (archive / restore, and delete for
  archived notes)."
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]))

(defn- post [db path params on-success]
  {:method :post
   :uri (str (:backend-url db) "/actions/" path)
   :headers {:Authorization (str "Token " (-> db :user :login :token))}
   :params params
   :format (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success on-success
   :on-failure [::failure]})

(rf/reg-event-fx ::failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)]
      {:dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> resp :response :error)
                                (tr :error/unknown))
                   :success? false}]})))

;; --- Per site ---------------------------------------------------------------

(rf/reg-event-fx ::get-site-notes
  (fn [{:keys [db]} [_ lipas-id]]
    {:http-xhrio (post db "get-site-notes" {:lipas-id lipas-id}
                       [::get-site-notes-success lipas-id])}))

(rf/reg-event-db ::get-site-notes-success
  (fn [db [_ lipas-id notes]]
    (assoc-in db [:site-notes :by-site lipas-id] notes)))

(rf/reg-event-db ::open-drawer
  (fn [db [_ {:keys [lipas-id org-id org-name]}]]
    (assoc-in db [:site-notes :drawer] {:lipas-id lipas-id
                                        :org-id org-id
                                        :org-name org-name
                                        :show-archived? false})))

(rf/reg-event-db ::close-drawer
  (fn [db _]
    (update db :site-notes dissoc :drawer)))

(rf/reg-event-db ::toggle-show-archived
  (fn [db _]
    (update-in db [:site-notes :drawer :show-archived?] not)))

(rf/reg-event-db ::set-draft
  (fn [db [_ lipas-id text]]
    (assoc-in db [:site-notes :drafts lipas-id] text)))

(rf/reg-event-fx ::create-note
  (fn [{:keys [db]} [_ {:keys [lipas-id org-id body]}]]
    {:db (assoc-in db [:site-notes :saving?] true)
     :http-xhrio (-> (post db "create-site-note"
                           {:lipas-id lipas-id :org-id org-id :body body}
                           [::create-note-success lipas-id])
                     (assoc :on-failure [::save-failure]))}))

(rf/reg-event-fx ::create-note-success
  (fn [{:keys [db]} [_ lipas-id _note]]
    {:db (-> db
             (assoc-in [:site-notes :saving?] false)
             (update-in [:site-notes :drafts] dissoc lipas-id))
     :fx [[:dispatch [::get-site-notes lipas-id]]
          [:dispatch [::refresh-org-notes]]]}))

(rf/reg-event-fx ::save-failure
  (fn [{:keys [db]} [_ resp]]
    {:db (assoc-in db [:site-notes :saving?] false)
     :fx [[:dispatch [::failure resp]]]}))

;; --- Status changes ---------------------------------------------------------

(rf/reg-event-fx ::set-status
  (fn [{:keys [db]} [_ note status]]
    {:http-xhrio (post db "set-site-note-status"
                       {:note-id (:id note) :status status}
                       [::set-status-success note])}))

(rf/reg-event-fx ::set-status-success
  (fn [{:keys [db]} [_ note _resp]]
    {:fx [(when (get-in db [:site-notes :by-site (:lipas-id note)])
            [:dispatch [::get-site-notes (:lipas-id note)]])
          [:dispatch [::refresh-org-notes]]]}))

;; --- Per org ----------------------------------------------------------------

(rf/reg-event-fx ::get-org-notes
  (fn [{:keys [db]} [_ org-id status]]
    {:db (assoc-in db [:site-notes :org] {:org-id org-id
                                          :status status
                                          :notes (when (= [org-id status]
                                                          [(get-in db [:site-notes :org :org-id])
                                                           (get-in db [:site-notes :org :status])])
                                                   (get-in db [:site-notes :org :notes]))})
     :http-xhrio (post db "get-org-site-notes" {:org-id org-id :statuses [status]}
                       [::get-org-notes-success org-id status])}))

(rf/reg-event-db ::get-org-notes-success
  (fn [db [_ org-id status notes]]
    ;; Drop a late response for a filter the user already left.
    (if (= [org-id status] [(get-in db [:site-notes :org :org-id])
                            (get-in db [:site-notes :org :status])])
      (assoc-in db [:site-notes :org :notes] notes)
      db)))

(rf/reg-event-fx ::refresh-org-notes
  (fn [{:keys [db]} _]
    (let [{:keys [org-id status]} (get-in db [:site-notes :org])]
      (when org-id
        {:fx [[:dispatch [::get-org-notes org-id status]]]}))))
