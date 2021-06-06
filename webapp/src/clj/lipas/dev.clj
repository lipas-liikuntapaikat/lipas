(ns lipas.dev
  (:require
   [lipas.backend.system :as backend]
   [lipas.backend.config :as config]
   [ring.middleware.reload :refer [wrap-reload]]))

(def system (backend/start-system! (dissoc config/default-config :server)))
(def app (:app system))
(def dev-handler (-> #'app wrap-reload))

(comment
  )
