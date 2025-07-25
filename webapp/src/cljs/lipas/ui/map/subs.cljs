(ns lipas.ui.map.subs
  (:require [clojure.string :as str]
            [goog.array :as garray]
            [goog.object :as gobj]
            [re-frame.core :as rf]
            [reagent.ratom :as ratom]))

(rf/reg-sub ::map
  (fn [db]
    (:map db)))

(rf/reg-sub ::view
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

(rf/reg-sub ::show-default-tools?
  :<- [::view]
  :<- [:lipas.ui.search.subs/search-results-view]
  (fn [[view result-view] _]
    (and (= :list result-view) (#{:search :analysis} view))))

(rf/reg-sub ::basemap
  :<- [::map]
  (fn [m _]
    (:basemap m)))

(rf/reg-sub ::basemap-opacity
  :<- [::basemap]
  (fn [m _]
    (:opacity m)))

(rf/reg-sub ::selected-overlays
  :<- [::map]
  (fn [m _]
    (:selected-overlays m)))

(rf/reg-sub ::overlay-visible?
  :<- [::selected-overlays]
  (fn [overlays [_ layer]]
    (contains? overlays layer)))

(rf/reg-sub ::center
  :<- [::map]
  (fn [m _]
    (:center m)))

(rf/reg-sub ::zoom
  :<- [::map]
  (fn [m _]
    (:zoom m)))

(rf/reg-sub ::popup
  :<- [::map]
  (fn [m _]
    (:popup m)))

(rf/reg-sub ::drawer-width
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

(rf/reg-sub ::selected-sports-site-tab
  :<- [::map]
  (fn [m _]
    (:selected-sports-site-tab m)))

(rf/reg-sub ::selected-new-sports-site-tab
  :<- [::map]
  (fn [m _]
    (:selected-new-sports-site-tab m)))

(rf/reg-sub-raw ::selected-sports-site
  (fn [app-db _event]
    (ratom/reaction
      (let [lipas-id (-> @app-db :map :mode :lipas-id)]
        (when lipas-id
          {:display-data
           @(rf/subscribe [:lipas.ui.sports-sites.subs/display-site lipas-id])
           :edit-data
           @(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])})))))

(rf/reg-sub ::geometries-fast
  ;; NOTE: This is JSON.parse result from the ajax call
  :<- [:lipas.ui.search.subs/search-results-fast]
  :<- [::editing-lipas-id]
  (fn [[^js results lipas-id'] _]
    (when results
      (let [data (or (some-> results .-hits .-hits)
                     #js [])]
        (->> data
             (keep
               (fn [^js obj]
                 (let [obj              (.-_source obj)
                       ;; Hmm, consider cljs-bean here? Should be nearly as fast
                       geoms            (or
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
                       type-code        (gobj/getValueByKeys obj "type" "type-code")
                       lipas-id         (gobj/get obj "lipas-id")
                       name             (gobj/get obj "name")
                       status           (gobj/get obj "status")
                       travel-direction (gobj/get obj "travel-direction")]

                 ;; To avoid displaying duplicates when editing
                   (when-not (= lipas-id' lipas-id)
                     #js {:type     "FeatureCollection"
                          :features (garray/map
                                      geoms
                                      (fn [geom idx]
                                        (set! (.-id geom) (str lipas-id "-" idx))
                                        (set! (.-properties geom) #js {:lipas-id         lipas-id
                                                                       :name             name
                                                                       :type-code        type-code
                                                                       :status           status
                                                                       :travel-direction travel-direction})
                                        geom))}))))
             not-empty)))))

(rf/reg-sub ::loi-geoms
  :<- [:lipas.ui.loi.subs/search-results]
  (fn [results _]
    (when results
      (map (fn [{:keys [geometries] :as m}]
             (update geometries :features
                     (fn [fs]
                       (map (fn [f]
                              (assoc f :properties (-> m
                                                       (dissoc :geometries :search-meta :id)
                                                       (assoc :loi-id (:id m)))))
                            fs))))
           results))))

(rf/reg-sub ::content-padding
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

(rf/reg-sub ::mode*
  :<- [::map]
  (fn [m _]
    (:mode m)))

(rf/reg-sub ::mode-name
  :<- [::mode*]
  (fn [mode _]
    (:name mode)))

(rf/reg-sub ::sub-mode
  :<- [::mode*]
  (fn [mode _]
    (:sub-mode mode)))

(rf/reg-sub ::mode
  :<- [::content-padding]
  :<- [::mode*]
  :<- [:lipas.ui.analysis.reachability.subs/reachability]
  :<- [:lipas.ui.analysis.diversity.subs/diversity]
  :<- [:lipas.ui.analysis.heatmap.subs/heatmap]
  :<- [::simplify]
  (fn [[content-padding mode reachability diversity heatmap simplify] _]
    (let [analysis?  (= (:name mode) :analysis)
          simplify? (and (#{:adding :editing} (:name mode))
                         (= (:sub-mode mode) :simplifying))]
      (cond-> mode
        true       (assoc :content-padding content-padding)
        analysis?  (assoc :analysis {:reachability reachability
                                     :diversity diversity
                                     :heatmap heatmap})
        simplify? (assoc :simplify simplify)))))

(rf/reg-sub ::selected-features
  :<- [::mode]
  (fn [mode _]
    (:selected-features mode)))

(rf/reg-sub ::editing-lipas-id
  :<- [::map]
  (fn [m _]
    (when (#{:editing :drawing} (-> m :mode :name))
      (-> m :mode :lipas-id))))

(rf/reg-sub ::zoomed-for-drawing?
  :<- [::zoom]
  (fn [zoom _]
    (> zoom 12)))

(rf/reg-sub ::drawing-geom-type
  :<- [::map]
  (fn [m _]
    (-> m :drawing :geom-type)))

(rf/reg-sub ::new-geom
  :<- [::map]
  (fn [m _]
    (when (= :adding (-> m :mode :name))
      (-> m :mode :geoms))))

(rf/reg-sub ::undo
  :<- [::map]
  (fn [m [_ lipas-id]]
    (let [undo-stack (get-in m [:temp lipas-id :undo-stack])]
      (seq undo-stack))))

(rf/reg-sub ::redo
  :<- [::map]
  (fn [m [_ lipas-id]]
    (let [redo-stack (get-in m [:temp lipas-id :redo-stack])]
      (seq redo-stack))))

(rf/reg-sub ::drawer-open?
  :<- [::map]
  (fn [m _]
    (-> m :drawer-open? boolean)))

;; Import geoms ;;

(rf/reg-sub ::import
  :<- [::map]
  (fn [m _]
    (:import m)))

(rf/reg-sub ::import-dialog-open?
  :<- [::import]
  (fn [m _]
    (:dialog-open? m)))

(rf/reg-sub ::selected-import-file-encoding
  :<- [::import]
  (fn [m _]
    (:selected-encoding m)))

(rf/reg-sub ::import-error
  :<- [::import]
  (fn [m _]
    (:error m)))

(rf/reg-sub ::import-data
  :<- [::import]
  (fn [m _]
    (:data m)))

(rf/reg-sub ::import-candidates
  :<- [::import-data]
  (fn [data _]
    data))

(def ignored-headers #{"id" "coordTimes"})

(rf/reg-sub ::import-candidates-headers
  :<- [::import-data]
  (fn [data _]
    (let [->entry (fn [s]
                    (assoc {} :label s :hidden? (contains? ignored-headers s)))]
      (when (not-empty data)
        (-> data keys first data :properties
            (->> (mapv (juxt first (comp ->entry name first)))))))))

(rf/reg-sub ::import-batch-id
  :<- [::map]
  (fn [m _]
    (-> m :import :batch-id)))

(rf/reg-sub ::selected-import-items
  (fn [db _]
    (-> db :map :import :selected-items)))

(rf/reg-sub ::replace-existing-geoms?
  :<- [::map]
  (fn [m _]
    (-> m :import :replace-existing?)))

;;; Simplify geoms ;;;

(rf/reg-sub ::simplify
  (fn [db _]
    (-> db :map :simplify)))

(rf/reg-sub ::simplify-dialog-open?
  :<- [::simplify]
  (fn [simplify _]
    (:dialog-open? simplify)))

(rf/reg-sub ::simplify-tolerance
  :<- [::simplify]
  (fn [simplify _]
    (:tolerance simplify)))

;;; Address search ;;;

(rf/reg-sub ::address-search-dialog-open?
  :<- [::map]
  (fn [m _]
    (-> m :address-search :dialog-open?)))

(rf/reg-sub ::address-search-keyword
  :<- [::map]
  (fn [m _]
    (-> m :address-search :keyword)))

(defn ->result [f]
  {:geometry (-> f :geometry)
   :label    (-> f :properties :label)})

(rf/reg-sub ::address-search-results
  :<- [::map]
  (fn [m _]
    (-> m :address-search :results :features
        (->> (map ->result)))))

(rf/reg-sub ::more-tools-menu-anchor
  :<- [::map]
  (fn [m]
    (-> m :more-tools-menu :anchor)))

(rf/reg-sub ::sports-site-view
  (fn [[_ lipas-id type-code] _]
    [(rf/subscribe [:lipas.ui.user.subs/permission-to-types])
     (rf/subscribe [:lipas.ui.sports-sites.subs/geom-type lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/admins])
     (rf/subscribe [:lipas.ui.sports-sites.subs/owners])
     (rf/subscribe [:lipas.ui.sports-sites.subs/editing? lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/edits-valid? lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/editing-allowed? lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/save-in-progress?])
     (rf/subscribe [:lipas.ui.sports-sites.subs/delete-dialog-open? lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/type-by-type-code type-code])
     (rf/subscribe [:lipas.ui.sports-sites.subs/types-props type-code])
     (rf/subscribe [:lipas.ui.sports-sites.subs/dead? lipas-id])
     (rf/subscribe [:lipas.ui.user.subs/permission-to-publish? lipas-id])
     (rf/subscribe [:lipas.ui.user.subs/logged-in?])
     (rf/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
     (rf/subscribe [::mode])
     (rf/subscribe [::undo lipas-id])
     (rf/subscribe [::redo lipas-id])
     (rf/subscribe [::more-tools-menu-anchor])
     (rf/subscribe [::selected-sports-site-tab])])
  (fn [[types geom-type admins owners editing?
        edits-valid? editing-allowed? save-in-progress?
        delete-dialog-open? type types-props dead? can-publish?
        logged-in? size-categories mode undo redo
        more-tools-menu-anchor selected-tab] _]

    {:types                  (filter
                               (comp #{geom-type} :geometry-type second) types)
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

(rf/reg-sub ::add-sports-site-view
  (fn [_]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/new-site-type])
     (rf/subscribe [:lipas.ui.sports-sites.subs/new-site-data])
     (rf/subscribe [:lipas.ui.sports-sites.subs/new-site-is-planning])
     (rf/subscribe [:lipas.ui.sports-sites.subs/new-site-valid?])
     (rf/subscribe [:lipas.ui.sports-sites.subs/save-in-progress?])
     (rf/subscribe [:lipas.ui.sports-sites.subs/admins])
     (rf/subscribe [:lipas.ui.sports-sites.subs/owners])
     (rf/subscribe [:lipas.ui.sports-sites.subs/prop-types])
     (rf/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
     (rf/subscribe [::zoomed-for-drawing?])
     (rf/subscribe [::new-geom])
     (rf/subscribe [::mode])
     (rf/subscribe [::undo "new"])
     (rf/subscribe [::redo "new"])
     (rf/subscribe [::selected-new-sports-site-tab])])
  (fn [[type data is-planning? valid? save-in-progress? admins owners
        prop-types size-categories zoomed? geom mode undo redo
        selected-tab] _]
    (let [sub-mode  (mode :sub-mode)]
      {:type            type
       :type-code       (:type-code type)
       :geom-type       (:geometry-type type)
       :data            data
       :is-planning?    is-planning?
       :save-enabled?   (and valid? (= :finished sub-mode) (not save-in-progress?))
       :admins          admins
       :owners          owners
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

(rf/reg-sub ::new-site-types
  (fn [[_ is-planning? _geom-type]]
    ;; all types are allowed when creating planning site from analysis tool
    (rf/subscribe (if is-planning?
                    [:lipas.ui.sports-sites.subs/active-types]
                    [:lipas.ui.user.subs/permission-to-types])))
  (fn [types [_ _is-planning? geom-type]]
    (if geom-type
      (->> types
           (filter (comp #{geom-type} :geometry-type second))
           (into {}))
      types)))

(rf/reg-sub ::show-create-button?
  :<- [::map]
  :<- [:lipas.ui.user.subs/logged-in?]
  :<- [:lipas.ui.user.subs/can-add-sports-sites?]
  :<- [:lipas.ui.user.subs/can-add-lois?]
  (fn [[m logged-in? can-add-sports-sites? can-add-lois?] _]
    (and logged-in?
         (-> m :mode :name #{:default})
         (or can-add-sports-sites? can-add-lois?))))

(rf/reg-sub ::hide-actions?
  :<- [::map]
  :<- [:lipas.ui.sports-sites.activities.subs/mode]
  (fn [[m activity-mode]]
    (and (-> m :mode :name #{:editing})
         (#{:add-route :route-details} activity-mode)
         #_(-> m :mode :sub-mode #{:selecting}))))

(rf/reg-sub ::selected-add-mode
  :<- [::map]
  :<- [:lipas.ui.user.subs/can-add-lois-only?]
  :<- [:lipas.ui.sports-sites.subs/new-site-is-planning]
  (fn [[m can-add-lois-only? is-planning?] _]
    (if (and (not is-planning?) can-add-lois-only?)
      "loi"
      (:add-mode m))))

;; Address locator (reverse geocoding)

(rf/reg-sub ::address-locator
  :<- [::map]
  (fn [m]
    (:address-locator m)))

(rf/reg-sub ::address-locator-dialog-open?
  :<- [::address-locator]
  (fn [m]
    (boolean (:dialog-open? m))))

(rf/reg-sub ::address-locator-addresses
  :<- [::address-locator]
  (fn [m]
    (:reverse-geocoding-results m)))

(rf/reg-sub ::address-locator-selected-address
  :<- [::address-locator]
  (fn [m]
    (:selected-address m)))

(rf/reg-sub ::address-locator-error
  :<- [::address-locator]
  (fn [m]
    (:error m)))

(rf/reg-sub ::edit-geom-properties
  (fn [db [_ fid]]
    (->> (get-in db [:map :mode :geoms])
         :features
         (some (fn [f]
                 (when (= (:id f) fid)
                   (:properties f)))))))

(rf/reg-sub ::restore-site-backup-dialog
  :<- [::map]
  (fn [m]
    (:restore-site-backup-dialog m)))

(rf/reg-sub ::restore-site-backup-dialog-open?
  :<- [::restore-site-backup-dialog]
  (fn [m]
    (boolean (:open? m))))

(rf/reg-sub ::restore-site-backup-lipas-id
  :<- [::restore-site-backup-dialog]
  (fn [m]
    (:lipas-id m)))

(rf/reg-sub ::restore-site-backup-error
  :<- [::restore-site-backup-dialog]
  (fn [m]
    (:error m)))
