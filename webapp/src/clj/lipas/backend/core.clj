(ns lipas.backend.core
  (:require [buddy.hashers :as hashers]))

(def default-permissions {:draft true})

(defn username-exists? [db user]
  (some? (.get-user-by-username db user)))

(defn email-exists? [db user]
  (some? (.get-user-by-email db user)))

(defn add-user [db user]
  (when (username-exists? db user)
    (throw (ex-info "Username is already in use!"
                    {:type :username-conflict})))

  (when (email-exists? db user)
    (throw (ex-info "Email is already in use!"
                    {:type :email-conflict})))

  (let [user (-> user
                 (assoc :permissions (or (:permissions user)
                                         default-permissions))
                 (update :password hashers/encrypt))]

    (.add-user db user)
    {:status "OK"}))

(defn get-user [db identifier]
  (let [user (or (.get-user-by-email db {:email identifier})
                 (.get-user-by-username db {:username identifier})
                 (when (uuid? identifier)
                   (.get-user-by-id db {:id identifier})))]
       user))
