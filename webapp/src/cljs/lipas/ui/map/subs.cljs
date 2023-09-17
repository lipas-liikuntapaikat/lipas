(ns lipas.ui.map.subs
  (:require
   [goog.array :as garray]
   [goog.object :as gobj]
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.ratom :as ratom]))

(re-frame/reg-sub
 ::map
 (fn [db]
   (:map db)))

(re-frame/reg-sub
 ::view
 :<- [:lipas.ui.sports-sites.subs/adding-new-site?]
 :<- [::selected-sports-site]
 :<- [::mode*]
 :<- [:lipas.ui.loi.subs/selected-loi]
 :<- [:lipas.ui.loi.subs/view-mode]
 :<- [::selected-add-mode]
 (fn [[adding-new-site? selected-site mode selected-loi loi-mode add-mode] _]
   (let [analysis? (-> mode :name (= :analysis))
         adding? (or (and (#{"sports-site"} add-mode) adding-new-site?)
                     (and (#{"loi"} add-mode) (#{:adding} loi-mode)))]
     (cond
       adding?       :adding
       analysis?     :analysis
       selected-site :site
       selected-loi  :loi
       :else         :search))))

(re-frame/reg-sub
 ::show-default-tools?
 :<- [::view]
 :<- [:lipas.ui.search.subs/search-results-view]
 (fn [[view result-view] _]
   (and (= :list result-view) (#{:search :analysis} view))))

(re-frame/reg-sub
 ::basemap
 :<- [::map]
 (fn [m _]
   (:basemap m)))

(re-frame/reg-sub
 ::selected-overlays
 :<- [::map]
 (fn [m _]
   (:selected-overlays m)))

(re-frame/reg-sub
 ::overlay-visible?
 :<- [::selected-overlays]
 (fn [overlays [_ layer]]
   (contains? overlays layer)))

(re-frame/reg-sub
 ::center
 :<- [::map]
 (fn [m _]
   (:center m)))

(re-frame/reg-sub
 ::zoom
 :<- [::map]
 (fn [m _]
   (:zoom m)))

(re-frame/reg-sub
 ::popup
 :<- [::map]
 (fn [m _]
   (:popup m)))

(re-frame/reg-sub
 ::drawer-width
 :<- [:lipas.ui.search.subs/search-results-view]
 :<- [::selected-sports-site]
 :<- [::mode-name]
 (fn [[result-view selected-site mode-name] [_ media-width]]
   (cond
     (#{"xs"} media-width)         "100%"
     (and (#{"sm"} media-width)
          (= :table result-view))  "100%"
     (and (= :table result-view)
          (empty? selected-site))  "100%"
     (and (not (#{"xs" "sm"} media-width))
          (= :analysis mode-name)) "700px"
     :else                         "530px")))

(re-frame/reg-sub
 ::selected-sports-site-tab
 :<- [::map]
 (fn [m _]
   (:selected-sports-site-tab m)))

(re-frame/reg-sub
 ::selected-new-sports-site-tab
 :<- [::map]
 (fn [m _]
   (:selected-new-sports-site-tab m)))

(re-frame/reg-sub-raw
 ::selected-sports-site
 (fn [app-db _event]
   (ratom/reaction
    (let [lipas-id (-> @app-db :map :mode :lipas-id)]
      (when lipas-id
        {:display-data
         @(re-frame/subscribe [:lipas.ui.sports-sites.subs/display-site lipas-id])
         :edit-data
         @(re-frame/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])})))))

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
                     name      (gobj/get obj "name")
                     status    (gobj/get obj "status")]

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
                                                  :type-code type-code
                                                  :status    status})
                                    geom))}))))
            not-empty)))))

(re-frame/reg-sub
 ::loi-geoms
 :<- [:lipas.ui.loi.subs/search-results]
 (fn [results _]
   (when results
     (map (fn [{:keys [geometries] :as m}]
            (update geometries :features
                    (fn [fs]
                      (map (fn [f]
                             (-> f
                                 (assoc :properties (dissoc m :geometries :search-meta))
                                 (assoc :id (:id m))))
                           fs))))
          results))))

(re-frame/reg-sub
 ::content-padding
 :<- [:lipas.ui.subs/screen-size]
 :<- [::drawer-open?]
 :<- [::drawer-width]
 (fn [[screen-size drawer-open? drawer-width] _]
   (let [drawer-width (condp #(str/ends-with? %2 %1) drawer-width
                        "%"  0
                        "px" (js/parseInt drawer-width))
         margin       20]
     (if (and (#{"xs sm"} screen-size) (not drawer-open?))
       [margin margin margin margin]
       [margin margin margin (+ margin drawer-width)]))))

(re-frame/reg-sub
 ::mode*
 :<- [::map]
 (fn [m _]
   (:mode m)))

(re-frame/reg-sub
 ::mode-name
 :<- [::mode*]
 (fn [mode _]
   (:name mode)))

(re-frame/reg-sub
 ::sub-mode
 :<- [::mode*]
 (fn [mode _]
   (:sub-mode mode)))

(re-frame/reg-sub
 ::mode
 :<- [::content-padding]
 :<- [::mode*]
 :<- [:lipas.ui.analysis.reachability.subs/reachability]
 :<- [:lipas.ui.analysis.diversity.subs/diversity]
 (fn [[content-padding mode reachability diversity] _]
   (let [analysis? (= (:name mode) :analysis)]
     (cond-> mode
       true      (assoc :content-padding content-padding)
       analysis? (assoc :analysis {:reachability reachability
                                   :diversity    diversity})))))

(re-frame/reg-sub
 ::selected-features
 :<- [::mode]
 (fn [mode _]
   (:selected-features mode)))

(re-frame/reg-sub
 ::editing-lipas-id
 :<- [::map]
 (fn [m _]
   (when (#{:editing :drawing} (m :mode :name))
     (-> m :mode :lipas-id))))

(re-frame/reg-sub
 ::zoomed-for-drawing?
 :<- [::zoom]
 (fn [zoom _]
   (> zoom 12)))

(re-frame/reg-sub
 ::drawing-geom-type
 :<- [::map]
 (fn [m _]
   (-> m :drawing :geom-type)))

(re-frame/reg-sub
 ::new-geom
 :<- [::map]
 (fn [m _]
   (when (= :adding (-> m :mode :name))
     (-> m :mode :geoms))))

(re-frame/reg-sub
 ::undo
 :<- [::map]
 (fn [m [_ lipas-id]]
   (let [undo-stack (get-in m [:temp lipas-id :undo-stack])]
     (seq undo-stack))))

(re-frame/reg-sub
 ::redo
 :<- [::map]
 (fn [m [_ lipas-id]]
   (let [redo-stack (get-in m [:temp lipas-id :redo-stack])]
     (seq redo-stack))))

(re-frame/reg-sub
 ::drawer-open?
 :<- [::map]
 (fn [m _]
   (-> m :drawer-open? boolean)))

;; Import geoms ;;

(re-frame/reg-sub
 ::import-dialog-open?
 :<- [::map]
 (fn [m _]
   (-> m :import :dialog-open?)))

(re-frame/reg-sub
 ::selected-import-file-encoding
 :<- [::map]
 (fn [m _]
   (-> m :import :selected-encoding)))

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
 :<- [::map]
 (fn [m _]
   (-> m :import :batch-id)))

(re-frame/reg-sub
 ::selected-import-items
 (fn [db _]
   (-> db :map :import :selected-items)))

(re-frame/reg-sub
 ::replace-existing-geoms?
 :<- [::map]
 (fn [m _]
   (-> m :import :replace-existing?)))

(re-frame/reg-sub
 ::address-search-dialog-open?
 :<- [::map]
 (fn [m _]
   (-> m :address-search :dialog-open?)))

(re-frame/reg-sub
 ::address-search-keyword
 :<- [::map]
 (fn [m _]
   (-> m :address-search :keyword)))

(defn ->result [f]
  {:geometry (-> f :geometry)
   :label    (-> f :properties :label)})

(re-frame/reg-sub
 ::address-search-results
 :<- [::map]
 (fn [m _]
   (-> m :address-search :results :features
       (->> (map ->result)))))

(re-frame/reg-sub
 ::more-tools-menu-anchor
 :<- [::map]
 (fn [m]
   (-> m :more-tools-menu :anchor)))

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
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/dead? lipas-id])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-publish? lipas-id])
    (re-frame/subscribe [:lipas.ui.user.subs/logged-in?])
    (re-frame/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
    (re-frame/subscribe [::mode])
    (re-frame/subscribe [::undo lipas-id])
    (re-frame/subscribe [::redo lipas-id])
    (re-frame/subscribe [::more-tools-menu-anchor])
    (re-frame/subscribe [::selected-sports-site-tab])])
 (fn [[cities types geom-type admins owners editing? edits-valid?
       editing-allowed? save-in-progress? delete-dialog-open? type
       types-props dead? can-publish? logged-in? size-categories mode
       undo redo more-tools-menu-anchor selected-tab] _]

   {:types                  (filter
                             (comp #{geom-type} :geometry-type second) types)
    :cities                 cities
    :admins                 admins
    :owners                 owners
    :editing?               editing?
    :edits-valid?           edits-valid?
    :editing-allowed?       editing-allowed?
    :delete-dialog-open?    delete-dialog-open?
    :can-publish?           can-publish?
    :logged-in?             logged-in?
    :size-categories        size-categories
    :mode                   mode
    :sub-mode               (:sub-mode mode)
    :type                   type
    :types-props            types-props
    :geom-type              (:geometry-type type)
    :save-in-progress?      save-in-progress?
    :problems?              (-> mode :problems :data :features seq)
    :portal                 (case (:type-code type)
                              (3110 3130) "uimahallit"
                              (2510 2520) "jaahallit"
                              nil)
    :dead?                  dead?
    :undo                   undo
    :redo                   redo
    :more-tools-menu-anchor more-tools-menu-anchor
    :selected-tab           selected-tab}))

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
    (re-frame/subscribe [::redo "new"])
    (re-frame/subscribe [::selected-new-sports-site-tab])])
 (fn [[type data valid? save-in-progress? admins owners cities types
       prop-types size-categories zoomed? geom mode undo redo
       selected-tab] _]
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
                         (->> types
                              (filter (comp #{geom-type} :geometry-type second))
                              (into {}))
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
      :redo            redo
      :selected-tab    selected-tab})))

(re-frame/reg-sub
 ::hide-actions?
 :<- [::map]
 :<- [:lipas.ui.sports-sites.activities.subs/mode]
 (fn [[m activity-mode]]
   (and (-> m :mode :name #{:editing})
        (#{:add-route :route-details} activity-mode)
        #_(-> m :mode :sub-mode #{:selecting}))))

(re-frame/reg-sub
 ::selected-add-mode
 :<- [::map]
 (fn [m _]
   (:add-mode m)))
