(ns lipas.backend.db.db
  (:require
   [clojure.java.jdbc :as jdbc]
   [lipas.backend.db.sports-site :as sports-site]
   [lipas.backend.db.user :as user]
   [lipas.backend.db.integration :as integration]
   [lipas.utils :as utils]
   [lipas.backend.db.utils :as db-utils]))

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

(defn reset-user-password! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-password! db-spec)))

;; Sports Site ;;

(defn upsert-sports-site!
  ([db-spec user sports-site]
   (upsert-sports-site! db-spec user sports-site false))
  ([db-spec user sports-site draft?]
   (jdbc/with-db-transaction [tx db-spec]
     (let [status      (if draft? "draft" "published")
           lipas-id    (or (:lipas-id sports-site)
                           (:nextval (sports-site/next-lipas-id tx)))
           sports-site (assoc sports-site :lipas-id lipas-id)]
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

(defn get-sports-sites-by-type-code [db-spec type-code {:keys [revs]
                                                        :or   {revs "latest"}}]
  (let [db-fn  (cond
                 (= revs "latest") sports-site/get-latest-by-type-code
                 (= revs "yearly") sports-site/get-yearly-by-type-code
                 (number? revs)    sports-site/get-by-type-code-and-year)
        params (-> (merge {:type-code type-code}
                          (when (number? revs)
                            {:year revs}))
                   db-utils/->snake-case-keywords)]
    (->> (db-fn db-spec params)
         (map sports-site/unmarshall))))

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

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (get-last-integration-timestamp db-spec "old-lipas")
  (get-users-drafts db-spec {:id "a112fd21-9470-480a-8961-6ddd308f58d9"})
  (add-to-integration-out-queue db-spec 234)
  (get-integration-out-queue db-spec)
  (delete-from-integration-out-queue db-spec 234))
