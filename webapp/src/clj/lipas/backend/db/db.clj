(ns lipas.backend.db.db
  (:require [clojure.java.jdbc :as jdbc]
            [lipas.backend.db.sports-site :as sports-site]
            [lipas.backend.db.user :as user]
            [lipas.backend.db.utils :as utils]))

(defprotocol Database
  (get-users [db])
  (get-user-by-id [db user-id])
  (get-user-by-email [db email])
  (get-user-by-username [db username])
  (get-user-by-refresh-token [db refresh-token])
  (add-user! [db user])
  (reset-user-password! [db user])
  (upsert-sports-site! [db sports-site user])
  (upsert-sports-sites! [db sports-sites user])
  (get-sports-site-history [db lipas-id])
  (get-sports-sites-by-type-code [db type-code opts]))

(defrecord SqlDatabase [db-spec]
  Database

  ;; User ;;

  (get-users [_]
    (-> (user/all-users db-spec)
        (user/unmarshall)))

  (get-user-by-id [_ user-id]
    (-> (user/get-user-by-id db-spec user-id)
        (user/unmarshall)))

  (get-user-by-email [_ email]
    (-> (user/get-user-by-email db-spec email)
        (user/unmarshall)))

  (get-user-by-username [_ username]
    (-> (user/get-user-by-username db-spec username)
        (user/unmarshall)))

  (add-user! [_ user]
    (->> user
         (user/marshall)
         (user/insert-user! db-spec)))

  (reset-user-password! [_ user]
    (->> user
         (user/marshall)
         (user/update-user-password! db-spec)))

  ;; Sports Site ;;

  (upsert-sports-site! [_ user sports-site]
    (jdbc/with-db-transaction [tx db-spec]
      (let [lipas-id    (or (:lipas-id sports-site)
                            (:nextval (sports-site/next-lipas-id tx)))
            sports-site (assoc sports-site :lipas-id lipas-id)]
        (->> (sports-site/marshall sports-site user)
             (sports-site/insert-sports-site-rev! tx)
             (sports-site/unmarshall)))))

  (upsert-sports-sites! [_ user sports-sites]
    (jdbc/with-db-transaction [tx db-spec]
      (doseq [sports-site sports-sites]
        (let [lipas-id    (or (:lipas-id sports-site)
                              (:nextval (sports-site/next-lipas-id tx)))
              sports-site (assoc sports-site :lipas-id lipas-id)]
          (->> (sports-site/marshall sports-site user)
               (sports-site/insert-sports-site-rev! tx))))))

  (get-sports-site-history [_ lipas-id]
    (let [params (-> {:lipas-id lipas-id}
                     utils/->snake-case-keywords)]
      (->> (sports-site/get-history db-spec params)
           (map sports-site/unmarshall))))

  (get-sports-sites-by-type-code [_ type-code {:keys [revs]
                                               :or   {revs "latest"}}]
    (let [db-fn  (case revs
                   "latest" sports-site/get-latest-by-type-code
                   "yearly" sports-site/get-yearly-by-type-code)
          params (-> {:type-code type-code}
                     utils/->snake-case-keywords)]
      (->> (db-fn db-spec params)
           (map sports-site/unmarshall)))))
