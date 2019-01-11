(ns lipas.ui.search.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
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
    (:score)                         :_score
    (keyword (str (name k) ".keyword"))))

(defn resolve-sort [{:keys [score? sort-fn asc?]} locale]
  {:sort
   (filterv some?
            [(when score? (->sort-key :score locale))
             (when sort-fn
               {(->sort-key sort-fn locale)
                {:order (if asc? "asc" "desc")}})])})

(comment
  (resolve-sort {:score? :score :sort-fn :name :asc? true} :fi))

(defn resolve-pagination [{:keys [page page-size]}]
  {:from (* page page-size)
   :size page-size})

(defn ->es-search-body [{:keys [filters string center distance sort
                                locale pagination]}]
  (let [string            (or (not-empty string) "*")
        type-codes        (-> filters :type-codes not-empty)
        city-codes        (-> filters :city-codes not-empty)
        area-min          (-> filters :area-min)
        area-max          (-> filters :area-max)
        materials         (-> filters :surface-materials not-empty)
        retkikartta?      (-> filters :retkikartta?)
        admins            (-> filters :admins not-empty)
        owners            (-> filters :owners not-empty)
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
                     [{:query_string
                       {:query string}}]}}
                   :functions  [{:gauss
                                 {:search-meta.location.wgs84-point
                                  {:origin (str lat "," lon)
                                   :offset (str distance "m")
                                   :scale  (str (* 2 distance) "m")}}}]}}})]
    (cond-> params
      type-codes   (add-filter {:terms {:type.type-code type-codes}})
      city-codes   (add-filter {:terms {:location.city.city-code city-codes}})
      area-min     (add-filter {:range {:properties.area-m2 {:gte area-min}}})
      area-max     (add-filter {:range {:properties.area-m2 {:lte area-max}}})
      materials    (add-filter {:terms {:properties.surface-material.keyword materials}})
      admins       (add-filter {:terms {:admin.keyword admins}})
      owners       (add-filter {:terms {:owner.keyword owners}})
      retkikartta? (add-filter {:terms {:properties.may-be-shown-in-excursion-map-fi? [true]}}))))

(re-frame/reg-event-fx
 ::search
 (fn [{:keys [db]} [_ params]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/search")
     :params          (->es-search-body params)
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::search-success]
     :on-failure      [::search-failure]}}))

(re-frame/reg-event-fx
 ::search-success
 (fn [{:keys [db]} [_ resp]]
   (let [hits  (-> resp :hits :hits)
         sites (map :_source hits)]
     {:db (-> (reduce utils/add-to-db db sites)
              (assoc-in [:search :results] resp))})))

(re-frame/reg-event-fx
 ::search-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :search (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-db
 ::update-search-string
 (fn [db [_ s]]
   (assoc-in db [:search :string] s)))

(re-frame/reg-event-fx
 ::submit-search
 (fn [{:keys [db]} _]
   (let [params (-> db
                    :search
                    (select-keys [:string :filters :sort :pagination])
                    (assoc :locale ((-> db :translator)))
                    (assoc :center (-> db :map :center-wgs84))
                    (assoc :distance (/ (max (-> db :map :width)
                                             (-> db :map :height)) 2)))]
     {:dispatch [::search params]})))

(re-frame/reg-event-fx
 ::set-type-filter
 (fn [{:keys [db]} [_ type-codes append?]]
   {:db       (if append?
                (update-in db [:search :filters :type-codes] into type-codes)
                (assoc-in db [:search :filters :type-codes] type-codes))
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-city-filter
 (fn [{:keys [db]} [_ city-codes append?]]
   {:db       (if append?
                (update-in db [:search :filters :city-codes] into city-codes)
                (assoc-in db [:search :filters :city-codes] city-codes))
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-area-min-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :area-min] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-area-max-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :area-max] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-surface-materials-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :surface-materials] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-retkikartta-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :retkikartta?] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-admins-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :admins] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-owners-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :owners] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [{:keys [db]} _]
   {:db       (-> db
                  (assoc-in [:search :filters] {})
                  (assoc-in [:search :sort] {:score? true})
                  (assoc-in [:search :string] nil))
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::create-report-from-current-search
 (fn [{:keys [db]} _]
   (let [params (-> db
                    :search
                    (select-keys [:string :filters])
                    (assoc :center (-> db :map :center-wgs84))
                    (assoc :distance (/ (max (-> db :map :width)
                                             (-> db :map :height)) 2))
                    ->es-search-body
                    (assoc-in [:_source :excludes] ["location.geometries"]))
         fields (-> db :reports :selected-fields)]
     {:dispatch [:lipas.ui.reports.events/create-report params fields]})))

(re-frame/reg-event-fx
 ::toggle-results-view
 (fn [{:keys [db]} _]
   (let [path    [:search :results-view]
         current (get-in db path)
         new     (if (= :list current) :table :list)]
     {:db (update-in db path [:results-view] new)
      :dispatch-n [(when (= :list new)
                     [::reset-sort-order])]})))

(re-frame/reg-event-fx
 ::reset-sort-order
 (fn [{:keys [db]} _]
   {:db       (assoc-in db [:search :sort] {:asc? true :score? true})
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
   {:db       (update-in db [:search :sort :score?] not)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::change-result-page
 (fn [{:keys [db]} [_ page]]
   {:db       (assoc-in db [:search :pagination :page] page)
    :dispatch [::submit-search]}))
