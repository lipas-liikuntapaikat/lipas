(ns lipas.backend.db.db
  (:require
   [clojure.java.jdbc :as jdbc]
   [hikari-cp.core :as hikari]
   [lipas.backend.db.city :as city]
   [lipas.backend.db.email :as email]
   [lipas.backend.db.integration :as integration]
   [lipas.backend.db.reminder :as reminder]
   [lipas.backend.db.sports-site :as sports-site]
   [lipas.backend.db.user :as user]
   [lipas.backend.db.utils :as db-utils]
   [lipas.utils :as utils]))

;; User ;;

(defn get-users [db-spec]
  (->> (user/all-users db-spec)
       (map user/unmarshall)
       (map #(dissoc % :password))))

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

;; Sports Site ;;

(defn- invalidate-revs-since-this!
  "Invalidate changes made since site hasn't been active."
  [db-spec {:keys [lipas-id event-date]}]
  (let [params (-> {:event-date event-date :lipas-id lipas-id}
                   utils/->snake-case-keywords) ]
    (sports-site/invalidate-since! db-spec params)))

(defn upsert-sports-site!
  ([db-spec user sports-site]
   (upsert-sports-site! db-spec user sports-site false))
  ([db-spec user sports-site draft?]
   (jdbc/with-db-transaction [tx db-spec]
     (let [status      (if draft? "draft" "published")
           lipas-id    (or (:lipas-id sports-site)
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
      (let [lipas-id    (or (:lipas-id sports-site)
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

(defn get-sports-sites-by-type-code
  [db-spec type-code {:keys [revs raw?] :or {revs "latest" raw? false}}]
  (let [db-fn  (cond
                 (= revs "all")    sports-site/get-by-type-code
                 (= revs "latest") sports-site/get-latest-by-type-code
                 (= revs "yearly") sports-site/get-yearly-by-type-code
                 (number? revs)    sports-site/get-by-type-code-and-year)
        params (-> (merge {:type-code type-code}
                          (when (number? revs)
                            {:year revs}))
                   db-utils/->snake-case-keywords)]
    (cond->> (db-fn db-spec params)
      (not raw?) (map sports-site/unmarshall))))

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

;; Integration ;;

(defn get-last-integration-timestamp [db-spec name]
  (let [params {:name name :status "success"}]
    (-> (integration/get-last-timestamp-by-name-and-status db-spec params)
        :result
        (as-> $ (when $ (str (.toInstant $)))))))

(defn add-integration-entry! [db-spec entry]
  (->> (integration/marshall entry)
       (integration/insert-entry! db-spec)))

(defn get-integration-out-queue [db-spec]
  (->> db-spec
       integration/get-out-queue
       (map integration/unmarshall)))

(defn add-to-integration-out-queue! [db-spec lipas-id]
  (->> {:lipas-id lipas-id}
       utils/->snake-case-keywords
       (integration/add-to-out-queue! db-spec)))

(defn add-all-to-integration-out-queue! [db-spec lipas-ids]
  (jdbc/with-db-transaction [tx db-spec]
    (doseq [lipas-id lipas-ids]
      (add-to-integration-out-queue! tx lipas-id))))

(defn delete-from-integration-out-queue! [db-spec lipas-id]
  (->> {:lipas-id lipas-id}
       utils/->snake-case-keywords
       (integration/delete-from-out-queue! db-spec)))

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

;; Scheduled emails queue ;;

(defn get-email-out-queue! [db-spec]
  (->> (email/get-out-queue db-spec)
       (map email/unmarshall)))

(defn add-email-to-out-queue! [db-spec params]
  (email/add-to-out-queue! db-spec params))

(defn delete-email-from-out-queue! [db-spec params]
  (email/delete-from-out-queue! db-spec params))

;; DB connection pooling ;;

(defn- ->hikari-opts [{:keys [dbtype dbname host user port password]}]
  {:adapter       dbtype
   :username      user
   :password      password
   :database-name dbname
   :server-name   host
   :port-number   port})

(defn setup-connection-pool [db-spec]
  {:datasource (hikari/make-datasource (->hikari-opts db-spec))})

(defn stop-connection-pool [{:keys [datasource]}]
  (hikari/close-datasource datasource))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (get-last-integration-timestamp db-spec "old-lipas")
  (get-users-drafts db-spec {:id "a112fd21-9470-480a-8961-6ddd308f58d9"})
  (add-to-integration-out-queue db-spec 234)
  (get-integration-out-queue db-spec)
  (delete-from-integration-out-queue db-spec 234)
  (hikari/validate-options (->hikari-opts db-spec))
  (def cp1 (setup-connection-pool db-spec))
  (get-user-by-email cp1 {:email "admin@lipas.fi"})
  (add-reminder! db-spec {:account-id "94b1344e-6e06-4ebb-bfd8-1be28b2f511e"
                          :event-date "2019-01-01T00:00:00.000Z"
                          :status     "pending"
                          :body       {:message "Muista banaani"}}))
