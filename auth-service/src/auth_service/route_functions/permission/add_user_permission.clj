(ns auth-service.route-functions.permission.add-user-permission
  (:require [auth-service.query-defs :as query]
            [ring.util.http-response :as respond]))

(defn add-user-permission [id permission]
  (let [added-permission (try
                           (query/insert-permission-for-user! {:userid id :permission permission})
                           (catch Exception e 0))]
    (if (not= 0 added-permission)
      (respond/ok        {:message (format "Permission '%s' for user %s successfully added" permission id)})
      (respond/not-found {:error (format "Permission '%s' does not exist" permission)}))))

(defn add-user-permission-response [request id permission]
  (let [auth (get-in request [:identity :permissions])]
    (if (.contains auth "admin")
      (add-user-permission id permission)
      (respond/unauthorized {:error "Not authorized"}))))
