(ns lipas.ui.search.events
  (:require
   [ajax.core :as ajax]
   [clojure.string :as string]
   [lipas.ui.search.db :as db]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

;; Zoom level where we start fetching full geoms
(def full-geoms-threshold 9)

(defn- add-filter [m filter]
  (update-in m [:query :function_score :query :bool :filter] conj filter))

(defn ->sort-key [k locale]
  (case k
    (:location.city.name
     :type.name
     :admin.name
     :owner.name)       (-> k name
                            (->> (str "search-meta."))
                            (str "." (name locale) ".keyword")
                            keyword)
    (:event-date
     :construction-year
     :renovation-years) k

    (keyword (str (name k) ".keyword"))))

(defn resolve-sort [{:keys [sort-fn asc?]} locale decay? center]
  (if-not decay?

    {:sort
     {:_geo_distance
      {:search-meta.location.wgs84-point
       {:lon (:lon center)
        :lat (:lat center)}
       :order           "asc"
       :unit            "m"
       :mode            "min"
       :distance_type   "arc"
       :ignore_unmapped true}}}

    {:sort
     (filterv some?
              [(cond
                 (= sort-fn :score) :_score
                 sort-fn            {(->sort-key sort-fn locale)
                                     {:order (if asc? "asc" "desc")}}
                 :else              nil)])}))

(defn resolve-pagination [{:keys [page page-size]} decay?]
  (if decay?

    {:from (* page page-size)
     :size page-size}

    {:from 0
     :size 5000}))

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
        (string/replace "/" "")
        (string/replace #"\s+" " ")
        (string/split #" ")
        (->> (map #(str % "*"))
             (string/join " ")))))

(defn ->bbox-filter
  [{:keys [top-left bottom-right]}]
  {:geo_bounding_box
   {:search-meta.location.wgs84-point
    {:top_left top-left :bottom_right bottom-right}}})

(defn ->geo-intersects-filter
  [{:keys [top-left bottom-right]}]
  {:geo_shape
   {:search-meta.location.geometries
    {:shape
     {:type        "envelope"
      :coordinates #js[top-left bottom-right]}
     :relation    "intersects"}}})

(defn add-distance-fields [lat lon]
  {:script_fields
   {:distance-start-m
    {:script
     {:params {:lat lat
               :lon lon}
      :source "doc[\u0027search-meta.location.wgs84-point\u0027].arcDistance(params.lat, params.lon)"}},
    :distance-center-m
    {:script {:params {:lat lat
                       :lon lon},
              :source "doc[\u0027search-meta.location.wgs84-center\u0027].arcDistance(params.lat, params.lon)"}},
    :distance-end-m
    {:script
     {:params {:lat lat
               :lon lon},
      :source "doc[\u0027search-meta.location.wgs84-end\u0027].arcDistance(params.lat, params.lon)"}}}})

(defn ->es-search-body
  ([params]
   (->es-search-body params false))
  ([{:keys [filters string center distance sort decay?
            locale pagination zoom bbox]} terse?]
   (let [string            (resolve-query-string string)
         bbox?             (and
                            (> zoom 3)
                            (-> filters :bounding-box?))
         type-codes        (-> filters :type-codes not-empty)
         city-codes        (-> filters :city-codes not-empty)
         area-min          (-> filters :area-min)
         area-max          (-> filters :area-max)
         year-min          (-> filters :construction-year-min)
         year-max          (-> filters :construction-year-max)
         materials         (-> filters :surface-materials not-empty)
         retkikartta?      (-> filters :retkikartta?)
         harrastuspassi?   (-> filters :harrastuspassi?)
         school-use?       (-> filters :school-use?)
         admins            (-> filters :admins not-empty)
         owners            (-> filters :owners not-empty)
         statuses          (-> filters :statuses not-empty)
         {:keys [lon lat]} center

         params (merge
                 (add-distance-fields lat lon)
                 (resolve-sort sort locale decay? center)
                 (resolve-pagination pagination decay?)
                 {:track_total_hits 50000
                  :_source
                  {:includes (if terse?

                               ;; Used in result list view (while browsing map)
                               ["lipas-id"
                                "status"
                                "name"
                                "name-localized"
                                "type.type-code"
                                "location.city.city-code"
                                (if (> full-geoms-threshold zoom)
                                  "search-meta.location.simple-geoms"
                                  "location.geometries")]

                               ;; Used in results table view
                               ["lipas-id"
                                "status"
                                "event-date"
                                "name"
                                "marketing-name"
                                "www"
                                "phone-number"
                                "email"
                                "owner"
                                "admin"
                                "type.type-code"
                                "renovation-years"
                                "construction-year"
                                "location.address"
                                "location.postal-code"
                                "location.postal-office"
                                "location.city.city-code"
                                "search-meta.type.main-category"
                                "search-meta.type.sub-category"
                                (if (> full-geoms-threshold zoom)
                                  "search-meta.location.simple-geoms"
                                  "location.geometries")])}
                  :query
                  (when decay?
                    {:function_score
                     {:score_mode "max"
                      :query
                      {:bool
                       {:must
                        [{:simple_query_string
                          {:query            string
                           :fields
                           ["name^3"
                            "name-localized.*^3"
                            "marketing-name^3"
                            "lipas-id"
                            "search-meta.location.city.name.*^2"
                            "search-meta.type.name.*^2"
                            "search-meta.type.tags.*^2"
                            "search-meta.tags"
                            "search-meta.type.main-category.name.*"
                            "search-meta.type.sub-category.name.*"
                            "search-meta.location.province.name.*"
                            "search-meta.location.avi-area.name.*"
                            "admin.keyword"
                            "owner.keyword"
                            "comment"
                            "email"
                            "phone-number"
                            "location.address"
                            "location.postal-office"
                            "location.postal-code"
                            "location.city.neighborhood"
                            "properties.surface-material-info"]
                           :default_operator "AND"
                           :analyze_wildcard true}}]}}
                      :functions
                      (filterv some?
                               (for [kw [:search-meta.location.wgs84-point
                                         :search-meta.location.wgs84-center
                                         :search-meta.location.wgs84-end]]
                                 (when (every? pos? [lon lat distance])
                                   {:exp
                                    {kw {:origin (str lat "," lon)
                                         :offset (str distance "m")
                                         :scale  (str distance "m")}}})))}})})]

     (if-not decay?

       (assoc params :query
              {:bool
               {:must
                (if (not-empty type-codes)
                  {:terms
                   {:type.type-code type-codes}}
                  {:match_all {}})
                :filter
                (merge
                 {:geo_distance
                  {:distance "15km"
                   :search-meta.location.wgs84-point
                   {:lon lon
                    :lat lat}}})}})

       (cond-> params
         bbox?           (add-filter (->geo-intersects-filter bbox))
         statuses        (add-filter {:terms {:status.keyword statuses}})
         type-codes      (add-filter {:terms {:type.type-code type-codes}})
         city-codes      (add-filter {:terms {:location.city.city-code city-codes}})
         area-min        (add-filter {:range {:properties.area-m2 {:gte area-min}}})
         area-max        (add-filter {:range {:properties.area-m2 {:lte area-max}}})
         year-min        (add-filter {:range {:construction-year {:gte year-min}}})
         year-max        (add-filter {:range {:construction-year {:lte year-max}}})
         materials       (add-filter {:terms {:properties.surface-material.keyword materials}})
         admins          (add-filter {:terms {:admin.keyword admins}})
         owners          (add-filter {:terms {:owner.keyword owners}})
         retkikartta?    (add-filter {:terms {:properties.may-be-shown-in-excursion-map-fi? [true]}})
         harrastuspassi? (add-filter {:terms {:properties.may-be-shown-in-harrastuspassi-fi? [true]}})
         school-use?     (add-filter {:terms {:properties.school-use? [true]}}))))))

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
   {:db         (-> db
                    (assoc-in [:search :results] resp)
                    (assoc-in [:search :in-progress?] false))
    :dispatch-n [(when fit-view? [:lipas.ui.map.events/fit-to-current-vectors])]}))

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

(re-frame/reg-event-fx
 ::search-fast
 (fn [{:keys [db]} [_ params fit-view? terse?]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/search")
     :params          (->es-search-body params terse?)
     :format          (ajax/json-request-format)
     :response-format (ajax/raw-response-format)
     :on-success      [::search-success-fast fit-view?]
     :on-failure      [::search-failure]}
    :db (assoc-in db [:search :in-progress?] true)}))

(re-frame/reg-event-fx
 ::search-success-fast
 (fn [{:keys [db]} [_ fit-view? resp]]
   {:db         (-> db
                    (assoc-in [:search :results-fast] (js/JSON.parse resp))
                    (assoc-in [:search :in-progress?] false))
    :dispatch-n [(when fit-view? [:lipas.ui.map.events/fit-to-current-vectors])]}))

(re-frame/reg-event-db
 ::update-search-string
 (fn [db [_ s]]
   (assoc-in db [:search :string] s)))

(defn analysis-mode? [db]
  (and (= :default (-> db :map :mode :name))
       (= :population (-> db :map :mode :sub-mode))
       (-> db :map :mode :population :center :lon)
       (-> db :map :mode :population :center :lat)))

(defn- collect-search-data [db]
  (let [analysis? (analysis-mode? db)]
    (-> db
        :search
        (select-keys [:string :filters :sort :pagination])
        (assoc :locale ((-> db :translator)))
        (assoc :decay? (not analysis?))
        (assoc :zoom (-> db :map :zoom))
        (assoc :bbox {:top-left     (-> db :map :top-left-wgs84)
                      :bottom-right (-> db :map :bottom-right-wgs84)})
        (assoc :center (if analysis?
                         (-> db :map :mode :population :center)
                         (-> db :map :center-wgs84)))
        (assoc :distance (/ (max (-> db :map :width)
                                 (-> db :map :height)) 2)))))

(re-frame/reg-event-fx
 ::submit-search
 (fn [{:keys [db]} [_ fit-view?]]
   (let [params (collect-search-data db)
         terse? (-> db :search :results-view (= :list))]
     {:dispatch [::search-fast params fit-view? terse?]})))

(re-frame/reg-event-fx
 ::search-with-keyword
 (fn [{:keys [db]} [_ fit-view?]]
   (let [kw (-> db :search :string)]
     {:dispatch [::submit-search fit-view?]
      :ga/event ["search" "submit" (or kw "")]})))

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
 ::set-harrastuspassi-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :harrastuspassi?] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-school-use-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :school-use?] v)
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
 ::set-bounding-box-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :bounding-box?] v)
    :dispatch [::filters-updated :fit-view]}))

(re-frame/reg-event-fx
 ::set-logged-in-filters
 (fn [{:keys [db]} [_]]
   {:db       (update-in db [:search :filters :statuses] conj "planned" "planning")
    :dispatch [::filters-updated]}))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [{:keys [db]} _]
   (let [defaults  (-> (if (:logged-in? db) db/default-db-logged-in db/default-db)
                       (select-keys [:filters :sort :string])
                       (assoc-in [:filters :bounding-box?] false))
         fit-view? false]
     {:db       (update db :search merge defaults)
      :dispatch [::filters-updated fit-view?]})))

(re-frame/reg-event-fx
 ::create-report-from-current-search
 (fn [{:keys [db]} _]
   (let [params (-> db
                    collect-search-data
                    ->es-search-body
                    (assoc-in [:_source :includes] ["*"] )
                    (assoc-in [:_source :excludes] ["location.geometries"])
                    ;; track_total_hits is not supported by scroll
                    (dissoc :track_total_hits))
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
   {:db       (assoc-in db [:search :sort] {:asc? false :sort-fn :score})
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
   (let [path [:search :sort]]
     {:db       (update-in db path #(if (= (:sort-fn %) :score)
                                      (merge % {:sort-fn :name :asc? true})
                                      (merge % {:sort-fn :score :asc? false})))
      :dispatch [::submit-search]})))

(re-frame/reg-event-fx
 ::change-result-page
 (fn [{:keys [db]} [_ page]]
   {:db       (assoc-in db [:search :pagination :page] page)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::change-result-page-size
 (fn [{:keys [db]} [_ page-size fit-view?]]
   {:db       (assoc-in db [:search :pagination :page-size] page-size)
    :dispatch-n
    [[::submit-search fit-view?]
     #_(when (> page-size 500)
         [::set-bounding-box-filter true])]}))


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

;; Used by quick-edit feature in search results table
(re-frame/reg-event-fx
 ::save-edits
 (fn [{:keys [db]} [_ {:keys [lipas-id] :as data}]]
   ;; TODO maybe this would be better implemented in the backend?

   ;; Sports-site data is fetched asynchronously when editing is
   ;; started. This is a safe-guard that fetch has succeeded.
   (if-let [s (get-in db [:sports-sites lipas-id])]

     ;; When all is fine we create new revision merged with edits from
     ;; the table and commit the new revision to the backend.
     (let [d  (->> (select-keys data data-keys)
                   (reduce (fn [res [k v]] (assoc-in res (kw->path k) v)) {}))
           r  (-> (utils/make-revision s) (cutils/deep-merge d) utils/clean)
           cb (fn [] [[::submit-search]])]
       {:dispatch-n
        [[:lipas.ui.sports-sites.events/commit-rev r false cb]]})

     ;; If fetching failed we can't create revision and thus save the
     ;; edits.
     {:dispatch
      [:lipas.ui.events/set-active-notification
       {:message  ((:translator db) :notifications/save-failed)
        :success? false}]})))

;; Save search (for later use) ;;

(re-frame/reg-event-db
 ::toggle-save-dialog
 (fn [db _]
   (update-in db [:search :save-dialog-open?] not)))

(re-frame/reg-event-fx
 ::save-current-search
 (fn [{:keys [db]} [_ name]]
   (let [m         {:name    name
                    :string  (-> db :search :string)
                    :filters (-> db :search :filters)}
         user-data (-> db
                       :user
                       :login
                       :user-data
                       (update :saved-searches conj m))]
     {:dispatch-n
      [[:lipas.ui.user.events/update-user-data user-data]
       [::toggle-save-dialog]]
      :ga/event ["user" "save-my-search"]})))

(re-frame/reg-event-fx
 ::select-saved-search
 (fn [{:keys [db]} [_ {:keys [string filters]}]]
   {:db (-> db
        (assoc-in [:search :filters] filters)
        (assoc-in [:search :string] string))
    :dispatch [::submit-search]
    :ga/event ["user" "open-saved-search"]}))
