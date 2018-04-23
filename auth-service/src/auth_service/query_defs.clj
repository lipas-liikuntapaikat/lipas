(ns auth-service.query-defs
  (:require [mount.core   :refer [defstate]]
            [environ.core :refer [env]]
            [conman.core  :as conman]))

(def pool-spec {:jdbc-url (env :database-url)})

(defstate ^:dynamic *db*
          :start    (conman/connect! pool-spec)
          :stop     (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/truncate_all.sql"
                             "sql/user/password_reset_key.sql"
                             "sql/user/permission.sql"
                             "sql/user/registered_user.sql"
                             "sql/user/user_permission.sql")
