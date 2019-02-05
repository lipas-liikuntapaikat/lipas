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

;; TODO send email
(defn update-user-permissions! [db emailer user]
  (db/update-user-permissions! db user)
  (publish-users-drafts! db user))

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

(defn send-password-reset-link! [db emailer {:keys [email reset-url]}]
  (if-let [user (db/get-user-by-email db {:email email})]
    (let [params (create-magic-link reset-url user)]
      (email/send-reset-password-email! emailer email params))
    (throw (ex-info "User not found" {:type :email-not-found}))))

(defn send-magic-link! [db emailer {:keys [user login-url variant]}]
  (let [email      (-> user :email)
        magic-link (create-magic-link login-url user)]
    (email/send-magic-login-email! emailer email variant magic-link)))

(defn reset-password! [db user password]
  (db/reset-user-password! db (assoc user :password
                                    (hashers/encrypt password))))

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
  etc.). Doesn't check if lipas-ids exist or not."
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

(defn enrich
  "Enriches sports-site map with :search-meta key where we add data that
  is useful for searching."
  [sports-site]
  (let [geom      (-> sports-site :location :geometries :features first :geometry)
        coords    (case (:type geom)
                    "Point"      (-> geom :coordinates)
                    "LineString" (-> geom :coordinates first)
                    "Polygon"    (-> geom :coordinates first first))
        city-code (-> sports-site :location :city :city-code)
        type-code (-> sports-site :type :type-code)]
    (assoc sports-site :search-meta {:location
                                     {:wgs84-point coords
                                      :city
                                      {:name (-> city-code cities :name)}}
                                     :type
                                     {:name (-> type-code types :name)}})))

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
  (let [data (or
              (:all-cities @cache)
              (->> (db/get-cities db)
                   (swap! cache assoc :all-cities)
                   :all-cities))]
    (reports/finance-report city-codes data)))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (def search (search/create-cli (:search config/default-config)))
  (def fields ["lipas-id" "name" "admin" "owner" "properties.surface-material"
               "location.city.city-code"])
  (reset! cache {})
  (:all-cities @cache)
  (time (first (:all-cities @cache)))
  (time (cities-report db-spec [992]))

  ;; ES queries ;;

  (defn s [q] (search/search search "sports_sites_current" q))


  (def active
    {:size 0
     :query
     {:bool
      {:filter
       [{:terms {:status.keyword ["active"]}}]}}})

  (def city-query
    {:size 0
     :query
     {:bool
      {:filter
       [{:terms {:status.keyword ["active"]}}
        {:terms {:location.city.city-code [992]}}]}}})

  (def distinct-type-codes-in-city
    (merge
     city-query
     {:aggs
      {:type_count
       {:cardinality
        {:field :type.type-code}}}}))

  (def area-m2-stats-in-city
    (merge
     city-query
     {:aggs
      {:aream2_stats
       {:stats
        {:field :properties.area-m2}}}}))

  (def rings-around-city
    (merge
     city-query
     {:aggs
      {:rings_around_city
       {:geo_distance
        {:field  :search-meta.location.wgs84-point
         :origin {:lat 62.6028024 :lon 25.7184043}
         :ranges [{:from 0 :to 500}
                  {:from 500 :to 1000}
                  {:from 1000 :to 5000}
                  {:from 5000 :to 10000}]}
        :aggs
        {:tiptop
         {:top_hits {:size 1}}}}}}))

  (def sports-site-counts-per-city-naive
    (merge
     active
     {:aggs
      {:cities
       {:terms {:field :location.city.city-code}}}}))

  (def sports-site-counts-per-city
    (merge
     active
     {:aggs
      {:cities
       {:composite
        {:size    400
         :sources [{:city-code {:terms {:field :location.city.city-code}}}]}}}}))

  (def sports-site-counts-per-city-and-type
    (merge
     active
     {:aggs
      {:cities
       {:composite
        {:size    (* 462 140)
         :sources [{:city-code {:terms {:field :location.city.city-code}}}
                   {:type-code {:terms {:field :type.type-code}}}]}}}}))

  (def construction-year-histogram
    (merge
     active
     {:aggs
      {:cities
       {:composite
        {:size    100
         :sources
         [{:construction-year
           {:histogram
            {:field :construction-year :interval 10}}}]}}}}))

  (def construction-year-histogram-by-owner
    (merge
     active
     {:aggs
      {:years
       {:composite
        {:size    100
         :sources
         [{:construction-year
           {:histogram
            {:field :construction-year :interval 10}}}
          {:owner {:terms {:field :owner.keyword}}}]}}}}))
  (s construction-year-histogram-by-owner)

  (def construction-year-histogram-by-type
    (merge
     active
     {:aggs
      {:years
       {:composite
        {:size    1000
         :sources
         [{:construction-year
           {:histogram
            {:field :construction-year :interval 10}}}
          {:type {:terms {:field :type.type-code}}}]}}}}))

  (s construction-year-histogram-by-type)

  (s construction-year-histogram)

  (s sports-site-counts-per-city)
  (s sports-site-counts-per-city-and-type)
  (s city-query)
  (s distinct-type-codes-in-city)
  (s area-m2-stats-in-city)
  (s rings-around-city)

  ;; orig
  (s {:explain true
      :sort [:_score],
      :from 0,
      :size 15,
      :_source {:excludes ["search-meta"]},
      :query
      {:function_score
       {:score_mode "sum",
        :query
        {:bool
         {:must [{:query_string {:query "*"}}],
          :filter [{:terms {:status.keyword ["active"]}}]}},
        :functions
        [{:gauss
          {:search-meta.location.wgs84-point
           {:origin "63.75018578583665,25.304613947374467",
            :offset "2880m",
            :scale "5760m"}}}]}}})

  ;; new
  (s {:explain true
      :sort [:_score],
      :from 0,
      :size 150,
      :_source {:excludes []},
      :query
      {:function_score
       {:functions
        [{:gauss
          {:search-meta.location.wgs84-point
           {:origin "63.75018578583665,25.304613947374467",
            :offset "0m",
            :scale "1000m"}}}]}}})

  )
