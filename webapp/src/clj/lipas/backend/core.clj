(ns lipas.backend.core
  (:require [buddy.hashers :as hashers]
            [lipas.backend.db.db :as db]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.i18n.core :as i18n]
            [lipas.permissions :as permissions]
            [lipas.reports :as reports]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

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
    (str url "?token=" token)))

(defn send-password-reset-link! [db emailer {:keys [email reset-url]}]
  (if-let [user (db/get-user-by-email db {:email email})]
    (let [reset-link (create-magic-link reset-url user)]
      (email/send-reset-password-email! emailer email reset-link))
    (throw (ex-info "User not found"
                    {:type :email-not-found}))))

(defn send-magic-link! [db emailer {:keys [user login-url]}]
  (let [email      (-> user :email)
        user       (or (db/get-user-by-email db {:email email})
                       (do (add-user! db user)
                           (db/get-user-by-email db {:email email})))
        reset-link (create-magic-link login-url user)]
    (email/send-magic-login-email! emailer email reset-link)))

(defn reset-password! [db user password]
  (db/reset-user-password! db (assoc user :password
                                    (hashers/encrypt password))))

;;; Sports-sites ;;;

(defn upsert-sports-site! [db user sports-site draft?]
  (if (or draft?
          (permissions/publish? (:permissions user) sports-site))
    (db/upsert-sports-site! db user sports-site draft?)
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))

(defn get-sports-sites-by-type-code [db type-code {:keys [locale] :as opts}]
  (let [data (db/get-sports-sites-by-type-code db type-code opts)]
    (if (#{:fi :en :se} locale)
      (map (partial i18n/localize locale) data)
      data)))

(defn get-sports-site-history [db lipas-id]
  (db/get-sports-site-history db lipas-id))

;;; Reports ;;;

(defn energy-report [db type-code year]
  (let [data (get-sports-sites-by-type-code db type-code {:revs year})]
    (reports/energy-report data)))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (publish-users-drafts! db-spec admin))
