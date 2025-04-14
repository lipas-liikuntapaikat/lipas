(ns lipas.backend.org
  (:require [lipas.backend.db.user :as user]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

; (defn marshall [org]
;   (-> org))

; (defn unmarshall [org]
;   (when (not-empty org)
;     (-> org)))

(defn all-orgs [db]
  (sql/find-by-keys db :org :all {:columns [:id :name :data :ptv_data]
                                  :builder-fn rs/as-unqualified-kebab-maps}))

(defn create-org [db org]
  (sql/insert! db :org org))

(defn update-org! [db org-id org]
  (sql/update! db :org org ["id = ?" org-id]))

(defn update-org-users! [db org-id changes]
  (jdbc/with-transaction [tx db]
    (doseq [{:keys [user-id change role]} changes]
      (let [user (user/get-user-by-id db user-id)
            user (case change
                   "add" (update user [:permissions :roles] (fnil conj []) {:role role
                                                                            :org-id org-id})
                   "remove" (update user [:permissions :roles] (fn [roles]
                                                                 (remove (fn [x]
                                                                           (and (= role (:role x))
                                                                                (= org-id (:org-id x))))
                                                                         roles))))]
        (user/update-user-permissions! db user)))))

(comment
  (create-org (:lipas/db integrant.repl.state/system)
              {:name "Tampereen Liikuntatoimi"
               :data {:phone "+1234568"}
               :ptv_data {:org-id nil}}))

;; (hugsql/def-db-fns "sql/org.sql")
