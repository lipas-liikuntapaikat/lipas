(ns lipas.ui.help.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(defn- index-of-slug
  [coll slug]
  (when slug
    (first (keep-indexed
            (fn [idx x] (when (= (keyword slug) (:slug x)) idx))
            coll))))

(defn- resolve-selection
  "Resolve section/page slugs (strings or keywords) against loaded help
   data. Returns selection paths or nil when the section is unknown."
  [data section-slug page-slug]
  (when-let [section-idx (index-of-slug data section-slug)]
    (let [pages    (get-in data [section-idx :pages])
          page-idx (index-of-slug pages page-slug)]
      {:section-idx  section-idx
       :section-slug (get-in data [section-idx :slug])
       :page-idx     page-idx
       :page-slug    (when page-idx (get-in pages [page-idx :slug]))})))

(defn- apply-selection
  [db {:keys [section-idx section-slug page-idx page-slug]}]
  (-> db
      (assoc-in [:help :dialog :selected-section-idx] section-idx)
      (assoc-in [:help :dialog :selected-section-slug] section-slug)
      (assoc-in [:help :dialog :selected-page-idx] page-idx)
      (assoc-in [:help :dialog :selected-page-slug] page-slug)))

(defn- ohje-param
  "Current dialog selection as the ?ohje= query param value, or nil when
   the dialog is closed (nil removes the param from the URL)."
  [db]
  (when (get-in db [:help :dialog :open?])
    (let [section-slug (or (get-in db [:help :dialog :selected-section-slug])
                           (let [idx (get-in db [:help :dialog :selected-section-idx])]
                             (when (number? idx)
                               (get-in db [:help :data idx :slug]))))
          page-slug    (get-in db [:help :dialog :selected-page-slug])]
      (cond
        (and section-slug page-slug) (str (name section-slug) "/" (name page-slug))
        section-slug                 (name section-slug)
        :else                        ""))))

(rf/reg-fx ::sync-url!
  (fn [param]
    (let [url (js/URL. js/window.location.href)]
      (if (some? param)
        (.set (.-searchParams url) "ohje" param)
        (.delete (.-searchParams url) "ohje"))
      (.replaceState js/window.history nil "" (.toString url)))))

(rf/reg-event-fx ::open-dialog
  (fn [{:keys [db]} _]
    (let [db (assoc-in db [:help :dialog :open?] true)]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-fx ::close-dialog
  (fn [{:keys [db]} _]
    (let [db (assoc-in db [:help :dialog :open?] false)]
      {:db db ::sync-url! nil})))

(rf/reg-event-fx ::select-section
  (fn [{:keys [db]} [_ section-idx section-slug]]
    (let [db (-> db
                 (assoc-in [:help :dialog :selected-section-idx] section-idx)
                 (assoc-in [:help :dialog :selected-section-slug] section-slug)
                 (assoc-in [:help :dialog :selected-page-idx] nil)
                 (assoc-in [:help :dialog :selected-page-slug] nil))]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-fx ::select-page
  (fn [{:keys [db]} [_ page-idx page-slug]]
    (let [db (-> db
                 (assoc-in [:help :dialog :selected-page-idx] page-idx)
                 (assoc-in [:help :dialog :selected-page-slug] page-slug))]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-fx ::navigate-to
  (fn [{:keys [db]} [_ section-slug page-slug]]
    (let [data (get-in db [:help :data])
          db   (assoc-in db [:help :dialog :open?] true)
          db   (if (nil? data)
                 ;; Data not loaded yet — remember the target, ::get-success
                 ;; resolves it once content arrives.
                 (assoc-in db [:help :dialog :pending-slugs]
                           {:section section-slug :page page-slug})
                 (if-let [selection (resolve-selection data section-slug page-slug)]
                   (apply-selection db selection)
                   db))]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-fx ::open-edit-mode
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:help :dialog :mode] :edit)
     :fx [[:dispatch [:lipas.ui.help.manage/initialize-editor]]]}))

(rf/reg-event-db ::close-edit-mode
  (fn [db _]
    (assoc-in db [:help :dialog :mode] :read)))

(rf/reg-event-fx ::init
  ;; Dispatched once at app startup: loads help content and opens the
  ;; dialog when the URL carries an ?ohje=section/page deep link.
  (fn [_ _]
    (let [ohje (-> (js/URLSearchParams. js/window.location.search)
                   (.get "ohje"))]
      {:fx (cond-> [[:dispatch [::get-help-data]]]
             (some? ohje)
             (conj [:dispatch (let [[section page] (str/split ohje #"/")]
                                [::navigate-to (not-empty section) (not-empty page)])]))})))

(rf/reg-event-fx ::get-help-data
  (fn [{:keys [db]} _]
    {:fx [[:http-xhrio
           {:method          :post
            :uri             (str (:backend-url db) "/actions/get-help-data")
            :format          (ajax/transit-request-format)
            :response-format (ajax/transit-response-format)
            :on-success      [::get-success]
            :on-failure      [::get-failure]}]]}))

(rf/reg-event-fx ::get-success
  (fn [{:keys [db]} [_ help-data]]
    (let [pending (get-in db [:help :dialog :pending-slugs])
          db      (-> db
                      (assoc-in [:help :data] help-data)
                      (update-in [:help :dialog] dissoc :pending-slugs))
          db      (if-let [selection (and pending
                                          (resolve-selection help-data
                                                             (:section pending)
                                                             (:page pending)))]
                    (apply-selection db selection)
                    db)]
      (cond-> {:db db}
        pending (assoc ::sync-url! (ohje-param db))))))

(rf/reg-event-fx ::get-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-failed)
                        :success? false}]
      {:db (assoc-in db [:help :errors :get] resp)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))
