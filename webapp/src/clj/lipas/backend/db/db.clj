(ns lipas.backend.db.db
  (:require [clojure.java.jdbc :as jdbc]
            [lipas.backend.db.sports-site :as sports-site]
            [lipas.backend.db.user :as user]
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
  (let [params {:author_id (:id user)
                :status    "draft"}]
    (->> (sports-site/get-by-author-and-status db params)
         (map sports-site/unmarshall))))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (get-users-drafts db-spec {:id "a112fd21-9470-480a-8961-6ddd308f58d9"}))
