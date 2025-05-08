(ns lipas.backend.org
  (:require [honey.sql :as hsql]
            [honey.sql.pg-ops :as pgsql]
            [lipas.backend.db.user :as user]
            [lipas.backend.db.utils :as db-utils]
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
  (sql/insert! db :org org (assoc jdbc/unqualified-snake-kebab-opts
                                  :return-keys true)))

(defn update-org! [db org-id org]
  (sql/update! db :org org ["id = ?" org-id] jdbc/unqualified-snake-kebab-opts))

(defn update-org-users! [db org-id changes]
  (jdbc/with-transaction [tx db]
    (doseq [{:keys [user-id change role]} changes]
      (let [user (user/get-user-by-id tx user-id)
            user (case change
                   "add" (update user [:permissions :roles] (fnil conj []) {:role role
                                                                            :org-id org-id})
                   "remove" (update user [:permissions :roles] (fn [roles]
                                                                 (remove (fn [x]
                                                                           (and (= role (:role x))
                                                                                (= org-id (:org-id x))))
                                                                         roles))))]
        (user/update-user-permissions! tx user)))))

(defn user-orgs [db id]
  (let [q (hsql/format {:select [:o.*]
                        :from [[:org :o]]
                        :join [[:account :a] [pgsql/at> :a.permissions [:jsonb_build_object
                                                                        [:inline "roles"]
                                                                        [:jsonb_build_array [:jsonb_build_object [:inline "org_id"] [:jsonb_build_array [:cast :o.id :text]]]]]]]
                        :where [:= :a.id id]})]
    (sql/query db q)))

(defn get-org-users [db org-id]
  (let [q (hsql/format {:select [:id :email :username :permissions]
                        :from [:account]
                        ;; Use snake-case fn to ensure property names use same format as used by user/marshall fn
                        :where [pgsql/at> :permissions [:lift (db-utils/->snake-case-keywords {:roles [{:org-id [(str org-id)]}]})]]})]
    (sql/query db q)))

(comment
  (all-orgs (:lipas/db integrant.repl.state/system))

  (require '[lipas.backend.db.db :as db])
  (db/get-users (:lipas/db integrant.repl.state/system))
  (db/get-user-by-email (:lipas/db integrant.repl.state/system) {:email "vapaa.aika@kuhmo.fi"})

  (get-org-users (:lipas/db integrant.repl.state/system) #uuid "623d6109-505c-4509-a2e1-bf0221c7379b")
  (user-orgs (:lipas/db integrant.repl.state/system) #uuid "47fd4126-d923-47fb-afe3-ce9e7c257d1f")

  (create-org (:lipas/db integrant.repl.state/system)
              {:name "Tampereen Liikuntatoimi"
               :data {:phone "+1234568"}
               :ptv_data {:org-id nil}})
  (update-org! (:lipas/db integrant.repl.state/system)
               #uuid "623d6109-505c-4509-a2e1-bf0221c7379b"
               {:ptv_data {}}))

;; (hugsql/def-db-fns "sql/org.sql")
