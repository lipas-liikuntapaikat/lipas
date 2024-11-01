(ns lipas.ui.sports-sites.subs
  (:require ["@turf/helpers" :refer [lineString]]
            ["@turf/length$default" :as turf-length]
            [clojure.spec.alpha :as s]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.roles :as roles]
            [lipas.ui.utils :as utils]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-sub ::sports-sites
  (fn [db _]
    (->> db :sports-sites)))

(rf/reg-sub ::sports-site
  :<- [::sports-sites]
  (fn [sports-sites [_ lipas-id]]
    (get sports-sites lipas-id)))

(rf/reg-sub ::latest-sports-site-revs
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

(rf/reg-sub ::latest-rev
  :<- [::sports-sites]
  (fn [sports-sites [_ lipas-id]]
    (let [latest (get-in sports-sites [lipas-id :latest])]
      (get-in sports-sites [lipas-id :history latest]))))

(rf/reg-sub ::editing-rev
  :<- [::sports-sites]
  :<- [:lipas.ui.map.subs/mode*]
  (fn [[sports-sites map-mode] [_ lipas-id]]
   ;; Edit-time geoms are found under the map mode
    (let [geoms     (:geoms map-mode)
          edit-data (get-in sports-sites [lipas-id :editing])]
      (if (and edit-data geoms)
        (assoc-in edit-data [:location :geometries] geoms)
        edit-data))))

(rf/reg-sub ::editing-first-point
  (fn [[_ lipas-id] _]
    [(rf/subscribe [::editing-rev lipas-id])
     (rf/subscribe [::new-site-data])])
  (fn [[edit-data new-site-edit-data] [_ _lipas-id]]
    (let [sports-site (or edit-data new-site-edit-data)
          first-geom  (-> sports-site :location :geometries :features first :geometry)]
      (case (:type first-geom)
        "Point"      (-> first-geom :coordinates)
        "LineString" (-> first-geom :coordinates first)
        "Polygon"    (-> first-geom :coordinates first first)
        nil))))

(rf/reg-sub ::editing?
  (fn [[_ lipas-id] _]
    (rf/subscribe [::editing-rev lipas-id]))
  (fn [edit-data _]
    (seq edit-data)))

(rf/reg-sub ::editing-allowed?
  (fn [[_ lipas-id] _]
    [(rf/subscribe [::latest-rev lipas-id])
     (rf/subscribe [:lipas.ui.user.subs/user-data])])
  (fn [[rev user] _]
    (or (roles/check-privilege user (roles/site-roles-context rev) :site/edit-any-status)
        (->> rev :status #{"active"
                           "out-of-service-temporarily"
                           "planned"
                           "planning"}))))

(rf/reg-sub ::dead?
  (fn [[_ lipas-id] _]
    (rf/subscribe [::latest-rev lipas-id]))
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

(rf/reg-sub ::edits-valid?
  (fn [[_ lipas-id] _]
    (rf/subscribe [::editing-rev lipas-id]))
  (fn [edit-data _]
    (valid? edit-data)))

(rf/reg-sub ::save-in-progress?
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :save-in-progress?)))

;; TODO maybe refactor region related subs to other ns

(rf/reg-sub ::cities-by-city-code
  (fn [db _]
    (-> db :cities)))

(rf/reg-sub ::abolished-cities
  (fn [db _]
    (-> db :abolished-cities)))

(rf/reg-sub ::cities-list
  :<- [::cities-by-city-code]
  (fn [cities _]
    (vals cities)))

(rf/reg-sub ::avi-areas
  (fn [db _]
    (-> db :avi-areas)))

(rf/reg-sub ::provinces
  (fn [db _]
    (-> db :provinces)))

(rf/reg-sub ::regions
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

(rf/reg-sub ::type-categories
  :<- [::active-types]
  (fn [types _]
    (concat
      (for [[k v] types]
        (assoc v :cat-id (str "type-" k)))
      (for [[k v] types/main-categories]
        (assoc v :cat-id (str "main-cat-" k)))
      (for [[k v] types/sub-categories]
        (assoc v :cat-id (str "sub-cat-" k))))))

(rf/reg-sub ::type-table
  :<- [::active-types]
  :<- [:lipas.ui.subs/locale]
  (fn [[types locale]]
    (for [[type-code m] types]
      {:type-code     type-code
       :name          (get-in m [:name locale])
       :geometry-type (:geometry-type m)
       :main-category (get-in types/main-categories [(:main-category m) :name locale])
       :sub-category  (get-in types/sub-categories [(:sub-category m) :name locale])
       :description   (get-in m [:description locale])
       :tags          (get-in m [:tags locale])})))

(rf/reg-sub ::admins
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :admins)))

(rf/reg-sub ::owners
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :owners)))

(rf/reg-sub ::all-types
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :types)))

(rf/reg-sub ::active-types
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :active-types)))

(rf/reg-sub ::type-by-type-code
  :<- [::active-types]
  (fn [types [_ type-code]]
    (types type-code)))

(rf/reg-sub ::types-by-geom-type
  :<- [::active-types]
  (fn [types [_ geom-type]]
    (filter (comp #{geom-type} :geometry-type second) types)))

(rf/reg-sub ::allowed-types
  (fn [[_ geom-type] _]
    [(rf/subscribe [::type-by-geom-type geom-type])
     (rf/subscribe [:lipas.ui.user.subs/permission-to-types])])
  (fn [[m1 m2] _]
    (select-keys m2 (keys m1))))

(rf/reg-sub ::types-list
  :<- [::active-types]
  (fn [types _]
    (vals types)))

(rf/reg-sub ::materials
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :materials)))

(rf/reg-sub ::building-materials
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :building-materials)))

(rf/reg-sub ::supporting-structures
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :supporting-structures)))

(rf/reg-sub ::ceiling-structures
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :ceiling-structures)))

(rf/reg-sub ::surface-materials
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :surface-materials)))

(rf/reg-sub ::prop-types
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :prop-types)))

(rf/reg-sub ::types-props
  :<- [::active-types]
  :<- [::prop-types]
  (fn [[types prop-types] [_ type-code]]
    (let [props (-> (types type-code) :props)]
      (reduce (fn [res [k v]]
                (let [prop-type (prop-types k)]
                  (assoc res k (merge prop-type v))))
              {}
              props))))

(rf/reg-sub ::prop-type
  :<- [::prop-types]
  (fn [prop-types [_ prop-k]]
    (get prop-types prop-k)))

(rf/reg-sub ::geom-type
  (fn [[_ lipas-id]]
    (rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id]))
  (fn [rev _]
    (-> rev :location :geometries :features first :geometry :type)))

(rf/reg-sub ::elevation
  (fn [[_ lipas-id]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/geom-type lipas-id])])
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

(rf/reg-sub ::elevation-stats
  (fn [[_ lipas-id]]
    (rf/subscribe [:lipas.ui.sports-sites.subs/elevation lipas-id]))
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

(rf/reg-sub ::display-site
  (fn [[_ lipas-id] _]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/sports-site lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/cities-by-city-code])
     (rf/subscribe [:lipas.ui.sports-sites.subs/admins])
     (rf/subscribe [:lipas.ui.sports-sites.subs/owners])
     (rf/subscribe [:lipas.ui.sports-sites.subs/active-types])
     (rf/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
     (rf/subscribe [:lipas.ui.sports-sites.subs/materials])
     (rf/subscribe [:lipas.ui.sports-sites.subs/statuses])
     (rf/subscribe [:lipas.ui.subs/translator])
     (rf/subscribe [:lipas.ui.swimming-pools.subs/pool-types])
     (rf/subscribe [:lipas.ui.swimming-pools.subs/accessibility])
     (rf/subscribe [:lipas.ui.sports-sites.floorball.subs/type-codes])
     (rf/subscribe [:lipas.ui.sports-sites.floorball.subs/floor-elasticity])
     (rf/subscribe [:lipas.ui.sports-sites.floorball.subs/player-entrance])
     (rf/subscribe [:lipas.ui.sports-sites.floorball.subs/audience-stand-access])
     (rf/subscribe [:lipas.ui.sports-sites.floorball.subs/car-parking-economics-model])
     (rf/subscribe [:lipas.ui.sports-sites.floorball.subs/roof-trussess-operation-model])
     (rf/subscribe [:lipas.ui.sports-sites.subs/field-types])
     (rf/subscribe [:lipas.ui.map.subs/mode*])])
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

            get-material    #(get-in materials [% locale])
            get-travel-mode #(get-in prop-types/all [:travel-modes :opts % :label locale])

            get-parkour-structure #(get-in prop-types/all [:parkour-hall-equipment-and-structures :opts % :label locale])

            get-boating-service-class #(get-in prop-types/all [:boating-service-class :opts % :label locale])

            get-water-point #(get-in prop-types/all [:water-point :opts % :label locale])

            get-sport-specification #(get-in prop-types/all [:sport-specification :opts % :label locale])]

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
                           (update :training-spot-surface-material get-material)
                           (update :travel-modes #(map get-travel-mode %))
                           (update :parkour-hall-equipment-and-structures #(map get-parkour-structure %))
                           (update :boating-service-class get-boating-service-class)
                           (update :water-point get-water-point)
                           (update :sport-specification get-sport-specification))

           :location
           {:address       (-> latest :location :address)
            :postal-code   (-> latest :location :postal-code)
            :postal-office (-> latest :location :postal-office)
            :city
            {:city-code    (-> latest :location :city :city-code)
             :name         (-> city :name locale)
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
             :locker-rooms  (:locker-rooms latest)
             :audits        (:audits latest)})

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

(rf/reg-sub ::sites-list
  :<- [::latest-sports-site-revs]
  :<- [::cities-by-city-code]
  :<- [::admins]
  :<- [::owners]
  :<- [::active-types]
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

(rf/reg-sub ::new-sports-site
  (fn [db _]
    (-> db :new-sports-site)))

(rf/reg-sub ::adding-new-site?
  :<- [::new-sports-site]
  (fn [new-sports-site _]
    (-> new-sports-site :adding?)))

(rf/reg-sub ::new-site-data
  :<- [::new-sports-site]
  (fn [new-sports-site _]
    (-> new-sports-site :data)))

(rf/reg-sub ::new-site-is-planning
  :<- [::new-sports-site]
  (fn [new-sports-site _]
    (-> new-sports-site :adding-planning-site?)))

(rf/reg-sub ::new-site-type
  :<- [::new-sports-site]
  :<- [::active-types]
  (fn [[new-sports-site types]  _]
    (let [type-code (-> new-sports-site :type)]
      (get types type-code))))

(rf/reg-sub ::new-site-valid?
  :<- [::new-sports-site]
  (fn [new-sports-site _]
    (let [data (-> new-sports-site :data utils/make-saveable)]
      (tap> (s/explain :lipas/new-sports-site data))
      (s/valid? :lipas/new-sports-site data))))

(rf/reg-sub ::statuses
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :statuses)))

(rf/reg-sub ::delete-statuses
  :<- [::statuses]
  (fn [statuses _]
    (select-keys statuses ["out-of-service-temporarily"
                           "out-of-service-permanently"
                           "incorrect-data"])))

(rf/reg-sub ::resurrect-statuses
  :<- [::statuses]
  (fn [statuses _]
    (select-keys statuses ["out-of-service-temporarily"
                           "active"
                           "planned"
                           "planning"])))

(rf/reg-sub ::field-types
  :<- [::sports-sites]
  (fn [sports-sites _]
    (:field-types sports-sites)))

(rf/reg-sub ::delete-dialog-open?
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :delete-dialog :open?)))

(rf/reg-sub ::selected-delete-status
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :delete-dialog :selected-status)))

(rf/reg-sub ::selected-delete-year
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :delete-dialog :selected-year)))

(rf/reg-sub ::sports-site-name-conflict?
  :<- [::sports-sites]
  (fn [sports-sites _]
    (-> sports-sites :name-check :response :status (= :conflict))))

(rf/reg-sub ::city
  :<- [::cities-by-city-code]
  (fn [cities [_ city-code]]
    (get cities city-code)))
