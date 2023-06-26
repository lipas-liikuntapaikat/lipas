(ns lipas.backend.config
  (:require
   [environ.core :refer [env]]
   [integrant.core :as ig]))

(def default-config
  {:db
   {:dbtype   "postgresql"
    :dbname   (:db-name env)
    :host     (:db-host env)
    :user     (:db-user env)
    :port     (:db-port env)
    :password (:db-password env)}
   :emailer
   {:host (:smtp-host env)
    :user (:smtp-user env)
    :pass (:smtp-pass env)
    :from (:smtp-from env)}
   :search
   {:hosts [(:search-host env)] ; Notice vector!
    :user  (:search-user env)
    :pass  (:search-pass env)}
   :mailchimp
   {:api-key                (:mailchimp-api-key env)
    :api-url                (:mailchimp-api-url env)
    :list-id                (:mailchimp-list-id env)
    :newsletter-interest-id (:mailchimp-newsletter-interest-id env)
    :campaign-folder-id     (:mailchimp-campaign-folder-id env)}
   :app
   {:db        (ig/ref :db)
    :emailer   (ig/ref :emailer)
    :search    (ig/ref :search)
    :mailchimp (ig/ref :mailchimp)
    :accessibility-register
    {:base-url   (:accessibility-register-base-url env)
     :system-id  (:accessibility-register-system-id env)
     :secret-key (:accessibility-register-secret-key env)}
    :mml-api
    {:api-key      (:mml-api-key env)
     :coverage-url (:mml-coverage-url env)}}
   :server
   {:app  (ig/ref :app)
    :port 8091}
   :nrepl
   {:port 7888
    :bind "0.0.0.0"}})
