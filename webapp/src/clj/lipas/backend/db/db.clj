(ns lipas.backend.db.db
  (:require [clojure.java.jdbc :as jdbc]
            [lipas.backend.db.sports-site :as sports-site]
            [lipas.backend.db.user :as user]))

(defprotocol Database
  (get-users [db])
  (get-user-by-id [db user-id])
  (get-user-by-email [db email])
  (get-user-by-username [db username])
  (get-user-by-refresh-token [db refresh-token])
  (add-user! [db user])
  (upsert-sports-site! [db sports-site user]))

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

  ;; Sports Site ;;

  (upsert-sports-site! [_ user sports-site]
    (jdbc/with-db-transaction [tx db-spec]
      (let [lipas-id    (or (:lipas-id sports-site)
                            (:nextval (sports-site/next-lipas-id tx)))
            sports-site (assoc sports-site :lipas-id lipas-id)
            _ (prn sports-site)]
        (->> (sports-site/marshall sports-site user)
             (sports-site/insert-sports-site-rev! tx))))))
