(ns lipas.ui.search.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [lipas.search-query :as search-query]
            [lipas.ui.search.db :as db]
            [lipas.ui.utils :as utils]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::search
  (fn [{:keys [db]} [_ params fit-view?]]
    {:http-xhrio
     {:method :post
      :uri (str (:backend-url db) "/actions/search")
      :params (search-query/->es-search-body params (-> db :user :login))
      :format (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success [::search-success fit-view?]
      :on-failure [::search-failure]}
     :db (assoc-in db [:search :in-progress?] true)}))

(rf/reg-event-fx ::search-success
  (fn [{:keys [db]} [_ fit-view? resp]]
    {:db (-> db
             (assoc-in [:search :results] resp)
             (assoc-in [:search :in-progress?] false))
     :dispatch-n [(when fit-view? [:lipas.ui.map.events/fit-to-current-vectors])]}))

(rf/reg-event-fx ::search-failure
  (fn [{:keys [db]} [_ error]]
    (let [tr (:translator db)]
      {:db (-> db
               (assoc-in [:errors :search (utils/timestamp)] error)
               (assoc-in [:search :in-progress?] false))
       :dispatch [:lipas.ui.events/set-active-notification
                  {:message (tr :notifications/get-failed)
                   :success? false}]})))

(rf/reg-event-fx ::search-fast
  (fn [{:keys [db]} [_ params fit-view? terse?]]
    {:http-xhrio
     {:method :post
      :uri (str (:backend-url db) "/actions/search")
      :params (search-query/->es-search-body params (-> db :user :login) terse?)
      :format (ajax/json-request-format)
      :response-format (ajax/raw-response-format)
      :on-success [::search-success-fast fit-view?]
      :on-failure [::search-failure]}
     :db (assoc-in db [:search :in-progress?] true)}))

(rf/reg-event-fx ::search-success-fast
  (fn [{:keys [db]} [_ fit-view? resp]]
    ;; TODO: Not sure if calling JSON.parse here is any faster than letting ajax lib call it?
    ;; or well, that probably ALSO does clj conversion which we don't want.
    ;; What about fetch and .json method?
    ;; Consider also using cljs-bean. - Juho
    {:db (-> db
             (assoc-in [:search :results-fast] (js/JSON.parse resp))
             (assoc-in [:search :in-progress?] false))
     :dispatch-n [(when fit-view? [:lipas.ui.map.events/fit-to-current-vectors])]}))

(rf/reg-event-db ::update-search-string
  (fn [db [_ s]]
    (assoc-in db [:search :string] s)))

(defn analysis-mode? [db]
  (and (= :default (-> db :map :mode :name))
       (= :analysis (-> db :map :mode :sub-mode))
       (-> db :analysis :center :lon)
       (-> db :analysis :center :lat)))

(defn- collect-search-data [db]
  (let [analysis? (analysis-mode? db)]
    (-> db
        :search
        (select-keys [:string :filters :sort :pagination])
        (assoc :locale ((-> db :translator)))
        (assoc :decay? (not analysis?))
        (assoc :zoom (-> db :map :zoom))
        (assoc :bbox {:top-left (-> db :map :top-left-wgs84)
                      :bottom-right (-> db :map :bottom-right-wgs84)})
        (assoc :center (if analysis?
                         (-> db :analysis :center)
                         (-> db :map :center-wgs84)))
        (assoc :geom (-> db :analysis :buffer-geom :features first :geometry))
        (assoc :distance (if analysis?
                           (-> db :analysis :distance-km (* 1000))
                           (/ (max (-> db :map :width)
                                   (-> db :map :height)) 2))))))

(rf/reg-event-fx ::submit-search
  (fn [{:keys [db]} [_ fit-view?]]
    (let [params (collect-search-data db)
          terse? (-> db :search :results-view (= :list))]
      {:dispatch [::search-fast params fit-view? terse?]})))

(rf/reg-event-fx ::search-with-keyword
  (fn [{:keys [db]} [_ fit-view?]]
    (let [kw (-> db :search :string)]
      {:dispatch [::submit-search fit-view?]
       :tracker/search! [(or kw "")]})))

(rf/reg-event-fx ::filters-updated
  (fn [{:keys [db]} [_ fit-view?]]
    {:db (assoc-in db [:search :pagination :page] 0)
     :fx [[:dispatch [::submit-search fit-view?]]]}))

(rf/reg-event-fx ::set-status-filter
  (fn [{:keys [db]} [_ statuses append?]]
    {:db (if append?
           (update-in db [:search :filters :statuses] into statuses)
           (assoc-in db [:search :filters :statuses] statuses))
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::remove-status-filter
  (fn [{:keys [db]} [_ status]]
    {:db (update-in db [:search :filters :statuses] (comp disj set) status)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-type-filter
  (fn [{:keys [db]} [_ type-codes append?]]
    {:db (if append?
           (update-in db [:search :filters :type-codes] into type-codes)
           (assoc-in db [:search :filters :type-codes] type-codes))
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-city-filter
  (fn [{:keys [db]} [_ city-codes append?]]
    {:db (if append?
           (update-in db [:search :filters :city-codes] into city-codes)
           (assoc-in db [:search :filters :city-codes] city-codes))
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-construction-year-min-filter
  (fn [{:keys [db]} [_ v]]
    {:db (assoc-in db [:search :filters :construction-year-min] v)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-construction-year-max-filter
  (fn [{:keys [db]} [_ v]]
    {:db (assoc-in db [:search :filters :construction-year-max] v)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-admins-filter
  (fn [{:keys [db]} [_ v]]
    {:db (assoc-in db [:search :filters :admins] v)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-owners-filter
  (fn [{:keys [db]} [_ v]]
    {:db (assoc-in db [:search :filters :owners] v)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-bounding-box-filter
  (fn [{:keys [db]} [_ v]]
    {:db (assoc-in db [:search :filters :bounding-box?] v)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-logged-in-filters
  (fn [{:keys [db]} [_]]
    {:db (update-in db [:search :filters :statuses] conj "planned")
     :dispatch [::filters-updated]}))

(rf/reg-event-fx ::clear-filters
  (fn [{:keys [db]} _]
    (let [defaults (-> (if (:logged-in? db) db/default-db-logged-in db/default-db)
                       (select-keys [:filters :sort :string])
                       (assoc-in [:filters :bounding-box?] false))
          fit-view? false]
      {:db (update db :search merge defaults)
       :dispatch [::filters-updated fit-view?]})))

(rf/reg-event-fx ::replace-filters
                 ;; Replace the whole search spec (string + filters) starting
                 ;; from clean defaults and run a single search. Used by the
                 ;; AI assistant's action buttons.
  (fn [{:keys [db]} [_ {:keys [search-text city-codes type-codes edit-permission?]}]]
    (let [defaults (-> (if (:logged-in? db) db/default-db-logged-in db/default-db)
                       (select-keys [:filters :sort :string]))
          spec (cond-> defaults
                 search-text (assoc :string search-text)
                 (seq city-codes) (assoc-in [:filters :city-codes] (set city-codes))
                 (seq type-codes) (assoc-in [:filters :type-codes] (set type-codes))
                 edit-permission? (assoc-in [:filters :edit-permission?] true))]
      {:db (update db :search merge spec)
       :dispatch [::filters-updated :fit-view]})))

(rf/reg-event-fx ::create-report-from-current-search
  (fn [{:keys [db]} [_ fmt]]
    (let [params (-> db
                     collect-search-data
                     (search-query/->es-search-body (-> db :user :login))
                     (assoc-in [:_source :includes] ["*"])
                     (assoc-in [:_source :excludes] ["location.geometries"])
                    ;; :track_total_hits is not supported by scroll
                    ;; :from doesn't make sense when creating a report
                     (dissoc :track_total_hits :from)
                    ;; :size is set to a 'good guess' for optimal
                    ;; scrolling
                     (assoc :size 1000))
          fields (-> db :reports :selected-fields)]
      {:dispatch [:lipas.ui.reports.events/create-report params fields fmt]})))

(rf/reg-event-fx ::set-results-view
  (fn [{:keys [db]} [_ view]]
    {:db (assoc-in db [:search :results-view] view)
     :dispatch-n [(when (= :list view) [::reset-sort-order])
                  (when (= :list view) [::change-result-page-size 250])
                  (when (= :table view) [::change-result-page-size 25])]}))

(rf/reg-event-db ::select-results-table-columns
  (fn [db [_ v]]
    (assoc-in db [:search :selected-results-table-columns] v)))

(rf/reg-event-fx ::reset-sort-order
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:search :sort] {:asc? false :sort-fn :score})
     :dispatch [::submit-search]}))

(defn resolve-sort-change
  "If sort-fn has changed, reset sort order to ascending"
  [db sort]
  (if (= (-> db :search :sort :sort-fn) (sort :sort-fn))
    sort
    {:asc? true :sort-fn (sort :sort-fn)}))

(rf/reg-event-fx ::change-sort-order
  (fn [{:keys [db]} [_ sort]]
    (let [new-sort (resolve-sort-change db sort)]
      {:db (update-in db [:search :sort] merge new-sort)
       :dispatch [::submit-search]})))

;; This can be combined with other sort options
(rf/reg-event-fx ::toggle-sorting-by-distance
  (fn [{:keys [db]} _]
    (let [path [:search :sort]]
      {:db (update-in db path #(if (= (:sort-fn %) :score)
                                 (merge % {:sort-fn :name :asc? true})
                                 (merge % {:sort-fn :score :asc? false})))
       :dispatch [::submit-search]})))

(rf/reg-event-fx ::change-result-page
  (fn [{:keys [db]} [_ page]]
    {:db (assoc-in db [:search :pagination :page] page)
     :dispatch [::submit-search]}))

(rf/reg-event-fx ::change-result-page-size
  (fn [{:keys [db]} [_ page-size fit-view?]]
    {:db (assoc-in db [:search :pagination :page-size] page-size)
     :dispatch-n
     [[::submit-search fit-view?]
      #_(when (> page-size 500)
          [::set-bounding-box-filter true])]}))

(rf/reg-event-fx ::set-filters-by-permissions
  (fn [{:keys [db]} [_ v]]
    {:db (assoc-in db [:search :filters :edit-permission?] v)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::set-property-filter
  (fn [{:keys [db]} [_ prop-key filter-spec]]
    {:db (assoc-in db [:search :filters :properties-filters prop-key] filter-spec)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::remove-property-filter
  (fn [{:keys [db]} [_ prop-key]]
    {:db (update-in db [:search :filters :properties-filters] dissoc prop-key)
     :dispatch [::filters-updated :fit-view]}))

(rf/reg-event-fx ::clear-property-filters
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:search :filters :properties-filters] {})
     :dispatch [::filters-updated :fit-view]}))

(defn- kw->path [kw]
  (-> kw name (str/split #"\.") (->> (mapv keyword))))

(def data-keys
  [:name :name-localized.se :name-localized.en :marketing-name :www
   :phone-number :email :owner :admin :type.type-code
   :renovation-years :construction-year :location.address :location.postal-code
   :location.postal-office :location.city.city-code])

;; Used by quick-edit feature in search results table
(rf/reg-event-fx ::save-edits
  (fn [{:keys [db]} [_ {:keys [lipas-id] :as data}]]
   ;; TODO maybe this would be better implemented in the backend?

   ;; Sports-site data is fetched asynchronously when editing is
   ;; started. This is a safe-guard that fetch has succeeded.
    (if-let [s (get-in db [:sports-sites lipas-id])]

     ;; When all is fine we create new revision merged with edits from
     ;; the table and commit the new revision to the backend.
      (let [d (->> (select-keys data data-keys)
                   (reduce (fn [res [k v]] (assoc-in res (kw->path k) v)) {}))
            r (-> (utils/make-revision s) (cutils/deep-merge d) utils/clean)
            cb (fn [] [[::submit-search]])]
        {:dispatch-n
         [[:lipas.ui.sports-sites.events/commit-rev r false cb]]})

     ;; If fetching failed we can't create revision and thus save the
     ;; edits.
      {:dispatch
       [:lipas.ui.events/set-active-notification
        {:message ((:translator db) :notifications/save-failed)
         :success? false}]})))

;; Save search (for later use) ;;

(rf/reg-event-db ::toggle-save-dialog
  (fn [db _]
    (update-in db [:search :save-dialog-open?] not)))

(rf/reg-event-fx ::save-current-search
  (fn [{:keys [db]} [_ name]]
    (let [m {:name name
             :string (-> db :search :string)
             :filters (-> db :search :filters)}
          user-data (-> db
                        :user
                        :login
                        :user-data
                        (update :saved-searches conj m))]
      {:dispatch-n
       [[:lipas.ui.user.events/update-user-data user-data]
        [::toggle-save-dialog]]
       :tracker/event! ["user" "save-my-search"]})))

(rf/reg-event-fx ::select-saved-search
  (fn [{:keys [db]} [_ {:keys [string filters]}]]
    {:db (-> db
             (assoc-in [:search :filters] filters)
             (assoc-in [:search :string] string))
     :dispatch [::submit-search]
     :tracker/event! ["user" "open-saved-search"]}))
