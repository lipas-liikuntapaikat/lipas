(ns lipas.backend.core
  (:require [buddy.hashers :as hashers]
            [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as excel]
            [lipas.backend.accessibility :as accessibility]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.analysis.reachability :as reachability]
            [lipas.backend.db.db :as db]
            [lipas.backend.email :as email]
            [lipas.backend.gis :as gis]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.newsletter :as newsletter]
            [lipas.backend.s3 :as s3]
            [lipas.backend.search :as search]
            [lipas.data-model-export :as data-model-export]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.owners :as owners]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.i18n.core :as i18n]
            [lipas.integration.utp.cms :as utp-cms]
            [lipas.jobs.core :as jobs]
            [lipas.reports :as reports]
            [lipas.roles :as roles]
            [lipas.utils :as utils]
            [taoensso.timbre :as log])
  (:import [java.io OutputStreamWriter]))

(def cache "Simple atom cache for things that (hardly) never change."
  (atom {}))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)
(def admins admins/all)
(def owners owners/all)

;;; Jobs ;;;

(defn get-job-admin-metrics
  "Get comprehensive job queue metrics for admin dashboard."
  [db opts]
  (jobs/get-admin-metrics db opts))

(defn get-job-queue-health
  "Get current job queue health for admin monitoring."
  [db]
  (jobs/get-queue-health db))

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

  (let [defaults {:permissions {:roles []}
                  :status "active"
                  :username (:email user)
                  :user-data {}
                  :password (str (utils/gen-uuid))}
        user (-> (merge defaults user)
                 (update :password hashers/encrypt))]

    (db/add-user! db user)
    {:status "OK"}))

(defn- add-user-event!
  ([db user evt-name]
   (add-user-event! db user evt-name {}))
  ([db user evt-name data]
   (let [user (db/get-user-by-id db user)
         defaults {:event-date (utils/timestamp) :event evt-name}
         evt (merge defaults data)
         user (update-in user [:history :events] conj evt)]
     (db/update-user-history! db user))))

(defn login! [db user]
  (add-user-event! db user "login"))

(defn register! [db emailer user]
  (add-user! db user)
  (email/send-register-notification! emailer
                                     "lipasinfo@jyu.fi"
                                     (dissoc user :password))
  {:status "OK"})

(defn publish-users-drafts! [db {:keys [id] :as user}]
  (let [drafts (->> (db/get-users-drafts db user)
                    (filter (fn [draft]
                              (roles/check-privilege user (roles/site-roles-context draft) :site/create-edit))))]
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
    {:link (str url "?token=" token)
     :valid-days 7}))

(defn- send-permissions-updated-email!
  [emailer login-url {:keys [email] :as user}]
  (let [link (create-magic-link login-url user)]
    (email/send-permissions-updated-email! emailer email link)))

(defn update-user-permissions!
  [db emailer {:keys [id permissions login-url]}]
  (let [user (get-user! db id)
        old-perms (-> user :permissions)
        new-user (assoc user :permissions permissions)]
    (db/update-user-permissions! db new-user)
    (publish-users-drafts! db new-user)
    (send-permissions-updated-email! emailer login-url user)
    (add-user-event! db new-user "permissions-updated"
                     {:from old-perms :to permissions})))

(defn update-user-status!
  [db {:keys [id status] :as user}]
  (let [user (db/get-user-by-id db user)
        old-status (-> user :status)
        new-user (assoc user :status status)]
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
  (let [email (-> user :email)
        magic-link (create-magic-link login-url user)]
    (email/send-magic-login-email! emailer email variant magic-link)
    (add-user-event! db user "magic-link-sent")))

(defn reset-password! [db user password]
  (db/reset-user-password! db (assoc user :password
                                     (hashers/encrypt password)))
  (add-user-event! db user "password-reset"))

;;; Reminders ;;;

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

(defn gdpr-remove-user!
  "Removes personal data associated with the user and archives the user."
  [db user]
  (jdbc/with-db-transaction [tx db]
    (let [username (str "gdpr_removed_" (java.util.UUID/randomUUID))
          email (str username "@lipas.fi")]
      (db/update-user-username! tx (assoc user :username username))
      (db/update-user-email! tx (assoc user :email email))
      (update-user-data! tx user {})
      (add-user-event! tx user "GDPR removal")
      (update-user-status! tx (assoc user :status "archived"))))
  {:status "OK"})

(defn gdpr-remove?
  "User data is removed if the user has been inactive for > 5 years."
  [now {:keys [created-at history] :as user}]
  (let [created-at+5y (-> (.toInstant created-at)
                          (.atZone (java.time.ZoneId/of "UTC"))
                          (.plus 5 java.time.temporal.ChronoUnit/YEARS)
                          (.toInstant))]
    (and (not (str/ends-with? (:email user) "@lipas.fi"))
         (.isAfter now created-at+5y)
         (let [last-event (->> history :events (map :event-date) (sort utils/reverse-cmp) first)]
           (or (nil? last-event)
               (let [last-event+5y (-> (java.time.Instant/parse last-event)
                                       (.atZone (java.time.ZoneId/of "UTC"))
                                       (.plus 5 java.time.temporal.ChronoUnit/YEARS)
                                       (.toInstant))]
                 (.isAfter now last-event+5y)))))))

(defn process-gdpr-removals!
  [db]
  (let [now (java.time.Instant/now)
        users (->> (get-users db)
                   (filter (partial gdpr-remove? now)))]
    (log/info "Found" (count users) "users to GDPR remove.")

    (doseq [user users]
      (log/info "GDPR removing user" (:id user))
      (log/info (:created-at user)
                (->> user :history :events (map :event-date) (sort utils/reverse-cmp) first))
      #_(gdpr-remove-user! db user))

    (log/info "GDPR removals DONE!")))

;;; Sports-sites ;;;

(defn- deref-fids
  [sports-site route]
  (let [fids (-> route :fids set)
        geoms (when (seq fids)
                {:type "FeatureCollection"
                 :features (->> (get-in sports-site [:location :geometries :features])
                                (filterv #(contains? fids (:id %))))})]
    (if geoms
      (assoc route :geometries geoms)
      route)))

(defn enrich-activities
  [sports-site]
  (if (:activities sports-site)
    (update sports-site :activities
            (fn [activities]
              (reduce-kv (fn [m k v]
                           (if (and (get-in m [k :routes])
                                    (get-in sports-site [:activities k :routes]))
                             (update-in m [k :routes] (fn [routes]
                                                        (mapv #(deref-fids sports-site %) routes)))
                             m))
                         activities
                         activities)))
    sports-site))

(defn get-sports-site
  ([db lipas-id] (get-sports-site db lipas-id :none))
  ([db lipas-id locale]
   (let [m (-> (db/get-sports-site db lipas-id)
               (enrich-activities))]
     (cond
       (#{:fi :en :se} locale) (i18n/localize locale m)
       (#{:all} locale) (i18n/localize2 [:fi :se :en] m)
       :else m))))

(defn get-sports-site2
  ([search lipas-id] (get-sports-site2 search lipas-id :none))
  ([{:keys [client indices]} lipas-id locale]
   (let [idx (get-in indices [:sports-site :search])
         doc (try
               (search/fetch-document client idx lipas-id)
               (catch Exception ex
                 (if (= 404 (-> ex ex-data :status))
                   nil
                   (throw ex))))
         m (some-> doc
                   (get-in [:body :_source])
                   (enrich-activities))]
     (cond
       (nil? m) m
       (#{:fi :en :se} locale) (i18n/localize locale m)
       (#{:all} locale) (i18n/localize2 [:fi :se :en] m)
       :else m))))

(defn- new? [sports-site]
  (nil? (:lipas-id sports-site)))

(defn- check-permissions! [user sports-site draft?]
  (when-not (or draft?
                (new? sports-site)
                (roles/check-privilege user (roles/site-roles-context sports-site) :site/save-api))
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))

(defn- check-sports-site-exists! [db lipas-id]
  (when (empty? (db/get-sports-site db lipas-id))
    (throw (ex-info "Sports site not found"
                    {:type :sports-site-not-found
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
  [db user {:keys [lipas-id status] :as sports-site}]
  (let [regular-permission (roles/check-privilege user (roles/site-roles-context sports-site) :site/create-edit)
        is-planning (= "planning" status)
        planning-permission (and is-planning (roles/check-privilege user {} :analysis-tool/use))]
    (when (and (not regular-permission)
               (not planning-permission))
      (let [user (update-in user [:permissions :roles]
                            (fnil conj [])
                            {:role :site-manager
                             :lipas-id #{lipas-id}})]
        (db/update-user-permissions! db user)))))

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
   (let [data (->> (db/get-sports-sites-by-type-code db type-code opts)
                   (map enrich-activities))]
     (cond
       (#{:fi :en :se} locale) (map (partial i18n/localize locale) data)
       (#{:all} locale) (map (partial i18n/localize2 [:fi :se :en]) data)
       :else data))))

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
  (let [sports-site (fix-geoms sports-site)
        fcoll (-> sports-site :location :geometries)
        geom (-> fcoll :features first :geometry)
        start-coords (case (:type geom)
                       "Point" (-> geom :coordinates)
                       "LineString" (-> geom :coordinates first)
                       "Polygon" (-> geom :coordinates first first))

        center-coords (try (-> fcoll gis/centroid :coordinates)
                           (catch Exception ex
                             (log/warn ex "Failed to calc centroid for lipas-id"
                                       (:lipas-id sports-site) "fcoll" fcoll)
                             nil))

        geom2 (-> fcoll :features last :geometry)
        end-coords (case (:type geom2)
                     "Point" (-> geom2 :coordinates)
                     "LineString" (-> geom2 :coordinates last)
                     "Polygon" (-> geom2 :coordinates last last))

        city-code (-> sports-site :location :city :city-code)
        province (-> city-code cities :province-id cities/provinces)
        avi-area (-> city-code cities :avi-id cities/avi-areas)

        type-code (-> sports-site :type :type-code)
        main-category (-> type-code types :main-category types/main-categories)
        sub-category (-> type-code types :sub-category types/sub-categories)
        field-types (->> sports-site :fields (map :type) distinct)
        latest-audit (some-> sports-site
                             :audits
                             (->> (sort-by :audit-date utils/reverse-cmp))
                             first
                             :audit-date)
        search-meta {:name (utils/->sortable-name (:name sports-site))
                     :admin {:name (-> sports-site :admin admins)}
                     :owner {:name (-> sports-site :owner owners)}
                     :audits {:latest-audit-date latest-audit}
                     :location
                     {:wgs84-point start-coords
                      :wgs84-center center-coords
                      :wgs84-end end-coords
                      :geometries (feature-coll->geom-coll (gis/strip-z-fcoll fcoll))
                      :city {:name (-> city-code cities :name)}
                      :province {:name (:name province)}
                      :avi-area {:name (:name avi-area)}
                      :simple-geoms (gis/simplify-safe fcoll)}
                     :type
                     {:name (-> type-code types :name)
                      :tags (-> type-code types :tags)
                      :main-category {:name (:name main-category)}
                      :sub-category {:name (:name sub-category)}}
                     :fields
                     {:field-types field-types}}]
    (assoc sports-site :search-meta search-meta)))

#_(defn enrich-ice-stadium [{:keys [envelope building] :as ice-stadium}]
    (let [smaterial (-> envelope :base-floor-structure)
          area-m2 (-> building :total-ice-area-m2)]
      (-> ice-stadium
          (cond->
           smaterial (assoc-in [:properties :surface-material] [smaterial])
           area-m2 (assoc-in [:properties :area-m2] area-m2))
          utils/clean
          enrich*)))

#_(defn enrich-swimming-pool [{:keys [building] :as swimming-pool}]
    (-> swimming-pool
        (assoc-in [:properties :area-m2] (-> building :total-water-area-m2))
        utils/clean
        enrich*))

(defn enrich-cycling-route [sports-site]
  (-> sports-site
      (update-in [:location :geometries] gis/sequence-features)
      enrich*))

(defmulti enrich (comp :type-code :type))
(defmethod enrich :default [sports-site] (enrich* sports-site))
(defmethod enrich 4412 [sports-site] (enrich-cycling-route sports-site))
#_(defmethod enrich 2510 [sports-site] (enrich-ice-stadium sports-site))
#_(defmethod enrich 2520 [sports-site] (enrich-ice-stadium sports-site))
#_(defmethod enrich 3110 [sports-site] (enrich-swimming-pool sports-site))
#_(defmethod enrich 3130 [sports-site] (enrich-swimming-pool sports-site))

(defn index!
  ([search sports-site]
   (index! search sports-site false))
  ([{:keys [indices client]} sports-site sync?]
   (let [idx-name (get-in indices [:sports-site :search])
         data (enrich sports-site)]
     (search/index! client idx-name :lipas-id data sync?))))

(defn search
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:sports-site :search])]
    (search/search client idx-name params)))

(defn search-fields
  [{:keys [indices client]}
   {:keys [field-types]}]
  (let [idx-name (get-in indices [:sports-site :search])
        params {:size 1000
                :track_total_hits 50000
                :_source
                {:excludes ["search-meta.*"]}
                :query
                {:bool
                 {:must [{:terms {:status.keyword ["active" "out-of-service-temporarily"]}}
                         {:terms {:search-meta.fields.field-types.keyword field-types}}]}}}]
    (-> (search/search client idx-name params)
        :body
        :hits
        :hits
        (->> (map :_source)))))

(defn add-to-integration-out-queue!
  "DEPRECATED: Integration system is being phased out."
  [db sports-site]
  (db/add-to-integration-out-queue! db (:lipas-id sports-site)))

(defn sync-ptv! [tx search ptv-component user props]
  (let [f (resolve 'lipas.backend.ptv.core/sync-ptv!)]
    (f tx search ptv-component user props)))

;; TODO refactor upsert-sports-site!, upsert-sports-site!* and
;; save-sports-site! to form more sensible API.
(defn save-sports-site!
  "Saves sports-site to db and search and appends it to outbound
  integrations queue."
  ([db search ptv user sports-site]
   (save-sports-site! db search ptv user sports-site false))
  ([db search ptv user sports-site draft?]
   (let [correlation-id (jobs/gen-correlation-id)]
     (jobs/with-correlation-context correlation-id
       (fn []
         (jdbc/with-db-transaction [tx db]
           (let [resp (upsert-sports-site! tx user sports-site draft?)
                 route? (-> resp :type :type-code types/all :geometry-type #{"LineString"})]

             (when-not draft?
               (log/info "Saving sports site with background jobs"
                         {:lipas-id (:lipas-id resp)
                          :is-route? route?
                          :user (:email user)})

               ;; NOTE: routes will be re-indexed after elevation has been
               ;; resolved.
               (index! search resp :sync)

               (when route?
                 (jobs/enqueue-job! tx "elevation"
                                    {:lipas-id (:lipas-id resp)}
                                    {:correlation-id correlation-id
                                     :priority 70}))

               (when-not route?
                 ;; Routes will be integrated only after elevation has been
                 ;; resolved. See `process-elevation-queue!`
                 ;; NOTE: Integration system is being phased out, keeping for now
                 (add-to-integration-out-queue! tx resp))

               ;; Analysis doesn't require elevation information
               (jobs/enqueue-job! tx "analysis"
                                  {:lipas-id (:lipas-id resp)}
                                  {:correlation-id correlation-id
                                   :priority 80})

               ;; Webhook Notification

               ;; NOTE: Webhook is disabled until UTP or someone else
               ;; starts using it again.

               #_(jobs/enqueue-job! tx "webhook"
                                  {:lipas-ids [(:lipas-id resp)]
                                   :operation-type (if (new? sports-site) "create" "update")
                                   :initiated-by (:id user)}
                                  {:correlation-id correlation-id
                                   :priority 85}))

             ;; Sync the site to PTV if
             ;; - it was previously sent to PTV (we might archive it now if it no longer looks like PTV candidate)
             ;; - it is PTV candidate now
             ;; - do nothing (keep the previous data in PTV if site was previously sent there) if sync-enabled is false
             ;; Note: if site status or something is updated in Lipas, so that the site is no longer candidate,
             ;; that doesn't trigger update if sync-enabled is false.
             (if (and (not draft?)
                      (or (:sync-enabled (:ptv resp))
                          (:delete-existing (:ptv resp)))
                      ;; TODO: Check privilage :ptv/basic or such
                      (or (ptv-data/ptv-candidate? resp)
                          (ptv-data/is-sent-to-ptv? resp)))
               ;; NOTE:  this will create a new sports-site rev.
               ;; Make it instead update the sports-site already created in the tx?
               ;; Otherwise each save-sports-site! will create two sports-site revs.
               (let [new-ptv-data (sync-ptv! tx search ptv user
                                             {:sports-site resp
                                              :org-id (:org-id (:ptv resp))
                                              :lipas-id (:lipas-id resp)
                                              :ptv (:ptv resp)})]
                 (log/infof "Sports site updated and PTV integration enabled")
                 (assoc resp :ptv new-ptv-data))
               resp))))))))

;;; Cities ;;;

(defn get-cities
  ([db]
   (get-cities db false))
  ([db no-cache]
   (or
    (and (not no-cache) (:all-cities @cache))
    (->> (db/get-cities db)
         (swap! cache assoc :all-cities)
         :all-cities))))

(defn get-populations
  [{:keys [indices client]} year]
  (let [idx (get-in indices [:report :city-stats])]
    (-> (search/search client idx {:size 500 ;; Finland has ~300 cities
                                   :_source {:includes ["city-code" "population" "year"]}
                                   :query
                                   {:terms {:year [year]}}})
        (get-in [:body :hits :hits])
        (->> (map :_source)
             (utils/index-by :city-code))
        (update-vals :population))))

;;; Subsidies ;;;

(defn get-subsidies [db]
  (db/get-subsidies db))

(defn query-subsidies
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:report :subsidies])]
    (:body (search/search client idx-name params))))

;;; Reports ;;;

(defn energy-report [db type-code year]
  (let [data (get-sports-sites-by-type-code db type-code {:revs year})]
    (reports/energy-report data)))

(defn sports-sites-report-excel
  [{:keys [indices client]} params fields locale out]
  (let [idx-name (get-in indices [:sports-site :search])
        in-chan (search/scroll client idx-name params)
        locale (or locale :fi)
        headers (mapv #(get-in reports/fields [% locale]) fields)
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

(defn sports-sites-report-geojson
  [{:keys [indices client]} params fields locale out]
  (let [idx-name (get-in indices [:sports-site :search])
        in-chan (search/scroll client idx-name (update params :_source dissoc :excludes))
        locale (or locale :fi)
        headers (mapv #(get-in reports/fields [% locale]) fields)
        localize (partial i18n/localize locale)
        ->row (partial reports/->row fields)]
    (with-open [writer (OutputStreamWriter. out)]
      (.write writer "{\"type\":\"FeatureCollection\",\"features\":[")
      (loop [page-num 0]
        (when-let [page (async/<!! in-chan)]
          (let [ms (-> page :body :hits :hits)
                feats (mapcat
                       (fn [m]
                         (let [props (-> m
                                         :_source
                                         localize
                                         ->row
                                         (->> (zipmap headers)))]
                           (->> m :_source :location :geometries :features
                                (map (fn [f] (assoc f :properties props))))))
                       ms)]
            (loop [feat-num 0
                   f (first feats)
                   fs (rest feats)]
              (.write writer (str (when-not (= 0 page-num feat-num) ",")
                                  (json/encode f)))
              (when-let [next-f (first fs)]
                (recur (inc feat-num) next-f (rest fs)))))
          (recur (inc page-num))))
      (.write writer "]}"))))

(defn sports-sites-report-csv
  [{:keys [indices client]} params fields locale out]
  (let [idx-name (get-in indices [:sports-site :search])
        in-chan (search/scroll client idx-name params)
        locale (or locale :fi)
        headers (mapv #(get-in reports/fields [% locale]) fields)
        xform (comp (partial reports/->row fields)
                    (partial i18n/localize locale)
                    :_source)]
    (with-open [writer (OutputStreamWriter. out)]
      (csv/write-csv writer [headers])
      (loop []
        (when-let [page (async/<!! in-chan)]
          (let [ms (-> page :body :hits :hits)]
            (csv/write-csv writer (map xform ms)))
          (recur))))))

(defn finance-report [db {:keys [city-codes]}]
  (let [data (get-cities db)]
    (reports/finance-report city-codes data)))

(defn query-finance-report
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:report :city-stats])]
    (:body (search/search client idx-name params))))

(defn calculate-stats
  [db search* {:keys [city-codes type-codes grouping year]
               :or {grouping "location.city.city-code"}}]
  (let [pop-data (get-populations search* year)
        statuses ["active" "out-of-service-temporarily"]
        query {:size 0,
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
                 {:area_m2_stats {:stats {:field :properties.area-m2}}
                  :length_km_stats {:stats {:field :properties.route-length-km}}}}}}
        aggs-data (-> (search search* query) :body :aggregations :grouping :buckets)]
    (if (= "location.city.city-code" grouping)
      (reports/calculate-stats-by-city aggs-data pop-data)
      (reports/calculate-stats-by-type aggs-data pop-data city-codes))))

(defn data-model-report
  [out]
  (data-model-export/create-excel out))

;;; Accessibility register ;;;

(defn get-accessibility-statements [lipas-id]
  (accessibility/get-statements lipas-id))

(defn get-accessibility-app-url [db user lipas-id]
  (when-let [sports-site (get-sports-site db lipas-id)]
    {:url (accessibility/make-app-url user sports-site)}))

;;; Analysis ;;;

(defn search-schools
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:analysis :schools])]
    (search/search client idx-name params)))

(defn search-population
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:analysis :population])]
    (search/search client idx-name params)))

(defn calc-distances-and-travel-times [search params]
  (reachability/calc-distances-and-travel-times search params))

(defn create-analysis-report [data out]
  (->> data
       (reachability/create-report)
       (apply excel/create-workbook)
       (excel/save-workbook-into-stream! out)))

(defn calc-diversity-indices [search params]
  (diversity/calc-diversity-indices-2 search params))

;;; Newsletter ;;;

(defn get-newsletter [config]
  (newsletter/retrieve config))

(defn subscribe-newsletter [config user]
  (newsletter/subscribe config user))

(defn send-feedback! [emailer feedback]
  (email/send-feedback-email! emailer "lipasinfo@jyu.fi" feedback))

(defn check-sports-site-name [search-cli {:keys [lipas-id name]}]
  (let [query {:size 1
               :_source {:includes ["lipas-id" "name" "status"]}
               :query
               {:bool
                {:must [{:match_phrase {:name.keyword name}}
                        {:terms {:status.keyword ["active" "out-of-service-temporarily"]}}]
                 :must_not {:term {:lipas-id lipas-id}}}}}
        resp (search search-cli query)]
    (merge
     {:status (if (-> resp :body :hits :total :value (>= 1))
                :conflict
                :ok)}
     (when-let [conflict (-> resp :body :hits :hits first :_source)]
       {:conflict conflict}))))

(defn presign-upload-url
  [{:keys [s3-bucket s3-bucket-prefix region credentials-provider]}
   {:keys [lipas-id user extension]}]
  (let [k (str s3-bucket-prefix
               "/"
               lipas-id
               "/"
               (utils/gen-uuid)
               "."
               extension)]
    (s3/presign-put {:region region
                     :bucket s3-bucket
                     :content-type (str "image/" extension)
                     :object-key k
                     :meta {:lipas-id lipas-id
                            :user-id (:id user)}
                     :credentials-provider credentials-provider})))

;;; LOI ;;;

(defn ->lois-es-query
  [{:keys [location loi-statuses]}]
  (let [lon (:lon location)
        lat (:lat location)
        distance (:distance location)
        origin (str lat "," lon)
        decay-factor 2
        offset (str (* distance (* decay-factor 0.5)) "m")
        scale (str (* distance decay-factor) "m")
        size 100
        from 0
        excludes ["search-meta"]
        query {:size size
               :from from
               :sort ["_score"]
               :_source {:excludes excludes}
               :query {:function_score
                       {:score_mode "max"
                        :boost_mode "max"
                        :functions [{:exp
                                     {:search-meta.location.wgs84-point
                                      {:origin origin
                                       :offset offset
                                       :scale scale}}}]
                        :query {:bool
                                {:filter
                                 [{:terms {:status.keyword loi-statuses}}]}}}}}
        default-query {:size size :query {:match_all {}}}]
    (if (and lat lon distance)
      query
      default-query)))

(defn search-lois*
  [{:keys [indices client]} es-query]
  (let [idx-name (get-in indices [:lois :search])]
    (search/search client idx-name es-query)))

(defn search-lois
  [search es-query]
  (-> (search-lois* search es-query)
      :body
      :hits
      :hits
      (->> (map :_source))))

(defn get-loi
  [{:keys [indices client]} loi-id]
  (let [idx-name (get-in indices [:lois :search])]
    (-> (search/fetch-document client idx-name loi-id)
        :body
        :_source
        (dissoc :search-meta))))

(defn enrich-loi
  [{:keys [geometries] :as loi}]
  (let [geom-coll (feature-coll->geom-coll geometries)]
    (-> loi
        (assoc-in [:search-meta :location :geometries] geom-coll)
        (assoc-in [:search-meta :location :wgs84-point] (-> (gis/->flat-coords geometries)
                                                            first)))))

(defn search-lois-with-params
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:lois :search])
        es-query (->lois-es-query params)]
    (-> (search/search client idx-name es-query)
        :body
        :hits
        :hits)))

(defn index-loi!
  ([search loi]
   (index-loi! search loi false))
  ([{:keys [indices client]} loi sync?]
   (let [idx-name (get-in indices [:lois :search])
         loi (enrich-loi loi)]
     (search/index! client idx-name :id loi sync?))))

(defn upsert-loi!
  [db search user loi]
  (let [correlation-id (jobs/gen-correlation-id)]
    (jdbc/with-db-transaction [tx db]
      (let [result (db/upsert-loi! tx user loi)]
        (log/info "Saving LOI with background jobs"
                  {:loi-id (:id loi)
                   :user (:email user)})

        ;; Enqueue webhook with same correlation ID
        (jobs/enqueue-job! tx "webhook"
                           {:loi-ids [(:id loi)]
                            :operation-type (if (nil? (:id loi)) "create" "update")
                            :initiated-by (:id user)}
                           {:correlation-id correlation-id
                            :priority 85})
        (index-loi! search loi :sync)
        result))))

(defn upload-utp-image!
  [{:keys [_filename _data _user] :as params}]
  (utp-cms/upload-image! params))

;;; Types ;;;

(defn get-categories
  []
  (map types/->type (vals types/active)))

(defn get-category
  [type-code]
  (types/->type (types/active type-code)))

;;; Help ;;;

(defn get-help-data
  [db]
  (db/get-versioned-data db "help" "active"))

(defn save-help-data
  [db help-data]
  (db/add-versioned-data! db "help" "active" help-data))

(comment
  (get-categories)
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (def search2 {:client (search/create-cli (:search config/default-config))
                :indices (-> config/default-config :search :indices)})
  (def fields ["lipas-id" "name" "admin" "owner" "properties.surface-material"
               "location.city.city-code"])
  (reset! cache {})
  (:all-cities @cache)

  (check-sports-site-name search2 {:lipas-id 89212 :name "Tapanilan Urheilukeskus / Salibandyhalli"})

  (check-sports-site-name search2 {:lipas-id 89211 :name "Tapanilan Urheilukeskus / "})
  (check-sports-site-name search2 {:lipas-id 0 :name "Kirkonkylän kaukalo"})

  (search-fields search2 {:field-types ["floorball-field"]})

  (def results (atom []))

  (async/go
    (let [ch (search/scroll search2 "sports_sites_current" {:query
                                                            {:term {:type.type-code 1170}}})]
      (loop []
        (when-let [page (async/<! ch)]
          ;; todo GeoJSON writing to stream
          (swap! results conj page)
          (recur)))))

  @results
  (count @results)

  (let [statuses ["active" "out-of-service-temporarily"]
        grouping "location.city.city-code"
        type-codes [1340]
        city-codes [992]
        query {:size 0,
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
                 :aggs {:area_m2_stats {:stats {:field "properties.area-m2"}}}}}}]
    (search search2 query))

  #_(flat-finance-report db-spec [992 175])

  (process-elevation-queue! db-spec search2)
  search2
  (first (get-cities db-spec))
  (time (get-populations db-spec 2017))
  (time (:all-cities @cache))
  (time (cities-report db-spec [992]))
  (time (m2-per-capita-report db-spec search2 [] [])))
