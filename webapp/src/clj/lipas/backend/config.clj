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
   {:dbtype   "postgresql"
    :dbname   (env! :db-name)
    :host     (env! :db-host)
    :user     (env! :db-user)
    :port     (env! :db-port)
    :password (env! :db-password)
    :dev      (= "dev" (env! :environment))}
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
   :ptv
   (let [test-env? (= "test" (:ptv-env e/env "prod"))]
     {:env       (:ptv-env e/env "prod")
      :api-url   (env! :ptv-api-url)
      :token-url (env! :ptv-token-url)
      :creds     (when-not test-env?
                   {:api {:username (env! :ptv-api-username)
                          :password (env! :ptv-api-password)}})})
   :open-ai
   {:api-key         (env! :open-ai-api-key)
    :project         "ptv"
    :completions-url "https://api.openai.com/v1/chat/completions"
    :models-url      "https://api.openai.com/v1/models"
    :model           #_"gpt-4o-mini" "gpt-4.1-mini"}
   :app
   {:db        (ig/ref :lipas/db)
    :emailer   (ig/ref :lipas/emailer)
    :search    (ig/ref :lipas/search)
    :mailchimp (ig/ref :lipas/mailchimp)
    :aws       (ig/ref :lipas/aws)
    :ptv       (ig/ref :lipas/ptv)
    :utp
    {:cms-api-url                 (env! :utp-cms-api-url)
     :cms-api-user                (env! :utp-cms-api-user)
     :cms-api-pass                (env! :utp-cms-api-pass)
     :webhook-source-env          (env! :utp-webhook-source-env)
     :webhook-token-url           (env! :utp-webhook-token-url)
     :webhook-token-client-id     (env! :utp-webhook-token-client-id)
     :webhook-token-client-secret (env! :utp-webhook-token-client-secret)
     :webhook-url                 (env! :utp-webhook-url)
     :webhook-subscription-key    (env! :utp-webhook-subscription-key)}
    :accessibility-register
    {:base-url   (env! :accessibility-register-base-url)
     :system-id  (env! :accessibility-register-system-id)
     :secret-key (env! :accessibility-register-secret-key)}
    :mml-api
    {:api-key      (env! :mml-api-key)
     :coverage-url (env! :mml-coverage-url)}}
   :server
   {:app  (ig/ref :lipas/app)
    :port 8091}
   :nrepl
   {:port 7888
    :bind "0.0.0.0"}})

(defn ->system-config
  [config]
  (update-keys config #(keyword "lipas" (name %))))

(def system-config
  "Integrant requires system map keys to be fully qualified since
  2020. There are several references directly to `default-config` in
  the code, so we create another var for system config and leave the
  `default-config` as is."
  (->system-config default-config))
