(ns auth-service.route-functions.password.password-reset
  (:require [auth-service.query-defs :as query]
            [buddy.hashers :as hashers]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [ring.util.http-response :as respond]))

(defn update-password [reset-key key-row new-password]
  (let [user-id         (:user_id key-row)
        hashed-password (hashers/encrypt new-password)]
    (query/invalidate-reset-key! {:reset_key reset-key})
    (query/update-registered-user-password! {:id user-id :password hashed-password})
    (respond/ok {:message "Password successfully reset"})))

(defn password-reset-response [reset-key new-password]
  (let [key-row             (query/get-reset-row-by-reset-key {:reset_key reset-key})
        key-does-not-exist? (empty? key-row)
        key-valid-until     (c/from-sql-time (:valid_until key-row))
        key-valid?          (t/before? (t/now) key-valid-until)]
    (cond
      key-does-not-exist?     (respond/not-found {:error "Reset key does not exist"})
      (:already_used key-row) (respond/not-found {:error "Reset key already used"})
      key-valid?              (update-password reset-key key-row new-password)
      :else                   (respond/not-found {:error "Reset key has expired"}))))
