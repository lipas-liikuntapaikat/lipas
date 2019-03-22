(ns lipas.backend.core
  (:require
   [buddy.hashers :as hashers]
   [clojure.core.async :as async]
   [dk.ative.docjure.spreadsheet :as excel]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [lipas.backend.jwt :as jwt]
   [lipas.backend.search :as search]
   [lipas.data.cities :as cities]
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

;; TODO send email
(defn update-user-permissions! [db emailer {:keys [id permissions]}]
  (let [user      (get-user! db id)
        old-perms (-> user :permissions)
        new-user  (assoc user :permissions permissions)]
    (db/update-user-permissions! db new-user)
    (publish-users-drafts! db new-user)
    (add-user-event! db new-user "permissions-updated"
                     {:from old-perms :to permissions})))

(defn create-magic-link [url user]
  (let [token (jwt/create-token user :terse? true :valid-seconds (* 7 24 60 60))]
    {:link       (str url "?token=" token)
     :valid-days 7}))

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

;;; Sports-sites ;;;

(defn get-sports-site [db lipas-id]
  (-> (db/get-sports-site db lipas-id)
      not-empty))

(defn- check-permissions! [user sports-site draft?]
  (when-not (or draft?
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

(defn upsert-sports-site!
  ([db user sports-site]
   (upsert-sports-site! db user sports-site false))
  ([db user sports-site draft?]
   (check-permissions! user sports-site draft?)
   (when-let [lipas-id (:lipas-id sports-site)]
     (check-sports-site-exists! db lipas-id))
   (upsert-sports-site!* db user sports-site draft?)))

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

(defn enrich*
  "Enriches sports-site map with :search-meta key where we add data that
  is useful for searching."
  [sports-site]
  (let [geom   (-> sports-site :location :geometries :features first :geometry)
        coords (case (:type geom)
                 "Point"      (-> geom :coordinates)
                 "LineString" (-> geom :coordinates first)
                 "Polygon"    (-> geom :coordinates first first))

        city-code (-> sports-site :location :city :city-code)
        province  (-> city-code cities :province-id cities/provinces)
        avi-area  (-> city-code cities :avi-id cities/avi-areas)

        type-code     (-> sports-site :type :type-code)
        main-category (-> type-code types :main-category types/main-categories)
        sub-category  (-> type-code types :sub-category types/sub-categories)
        search-meta   {:location
                       {:wgs84-point coords
                        :city        {:name (-> city-code cities :name)}
                        :province    {:name (:name province)}
                        :avi-area    {:name (:name avi-area)}}
                       :type
                       {:name          (-> type-code types :name)
                        :tags          (-> type-code types :tags)
                        :main-category {:name (:name main-category)}
                        :sub-category  {:name (:name sub-category)}}}]
    (assoc sports-site :search-meta search-meta)))

(defn enrich-ice-stadium [{:keys [envelope building] :as ice-stadium}]
  (-> ice-stadium
      (assoc-in [:properties :surface-material] [(-> envelope :base-floor-structure)])
      (assoc-in [:properties :area-m2] (-> building :total-ice-area-m2))
      utils/clean
      enrich*))

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

;;; Reports ;;;

(defn energy-report [db type-code year]
  (let [data (get-sports-sites-by-type-code db type-code {:revs year})]
    (reports/energy-report data)))

;; TODO support :se and :en locales
(defn sports-sites-report [search params fields out]
  (let [idx-name  "sports_sites_current"
        in-chan   (search/scroll search idx-name params)
        locale    :fi
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

(defn finance-report [db city-codes]
  (let [data (get-cities db)]
    (reports/finance-report city-codes data)))

(defn calculate-stats
  [db search* city-codes type-codes grouping]
  (let [pop-data (get-populations db 2017)
        statuses ["active" "out-of-service-temporarily"]
        query    {:size 0,
                  :query
                  {:bool
                   {:filter
                    (into [] (remove nil?)
                          [{:terms {:status.keyword statuses}}
                           (when (not-empty type-codes)
                             {:terms {:type.type-code type-codes}})
                           (when (not-empty city-codes)
                             {:terms {:location.city.city-code city-codes}})])}},
                  :aggs
                  {:grouping
                   {:terms {:field (keyword grouping), :size 400},
                    :aggs  {:area_m2_sum {:sum {:field :properties.area-m2}}}}}}
        m2-data  (-> (search search* query) :body :aggregations :grouping :buckets)]
    (if (= "location.city.city-code" grouping)
      (reports/calculate-stats-by-city m2-data pop-data)
      (reports/calculate-stats-by-type m2-data pop-data city-codes))))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (def search2 (search/create-cli (:search config/default-config)))
  (def fields ["lipas-id" "name" "admin" "owner" "properties.surface-material"
               "location.city.city-code"])
  (reset! cache {})
  (:all-cities @cache)

  (first (get-cities db-spec))
  (time (get-populations db-spec 2017))
  (time (:all-cities @cache))
  (time (cities-report db-spec [992]))
  (time (m2-per-capita-report db-spec search2 [] [])))
