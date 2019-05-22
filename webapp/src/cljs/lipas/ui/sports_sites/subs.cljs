(ns lipas.ui.sports-sites.subs
  (:require
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
 (fn [db [_ lipas-id]]
   (get-in db [:sports-sites lipas-id])))

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
 (fn [db [_ lipas-id]]
   (let [latest (get-in db [:sports-sites lipas-id :latest])]
     (get-in db [:sports-sites lipas-id :history latest]))))

(re-frame/reg-sub
 ::editing-rev
 (fn [db [_ lipas-id]]
   (get-in db [:sports-sites lipas-id :editing])))

(re-frame/reg-sub
 ::editing?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::editing-rev lipas-id]))
 (fn [edit-data _]
   ((complement empty?) edit-data)))

(re-frame/reg-sub
 ::editing-allowed?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::latest-rev lipas-id]))
 (fn [rev _]
   (->> rev :status #{"active" "out-of-service-temporarily"})))

(defn- valid? [sports-site]
  (let [spec (case (-> sports-site :type :type-code)
               (3110 3120 3130) :lipas.sports-site/swimming-pool
               (2510 2520)      :lipas.sports-site/ice-stadium
               :lipas/sports-site)]
    (as-> sports-site $
      (utils/make-saveable $)
      ;; (do (s/explain spec $) $)
      (s/valid? spec $))))

(re-frame/reg-sub
 ::edits-valid?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::editing-rev lipas-id]))
 (fn [edit-data _]
   (valid? edit-data)))

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
 ::admins
 (fn [db _]
   (-> db :sports-sites :admins)))

(re-frame/reg-sub
 ::owners
 (fn [db _]
   (-> db :sports-sites :owners)))

(re-frame/reg-sub
 ::all-types
 (fn [db _]
   (-> db :sports-sites :types)))

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
   [(re-frame/subscribe [::types-by-geom-type geom-type])
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
 (fn [db _]
   (-> db :sports-sites :materials)))

(re-frame/reg-sub
 ::building-materials
 (fn [db _]
   (-> db :sports-sites :building-materials)))

(re-frame/reg-sub
 ::supporting-structures
 (fn [db _]
   (-> db :sports-sites :supporting-structures)))

(re-frame/reg-sub
 ::ceiling-structures
 (fn [db _]
   (-> db :sports-sites :ceiling-structures)))

(re-frame/reg-sub
 ::surface-materials
 (fn [db _]
   (-> db :sports-sites :surface-materials)))

(re-frame/reg-sub
 ::prop-types
 (fn [db _]
   (-> db :sports-sites :prop-types)))

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
    (re-frame/subscribe [:lipas.ui.subs/translator])])
 (fn [[site cities admins owners types size-categories
       materials statuses translator] _]
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

       {:status            (-> status locale)
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

        :building (:building latest)}))))

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
 ::adding-new-site?
 (fn [db _]
   (-> db :new-sports-site :adding?)))

(re-frame/reg-sub
 ::new-site-data
 (fn [db _]
   (-> db :new-sports-site :data)))

(re-frame/reg-sub
 ::new-site-type
 (fn [db _]
   (let [type-code (-> db :new-sports-site :type)]
     (get-in db [:sports-sites :types type-code]))))

(re-frame/reg-sub
 ::new-site-valid?
 (fn [db _]
   (let [data (-> db :new-sports-site :data)]
     ;; (s/explain :lipas/new-sports-site data)
     (s/valid? :lipas/new-sports-site data))))

(re-frame/reg-sub
 ::statuses
 (fn [db _]
   (-> db :sports-sites :statuses)))

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
   (select-keys statuses ["out-of-service-temporarily" "active"])))

(re-frame/reg-sub
 ::delete-dialog-open?
 (fn [db _]
   (-> db :sports-sites :delete-dialog :open?)))

(re-frame/reg-sub
 ::selected-delete-status
 (fn [db _]
   (-> db :sports-sites :delete-dialog :selected-status)))

(re-frame/reg-sub
 ::selected-delete-year
 (fn [db _]
   (-> db :sports-sites :delete-dialog :selected-year)))
