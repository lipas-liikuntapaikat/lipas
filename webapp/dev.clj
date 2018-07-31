(ns lipas.dev
  (:require [lipas.backend.system :as backend]
            [ring.middleware.reload :refer [wrap-reload]]))

(def system (backend/start-system!))
(def app (:app system))
(def dev-handler (-> #'app wrap-reload))
