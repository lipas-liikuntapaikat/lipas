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
    :pass  (:search-pass env)
    :indices
    {:sports-site
     {:search    "sports_sites_current"
      :analytics "analytics"}
     :report
     {:subsidies  "subsidies"
      :city-stats "city_stats"}
     :analysis
     {:schools             "schools"
      :population          "vaestoruutu_1km"
      :population-high-def "vaestoruutu_250m"
      :diversity           "diversity"}}}
   :mailchimp
   {:api-key                (:mailchimp-api-key env)
    :api-url                (:mailchimp-api-url env)
    :list-id                (:mailchimp-list-id env)
    :newsletter-interest-id (:mailchimp-newsletter-interest-id env)
    :campaign-folder-id     (:mailchimp-campaign-folder-id env)}
   :aws
   {:access-key-id     (:aws-access-key-id env)
    :secret-access-key (:aws-secret-access-key env)
    :region            (:aws-region env)
    :s3-bucket         (:aws-s3-bucket env)
    :s3-bucket-prefix  (:aws-s3-bucket-prefix env)}
   :app
   {:db        (ig/ref :db)
    :emailer   (ig/ref :emailer)
    :search    (ig/ref :search)
    :mailchimp (ig/ref :mailchimp)
    :aws       (ig/ref :aws)
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
