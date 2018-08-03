(ns lipas.backend.system
  (:require [environ.core :refer [env]]
            [clojure.pprint :refer [pprint]]
            [ring.adapter.jetty :as jetty]
            [integrant.core :as ig]
            [lipas.backend.db.db :as db]
            [lipas.backend.handler :as handler]))

(def default-config
  {:db     {:dbtype   "postgresql"
            :dbname   (:db-name env)
            :host     (:db-host env)
            :user     (:db-user env)
            :port     (:db-port env)
            :password (:db-password env)}
   :app    {:db (ig/ref :db)}
   :server {:app  (ig/ref :app)
            :port 8091}})

(defmethod ig/init-key :db [_ db-spec]
  (db/->SqlDatabase db-spec))

(defmethod ig/init-key :app [_ config]
  (handler/create-app config))

(defmethod ig/init-key :server [_ {:keys [app port]}]
  (jetty/run-jetty app {:port port :join? false}))

(defmethod ig/halt-key! :server [_ server]
  (.stop server))

(defn mask [s]
  "[secret]")

(defn start-system!
  ([]
   (start-system! default-config))
  ([config]
   (let [system (ig/init config)]
     (prn "System started with config:")
     (pprint (-> config
                 (update-in [:db :password] mask)))
     system)))

(defn stop-system! [system]
  (ig/halt! system))

(defn -main [& args]
  (start-system! default-config))
