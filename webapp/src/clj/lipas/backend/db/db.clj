(ns lipas.backend.db.db
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [hikari-cp.core :as hikari]
    [lipas.backend.db.city :as city]
    [lipas.backend.db.loi :as loi]
    [lipas.backend.db.reminder :as reminder]
    [lipas.backend.db.sports-site :as sports-site]
    [lipas.backend.db.subsidy :as subsidy]
    [lipas.backend.db.user :as user]
    [lipas.backend.db.utils :as db-utils]
    [lipas.backend.db.versioned-data :as versioned-data]
    [lipas.utils :as utils]))

;; User ;;

(defn get-users [db-spec]
  (->> (user/all-users db-spec)
       (map user/unmarshall)
       (map #(dissoc % :password))))

(defn users-with-permissions-matching
  "Active users whose stored roles reference any of the given context values — a
  candidate pre-filter for the \"who can edit site Z\" legacy-user lookup
  (design-spec §6 step 4). Filters with jsonb containment on `account.permissions`
  (both `city-code`/`city_code` key spellings, since the raw column carries both;
  the app only normalizes on read), narrowing the table to a small candidate set
  the caller then confirms with the exact `check-privilege`. Containment params
  are Clojure maps — `ISQLValue` turns them into jsonb, so no string building.
  Backed by the `account_permissions_gin` index for the selective predicates.
  Returns unmarshalled users (kebab-normalized + conformed permissions)."
  [db-spec {:keys [city-code type-code lipas-id activities]}]
  (let [contain (fn [k v] {:roles [{k [v]}]})
        params  (concat
                  (when city-code (for [k [:city-code :city_code]] (contain k city-code)))
                  (when type-code (for [k [:type-code :type_code]] (contain k type-code)))
                  (when lipas-id  (for [k [:lipas-id :lipas_id]] (contain k lipas-id)))
                  (for [a activities] (contain :activity a)))]
    (when (seq params)
      (let [sql (str "SELECT * FROM account WHERE status = 'active' AND ("
                     (str/join " OR " (repeat (count params) "permissions @> ?")) ")")]
        (->> (jdbc/query db-spec (into [sql] params))
             (map user/unmarshall)
             (map #(dissoc % :password)))))))

(comment
  (get-users (:lipas/db integrant.repl.state/system))
  (get-orgs (:lipas/db integrant.repl.state/system)))

(defn get-user-by-id [db-spec params]
  (when (uuid? (utils/->uuid-safe (:id params)))
    (-> (user/get-user-by-id db-spec params)
        (user/unmarshall))))

(defn get-user-by-email [db-spec params]
  (-> (user/get-user-by-email db-spec params)
      (user/unmarshall)))

(defn get-user-by-username [db-spec params]
  (-> (user/get-user-by-username db-spec params)
      (user/unmarshall)))

(defn add-user! [db-spec user]
  (->> user
       (user/marshall)
       (user/insert-user! db-spec)))

(defn update-user-permissions! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-permissions! db-spec)))

(defn update-user-status! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-status! db-spec)))

(defn update-user-history! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-history! db-spec)))

(defn update-user-data! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-data! db-spec)))

(defn reset-user-password! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-password! db-spec)))

(defn update-user-email! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-email! db-spec)))

(defn update-user-username! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-username! db-spec)))

;; Sports Site ;;

(defn- invalidate-revs-since-this!
  "Invalidate changes made since site hasn't been active."
  [db-spec {:keys [lipas-id event-date]}]
  (let [params (-> {:event-date event-date :lipas-id lipas-id}
                   utils/->snake-case-keywords)]
    (sports-site/invalidate-since! db-spec params)))

(defn upsert-sports-site!
  ([db-spec user sports-site]
   (upsert-sports-site! db-spec user sports-site false))
  ([db-spec user sports-site draft?]
   (jdbc/with-db-transaction [tx db-spec]
     (let [status (if draft? "draft" "published")
           lipas-id (or (:lipas-id sports-site)
                        (:nextval (sports-site/next-lipas-id tx)))
           sports-site (assoc sports-site :lipas-id lipas-id)]

       ;; It's possible to posthumously say "this site was actually
       ;; abolished in 2017". It's also possible that someone has made
       ;; changes to site data since 2017 and therefore we have to
       ;; invalidate those revisions.
       (when (= "out-of-service-permanently" (:status sports-site))
         (invalidate-revs-since-this! tx sports-site))

       (->> (sports-site/marshall sports-site user status)
            (sports-site/insert-sports-site-rev! tx)
            (sports-site/unmarshall))))))

(defn upsert-sports-sites! [db-spec user sports-sites]
  (jdbc/with-db-transaction [tx db-spec]
    (doseq [sports-site sports-sites]
      (let [lipas-id (or (:lipas-id sports-site)
                         (:nextval (sports-site/next-lipas-id tx)))
            sports-site (assoc sports-site :lipas-id lipas-id)]
        (->> (sports-site/marshall sports-site user)
             (sports-site/insert-sports-site-rev! tx))))))

(defn get-sports-site [db-spec lipas-id]
  (let [params (-> {:lipas-id lipas-id}
                   db-utils/->snake-case-keywords)]
    (-> (sports-site/get db-spec params)
        sports-site/unmarshall)))

(defn get-sports-site-history [db-spec lipas-id]
  (let [params (-> {:lipas-id lipas-id}
                   db-utils/->snake-case-keywords)]
    (->> (sports-site/get-history db-spec params)
         (map sports-site/unmarshall))))

(defn get-sports-site-edit-history
  "Lightweight per-revision edit history (event-date + author-id + status, no
  documents) for a single sports-site, newest first."
  [db-spec lipas-id]
  (sports-site/get-edit-history db-spec {:lipas_id lipas-id}))

(defn get-sports-sites-by-type-code
  [db-spec type-code {:keys [revs raw?] :or {revs "latest" raw? false}}]
  (let [db-fn (cond
                (= revs "all") sports-site/get-by-type-code
                (= revs "latest") sports-site/get-latest-by-type-code
                (= revs "yearly") sports-site/get-yearly-by-type-code
                (number? revs) sports-site/get-by-type-code-and-year)
        params (-> (merge {:type-code type-code}
                          (when (number? revs)
                            {:year revs}))
                   db-utils/->snake-case-keywords)]
    (cond->> (db-fn db-spec params)
      (not raw?) (map sports-site/unmarshall))))

(defn get-sports-sites-by-city-code
  [db-spec city-code]
  (let [params (-> {:city-code (str city-code)} db-utils/->snake-case-keywords)]
    (->> params
         (sports-site/get-latest-by-city-code db-spec)
         (map sports-site/unmarshall))))

(defn get-sports-sites-by-ptv-service-channel-id
  "Returns [{:lipas-id _ :name _} ...] for current sports-site revisions whose
  PTV :service-channel-ids contain `service-channel-id`. Used to detect
  double-linking (more than one site bound to the same PTV service-location)."
  [db-spec service-channel-id]
  (->> (sports-site/get-lipas-ids-by-ptv-service-channel-id
         db-spec {:service_channel_id service-channel-id})
       (map (fn [{:keys [lipas_id name]}]
              {:lipas-id lipas_id :name name}))))

(defn get-users-drafts [db user]
  (let [params {:author-id (:id user) :status "draft"}]
    (->> params
         utils/->snake-case-keywords
         (sports-site/get-by-author-and-status db)
         (map sports-site/unmarshall))))

(defn get-last-modified [db lipas-ids]
  (if (empty? lipas-ids)
    lipas-ids
    (sports-site/get-last-modified db {:lipas_ids lipas-ids})))

(defn get-sports-sites-modified-since [db timestamp]
  (->> (sports-site/get-modified-since db {:timestamp timestamp})
       (map sports-site/unmarshall)))

(defn get-sports-sites-by-lipas-ids [db lipas-ids]
  (when-not (empty? lipas-ids)
    (->> {:lipas-ids lipas-ids}
         utils/->snake-case-keywords
         (sports-site/get-latest-by-ids db)
         (map sports-site/unmarshall))))

;; City ;;

(defn add-city! [db-spec city]
  (->> (city/marshall city)
       (city/insert! db-spec)))

(defn get-cities [db-spec]
  (->> (city/get-all db-spec)
       (map city/unmarshall)))

(defn get-cities-by-type-codes [db-spec city-codes]
  (->> {:city-codes city-codes}
       utils/->snake-case-keywords
       (city/get-by-city-codes db-spec)
       (map city/unmarshall)))

;; Subsidies ;;

(defn add-subsidy! [db-spec subsidy]
  (->> (subsidy/marshall subsidy)
       (subsidy/insert! db-spec)))

(defn get-subsidies [db-spec]
  (->> (subsidy/get-all db-spec)
       (map subsidy/unmarshall)))

(defn get-subsidies-by-year [db-spec years]
  (->> {:years years}
       (subsidy/get-by-years db-spec)
       (map subsidy/unmarshall)))

;; Reminders ;;

(defn add-reminder! [db-spec params]
  (->> params
       reminder/marshall
       (reminder/insert! db-spec)
       reminder/unmarshall))

(defn update-reminder-status! [db-spec params]
  (reminder/update-status! db-spec params))

(defn get-overdue-reminders [db-spec]
  (->> (reminder/get-overdue db-spec)
       (map reminder/unmarshall)))

(defn get-users-pending-reminders [db-spec user-id]
  (->> {:status "pending" :account-id user-id}
       utils/->snake-case-keywords
       (reminder/get-by-user-and-status db-spec)
       (map reminder/unmarshall)))

;; Loi's ;;

(defn get-lois [db]
  (->> (loi/get-latest db)
       (map loi/unmarshall)))

(defn get-lois-by-type
  [db {:keys [loi-type]}]
  (->> (loi/get-latest-by-loi-type db {:loi-type loi-type})
       (map loi/unmarshall)))

(defn get-lois-by-ids
  [db ids]
  (->> (loi/get-latest-by-ids db {:ids (map utils/->uuid ids)})
       (map loi/unmarshall)))

(defn upsert-loi!
  [db user loi]
  (-> loi
      (assoc :author-id (:id user))
      (loi/marshall user)
      (->> (loi/insert-loi-rev! db))))

;; Versioned Data ;;

(defn get-versioned-data
  [db-spec type status]
  (->> {:status status :type type}
       (versioned-data/get-latest-by-type-and-status db-spec)
       (versioned-data/unmarshall)))

(defn add-versioned-data!
  [db-spec type status body]
  (->> (versioned-data/marshall type status body)
       (versioned-data/insert! db-spec)
       (versioned-data/unmarshall)))

;; DB connection pooling ;;

(defn- ->hikari-opts [{:keys [dbtype dbname host user port password]}]
  {:adapter dbtype
   :username user
   :password password
   :database-name dbname
   :server-name host
   :port-number port})

(defn setup-connection-pool [db-spec]
  {:datasource (hikari/make-datasource (->hikari-opts db-spec))})

(defn stop-connection-pool [{:keys [datasource]}]
  (hikari/close-datasource datasource))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (get-users-drafts db-spec {:id "a112fd21-9470-480a-8961-6ddd308f58d9"})
  (hikari/validate-options (->hikari-opts db-spec))
  (def cp1 (setup-connection-pool db-spec))
  (get-user-by-email cp1 {:email "admin@lipas.fi"})
  (add-reminder! db-spec {:account-id "94b1344e-6e06-4ebb-bfd8-1be28b2f511e"
                          :event-date "2019-01-01T00:00:00.000Z"
                          :status "pending"
                          :body {:message "Muista banaani"}})
  (get-sports-sites-by-city-code db-spec 911))
