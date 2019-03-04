(ns lipas.backend.system
  (:require
   [clojure.pprint :refer [pprint]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [lipas.backend.handler :as handler]
   [lipas.backend.search :as search]
   [ring.adapter.jetty :as jetty]))

(defmethod ig/init-key :db [_ db-spec]
  (db/setup-connection-pool db-spec))

(defmethod ig/halt-key! :db [_ pool]
  (db/stop-connection-pool pool))

(defmethod ig/init-key :emailer [_ config]
  (email/->SMTPEmailer config))

(defmethod ig/init-key :search [_ config]
  (search/create-cli config))

(defmethod ig/init-key :app [_ config]
  (handler/create-app config))

(defmethod ig/init-key :server [_ {:keys [app port]}]
  (jetty/run-jetty app {:port port :join? false}))

(defmethod ig/init-key :server [_ {:keys [app port]}]
  (jetty/run-jetty app {:port port :join? false}))

(defmethod ig/halt-key! :server [_ server]
  (.stop server))

(defn mask [s]
  "[secret]")

(defn start-system!
  ([]
   (start-system! config/default-config))
  ([config]
   (let [system (ig/init config)]
     (prn "System started with config:")
     (pprint (-> config
                 (update-in [:db :password] mask)
                 (update-in [:emailer :pass] mask)
                 (update-in [:search :pass] mask)))
     system)))

(defn stop-system! [system]
  (ig/halt! system))

(defn -main [& args]
  (start-system! config/default-config))
