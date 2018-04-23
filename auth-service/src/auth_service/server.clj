(ns auth-service.server
  (:require [auth-service.handler :refer [app]]
            [org.httpkit.server :as httpkit]
            [taoensso.timbre    :as timbre]
            [mount.core         :as mount]))

(defn -main [port]
  (httpkit/run-server app {:port (Integer/parseInt port) :join false})
  (timbre/merge-config! {:level :warn})
  (mount/start)
  (println "server started on port:" port))
