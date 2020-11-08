(ns lipas.backend.accessibility
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [lipas.backend.config :as config]
            [lipas.backend.gis :as gis]
            [lipas.data.cities :as cities])
  (:import java.security.MessageDigest))

(def config (-> config/default-config :app :accessibility-register))

(defn hexdigest
  ([input]
   (hexdigest input "SHA-256"))
  ([input hash-algo]
   (let [hash (MessageDigest/getInstance hash-algo)]
     (. hash update (.getBytes input "UTF-8"))
     (let [digest (.digest hash)]
       (string/upper-case
        (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))))))

(defn calc-checksum
  [{:keys [secret-key system-id]} params]
  (hexdigest
   (str
    secret-key
    system-id
    (get params "servicePointId")
    (get params "user")
    (get params "validUntil")
    (get params "streetAddress")
    (get params "postOffice")
    (get params "name")
    (get params "northing")
    (get params "easting"))))

(def timezone (java.time.ZoneId/of "UTC"))

(def date-format
  "Strict ISO date format with fixed precision of one second and no
  timezone. Used by Accessibility register."
  (java.time.format.DateTimeFormatter/ofPattern
   "yyyy-MM-dd'T'HH:mm:ss"))

(defn now+hours [hours]
  (-> (java.time.Instant/now)
      (.plusSeconds (* hours 60 60))
      (.atZone timezone)
      (.format date-format)))

(defn start-point [sports-site]
  (let [fcoll        (-> sports-site :location :geometries)
        geom         (-> fcoll :features first :geometry)]
    (case (:type geom)
      "Point"      (-> geom :coordinates)
      "LineString" (-> geom :coordinates first)
      "Polygon"    (-> geom :coordinates first first))))

(defn make-params
  [user sports-site]
  (let [coords (gis/wgs84->tm35fin (start-point sports-site))]
    {"systemId"       (:system-id config)
     "servicePointId" (:lipas-id sports-site)
     "user"           (:email user)
     "validUntil"     (now+hours 4)
     "name"           (:name sports-site)
     "streetAddress"  (-> sports-site :location :address)
     "postOffice"     (-> sports-site
                          :location
                          :city
                          :city-code
                          cities/by-city-code
                          :name
                          :fi)
     "northing"       (long (:northing coords))
     "easting"        (long (:easting coords))}))

(defn make-app-url [user sports-site]
  (let [params (make-params user sports-site)]
    (str
     (:base-url config)
     "/app/ServicePoint/"
     "?"
     (client/generate-query-string
      (assoc params "checksum" (calc-checksum config params))))))

(defn get-statements [lipas-id]
  (let [url (str
             (:base-url config)
             "/api/v1/accessibility/servicepoints/"
             (:system-id config)
             "/"
             lipas-id
             "/sentences")]
    (:body (client/get url {:as :json}))))

(comment
  (make-app-url
   {:email "kissa@koira.fi"}
   {:name     "testi"
    :lipas-id 12345
    :location
    {:address "testikatu 13"
     :city    {:city-code 992}
     :geometries
     {:features
      [{:geometry
        {:type        "Point"
         :coordinates [23.8259457479965 61.4952794263427]}}]}}})

  (get-statements 12345))
