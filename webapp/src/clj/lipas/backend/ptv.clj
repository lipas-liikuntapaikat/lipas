(ns lipas.backend.ptv
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [lipas.data.types :as types]))

;; Exploring PTV prod data

(def api-url "https://api.palvelutietovaranto.suomi.fi/api/v11")

(def headers {:Content-Type  "application/json"})

(defn get-services-by-class
  [class-uri page]
  (-> (client/get (str api-url "/Service/serviceClass")
                  {:headers headers
                   :query-params
                   {:uri  class-uri
                    :page page}})
        :body
        (json/decode keyword)))

(defn get-services-channels-by-type
  [type page]
  (-> (client/get (str api-url "/Service/serviceClass/" type)
                  {:headers headers
                   :query-params
                   {:page page}})
        :body
        (json/decode keyword)))

(comment

  (->> types/main-categories
       vals
       (mapcat (comp :service-classes :ptv))
       distinct)

  ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"
   "http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.1"]

  (def p1 (get-services-by-class "http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2" 1))
  (def p2 (get-services-by-class "http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2" 2))





  )
