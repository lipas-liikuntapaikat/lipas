(ns lipas.ui.sports-sites.subs
  (:require
   ["@turf/helpers" :refer [lineString]]
   ["@turf/length$default" :as turf-length]
   [clojure.spec.alpha :as s]
   [lipas.data.types :as types]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::sports-sites
 (fn [db _]
   (->> db :sports-sites)))

(re-frame/reg-sub
 ::sports-site
 :<- [::sports-sites]
 (fn [sports-sites [_ lipas-id]]
   (get sports-sites lipas-id)))

(re-frame/reg-sub
 ::latest-sports-site-revs
 :<- [::sports-sites]
 (fn [sites _]
   (not-empty
    (reduce-kv
     (fn [m k v]
       (if-let [site (get-in v [:history (:latest v)])]
         (assoc m k site)
         m))
     {}
     sites))))

(re-frame/reg-sub
 ::latest-rev
 :<- [::sports-sites]
 (fn [sports-sites [_ lipas-id]]
   (let [latest (get-in sports-sites [lipas-id :latest])]
     (get-in sports-sites [lipas-id :history latest]))))

(re-frame/reg-sub
 ::editing-rev
 :<- [::sports-sites]
 :<- [:lipas.ui.map.subs/mode*]
 (fn [[sports-sites map-mode] [_ lipas-id]]
   ;; Edit-time geoms are found under the map mode
   (let [geoms     (:geoms map-mode)
         edit-data (get-in sports-sites [lipas-id :editing])]
     (if (and edit-data geoms)
       (assoc-in edit-data [:location :geometries] geoms)
       edit-data))))

(re-frame/reg-sub
 ::editing?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::editing-rev lipas-id]))
 (fn [edit-data _]
   (seq edit-data)))

(re-frame/reg-sub
 ::editing-allowed?
 (fn [[_ lipas-id] _]
   [(re-frame/subscribe [::latest-rev lipas-id])
    (re-frame/subscribe [:lipas.ui.user.subs/admin?])])
 (fn [[rev admin?] _]
   (or admin?
       (->> rev :status #{"active"
                          "out-of-service-temporarily"
                          "planned"
                          "planning"}))))

(re-frame/reg-sub
 ::dead?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::latest-rev lipas-id]))
 (fn [rev _]
   (->> rev :status #{"out-of-service-permanently"})))

(defn- valid? [sports-site]
  (let [spec (case (-> sports-site :type :type-code)
               (3110 3120 3130) :lipas.sports-site/swimming-pool
               (2510 2520)      :lipas.sports-site/ice-stadium
               :lipas/sports-site)]
    (as-> sports-site $
      (utils/make-saveable $)
      (do (tap> (with-out-str (s/explain spec $))) $)
      (s/valid? spec $))))

(re-frame/reg-sub
 ::edits-valid?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::editing-rev lipas-id]))
 (fn [edit-data _]
   (valid? edit-data)))

(re-frame/reg-sub
 ::save-in-progress?
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :save-in-progress?)))

;; TODO maybe refactor region related subs to other ns

(re-frame/reg-sub
 ::cities-by-city-code
 (fn [db _]
   (-> db :cities)))

(re-frame/reg-sub
 ::abolished-cities
 (fn [db _]
   (-> db :abolished-cities)))

(re-frame/reg-sub
 ::cities-list
 :<- [::cities-by-city-code]
 (fn [cities _]
   (vals cities)))

(re-frame/reg-sub
 ::avi-areas
 (fn [db _]
   (-> db :avi-areas)))

(re-frame/reg-sub
 ::provinces
 (fn [db _]
   (-> db :provinces)))

(re-frame/reg-sub
 ::regions
 :<- [::cities-by-city-code]
 :<- [::avi-areas]
 :<- [::provinces]
 (fn [[cities avis provinces] _]
   (concat
    (for [[k v] cities]
      (assoc v :region-id (str "city-" k)))
    (for [[k v] avis]
      (assoc v :region-id (str "avi-" k)))
    (for [[k v] provinces]
      (assoc v :region-id (str "province-" k))))))

(re-frame/reg-sub
 ::type-categories
 :<- [::all-types]
 (fn [types _]
   (concat
    (for [[k v] types]
      (assoc v :cat-id (str "type-" k)))
    (for [[k v] types/main-categories]
      (assoc v :cat-id (str "main-cat-" k)))
    (for [[k v] types/sub-categories]
      (assoc v :cat-id (str "sub-cat-" k))))))

(re-frame/reg-sub
 ::type-table
 :<- [::all-types]
 :<- [:lipas.ui.subs/locale]
 (fn [[types locale]]
   (for [[type-code m] types]
     {:type-code     type-code
      :name          (get-in m [:name locale])
      :geometry-type (:geometry-type m)
      :main-category (get-in types/main-categories [(:main-category m) :name locale])
      :sub-category  (get-in types/sub-categories [(:sub-category m) :name locale])
      :description   (get-in m [:description locale])})))

(re-frame/reg-sub
 ::admins
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :admins)))

(re-frame/reg-sub
 ::owners
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :owners)))

(re-frame/reg-sub
 ::all-types
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :types)))

(re-frame/reg-sub
 ::type-by-type-code
 :<- [::all-types]
 (fn [types [_ type-code]]
   (types type-code)))

(re-frame/reg-sub
 ::types-by-geom-type
 :<- [::all-types]
 (fn [types [_ geom-type]]
   (filter (comp #{geom-type} :geometry-type second) types)))

(re-frame/reg-sub
 ::allowed-types
 (fn [[_ geom-type] _]
   [(re-frame/subscribe [::type-by-geom-type geom-type])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-types])])
 (fn [[m1 m2] _]
   (select-keys m2 (keys m1))))

(re-frame/reg-sub
 ::types-list
 :<- [::all-types]
 (fn [types _]
   (vals types)))

(re-frame/reg-sub
 ::materials
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :materials)))

(re-frame/reg-sub
 ::building-materials
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :building-materials)))

(re-frame/reg-sub
 ::supporting-structures
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :supporting-structures)))

(re-frame/reg-sub
 ::ceiling-structures
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :ceiling-structures)))

(re-frame/reg-sub
 ::surface-materials
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :surface-materials)))

(re-frame/reg-sub
 ::prop-types
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :prop-types)))

(re-frame/reg-sub
 ::types-props
 :<- [::all-types]
 :<- [::prop-types]
 (fn [[types prop-types] [_ type-code]]
   (let [props (-> (types type-code) :props)]
     (reduce (fn [res [k v]]
               (let [prop-type (prop-types k)]
                 (assoc res k (merge prop-type v))))
             {}
             props))))

(re-frame/reg-sub
 ::geom-type
 (fn [[_ lipas-id]]
   (re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id]))
 (fn [rev _]
   (-> rev :location :geometries :features first :geometry :type)))

(re-frame/reg-sub
 ::elevation
 (fn [[_ lipas-id]]
   [(re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/geom-type lipas-id])])
 (fn [[rev geom-type] _]
   (when (= "LineString" geom-type)
     (-> rev :location :geometries :features
         (->> (map (comp :coordinates :geometry))
              (map (fn [coll]
                     (->> coll
                          (map-indexed
                           (fn [idx coords]
                             (let [line        (when-not (zero? idx)
                                                 (lineString (clj->js (subvec coll 0 (inc idx)))))
                                   distance-km (if (zero? idx)
                                                 0
                                                 (turf-length line))]
                               {:coords      coords
                                :elevation-m (get coords 2)
                                :distance-km distance-km
                                :distance-m  (* 1000 distance-km)})))))))))))

(re-frame/reg-sub
 ::elevation-stats
 (fn [[_ lipas-id]]
   (re-frame/subscribe [:lipas.ui.sports-sites.subs/elevation lipas-id]))
 (fn [elevation _]
   (for [segment elevation]
     (->> segment
          (map :elevation-m)
          (partition 2 1)
          (reduce (fn [res [prev curr]]
                    (let [d (- curr prev)]
                      (cond
                        (zero? d) res
                        (pos? d)  (update res :ascend-m + d)
                        (neg? d)  (update res :descend-m + (Math/abs d)))))
                  {:ascend-m 0 :descend-m 0})))))

(re-frame/reg-sub
 ::display-site
 (fn [[_ lipas-id] _]
   [(re-frame/subscribe [:lipas.ui.sports-sites.subs/sports-site lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/cities-by-city-code])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/admins])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/owners])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/all-types])
    (re-frame/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/materials])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/statuses])
    (re-frame/subscribe [:lipas.ui.subs/translator])
    (re-frame/subscribe [:lipas.ui.swimming-pools.subs/pool-types])
    (re-frame/subscribe [:lipas.ui.swimming-pools.subs/accessibility])
    (re-frame/subscribe [:lipas.ui.sports-sites.floorball.subs/type-codes])
    (re-frame/subscribe [:lipas.ui.sports-sites.floorball.subs/floor-elasticity])
    (re-frame/subscribe [:lipas.ui.sports-sites.floorball.subs/player-entrance])
    (re-frame/subscribe [:lipas.ui.sports-sites.floorball.subs/audience-stand-access])
    (re-frame/subscribe [:lipas.ui.sports-sites.floorball.subs/car-parking-economics-model])
    (re-frame/subscribe [:lipas.ui.sports-sites.floorball.subs/roof-trussess-operation-model])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/field-types])
    (re-frame/subscribe [:lipas.ui.map.subs/mode*])])
 (fn [[site cities admins owners types size-categories materials
       statuses translator pool-types pool-accessibility
       floorball-types floor-elasticity player-entrance
       audience-stand-access car-parking-economics-model
       roof-trussess-operation-model field-types map-mode] _]
   (when site
     (let [locale        (translator)
           latest        (or (utils/latest-edit (:edits site))
                             (get-in site [:history (:latest site)]))
           type          (types (-> latest :type :type-code))
           size-category (size-categories (-> latest :type :size-category))
           admin         (admins (-> latest :admin))
           owner         (owners (-> latest :owner))
           city          (get cities (-> latest :location :city :city-code))
           status        (statuses (-> latest :status))

           get-material #(get-in materials [% locale])]

       (merge
        {:status            (-> status locale)
         :event-date        (-> latest :event-date utils/->human-date-time-at-user-tz)
         :lipas-id          (-> latest :lipas-id)
         :name              (-> latest :name)
         :name-localized    (-> latest :name-localized)
         :marketing-name    (-> latest :marketing-name)
         :type
         {:name          (-> type :name locale)
          :type-code     (-> latest :type :type-code)
          :size-category (-> size-category locale)}
         :owner             (-> owner locale)
         :admin             (-> admin locale)
         :phone-number      (-> latest :phone-number)
         :www               (-> latest :www)
         :reservations-link (-> latest :reservations-link)
         :email             (-> latest :email)
         :comment           (-> latest :comment)

         :construction-year (-> latest :construction-year)
         :renovation-years  (-> latest :renovation-years)

         :properties (-> latest
                         :properties
                         (update :surface-material #(map get-material %))
                         (update :running-track-surface-material get-material)
                         (update :training-spot-surface-material get-material))

         :location
         {:address       (-> latest :location :address)
          :postal-code   (-> latest :location :postal-code)
          :postal-office (-> latest :location :postal-office)
          :city
          {:name         (-> city :name locale)
           :neighborhood (-> latest :location :city :neighborhood)}}

         :building (:building latest)}

        ;; TODO put type-specific stuff behind a multi-method

        (when (#{3110 3130} (:type-code type)) ; swimming pools
          (let [get-pool-type     #(get-in pool-types [% locale])
                get-accessibility #(get-in pool-accessibility [% locale])]
            {:pools
             (->> (:pools latest)
                  (map #(update % :type get-pool-type))
                  (map #(update % :structure get-material))
                  (map #(update % :accessibility (fn [coll] (map get-accessibility coll)))))
             :slides     (:slides latest)
             :facilities (:facilities latest)}))

        (when (#{2510 2520} (:type-code type)) ; ice stadiums
          {:rinks (:rinks latest)})

        (when (or (contains? floorball-types (:type-code type)) ; floorball
                  #_(#{2230 1350 1340} (:type-code type))) ; football
          {:circumstances (-> latest
                              :circumstances
                              (update :player-entrance
                                      #(get-in player-entrance [% locale]))
                              (update :car-parking-economics-model
                                      #(get-in car-parking-economics-model [% locale]))
                              (update :roof-trussess-operation-model
                                      #(get-in roof-trussess-operation-model [% locale])))
           :fields        (->> (:fields latest)
                               (map #(update % :type
                                             (fn [v]
                                               (get-in field-types [v locale]))))
                               (map #(update % :surface-material get-material))
                               (map #(update % :floor-elasticity
                                             (fn [v]
                                               (get-in floor-elasticity [v locale]))))
                               (map #(update % :audience-stand-access
                                             (fn [v]
                                               (get-in audience-stand-access [v locale])))))
           :locker-rooms  (:locker-rooms latest)})

        ;; TODO maybe check activities for type
        (when true
          {:activities (:activities latest)}))))))

(defn ->list-entry [{:keys [cities admins owners types locale size-categories]}
                    sports-site]
  (let [type          (types (-> sports-site :type :type-code))
        size-category (and
                       (= 2520 (-> sports-site :type :type-code))
                       (-> sports-site :type :size-category size-categories locale))
        admin         (admins (-> sports-site :admin))
        owner         (owners (-> sports-site :owner))
        city          (get cities (-> sports-site :location :city :city-code))]
    {:lipas-id          (-> sports-site :lipas-id)
     :name              (-> sports-site :name)
     :type              (or (utils/truncate-size-category size-category)
                            (-> type :name locale))
     :address           (-> sports-site :location :address)
     :postal-code       (-> sports-site :location :postal-code)
     :postal-office     (-> sports-site :location :postal-office)
     :city              (-> city :name locale)
     :construction-year (-> sports-site :construction-year)
     :renovation-years  (-> sports-site :renovation-years)
     :owner             (-> owner locale)
     :admin             (-> admin locale)
     :www               (-> sports-site :www)
     :reservations-link (-> sports-site :reservations-link)
     :email             (-> sports-site :email)
     :phone-number      (-> sports-site :phone-number)}))

(defn filter-matching [s coll]
  (if (empty? s)
    coll
    (filter (partial cutils/str-matches? s) coll)))

(re-frame/reg-sub
 ::sites-list
 :<- [::latest-sports-site-revs]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::all-types]
 :<- [:lipas.ui.ice-stadiums.subs/size-categories]
 (fn [[sites cities admins owners types size-categories]
      [_ locale type-codes sites-filter]]
   (let [data {:locale          locale
               :cities          cities
               :admins          admins
               :owners          owners
               :types           types
               :size-categories size-categories}]
     (->> sites
          vals
          (filter (comp type-codes :type-code :type))
          (map (partial ->list-entry data))
          (filter-matching sites-filter)))))

(re-frame/reg-sub
 ::new-sports-site
 (fn [db _]
   (-> db :new-sports-site)))

(re-frame/reg-sub
 ::adding-new-site?
 :<- [::new-sports-site]
 (fn [new-sports-site _]
   (-> new-sports-site :adding?)))

(re-frame/reg-sub
 ::new-site-data
 :<- [::new-sports-site]
 (fn [new-sports-site _]
   (-> new-sports-site :data)))

(re-frame/reg-sub
 ::new-site-type
 :<- [::new-sports-site]
 :<- [::all-types]
 (fn [[new-sports-site types]  _]
   (let [type-code (-> new-sports-site :type)]
     (get types type-code))))

(re-frame/reg-sub
 ::new-site-valid?
 :<- [::new-sports-site]
 (fn [new-sports-site _]
   (let [data (-> new-sports-site :data utils/make-saveable)]
     (tap> (s/explain :lipas/new-sports-site data))
     (s/valid? :lipas/new-sports-site data))))

(re-frame/reg-sub
 ::statuses
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :statuses)))

(re-frame/reg-sub
 ::delete-statuses
 :<- [::statuses]
 (fn [statuses _]
   (select-keys statuses ["out-of-service-temporarily"
                          "out-of-service-permanently"
                          "incorrect-data"])))

(re-frame/reg-sub
 ::resurrect-statuses
 :<- [::statuses]
 (fn [statuses _]
   (select-keys statuses ["out-of-service-temporarily"
                          "active"
                          "planned"
                          "planning"])))

(re-frame/reg-sub
 ::field-types
 :<- [::sports-sites]
 (fn [sports-sites _]
   (:field-types sports-sites)))

(re-frame/reg-sub
 ::delete-dialog-open?
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :delete-dialog :open?)))

(re-frame/reg-sub
 ::selected-delete-status
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :delete-dialog :selected-status)))

(re-frame/reg-sub
 ::selected-delete-year
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :delete-dialog :selected-year)))

(re-frame/reg-sub
 ::sports-site-name-conflict?
 :<- [::sports-sites]
 (fn [sports-sites _]
   (-> sports-sites :name-check :response :status (= :conflict))))
