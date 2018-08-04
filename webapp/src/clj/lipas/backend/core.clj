(ns lipas.backend.core
  (:require [buddy.hashers :as hashers]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]))

;;; User ;;;

(def default-permissions {:draft? true})

(defn username-exists? [db user]
  (some? (.get-user-by-username db user)))

(defn email-exists? [db user]
  (some? (.get-user-by-email db user)))

(defn add-user! [db user]
  (when (username-exists? db user)
    (throw (ex-info "Username is already in use!"
                    {:type :username-conflict})))

  (when (email-exists? db user)
    (throw (ex-info "Email is already in use!"
                    {:type :email-conflict})))

  (let [user (-> user
                 (assoc :permissions (merge
                                      default-permissions
                                      (:permissions user)))
                 (update :password hashers/encrypt))]

    (.add-user! db user)
    {:status "OK"}))

(defn get-user [db identifier]
  (or (.get-user-by-email db {:email identifier})
      (.get-user-by-username db {:username identifier})
      (when (uuid? identifier)
        (.get-user-by-id db {:id identifier}))))

(defn create-reset-link [reset-url user]
  (let [token (jwt/create-token user :terse? true)]
    (str reset-url "?token=" token) ))

(defn send-password-reset-link! [db emailer {:keys [email reset-url]}]
  (if-let [user (.get-user-by-email db {:email email})]
    (let [reset-link (create-reset-link reset-url user)]
      (email/send-reset-password-email! emailer email reset-link))
    (throw (ex-info "User not found"
                    {:type :email-not-found}))))

(defn reset-password! [db user password]
  (.reset-user-password! db (assoc user :password
                                    (hashers/encrypt password))))

;;; Sports-sites ;;;

(defn draft? [sports-site]
  (= (-> sports-site :status) "draft"))

(defn user-can-publish? [{:keys [cities types sports-sites admin?]} sports-site]
  (or (some #{(-> sports-site :lipas-id)} sports-sites)
      (some #{(-> sports-site :location :city :city-code)} cities)
      (some #{(-> sports-site :type :type-code)} types)
      admin?))

(defn upsert-sports-site! [db user sports-site]
  (if (or (draft? sports-site)
          (user-can-publish? (:permissions user) sports-site))
    (.upsert-sports-site! db user sports-site)
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))

(defn get-sports-sites-by-type-code [db type-code]
  (.get-sports-sites-by-type-code db type-code))

(defn get-sports-site-history [db lipas-id]
  (.get-sports-site-history db lipas-id))
