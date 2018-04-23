(ns auth-service.route-functions.user.delete-user
  (:require [auth-service.query-defs :as query]
            [ring.util.http-response :as respond]))

(defn delete-user [id]
  (let [deleted-user (query/delete-registered-user! {:id id})]
    (if (not= 0 deleted-user)
      (respond/ok        {:message (format "User id %s successfully removed" id)})
      (respond/not-found {:error "Userid does not exist"}))))

(defn delete-user-response [request id]
  (let [auth           (get-in request [:identity :permissions])
        deleting-self? (= (str id) (get-in request [:identity :id]))]
    (if (or (.contains auth "admin") deleting-self?)
      (delete-user id)
      (respond/unauthorized {:error "Not authorized"}))))
