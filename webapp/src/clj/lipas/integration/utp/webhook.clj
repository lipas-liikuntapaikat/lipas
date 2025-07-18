(ns lipas.integration.utp.webhook
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [lipas.backend.db.db :as db]
            [taoensso.timbre :as log]))

(def current-token (atom nil))

;; https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow
(defn get-token
  [config]
  (let [params {:client-id (:webhook-token-client-id config)
                :client-secret (:webhook-token-client-secret config)
                :grant-type "client_credentials"}]
    (-> (:webhook-token-url config)
        (client/post {:form-params (update-keys params csk/->snake_case)})
        :body
        (json/decode keyword)
        (update-keys csk/->kebab-case))))

(defn unix-time []
  (/ (System/currentTimeMillis) 1000))

(defn expired?
  [{:keys [expires-on]}]
  (< (parse-long expires-on) (long (unix-time))))

(def geom-types
  {"Polygon" "polygon"
   "LineString" "line"
   "Point" "point"})

(defn sports-site->webhook-entry
  [sports-site]
  (let [geom-type (-> sports-site :location :geometries :features first :geometry :type)]
    (-> {:object-type (geom-types geom-type)
         :source-type-spec (-> sports-site :type :type-code str)
         :source-id (-> sports-site :lipas-id str)
         :source-type-id "sports-site"
         :updated-at (:event-date sports-site)}
        (update-keys csk/->camelCase))))

(defn loi->webhook-entry
  [loi]
  (let [geom-type (-> loi :geometries :features first :geometry :type)]
    (-> {:object-type (geom-types geom-type)
         :source-type-spec (:loi-type loi)
         :source-id (-> loi :id str)
         :source-type-id "loi"
         :updated-at (:event-date loi)}
        (update-keys csk/->camelCase))))

(defn call-webhook!
  [config payload]
  (let [token (if (or (nil? @current-token) (expired? @current-token))
                (reset! current-token (get-token config))
                @current-token)
        params {:headers
                {:Authorization (str "Bearer " (:access-token token))
                 :Ocp-Apim-Subscription-Key (:webhook-subscription-key config)}
                :connection-timeout 10000
                :socket-timeout 10000
                :content-type :json
                :accept :json
                :body (json/encode {:source "lipas"
                                    :sourceEnv (:webhook-source-env config)
                                    :objects payload})}]
    (client/post (:webhook-url config) params)))


(defn process-v2!
  "Process webhook payload in the new jobs system format.
   Payload should contain :lipas-ids and/or :loi-ids vectors.
   Supports both single and batch updates with the same interface."
  [db config payload]
  (let [{:keys [lipas-ids loi-ids operation-type correlation-id]} payload
        sites (when (seq lipas-ids)
                (->> (db/get-sports-sites-by-lipas-ids db lipas-ids)
                     (map sports-site->webhook-entry)))
        lois (when (seq loi-ids)
               (->> (db/get-lois-by-ids db loi-ids)
                    (map loi->webhook-entry)))
        entries (into (or sites []) lois)]

    (if (seq entries)
      (do
        (log/info "Sending UTP webhook entries"
                  {:count (count entries)
                   :operation-type operation-type
                   :correlation-id correlation-id})
        (call-webhook! config entries))
      (log/debug "No webhook entries to send"
                 {:lipas-ids lipas-ids
                  :loi-ids loi-ids
                  :correlation-id correlation-id}))))

(comment

  (> (parse-long (:expires-on r1)) (long (unix-time)))

  (require '[lipas.backend.config :as config])
  (def config (get-in config/default-config [:app :utp]))

  config
  @current-token
  (get-token config)
  (def r1 *1)
  (call-webhook! config []))
