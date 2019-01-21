(ns lipas.backend.config
  (:require
   [environ.core :refer [env]]
   [integrant.core :as ig]))

(def default-config
  {:db      {:dbtype   "postgresql"
             :dbname   (:db-name env)
             :host     (:db-host env)
             :user     (:db-user env)
             :port     (:db-port env)
             :password (:db-password env)}
   :emailer {:host (:smtp-host env)
             :user (:smtp-user env)
             :pass (:smtp-pass env)
             :from (:smtp-from env)}
   :search  {:hosts [(:search-host env)] ; Notice vector!
             :user  (:search-user env)
             :pass  (:search-pass env)}
   :app     {:db      (ig/ref :db)
             :emailer (ig/ref :emailer)
             :search  (ig/ref :search)}
   :server  {:app  (ig/ref :app)
             :port 8091}})
