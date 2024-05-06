(ns lipas.integration.utp.webhook
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.java.jdbc :as jdbc]
            [lipas.backend.db.db :as db]
            [taoensso.timbre :as log]))

(def current-token (atom nil))

(defn get-token
  [config]
  (let [params {:client-id     (:webhook-token-client-id config)
                :client-secret (:webhook-token-client-secret config)
                :grant-type    "client_credentials"}]
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
  {"Polygon"    "polygon"
   "LineString" "line"
   "Point"      "point"})

(defn sports-site->webhook-entry
  [sports-site]
  (let [geom-type (-> sports-site :location :geometries :features first :geometry :type)]
    (-> {:object-type      (geom-types geom-type)
         :source-type-spec (-> sports-site :type :type-code str)
         :source-id        (-> sports-site :lipas-id str)
         :source-type-id   "sports-site"
         :updated-at       (:event-date sports-site)}
        (update-keys csk/->camelCase))))

(defn loi->webhook-entry
  [loi]
  (let [geom-type (-> loi :geometries :features first :geometry :type)]
    (-> {:object-type      (geom-types geom-type)
         :source-type-spec (:loi-type loi)
         :source-id        (-> loi :id str)
         :source-type-id   "loi"
         :updated-at       (:event-date loi)}
        (update-keys csk/->camelCase))))

(defn call-webhook!
  [config payload]
  (let [token  (if (or (nil? @current-token) (expired? @current-token))
                 (reset! current-token (get-token config))
                 @current-token)
        params {:headers
                {:Authorization             (str "Bearer " (:access-token token))
                 :Ocp-Apim-Subscription-Key (:webhook-subscription-key config)}
                :connection-timeout 10000
                :socket-timeout     10000
                :content-type       :json
                :accept             :json
                :body               (json/encode {:source "lipas" :objects payload})}]
    (client/post (:webhook-url config) params)))

(defn process!
  [db config]
  (let [batches   (db/get-webhook-queue db)
        lipas-ids (mapcat (comp :lipas-ids :batch-data) batches)
        loi-ids   (mapcat (comp :loi-ids :batch-data) batches)
        sites     (when (seq lipas-ids)
                    (->> (db/get-sports-sites-by-lipas-ids db lipas-ids)
                         (map sports-site->webhook-entry)))
        lois      (when (seq loi-ids)
                    (->> (db/get-lois-by-ids db loi-ids)
                         (map loi->webhook-entry)))
        entries   (into (or sites []) lois)]

    (when (seq entries)
      (jdbc/with-db-transaction [tx db]

        (log/info "Sending" (count entries) "UTP webhook entries")

        (doseq [batch batches]
          (db/update-webhook-batch-status! tx (:id batch) "in-progress"))

        (try
          (call-webhook! config entries)

          (doseq [batch batches]
            (db/update-webhook-batch-status! tx (:id batch) "finished"))

          (log/info "Great success!")

          (catch Exception e
            (log/error e)
            (log/info "Resetting batches to 'pending' status for retry")
            (doseq [batch batches]
              (db/update-webhook-batch-status! tx (:id batch) "pending"))))))))

(comment

  (> (parse-long (:expires-on r1)) (long (unix-time)))

  (require '[lipas.backend.config :as config])
  (def config (get-in config/default-config [:app :utp]))

  config
  @current-token
  (get-token config)
  (def r1 *1)
  (call-webhook! config [])

  )
