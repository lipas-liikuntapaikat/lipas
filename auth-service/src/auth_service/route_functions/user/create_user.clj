(ns auth-service.route-functions.user.create-user
  (:require [auth-service.query-defs :as query]
            [buddy.hashers :as hashers]
            [ring.util.http-response :as respond]))

(defn create-new-user [{:keys [password user-data] :as user}]
  (let [hashed-password (hashers/encrypt password)
        db-safe-user    (-> user
                            (assoc :password hashed-password)
                            (assoc :user_data (when user-data (pr-str user-data))))
        new-user        (query/insert-registered-user! db-safe-user)
        _               (query/insert-permission-for-user!
                         {:userid     (:id new-user)
                          :permission "basic"})]
    (respond/created {} {:username (str (:username new-user))})))

(defn create-user-response [user]
  (let [username-query   (query/get-registered-user-by-username user)
        email-query      (query/get-registered-user-by-email user)
        email-exists?    (not-empty email-query)
        username-exists? (not-empty username-query)]
    (cond
      (and username-exists? email-exists?)
      (respond/conflict {:error "Username and Email already exist"})
      username-exists?
      (respond/conflict {:error "Username already exists"})
      email-exists?
      (respond/conflict {:error "Email already exists"})
      :else (create-new-user user))))
