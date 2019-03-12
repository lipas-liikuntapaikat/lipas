(ns lipas.ui.search.events
  (:require
   [ajax.core :as ajax]
   [clojure.string :as string]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [lipas.ui.db :as db]
   [re-frame.core :as re-frame]))

(defn- add-filter [m filter]
  (update-in m [:query :function_score :query :bool :filter] conj filter))

(defn ->sort-key [k locale]
  (case k
    (:location.city.name :type.name) (-> k name
                                         (->> (str "search-meta."))
                                         (str "." (name locale) ".keyword")
                                         keyword)
    (:event-date)                    :event-date
    (keyword (str (name k) ".keyword"))))

(defn resolve-sort [{:keys [sort-fn asc?]} locale]
  {:sort
   (filterv some?
            [(cond
               (= sort-fn :score) :_score
               sort-fn            {(->sort-key sort-fn locale)
                                   {:order (if asc? "asc" "desc")}}
               :else              nil)])})

(defn resolve-pagination [{:keys [page page-size]}]
  {:from (* page page-size)
   :size page-size})

(defn resolve-query-string
  "`s` is users input to search field. Goal is to transform `s` into ES
  query-string that returns relevant results for the user. Current
  implementation appends '*' wildcard after each word. Nil and Empty
  string generates a match-all query. Dashes '-' are replaced with
  whitespace because ES standard analyzer removes punctuation marks."
  [s]
  (if (empty? s)
    "*"
    (-> s
        (string/replace "-" " ")
        (string/split #" ")
        (->> (map #(str % "*"))
             (string/join " ")))))

(defn ->es-search-body [{:keys [filters string center distance sort
                                locale pagination]}]
  (let [string            (resolve-query-string string)
        type-codes        (-> filters :type-codes not-empty)
        city-codes        (-> filters :city-codes not-empty)
        area-min          (-> filters :area-min)
        area-max          (-> filters :area-max)
        year-min          (-> filters :construction-year-min)
        year-max          (-> filters :construction-year-max)
        materials         (-> filters :surface-materials not-empty)
        retkikartta?      (-> filters :retkikartta?)
        admins            (-> filters :admins not-empty)
        owners            (-> filters :owners not-empty)
        statuses          (-> filters :statuses not-empty)
        {:keys [lon lat]} center

        params (merge
                (resolve-sort sort locale)
                (resolve-pagination pagination)
                {;; :min_score 1
                 :_source {:excludes ["search-meta"]}
                 :query
                 {:function_score
                  {:score_mode "sum"
                   :query
                   {:bool
                    {:must
                     [{:simple_query_string
                       {:query            string
                        :default_operator "AND"}}]}}
                   :functions  (filterv some?
                                        [(when (and lat lon distance)
                                           {:gauss
                                            {:search-meta.location.wgs84-point
                                             {:origin (str lat "," lon)
                                              :offset (str distance "m")
                                              :scale  (str distance "m")}}})])}}})]
    (cond-> params
      statuses     (add-filter {:terms {:status.keyword statuses}})
      type-codes   (add-filter {:terms {:type.type-code type-codes}})
      city-codes   (add-filter {:terms {:location.city.city-code city-codes}})
      area-min     (add-filter {:range {:properties.area-m2 {:gte area-min}}})
      area-max     (add-filter {:range {:properties.area-m2 {:lte area-max}}})
      year-min     (add-filter {:range {:construction-year {:gte year-min}}})
      year-max     (add-filter {:range {:construction-year {:lte year-max}}})
      materials    (add-filter {:terms {:properties.surface-material.keyword materials}})
      admins       (add-filter {:terms {:admin.keyword admins}})
      owners       (add-filter {:terms {:owner.keyword owners}})
      retkikartta? (add-filter {:terms {:properties.may-be-shown-in-excursion-map-fi? [true]}}))))

(re-frame/reg-event-fx
 ::search
 (fn [{:keys [db]} [_ params fit-view?]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/search")
     :params          (->es-search-body params)
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::search-success fit-view?]
     :on-failure      [::search-failure]}
    :db (assoc-in db [:search :in-progress?] true)}))

(re-frame/reg-event-fx
 ::search-success
 (fn [{:keys [db]} [_ fit-view? resp]]
   (let [hits  (-> resp :hits :hits)
         sites (map :_source hits)]
     {:db         (-> (reduce utils/add-to-db db sites)
                      (assoc-in [:search :results] resp)
                      (assoc-in [:search :in-progress?] false))
      :dispatch-n [(when fit-view? [:lipas.ui.map.events/fit-to-current-vectors])]})))

(re-frame/reg-event-fx
 ::search-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (-> db
                    (assoc-in [:errors :search (utils/timestamp)] error)
                    (assoc-in [:search :in-progress?] false))
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-db
 ::update-search-string
 (fn [db [_ s]]
   (assoc-in db [:search :string] s)))

(defn- collect-search-data [db]
  (-> db
      :search
      (select-keys [:string :filters :sort :pagination])
      (assoc :locale ((-> db :translator)))
      (assoc :center (-> db :map :center-wgs84))
      (assoc :distance (/ (max (-> db :map :width)
                               (-> db :map :height)) 2))))

(re-frame/reg-event-fx
 ::submit-search
 (fn [{:keys [db]} [_ fit-view?]]
   (let [params (collect-search-data db)]
     {:dispatch [::search params fit-view?]})))

(re-frame/reg-event-fx
 ::filters-updated
 (fn [_ [_ fit-view?]]
   {:dispatch-n
    [[::submit-search fit-view?]
     [::change-result-page 0]]}))

(re-frame/reg-event-fx
 ::set-status-filter
 (fn [{:keys [db]} [_ statuses append?]]
   {:db       (if append?
                (update-in db [:search :filters :statuses] into statuses)
                (assoc-in db [:search :filters :statuses] statuses))
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-type-filter
 (fn [{:keys [db]} [_ type-codes append?]]
   {:db       (if append?
                (update-in db [:search :filters :type-codes] into type-codes)
                (assoc-in db [:search :filters :type-codes] type-codes))
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-city-filter
 (fn [{:keys [db]} [_ city-codes append?]]
   {:db       (if append?
                (update-in db [:search :filters :city-codes] into city-codes)
                (assoc-in db [:search :filters :city-codes] city-codes))
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-area-min-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :area-min] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-area-max-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :area-max] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-construction-year-min-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :construction-year-min] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-construction-year-max-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :construction-year-max] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-surface-materials-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :surface-materials] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-retkikartta-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :retkikartta?] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-admins-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :admins] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-owners-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :owners] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [{:keys [db]} _]
   (let [defaults  (-> db/default-db
                       :search
                       (select-keys [:filters :sort :string]))
         fit-view? false]
     {:db       (update db :search merge defaults)
      :dispatch [::filters-updated fit-view?]})))

(re-frame/reg-event-fx
 ::create-report-from-current-search
 (fn [{:keys [db]} _]
   (let [params (-> db
                    collect-search-data
                    ->es-search-body
                    (assoc-in [:_source :excludes] ["location.geometries"]))
         fields (-> db :reports :selected-fields)]
     {:dispatch [:lipas.ui.reports.events/create-report params fields]})))

(re-frame/reg-event-fx
 ::set-results-view
 (fn [{:keys [db]} [_ view]]
   {:db         (assoc-in db [:search :results-view] view)
    :dispatch-n [(when (= :list view) [::reset-sort-order])
                 (when (= :list view) [::change-result-page-size 250])
                 (when (= :table view) [::change-result-page-size 25])]}))

(re-frame/reg-event-db
 ::select-results-table-columns
 (fn [db [_ v]]
   (assoc-in db [:search :selected-results-table-columns] v)))

(re-frame/reg-event-fx
 ::reset-sort-order
 (fn [{:keys [db]} _]
   {:db       (assoc-in db [:search :sort] {:asc? true :sort-fn :score})
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::change-sort-order
 (fn [{:keys [db]} [_ sort]]
   {:db       (update-in db [:search :sort] merge sort)
    :dispatch [::submit-search]}))

;; This can be combined with other sort options
(re-frame/reg-event-fx
 ::toggle-sorting-by-distance
 (fn [{:keys [db]} _]
   (let [path [:search :sort :sort-fn]]
     {:db       (update-in db path #(if (= % :score) :name :score))
      :dispatch [::submit-search]})))

(re-frame/reg-event-fx
 ::change-result-page
 (fn [{:keys [db]} [_ page]]
   {:db       (assoc-in db [:search :pagination :page] page)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::change-result-page-size
 (fn [{:keys [db]} [_ page-size]]
   {:db       (assoc-in db [:search :pagination :page-size] page-size)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-filters-by-permissions
 (fn [{:keys [db]} _]
   (let [permissions (-> db :user :login :permissions)]
     {:db (assoc-in db [:search :filters] {:type-codes (-> permissions :types)
                                           :city-codes (-> permissions :cities)})
      :dispatch [::submit-search]})))

(defn- kw->path [kw]
  (-> kw name (string/split #"\.") (->> (mapv keyword))))

(def data-keys
  [:name :marketing-name :www :phone-numer :email :owner :admin :type.type-code
   :renovation-years :construction-year :location.address :location.postal-code
   :location.postal-office :location.city.city-code])

(re-frame/reg-event-fx
 ::save-edits
 (fn [{:keys [db]} [_ {:keys [lipas-id] :as data}]]
   (let [d  (->> (select-keys data data-keys)
                 (reduce (fn [res [k v]] (assoc-in res (kw->path k) v)) {}))
         s  (get-in db [:sports-sites lipas-id])
         r  (-> (utils/make-revision s) (cutils/deep-merge d) utils/clean)
         cb (fn [] [[::submit-search]])]
     {:dispatch-n
      [[:lipas.ui.sports-sites.events/commit-rev r false cb]]})))
