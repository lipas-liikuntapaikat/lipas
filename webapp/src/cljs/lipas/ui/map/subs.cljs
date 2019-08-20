(ns lipas.ui.map.subs
  (:require
   [clojure.string :as string]
   [goog.array :as garray]
   [goog.object :as gobj]
   [lipas.utils :as utils]
   [re-frame.core :as re-frame]
   [reagent.ratom :as ratom]))

(re-frame/reg-sub
 ::view
 :<- [:lipas.ui.sports-sites.subs/adding-new-site?]
 :<- [::selected-sports-site]
 :<- [::mode*]
 (fn [[adding? selected-site mode] _]
   (let [population? (-> mode :sub-mode (= :population))]
     (cond
       adding?       :adding
       population?   :population
       selected-site :site
       :else         :search))))

(re-frame/reg-sub
 ::show-default-tools?
 :<- [::view]
 :<- [:lipas.ui.search.subs/search-results-view]
 (fn [[view result-view] _]
   (and (= :list result-view) (#{:search :population} view))))

(re-frame/reg-sub
 ::basemap
 (fn [db _]
   (-> db :map :basemap)))

(re-frame/reg-sub
 ::center
 (fn [db _]
   (-> db :map :center)))

(re-frame/reg-sub
 ::zoom
 (fn [db _]
   (-> db :map :zoom)))

(re-frame/reg-sub
 ::popup
 (fn [db _]
   (-> db :map :popup)))

(re-frame/reg-sub-raw
 ::selected-sports-site
 (fn [app-db _event]
   (ratom/reaction
    (let [lipas-id (-> @app-db :map :mode :lipas-id)]
      (when lipas-id
        {:display-data @(re-frame/subscribe
                         [:lipas.ui.sports-sites.subs/display-site lipas-id])
         :edit-data    @(re-frame/subscribe
                         [:lipas.ui.sports-sites.subs/editing-rev lipas-id])})))))

(re-frame/reg-sub
 ::geometries-fast
 :<- [:lipas.ui.search.subs/search-results-fast]
 :<- [::editing-lipas-id]
 (fn [[results lipas-id'] _]
   (when results
     (let [data (or (gobj/getValueByKeys results "hits" "hits") #js[])]
       (->> data
            (keep
             (fn [obj]
               (let [obj       (gobj/get obj "_source")
                     geoms     (or
                                ;; Full geoms
                                (gobj/getValueByKeys obj
                                                     "location"
                                                     "geometries"
                                                     "features")
                                ;; Simplified geoms
                                (gobj/getValueByKeys obj
                                                     "search-meta"
                                                     "location"
                                                     "simple-geoms"
                                                     "features"))
                     type-code (gobj/getValueByKeys obj "type" "type-code")
                     lipas-id  (gobj/get obj "lipas-id")
                     name      (gobj/get obj "name")]

                 ;; To avoid displaying duplicates when editing
                 (when-not (= lipas-id' lipas-id)
                   #js{:type     "FeatureCollection"
                       :features (garray/map
                                  geoms
                                  (fn [geom idx]
                                    (gobj/set geom "id" (str lipas-id "-" idx))
                                    (gobj/set geom
                                              "properties"
                                              #js{:lipas-id  lipas-id
                                                  :name      name
                                                  :type-code type-code})
                                    geom))}))))
            not-empty)))))

(re-frame/reg-sub
 ::content-padding
 :<- [:lipas.ui.subs/screen-size]
 :<- [::drawer-open?]
 (fn [[screen-size drawer-open?] _]
   (let [margin 20]
     (if (and (#{"xs sm"} screen-size) (not drawer-open?))
       [margin margin margin margin]
       [margin margin margin (+ margin 430)])))) ;; drawer width is 430px

(re-frame/reg-sub
 ::mode*
 (fn [db _]
   (-> db :map :mode)))

(re-frame/reg-sub
 ::population-data
 (fn [db _]
   (-> db :map :population :data)))

(re-frame/reg-sub
 ::population
 (fn [db _]
   (-> db :map :population)))

(re-frame/reg-sub
 ::mode
 :<- [::content-padding]
 :<- [::mode*]
 :<- [::population]
 (fn [[content-padding mode {:keys [data]}] _]
   (let [default? (= (:name mode) :default)]
     (cond-> mode
       true     (assoc :content-padding content-padding)
       default? (assoc-in [:population :data] data)))))

(re-frame/reg-sub
 ::editing-lipas-id
 (fn [db _]
   (when (#{:editing :drawing} (-> db :map :mode :name))
     (-> db :map :mode :lipas-id))))

(re-frame/reg-sub
 ::zoomed-for-drawing?
 :<- [::zoom]
 (fn [zoom _]
   (> zoom 12)))

(re-frame/reg-sub
 ::drawing-geom-type
 (fn [db _]
   (-> db :map :drawing :geom-type)))

(re-frame/reg-sub
 ::new-geom
 (fn [db _]
   (when (= :adding (-> db :map :mode :name))
     (-> db :map :mode :geoms))))

(re-frame/reg-sub
 ::undo
 (fn [db [_ lipas-id]]
   (let [undo-stack (get-in db [:map :temp lipas-id :undo-stack])]
     (seq undo-stack))))

(re-frame/reg-sub
 ::redo
 (fn [db [_ lipas-id]]
   (let [redo-stack (get-in db [:map :temp lipas-id :redo-stack])]
     (seq redo-stack))))

(re-frame/reg-sub
 ::drawer-open?
 (fn [db _]
   (-> db :map :drawer-open?)))

;; Import geoms ;;

(re-frame/reg-sub
 ::import-dialog-open?
 (fn [db _]
   (-> db :map :import :dialog-open?)))

(re-frame/reg-sub
 ::selected-import-file-encoding
 (fn [db _]
   (-> db :map :import :selected-encoding)))

(re-frame/reg-sub
 ::import-data
 (fn [db _]
   (-> db :map :import :data)))

(re-frame/reg-sub
 ::import-candidates
 :<- [::import-data]
 (fn [data _]
   data))

(def ignored-headers #{"id" "coordTimes"})

(re-frame/reg-sub
 ::import-candidates-headers
 :<- [::import-data]
 (fn [data _]
   (let [->entry (fn [s]
                   (assoc {} :label s :hidden? (contains? ignored-headers s)))]
     (when (not-empty data)
       (-> data keys first data :properties
           (->> (mapv (juxt first (comp ->entry name first)))))))))

(re-frame/reg-sub
 ::import-batch-id
 (fn [db _]
   (-> db :map :import :batch-id)))

(re-frame/reg-sub
 ::selected-import-items
 (fn [db _]
   (-> db :map :import :selected-items)))

(re-frame/reg-sub
 ::replace-existing-geoms?
 (fn [db _]
   (-> db :map :import :replace-existing?)))

(re-frame/reg-sub
 ::address-search-dialog-open?
 (fn [db _]
   (-> db :map :address-search :dialog-open?)))

(re-frame/reg-sub
 ::address-search-keyword
 (fn [db _]
   (-> db :map :address-search :keyword)))

(defn ->result [f]
  {:geometry (-> f :geometry)
   :label    (-> f :properties :label)})

(re-frame/reg-sub
 ::address-search-results
 (fn [db _]
   (-> db :map :address-search :results :features
       (->> (map ->result)))))

(re-frame/reg-sub
 ::sports-site-view
 (fn [[_ lipas-id type-code] _]
   [(re-frame/subscribe [:lipas.ui.user.subs/permission-to-cities])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-types])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/geom-type lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/admins])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/owners])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/editing? lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/edits-valid? lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/editing-allowed? lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/save-in-progress?])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/delete-dialog-open? lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/type-by-type-code type-code])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/types-props type-code])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-publish? lipas-id])
    (re-frame/subscribe [:lipas.ui.user.subs/logged-in?])
    (re-frame/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
    (re-frame/subscribe [::mode])
    (re-frame/subscribe [::undo lipas-id])
    (re-frame/subscribe [::redo lipas-id])])
 (fn [[cities types geom-type admins owners editing? edits-valid?
       editing-allowed? save-in-progress? delete-dialog-open? type
       types-props can-publish? logged-in?  size-categories mode undo redo] _]

   {:types               (filter
                          (comp #{geom-type} :geometry-type second) types)
    :cities              cities
    :admins              admins
    :owners              owners
    :editing?            editing?
    :edits-valid?        edits-valid?
    :editing-allowed?    editing-allowed?
    :delete-dialog-open? delete-dialog-open?
    :can-publish?        can-publish?
    :logged-in?          logged-in?
    :size-categories     size-categories
    :mode                mode
    :sub-mode            (:sub-mode mode)
    :type                type
    :types-props         types-props
    :geom-type           (:geometry-type type)
    :save-in-progress?   save-in-progress?
    :problems?           (-> mode :problems :data :features seq)
    :portal              (case (:type-code type)
                           (3110 3130) "uimahalliportaali"
                           (2510 2520) "jaahalliportaali"
                           nil)
    :undo                undo
    :redo                redo}))

(re-frame/reg-sub
 ::add-sports-site-view
 (fn [_]
   [(re-frame/subscribe [:lipas.ui.sports-sites.subs/new-site-type])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/new-site-data])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/new-site-valid?])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/save-in-progress?])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/admins])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/owners])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-cities])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-types])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/prop-types])
    (re-frame/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
    (re-frame/subscribe [::zoomed-for-drawing?])
    (re-frame/subscribe [::new-geom])
    (re-frame/subscribe [::mode])
    (re-frame/subscribe [::undo "new"])
    (re-frame/subscribe [::redo "new"])])
 (fn [[type data valid? save-in-progress? admins owners cities types
       prop-types size-categories zoomed? geom mode undo redo] _]
   (let [geom-type (-> geom :features first :geometry :type)
         sub-mode  (mode :sub-mode)]
     {:type            type
      :type-code       (:type-code type)
      :geom-type       (:geometry-type type)
      :data            data
      :save-enabled?   (and valid? (= :finished sub-mode) (not save-in-progress?))
      :admins          admins
      :owners          owners
      :cities          cities
      :types           (if geom-type
                         (filter (comp #{geom-type} :geometry-type second) types)
                         types)
      :types-props     (reduce (fn [res [k v]]
                                 (let [prop-type (prop-types k)]
                                   (assoc res k (merge prop-type v))))
                               {}
                               (:props type))
      :size-categories size-categories
      :zoomed?         zoomed?
      :geom            geom
      :problems?       (-> mode :problems :data :features seq)
      :sub-mode        sub-mode
      :active-step     (cond
                         (= :finished sub-mode) 2
                         (some? type)           1
                         :else                  0)
      :undo            undo
      :redo            redo})))

(re-frame/reg-sub
 ::sub-mode
 (fn [db _]
   (-> db :map :mode :sub-mode)))

;; Demographics ;;

(re-frame/reg-sub
 ::selected-population
 (fn [db _]
   (-> db :map :population :selected)))

(re-frame/reg-sub
 ::selected-population-center
 :<- [::mode*]
 (fn [mode _]
   (-> mode :population :site-name)))

(re-frame/reg-sub
 ::population-labels
 (fn [db _]
   (let [tr (-> db :translator)]
     {:zone1     "0-2 km"
      :zone2     "2-5 km"
      :zone3     "5-10 km"
      :range1    "2 km"
      :range2    "5 km"
      :range3    "10 km"
      :age-0-14  (str "0-14" (tr :duration/years-short))
      :age-15-64 (str "15-64" (tr :duration/years-short))
      :age-65-   (str "65" (tr :duration/years-short) "-")
      :men       (tr :general/men)
      :women     (tr :general/women)
      :total     (tr :general/total-short)})))

;; Tilastokeskus won't display demographics if population is less than
;; 10 (for privacy reasons). Missing data is encoded as -1 in data and
;; we decided to treat -1 as zero when calculating total sums.
(defn- pos+ [a b]
  (+ (if (<= 0 a) a 0) (if (<= 0 b) b 0)))

(re-frame/reg-sub
 ::population-chart-data
 :<- [::selected-population]
 (fn [fcoll _]
   (let [ks [:ika_0_14 :ika_15_64 :ika_65_ :naiset :miehet :vaesto :zone]
         fs (->> fcoll :features (map (comp #(select-keys % ks) :properties)))]
     (->> fs
          (group-by :zone)))))

(re-frame/reg-sub
 ::population-bar-chart-data
 :<- [::population-chart-data]
 :<- [::population-labels]
 (fn [[data labels] _]
   (->> data
        (reduce
           (fn [res [zone ms]]
             (let [zk (keyword (str "zone" zone))]
               (-> res
                   (assoc-in [:age-0-14 zk] (->> ms (map :ika_0_14) (reduce pos+ 0)))
                   (assoc-in [:age-15-64 zk] (->> ms (map :ika_15_64) (reduce pos+ 0)))
                   (assoc-in [:age-65- zk] (->> ms (map :ika_65_) (reduce pos+ 0)))
                   (assoc-in [:men zk] (->> ms (map :miehet) (reduce pos+ 0)))
                   (assoc-in [:women zk] (->> ms (map :naiset) (reduce pos+ 0)))
                   (assoc-in [:total zk] (->> ms (map :vaesto) (reduce pos+ 0))))))
           {})
        (map (fn [[k v]] (assoc v :group (labels k)))))))

(defn parse-km [s]
  (-> s (string/split " ") first utils/->int))

(re-frame/reg-sub
 ::population-area-chart-data
 :<- [::population-chart-data]
 :<- [::population-labels]
 (fn [[data labels] _]
   (->> data
        (reduce
         (fn [res [zone ms]]
           (conj res
                 {:zone      (labels (keyword (str "zone" zone)))
                  :age-0-14  (->> ms (map :ika_0_14) (reduce pos+ 0))
                  :age-15-64 (->> ms (map :ika_15_64) (reduce pos+ 0))
                  :age-65-   (->> ms (map :ika_65_) (reduce pos+ 0))})) [])
        (sort-by :zone))))
