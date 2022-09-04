(ns lipas.backend.core
  (:require
   [buddy.hashers :as hashers]
   [clojure.core.async :as async]
   [clojure.java.jdbc :as jdbc]
   [dk.ative.docjure.spreadsheet :as excel]
   [lipas.backend.accessibility :as accessibility]
   [lipas.backend.analysis.diversity :as diversity]
   [lipas.backend.analysis.reachability :as reachability]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [lipas.backend.gis :as gis]
   [lipas.backend.jwt :as jwt]
   [lipas.backend.search :as search]
   [lipas.data.admins :as admins]
   [lipas.data.cities :as cities]
   [lipas.data.owners :as owners]
   [lipas.data.types :as types]
   [lipas.i18n.core :as i18n]
   [lipas.permissions :as permissions]
   [lipas.reports :as reports]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]))

(def cache "Simple atom cache for things that (hardly) never change."
  (atom {}))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)
(def admins admins/all)
(def owners owners/all)

;;; User ;;;

(defn username-exists? [db user]
  (some? (db/get-user-by-username db user)))

(defn email-exists? [db user]
  (some? (db/get-user-by-email db user)))

(defn add-user! [db user]
  (when (username-exists? db user)
    (throw (ex-info "Username is already in use!"
                    {:type :username-conflict})))

  (when (email-exists? db user)
    (throw (ex-info "Email is already in use!"
                    {:type :email-conflict})))

  (let [defaults {:permissions permissions/default-permissions
                  :status      "active"
                  :username    (:email user)
                  :user-data   {}
                  :password    (str (utils/gen-uuid))}
        user     (-> (merge defaults user)
                     (update :password hashers/encrypt))]

    (db/add-user! db user)
    {:status "OK"}))

(defn- add-user-event!
  ([db user evt-name]
   (add-user-event! db user evt-name {}))
  ([db user evt-name data]
   (let [defaults {:event-date (utils/timestamp) :event evt-name}
         evt      (merge defaults data)
         user     (update-in user [:history :events] conj evt)]
     (db/update-user-history! db user))))

(defn login! [db user]
  (add-user-event! db user "login"))

(defn register! [db emailer user]
  (add-user! db user)
  (email/send-register-notification! emailer
                                     "lipasinfo@jyu.fi"
                                     (dissoc user :password))
  {:status "OK"})

(defn publish-users-drafts! [db {:keys [permissions id] :as user}]
  (let [drafts (->> (db/get-users-drafts db user)
                    (filter (partial permissions/publish? permissions)))]
    (log/info "Publishing" (count drafts) "drafts from user" id)
    (doseq [draft drafts]
      (db/upsert-sports-site! db user (assoc draft :status "active")))))

(defn get-user [db identifier]
  (or (db/get-user-by-email db {:email identifier})
      (db/get-user-by-username db {:username identifier})
      (db/get-user-by-id db {:id identifier})))

(defn get-user! [db identifier]
  (if-let [user (get-user db identifier)]
    user
    (throw (ex-info "User not found."
                    {:type :user-not-found}))))

(defn get-users [db]
  (db/get-users db))

(defn create-magic-link [url user]
  (let [token (jwt/create-token user :terse? true :valid-seconds (* 7 24 60 60))]
    {:link       (str url "?token=" token)
     :valid-days 7}))

(defn- send-permissions-updated-email!
  [emailer login-url {:keys [email] :as user}]
  (let [link (create-magic-link login-url user)]
    (email/send-permissions-updated-email! emailer email link)))

(defn update-user-permissions!
  [db emailer {:keys [id permissions login-url]}]
  (let [user      (get-user! db id)
        old-perms (-> user :permissions)
        new-user  (assoc user :permissions permissions)]
    (db/update-user-permissions! db new-user)
    (publish-users-drafts! db new-user)
    (send-permissions-updated-email! emailer login-url user)
    (add-user-event! db new-user "permissions-updated"
                     {:from old-perms :to permissions})))

(defn update-user-status!
  [db {:keys [id status]}]
  (let [user       (get-user! db id)
        old-status (-> user :status)
        new-user   (assoc user :status status)]
    (db/update-user-status! db new-user)
    (add-user-event! db new-user "status-changed"
                     {:from old-status :to status})
    new-user))

(defn send-password-reset-link! [db emailer {:keys [email reset-url]}]
  (if-let [user (db/get-user-by-email db {:email email})]
    (let [params (create-magic-link reset-url user)]
      (email/send-reset-password-email! emailer email params)
      (add-user-event! db user "password-reset-link-sent"))
    (throw (ex-info "User not found" {:type :email-not-found}))))

(defn send-magic-link! [db emailer {:keys [user login-url variant]}]
  (let [email      (-> user :email)
        magic-link (create-magic-link login-url user)]
    (email/send-magic-login-email! emailer email variant magic-link)
    (add-user-event! db user "magic-link-sent")))

(defn reset-password! [db user password]
  (db/reset-user-password! db (assoc user :password
                                     (hashers/encrypt password)))
  (add-user-event! db user "password-reset"))

(defn get-users-pending-reminders! [db {:keys [id]}]
  (db/get-users-pending-reminders db id))

(defn add-reminder! [db user m]
  (let [m (assoc m :status "pending" :account-id (:id user))]
    (db/add-reminder! db m)))

(defn update-reminder-status! [db user {:keys [id] :as params}]
  (let [exists (->> user
                    (get-users-pending-reminders! db)
                    (map :id)
                    (some #{id}))]

    (when-not exists
      (throw (ex-info "Reminder not found" {:type :reminder-not-found})))

    (db/update-reminder-status! db params)
    {:status "OK"}))

(defn update-user-data!
  [db user user-data]
  (db/update-user-data! db (assoc user :user-data user-data))
  user-data)

;;; Sports-sites ;;;

(defn get-sports-site [db lipas-id]
  (-> (db/get-sports-site db lipas-id)
      not-empty))

(defn- new? [sports-site]
  (nil? (:lipas-id sports-site)))

(defn- check-permissions! [user sports-site draft?]
  (when-not (or draft?
                (new? sports-site)
                (permissions/publish? (:permissions user) sports-site))
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))

(defn- check-sports-site-exists! [db lipas-id]
  (when (empty? (db/get-sports-site db lipas-id))
    (throw (ex-info "Sports site not found"
                    {:type     :sports-site-not-found
                     :lipas-id lipas-id}))))

(defn upsert-sports-site!*
  "Should be used only when data is from trusted sources (migrations
  etc.). Doesn't check users permissions or if lipas-id exists."
  ([db user sports-site]
   (upsert-sports-site!* db user sports-site false))
  ([db user sports-site draft?]
   (db/upsert-sports-site! db user sports-site draft?)))

(defn ensure-permission!
  "Checks if user has access to sports-site and if not explicit access
  is added to users permissions.

  Motivation is to ensures that user who creates the sports-site has
  permission to it."
  [db user {:keys [lipas-id] :as sports-site}]
  (when-not (permissions/publish? (:permissions user) sports-site)
    (let [user (update-in user [:permissions :sports-sites] conj lipas-id)]
      (db/update-user-permissions! db user))))

(defn upsert-sports-site!
  ([db user sports-site]
   (upsert-sports-site! db user sports-site false))
  ([db user sports-site draft?]
   (check-permissions! user sports-site draft?)
   (when-let [lipas-id (:lipas-id sports-site)]
     (check-sports-site-exists! db lipas-id))
   (let [resp (upsert-sports-site!* db user sports-site draft?)]
     (when (new? sports-site)
       (ensure-permission! db user resp))
     resp)))

(defn get-sports-sites-by-type-code
  ([db type-code]
   (get-sports-sites-by-type-code db type-code {}))
  ([db type-code {:keys [locale] :as opts}]
   (let [data (db/get-sports-sites-by-type-code db type-code opts)]
     (if (#{:fi :en :se} locale)
       (map (partial i18n/localize locale) data)
       data))))

(defn get-sports-site-history [db lipas-id]
  (db/get-sports-site-history db lipas-id))

;; ES doesn't support indexing FeatureCollections
(defn feature-coll->geom-coll
  "Transforms GeoJSON FeatureCollection to ElasticSearch
  geometrycollection."
  [{:keys [features]}]
  {:type "geometrycollection"
   :geometries (mapv :geometry features)})

(defn feature-type
  [sports-site]
  (-> sports-site :location :geometries :features first :geometry :type))

;; Elasticsearch doesn't like Polygons with consequent duplicate
;; coordinates so we fix them here. Multimethod was added because
;; there probably will be similar issues with LineStrings once we find
;; them out.
(defmulti fix-geoms feature-type)
(defmethod fix-geoms :default [sports-site] sports-site)
(defmethod fix-geoms "Polygon" [sports-site]
  (update-in sports-site [:location :geometries] gis/dedupe-polygon-coords))

(defn enrich*
  "Enriches sports-site map with :search-meta key where we add data that
  is useful for searching."
  [sports-site]
  (let [sports-site  (fix-geoms sports-site)
        fcoll        (-> sports-site :location :geometries)
        geom         (-> fcoll :features first :geometry)
        start-coords (case (:type geom)
                       "Point"      (-> geom :coordinates)
                       "LineString" (-> geom :coordinates first)
                       "Polygon"    (-> geom :coordinates first first))

        center-coords (-> fcoll gis/centroid :coordinates)

        geom2      (-> fcoll :features last :geometry)
        end-coords (case (:type geom2)
                     "Point"      (-> geom2 :coordinates)
                     "LineString" (-> geom2 :coordinates last)
                     "Polygon"    (-> geom2 :coordinates last last))

        city-code (-> sports-site :location :city :city-code)
        province  (-> city-code cities :province-id cities/provinces)
        avi-area  (-> city-code cities :avi-id cities/avi-areas)

        type-code     (-> sports-site :type :type-code)
        main-category (-> type-code types :main-category types/main-categories)
        sub-category  (-> type-code types :sub-category types/sub-categories)
        search-meta   {:admin {:name (-> sports-site :admin admins)}
                       :owner {:name (-> sports-site :owner owners)}
                       :location
                       {:wgs84-point  start-coords
                        :wgs84-center center-coords
                        :wgs84-end    end-coords
                        :geometries   (feature-coll->geom-coll fcoll)
                        :city         {:name (-> city-code cities :name)}
                        :province     {:name (:name province)}
                        :avi-area     {:name (:name avi-area)}
                        :simple-geoms (let [simplified (gis/simplify fcoll)]
                                        (if (gis/contains-coords? simplified)
                                          simplified
                                          ;; If simplification removes all coords
                                          ;; fallback to original geoms
                                          fcoll))}
                       :type
                       {:name          (-> type-code types :name)
                        :tags          (-> type-code types :tags)
                        :main-category {:name (:name main-category)}
                        :sub-category  {:name (:name sub-category)}}}]
    (assoc sports-site :search-meta search-meta)))

(defn enrich-ice-stadium [{:keys [envelope building] :as ice-stadium}]
  (let [smaterial (-> envelope :base-floor-structure)
        area-m2   (-> building :total-ice-area-m2)]
    (-> ice-stadium
        (cond->
            smaterial (assoc-in [:properties :surface-material] [smaterial])
            area-m2   (assoc-in [:properties :area-m2] area-m2))
        utils/clean
        enrich*)))

(defn enrich-swimming-pool [{:keys [building] :as swimming-pool}]
  (-> swimming-pool
      (assoc-in [:properties :area-m2] (-> building :total-water-area-m2))
      utils/clean
      enrich*))

(defmulti enrich (comp :type-code :type))
(defmethod enrich :default [sports-site] (enrich* sports-site))
(defmethod enrich 2510 [sports-site] (enrich-ice-stadium sports-site))
(defmethod enrich 2520 [sports-site] (enrich-ice-stadium sports-site))
(defmethod enrich 3110 [sports-site] (enrich-swimming-pool sports-site))
(defmethod enrich 3130 [sports-site] (enrich-swimming-pool sports-site))

(defn index!
  ([search sports-site]
   (index! search sports-site false))
  ([search sports-site sync?]
   (let [idx-name "sports_sites_current"]
     (let [data (enrich sports-site)]
       (search/index! search idx-name :lipas-id data sync?)))))

(defn search [search params]
  (let [idx-name "sports_sites_current"]
    (search/search search idx-name params)))

(defn add-to-integration-out-queue! [db sports-site]
  (db/add-to-integration-out-queue! db (:lipas-id sports-site)))

;; TODO refactor upsert-sports-site!, upsert-sports-site!* and
;; save-sports-site! to form more sensible API.
(defn save-sports-site!
  "Saves sports-site to db and search and appends it to outbound
  integrations queue."
  ([db search user sports-site]
   (save-sports-site! db search user sports-site false))
  ([db search user sports-site draft?]
   (jdbc/with-db-transaction [tx db]
     (let [resp (upsert-sports-site! tx user sports-site draft?)]
       (when-not draft?
         (index! search resp :sync)
         (add-to-integration-out-queue! tx resp)
         (diversity/recalc-grid! search (-> resp :location :geometries)))
       resp))))

;;; Cities ;;;

(defn get-cities [db]
  (or
   (:all-cities @cache)
   (->> (db/get-cities db)
        (swap! cache assoc :all-cities)
        :all-cities)))

(defn get-populations [db year]
  (->> (get-cities db)
       (reduce
        (fn [res m]
          (if-let [v (get-in m [:stats year :population])]
            (assoc res (:city-code m) v)
            res))
        {})))

;;; Subsidies ;;;

(defn get-subsidies [db]
  (db/get-subsidies db))

(defn query-subsidies [search params]
  (:body (search/search search "subsidies" params)))

;;; Reports ;;;

(defn energy-report [db type-code year]
  (let [data (get-sports-sites-by-type-code db type-code {:revs year})]
    (reports/energy-report data)))

(defn sports-sites-report [search params fields locale out]
  (let [idx-name  "sports_sites_current"
        in-chan   (search/scroll search idx-name params)
        locale    (or locale :fi)
        headers   (mapv #(get-in reports/fields [% locale]) fields)
        data-chan (async/go
                    (loop [res [headers]]
                      (if-let [page (async/<! in-chan)]
                        (recur (-> page :body :hits :hits
                                   (->>
                                    (map (comp (partial reports/->row fields)
                                               (partial i18n/localize locale)
                                               :_source))
                                    (into res))))
                        res)))]
    (->> (async/<!! data-chan)
         (excel/create-workbook "lipas")
         (excel/save-workbook-into-stream! out))))

(defn finance-report [db {:keys [city-codes]}]
  (let [data (get-cities db)]
    (reports/finance-report city-codes data)))

(defn query-finance-report [search params]
  (:body (search/search search "city_stats" params)))

(defn calculate-stats
  [db search* city-codes type-codes grouping]
  (let [pop-data  (get-populations db 2019)
        statuses  ["active" "out-of-service-temporarily"]
        query     {:size 0,
                   :query
                   {:bool
                    {:filter
                     (into [] (remove nil?)
                           [{:terms {:status.keyword statuses}}
                            (when (not-empty type-codes)
                              {:terms {:type.type-code type-codes}})
                            (when (not-empty city-codes)
                              {:terms {:location.city.city-code city-codes}})])}}
                   :aggs
                   {:grouping
                    {:terms {:field (keyword grouping) :size 400}
                     :aggs
                     {:area_m2_stats   {:stats {:field :properties.area-m2}}
                      :length_km_stats {:stats {:field :properties.route-length-km}}}}}}
        aggs-data (-> (search search* query) :body :aggregations :grouping :buckets)]
    (if (= "location.city.city-code" grouping)
      (reports/calculate-stats-by-city aggs-data pop-data)
      (reports/calculate-stats-by-type aggs-data pop-data city-codes))))

;;; Accessibility register ;;;

(defn get-accessibility-statements [lipas-id]
  (accessibility/get-statements lipas-id))

(defn get-accessibility-app-url [db user lipas-id]
  (when-let [sports-site (get-sports-site db lipas-id)]
    {:url (accessibility/make-app-url user sports-site)}))

;;; Analysis ;;;

(defn search-schools [search params]
  (let [idx-name "schools"]
    (search/search search idx-name params)))

(defn search-population [search params]
  (let [idx-name "vaestoruutu_1km_2019_kp"]
    (search/search search idx-name params)))

(defn calc-distances-and-travel-times [search params]
  (reachability/calc-distances-and-travel-times search params))

(defn create-analysis-report [data out]
  (->> data
       (reachability/create-report)
       (apply excel/create-workbook)
       (excel/save-workbook-into-stream! out)))

(defn calc-diversity-indices [search params]
  (diversity/calc-diversity-indices-2 search params))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (def search2 (search/create-cli (:search config/default-config)))
  (def fields ["lipas-id" "name" "admin" "owner" "properties.surface-material"
               "location.city.city-code"])
  (reset! cache {})
  (:all-cities @cache)

  (let [statuses ["active" "out-of-service-temporarily"]
        grouping "location.city.city-code"
        type-codes [1340]
        city-codes [992]
        query    {:size 0,
                  :query
                  {:bool
                   {:filter
                    (into [] (remove nil?)
                          [{:terms {:status.keyword statuses}}
                           (when (not-empty type-codes)
                             {:terms {:type.type-code type-codes}})
                           (when (not-empty city-codes)
                             {:terms {:location.city.city-code city-codes}})])}}
                  :aggs
                  {:grouping
                   {:terms {:field (keyword grouping) :size 400}
                    :aggs  {:area_m2_stats {:stats {:field :properties.area-m2}}}}}}]
    (search search2 query))

  (flat-finance-report db-spec [992 175] )

  (first (get-cities db-spec))
  (time (get-populations db-spec 2017))
  (time (:all-cities @cache))
  (time (cities-report db-spec [992]))
  (time (m2-per-capita-report db-spec search2 [] [])))
