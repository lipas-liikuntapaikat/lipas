(ns auth-service.route-functions.permission.delete-user-permission
  (:require [auth-service.query-defs :as query]
            [ring.util.http-response :as respond]))

(defn delete-user-permission [id permission]
  (let [deleted-permission (query/delete-user-permission! {:userid id :permission permission})]
    (if (not= 0 deleted-permission)
      (respond/ok        {:message (format "Permission '%s' for user %s successfully removed" permission id)})
      (respond/not-found {:error (format "User %s does not have %s permission" id)}))))

(defn delete-user-permission-response [request id permission]
  (let [auth (get-in request [:identity :permissions])]
    (if (.contains auth "admin")
      (delete-user-permission id permission)
      (respond/unauthorized {:error "Not authorized"}))))
