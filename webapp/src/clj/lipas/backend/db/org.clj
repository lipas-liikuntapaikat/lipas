(ns lipas.backend.db.org
  (:require [lipas.schema.core]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

(defn marshall [org]
  (-> org))

(defn unmarshall [org]
  (when (not-empty org)
    (-> org)))

;; TODO: Nearly all of these could be handled with next.jdbc.sql
;; (declare all-orgs)

(defn all-orgs [db]
  (sql/find-by-keys db :org :all {:columns [:id :name :data :ptv_data]
                                  :builder-fn rs/as-unqualified-kebab-maps}))

(defn create-org [db org]
  (sql/insert! db :org org))

(comment
  (create-org (:lipas/db integrant.repl.state/system)
              {:name "Tampereen Liikuntatoimi"
               :data {:phone "+1234568"}
               :ptv_data {:org-id nil}}))

;; (hugsql/def-db-fns "sql/org.sql")
