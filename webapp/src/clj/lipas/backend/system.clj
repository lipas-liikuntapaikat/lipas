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
   [ring.adapter.jetty :as jetty]
   [taoensso.timbre :as log])
  (:import
   (software.amazon.awssdk.auth.credentials DefaultCredentialsProvider)))

(defmethod ig/init-key :lipas/db [_ db-spec]
  (if (:dev db-spec)
    (do
      (println "Setting up db in dev mode (no pooling)")
      db-spec)
    (do
      (println "Setting up db with connection pool")
      (db/setup-connection-pool db-spec))))

(defmethod ig/halt-key! :lipas/db [_ pool]
  (when-not (and (map? pool) (:dev pool))
    (db/stop-connection-pool pool)))

(defmethod ig/init-key :lipas/emailer [_ config]
  (email/->SMTPEmailer config))

(defmethod ig/init-key :lipas/search [_ config]
  (let [client (search/create-cli config)
        indices (:indices config)]

    ;; Ensure indices exist
    (when (:create-indices config true)
      (doseq [[group m] indices
              [_k index-name] m]
        (println "Ensuring index" index-name "exists")
        (when-not (search/index-exists? client index-name)
          (let [mappings (get-in search/mappings [group] {})]
            (println "Creating index" index-name "with mappings:")
            (pprint mappings)
            (search/create-index! client index-name mappings)))))

    {:client client
     :indices indices
     :mappings search/mappings}))

(defmethod ig/init-key :lipas/mailchimp [_ config]
  config)

(defmethod ig/init-key :lipas/app [_ config]
  (handler/create-app config))

(defmethod ig/init-key :lipas/server [_ {:keys [app port]}]
  (jetty/run-jetty app {:port port :join? false}))

(defmethod ig/halt-key! :lipas/server [_ server]
  (.stop server))

(defmethod ig/init-key :lipas/nrepl [_ {:keys [port bind]}]
  (nrepl/start-server :port port :bind bind))

(defmethod ig/halt-key! :lipas/nrepl [_ server]
  (nrepl/stop-server server))

(defmethod ig/init-key :lipas/aws [_ config]
  (assoc config :credentials-provider (DefaultCredentialsProvider/create)))

(defmethod ig/halt-key! :lipas/aws [_ _m])

(defmethod ig/init-key :lipas/open-ai [_ _config])

(defmethod ig/halt-key! :lipas/open-ai [_ _m])

(defmethod ig/init-key :lipas/ptv [_ config]
  (assoc config :tokens (atom {})))

(defmethod ig/halt-key! :lipas/ptv [_ _m])

(defn mask [_s]
  "[secret]")

(defn start-system!
  ([]
   (start-system! config/system-config))
  ([config]
   (let [system (ig/init config)]
     (prn "System started with config:")
     (pprint (-> config
                 (update-in [:lipas/db :password] mask)
                 (update-in [:lipas/emailer :pass] mask)
                 (update-in [:lipas/search :pass] mask)
                 (update-in [:lipas/mailchimp :api-key] mask)
                 (update-in [:lipas/app :accessibility-register :secret-key] mask)
                 (update-in [:lipas/app :mml-api :api-key] mask)
                 (update-in [:lipas/aws :access-key-id] mask)
                 (update-in [:lipas/aws :secret-access-key] mask)))
     system)))

(def current-system (atom nil))

(defn stop-system! [system]
  (ig/halt! system))

(defn -main [& args]
  (let [mode (first args)]
    (case mode
      "server"
      (do
        (println "Starting LIPAS server...")
        (reset! current-system (start-system! config/system-config)))

      "worker"
      (do
        (log/set-min-level! :info)
        (println "Starting LIPAS worker...")
        (require 'lipas.jobs.system)
        (let [start-worker! (resolve 'lipas.jobs.system/start-worker-system!)]
          (reset! current-system (start-worker!))))

      ;; Default to server for backward compatibility
      (do
        (println "No mode specified, defaulting to server...")
        (println "Usage: java -jar backend.jar [server|worker]")
        (reset! current-system (start-system! config/system-config))))))
