(ns lipas.backend.config
  (:require [environ.core :as e]
            [integrant.core :as ig]))


(defn env!
  "Cleanly throws an exception of missing environment variables"
  [kw]
  (if (contains? e/env kw)
    (kw e/env)
    (throw (Exception. (str "Environment variable not set: " kw)))))

(def default-config
  {:db
   (merge
    {:dbtype   "postgresql"
     :dbname   (env! :db-name)
     :host     (env! :db-host)
     :user     (env! :db-user)
     :port     (env! :db-port)
     :password (env! :db-password)}
    ;; TODO add more explicit check
    (when (env! :lein-version)
      {:dev true}))
   :emailer
   {:host (env! :smtp-host)
    :user (env! :smtp-user)
    :pass (env! :smtp-pass)
    :from (env! :smtp-from)}
   :search
   {:hosts [(env! :search-host)] ; Notice vector!
    :user  (env! :search-user)
    :pass  (env! :search-pass)
    :indices
    {:lois
     {:search "lois"}
     :sports-site
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
   {:api-key                (env! :mailchimp-api-key)
    :api-url                (env! :mailchimp-api-url)
    :list-id                (env! :mailchimp-list-id)
    :newsletter-interest-id (env! :mailchimp-newsletter-interest-id)
    :campaign-folder-id     (env! :mailchimp-campaign-folder-id)}
   :aws
   {:access-key-id     (env! :aws-access-key-id)
    :secret-access-key (env! :aws-secret-access-key)
    :region            (env! :aws-region)
    :s3-bucket         (env! :aws-s3-bucket)
    :s3-bucket-prefix  (env! :aws-s3-bucket-prefix)}
   :app
   {:db        (ig/ref :db)
    :emailer   (ig/ref :emailer)
    :search    (ig/ref :search)
    :mailchimp (ig/ref :mailchimp)
    :aws       (ig/ref :aws)
    :utp
    {:cms-api-url  (env! :utp-cms-api-url)
     :cms-api-user (env! :utp-cms-api-user)
     :cms-api-pass (env! :utp-cms-api-pass)}
    :accessibility-register
    {:base-url   (env! :accessibility-register-base-url)
     :system-id  (env! :accessibility-register-system-id)
     :secret-key (env! :accessibility-register-secret-key)}
    :mml-api
    {:api-key      (env! :mml-api-key)
     :coverage-url (env! :mml-coverage-url)}}
   :server
   {:app  (ig/ref :app)
    :port 8091}
   :nrepl
   {:port 7888
    :bind "0.0.0.0"}})
