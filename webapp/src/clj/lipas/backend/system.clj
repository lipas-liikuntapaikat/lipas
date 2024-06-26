(ns lipas.backend.system
  (:require
   [clojure.pprint :refer [pprint]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [lipas.backend.handler :as handler]
   [lipas.backend.search :as search]
   [nrepl.server :as nrepl]
   [ring.adapter.jetty :as jetty])
  (:import
   (software.amazon.awssdk.auth.credentials DefaultCredentialsProvider)))

(defmethod ig/init-key :db [_ db-spec]
  (if (:dev db-spec)
    (do
      (println "Setting up db in dev mode (no pooling)")
      db-spec)
    (do
      (println "Setting up db with connection pool")
      (db/setup-connection-pool db-spec))))

(defmethod ig/halt-key! :db [_ pool]
  (when-not (and (map? pool) (:dev pool))
    (db/stop-connection-pool pool)))

(defmethod ig/init-key :emailer [_ config]
  (email/->SMTPEmailer config))

(defmethod ig/init-key :search [_ config]
  (let [client  (search/create-cli config)
        indices (:indices config)]

    ;; Ensure indices exist
    (when (:create-indices config true)
      (doseq [[group m]      indices
              [_k index-name] m]
        (println "Ensuring index" index-name "exists")
        (when-not (search/index-exists? client index-name)
          (let [mappings (get-in search/mappings [group] {})]
            (println "Creating index" index-name "with mappings:")
            (pprint mappings)
            (search/create-index! client index-name mappings)))))

    {:client   client
     :indices  indices
     :mappings search/mappings}))

(defmethod ig/init-key :mailchimp [_ config]
  config)

(defmethod ig/init-key :app [_ config]
  (handler/create-app config))

(defmethod ig/init-key :server [_ {:keys [app port]}]
  (jetty/run-jetty app {:port port :join? false}))

(defmethod ig/halt-key! :server [_ server]
  (.stop server))

(defmethod ig/init-key :nrepl [_ {:keys [port bind]}]
  (nrepl/start-server :port port :bind bind))

(defmethod ig/halt-key! :nrepl [_ server]
  (nrepl/stop-server server))

(defmethod ig/init-key :aws [_ config]
  (assoc config :credentials-provider (DefaultCredentialsProvider/create)))

(defmethod ig/halt-key! :aws [_ m]
  )

(defn mask [_s]
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
                 (update-in [:search :pass] mask)
                 (update-in [:mailchimp :api-key] mask)
                 (update-in [:app :accessibility-register :secret-key] mask)
                 (update-in [:app :mml-api :api-key] mask)
                 (update-in [:aws :access-key-id] mask)
                 (update-in [:aws :secret-access-key] mask)))
     system)))

(defn stop-system! [system]
  (ig/halt! system))

(defn -main [& _args]
  (start-system! config/default-config))
