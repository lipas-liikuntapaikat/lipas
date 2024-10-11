(ns lipas.backend.db.user
  (:require
   [clojure.spec.alpha :as s]
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]
   [lipas.schema.core]
   [spec-tools.core :as st]))

(defn marshall [{:keys [history] :as user}]
  (-> user
      (dissoc :history)
      (utils/->snake-case-keywords)
      (assoc :history history)))

(defn unmarshall [{:keys [history] :as user}]
  (when (not-empty user)
    (-> user
        (dissoc :history)
        (utils/->kebab-case-keywords)
        (assoc :history history)
        (update :permissions (fn [x]
                               ;; TODO: What if this fails?
                               ;; Maybe ignore failures, users just don't get roles?
                               (let [res (st/conform :lipas.user/permissions x st/json-transformer)]
                                 (if (= ::s/invalid res)
                                   x
                                   res)))))))

;; TODO: Nearly all of these could be handled with next.jdbc.sql
(declare all-users
         get-user-by-id
         get-user-by-username
         get-user-by-email
         insert-user!
         update-user-permissions!
         update-user-history!
         update-user-password!
         update-user-email!
         update-user-data!
         update-user-status!
         update-user-username!)

(hugsql/def-db-fns "sql/user.sql")
