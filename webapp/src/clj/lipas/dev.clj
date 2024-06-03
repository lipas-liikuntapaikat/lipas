(ns lipas.dev
  (:require
   [lipas.backend.system :as backend]
   [lipas.backend.config :as config]
   [ring.middleware.reload :refer [wrap-reload]]))

(def system (backend/start-system! (dissoc config/default-config :server :nrepl)))
(def app (:app system))
(def dev-handler (-> #'app wrap-reload))


(comment

  (require '[lipas.backend.config :as config])
  (require '[lipas.backend.system :as system])

  (def dev-config (dissoc config/default-config :nrepl))
  (def s0 (system/start-system!))

  (def current-system (atom nil))

  (do
    (when @current-system
      (system/stop-system! @current-system))
    (reset! current-system (system/start-system! dev-config)))


  )
