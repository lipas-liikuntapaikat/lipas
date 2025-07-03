(ns lipas.backend.org
  (:require [clojure.java.jdbc :as jdbc-old]
            [honey.sql :as hsql]
            [honey.sql.pg-ops :as pgsql]
            [lipas.backend.db.db :as db]
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
  (jdbc-old/with-db-transaction [tx db]
    (doseq [change-spec changes]
      (let [;; Handle both user-id and email cases
            user (cond
                   (:user-id change-spec)
                   (db/get-user-by-id tx {:id (:user-id change-spec)})

                   (:email change-spec)
                   (let [email (:email change-spec)
                         user (db/get-user-by-email tx {:email email})]
                     (when (nil? user)
                       (throw (ex-info (str "No user found with email address: " email ". "
                                            "The user must first register an account with LIPAS "
                                            "before they can be added to an organization.")
                                       {:type :user-not-found
                                        :email email})))
                     user))

            {:keys [change role]} change-spec

            updated-user (case change
                           "add" (update-in user [:permissions :roles]
                                            (fnil conj [])
                                            {:role (keyword role)
                                             :org-id [(str org-id)]})
                           "remove" (update-in user [:permissions :roles]
                                               (fn [roles]
                                                 (into (empty roles)
                                                       (remove (fn [x]
                                                                 (and (= (keyword role) (:role x))
                                                                    ;; Should always a vector with one item...
                                                                      (= [(str org-id)] (:org-id x))))
                                                               roles)))))]
        (db/update-user-permissions! tx updated-user)))))

(defn add-org-user-by-email!
  "Add user to organization by email address. For use by org admins who can't see all users."
  [db org-id email role]
  (try
    (update-org-users! db org-id [{:email email :change "add" :role role}])
    {:success? true :message "User successfully added to organization"}
    (catch Exception e
      (let [ex-data (ex-data e)]
        (if (= (:type ex-data) :user-not-found)
          {:success? false
           :message (str "No user found with email address: " email ". "
                         "The user must first register an account with LIPAS "
                         "before they can be added to an organization.")}
          (throw e))))))

(defn user-orgs [db id]
  (let [q (hsql/format {:select [:o.*]
                        :from [[:org :o]]
                        :join [[:account :a] [pgsql/at> :a.permissions [:jsonb_build_object
                                                                        [:inline "roles"]
                                                                        [:jsonb_build_array [:jsonb_build_object [:inline "org_id"] [:jsonb_build_array [:cast :o.id :text]]]]]]]
                        :where [:= :a.id id]})]
    (sql/query db q {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-org-users [db org-id]
  (let [q (hsql/format {:select [:id :email :username :permissions]
                        :from [:account]
                        ;; Use snake-case fn to ensure property names use same format as used by user/marshall fn
                        :where [pgsql/at> :permissions [:lift (db-utils/->snake-case-keywords {:roles [{:org-id [(str org-id)]}]})]]})]
    (->> (sql/query db q {:builder-fn rs/as-unqualified-kebab-maps})
         (map (fn [user]
                (db-utils/->kebab-case-keywords user))))))

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
