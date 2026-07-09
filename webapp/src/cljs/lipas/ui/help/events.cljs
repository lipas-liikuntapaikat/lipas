(ns lipas.ui.help.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [re-frame.core :as rf]))

;; Help content is v2: {:fi [sections] :se [...] :en [...]} — each
;; locale has its own independent tree. The UI shows the user's locale
;; when it has published content, otherwise falls back to Finnish.

(defn- match-slug?
  "Slug match including :aliases so old published ?ohje= links and KB
   citations keep resolving after renames."
  [{:keys [slug aliases]} target]
  (let [t (name target)]
    (boolean (or (= slug t) (some #(= t %) aliases)))))

(defn find-section [tree slug]
  (when (and tree slug)
    (first (filter #(match-slug? % slug) tree))))

(defn find-page [section slug]
  (when (and section slug)
    (first (filter #(match-slug? % slug) (:pages section)))))

(defn display-tree
  "The tree shown to the user: their locale's tree, or the fi tree when
   their locale has no published content."
  [db]
  (let [locale ((:translator db))
        data (get-in db [:help :data])
        tree (get data locale)]
    (if (seq tree) tree (:fi data))))

(defn- resolve-selection
  "Resolve section/page slugs (strings, keywords or aliases) against the
   display tree. Returns canonical slugs or nil when the section is
   unknown."
  [db section-slug page-slug]
  (let [tree (display-tree db)]
    (when-let [section (find-section tree section-slug)]
      (let [page (find-page section page-slug)]
        {:section-slug (:slug section)
         :page-slug (:slug page)}))))

(defn- apply-selection
  [db {:keys [section-slug page-slug]}]
  (-> db
      (assoc-in [:help :dialog :selected-section-slug] section-slug)
      (assoc-in [:help :dialog :selected-page-slug] page-slug)
      (update-in [:help :dialog :expanded-sections] (fnil conj #{}) section-slug)))

(defn- ohje-param
  "Current dialog selection as the ?ohje= query param value, or nil when
   the dialog is closed (nil removes the param from the URL)."
  [db]
  (when (get-in db [:help :dialog :open?])
    (let [section-slug (get-in db [:help :dialog :selected-section-slug])
          page-slug (get-in db [:help :dialog :selected-page-slug])]
      (cond
        (and section-slug page-slug) (str section-slug "/" page-slug)
        section-slug section-slug
        :else ""))))

(defn dialog-context
  "Open help page for the assistant's context snapshot, or nil."
  [db]
  (when (get-in db [:help :dialog :open?])
    (let [section-slug (get-in db [:help :dialog :selected-section-slug])
          page-slug (get-in db [:help :dialog :selected-page-slug])
          section (find-section (display-tree db) section-slug)
          page (find-page section page-slug)]
      (cond-> {}
        section-slug (assoc :section section-slug)
        page-slug (assoc :page page-slug)
        (:title page) (assoc :title (:title page))))))

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
  (fn [{:keys [db]} [_ section-slug]]
    (let [db (apply-selection db {:section-slug section-slug :page-slug nil})]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-fx ::select-page
  (fn [{:keys [db]} [_ section-slug page-slug]]
    (let [db (apply-selection db {:section-slug section-slug :page-slug page-slug})]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-fx ::go-home
  (fn [{:keys [db]} _]
    (let [db (-> db
                 (assoc-in [:help :dialog :selected-section-slug] nil)
                 (assoc-in [:help :dialog :selected-page-slug] nil))]
      {:db db ::sync-url! (ohje-param db)})))

(rf/reg-event-db ::toggle-section
  (fn [db [_ section-slug]]
    (update-in db [:help :dialog :expanded-sections]
               (fn [expanded]
                 (let [expanded (or expanded #{})]
                   (if (contains? expanded section-slug)
                     (disj expanded section-slug)
                     (conj expanded section-slug)))))))

(rf/reg-event-db ::set-search-term
  (fn [db [_ term]]
    (assoc-in db [:help :dialog :search-term] term)))

(rf/reg-event-fx ::navigate-to
  (fn [{:keys [db]} [_ section-slug page-slug]]
    (let [data (get-in db [:help :data])
          db (assoc-in db [:help :dialog :open?] true)
          db (if (nil? data)
               ;; Data not loaded yet — remember the target, ::get-success
               ;; resolves it once content arrives.
               (assoc-in db [:help :dialog :pending-slugs]
                         {:section section-slug :page page-slug})
               (if-let [selection (resolve-selection db section-slug page-slug)]
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
                                          (resolve-selection db
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
