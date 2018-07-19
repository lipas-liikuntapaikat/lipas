(ns lipas.backend.core
  (:require [buddy.hashers :as hashers]))

;;; User ;;;

(def default-permissions {:draft true})

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

(defn refresh-login! [db token]
  ;; TODO check that user has not explicitely logged out and issue new
  ;; access token.
  )

;;; Sports-sites ;;;

(defn draft? [sports-site]
  (= (-> sports-site :status) "draft"))

(defn user-can-publish? [{:keys [cities types sports-sites]} sports-site]
  (or (some #{(-> sports-site :lipas-id)} sports-sites)
      (some #{(-> sports-site :location :city :city-code)} cities)
      (some #{(-> sports-site :type :type-code)} types)))

(defn upsert-sports-site! [db user sports-site]
  (if (or (draft? sports-site)
          (user-can-publish? (:permissions user) sports-site))
    (.upsert-sports-site! db user sports-site)
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))
