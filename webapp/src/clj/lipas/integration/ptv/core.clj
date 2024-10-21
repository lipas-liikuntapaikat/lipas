(ns lipas.integration.ptv.core
  (:require
   [buddy.core.codecs.base64 :as b64]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [lipas.backend.config :as config]
   [lipas.backend.search :as search]
   [lipas.data.ptv :as ptv-data]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]))

;; Test creds are OK to "leak" to VCS since they're public anyway
(def test-config
  (utils/deep-merge
   (get config/default-config :ptv)
   {:api-url              "https://api.palvelutietovaranto.trn.suomi.fi/api"
    :token-url            "https://palvelutietovaranto.trn.suomi.fi/api/auth/api-login"
    :service-url          "https://api.palvelutietovaranto.trn.suomi.fi/api/v11/Service"
    :service-location-url "https://api.palvelutietovaranto.trn.suomi.fi/api/v11/ServiceChannel/ServiceLocation"
    :creds
    {:org-id ptv-data/uta-org-id-test
     :main-user
     {:username "paakayttaja41.testi@testi.fi"
      :password "Paatestaaja41-1041*"}
     :maintainer
     {:username ""
      :password ""}
     :api
     #_{:username "API17@testi.fi"
      :password "APIinterfaceUser17-1017*"}
     {:username "API9@testi.fi"
      :password "APIinterfaceUser9-1009*"}}}))

#_(def test-config
  {:api-url              "https://api.palvelutietovaranto.trn.suomi.fi/api"
   :token-url            "https://palvelutietovaranto.trn.suomi.fi/api/auth/api-login"
   :service-url          "https://api.palvelutietovaranto.trn.suomi.fi/api/v11/Service"
   :service-location-url "https://api.palvelutietovaranto.trn.suomi.fi/api/v11/ServiceChannel/ServiceLocation"
   :creds
   {:org-id ptv-data/uta-org-id-test
    :main-user
    {:username "paakayttaja35.testi@testi.fi"
     :password "Paatestaaja35-1035*"}
    :maintainer
    {:username "yllapitaja35.testi@testi.fi"
     :password "Yllapitajatestaaja35-1035*"}
    :api
    {:username "API14@testi.fi"
     :password "APIinterfaceUser14-1014*"}}})

(def current-token (atom nil))

(defn unix-time []
  (/ (System/currentTimeMillis) 1000))

(defn expired?
  [{:keys [exp]}]
  (< exp (long (unix-time))))

(defn test-env?
  [url]
  (str/includes? url ".trn.suomi.fi"))

(defn authenticate
  "If API account is connected to multiple organisations, user should
  define Palveluhallinta organisation ID by using apiUserOrganisation parameter.

  If parameter is not given, then token return authentication (token)
  for active organization (can be check from Palveluhallinta UI).

  In test-env token seems to be valid for 24h."
  [{:keys [token-url username password org-id]}]
  (let [token-key (if (test-env? token-url) :ptvToken :serviceToken) ; wtf
        params    {:headers {:Content-Type "application/json"}
                   :body    (json/encode
                             (merge {:username username :password password}
                                    (when org-id
                                      {:apiUserOrganisation org-id})))}]
    (-> (client/post token-url params)
        :body
        (json/decode keyword)
        token-key)))

(defn parse-payload
  [token]
  (-> token (str/split #"\.") second b64/decode (String.) (json/decode keyword)))

(defn get-token
  []
  (if (or (not @current-token) (expired? (:payload @current-token)))
    (let [new-token (authenticate {:token-url (:token-url test-config)
                                   :username  (get-in test-config [:creds :api :username])
                                   :password  (get-in test-config [:creds :api :password])
                                   :org-id    (get-in test-config [:creds :org-id])})]
      (:token (reset! current-token {:token   new-token
                                     :payload (parse-payload new-token)})))
    (:token @current-token)))

(def ->ptv-service ptv-data/->ptv-service)
(def ->ptv-service-location ptv-data/->ptv-service-location)

;; Need to proxy this with auth because otherwise the API doesn't
;; return :sourceId (wtf)
(defn get-org-services
  [{:keys [service-url token]
    :or   {service-url (:service-url test-config)
           token       (get-token)}}
   org-id]
  (let [params {:headers      {:Content-Type  "application/json"
                               :Authorization (str "bearer " token)}
                :query-params {:organizationId org-id}}]
    (-> (client/get (str service-url "/list/organization") params)
        :body
        (json/decode keyword))))

(defn create-service
  [{:keys [service-url token]
    :or   {service-url (:service-url test-config)
           token       (get-token)}}
   service]
  (let [params {:headers {:Content-Type  "application/json"
                          :Authorization (str "bearer " token)}
                :body    (json/encode service)}]
    (log/info "Create PTV service" service)
    (-> (client/post service-url params)
        :body
        (json/decode keyword))))

(defn update-service
  [{:keys [service-url token _org-id]
    :or   {service-url (:service-url test-config)
           token       (get-token)}}
   service-id
   data]
  (log/info "Update PTV service with id " service-id "and data" data)
  (let [ params {:headers {:Content-Type  "application/json"
                           :Authorization (str "bearer " token)}
                 :body    (json/encode data)}]
    (-> (client/put (str service-url "/" service-id) params)
        :body
        (json/decode keyword))))

(defn create-service-location
  [{:keys [service-location-url token _org-id]
    :or   {service-location-url (:service-location-url test-config)
           token                (get-token)}}
   service-location]
  (let [params {:headers {:Content-Type  "application/json"
                          :Authorization (str "bearer " token)}
                :body    (json/encode service-location)}]
    (log/info "Create PTV service location" service-location)
    (-> (client/post service-location-url params)
        :body
        (json/decode keyword))))

(defn update-service-location
  [{:keys [service-location-url token _org-id]
    :or   {service-location-url (:service-location-url test-config)
           token                (get-token)}}
   service-location-id
   data]
  (let [params {:headers {:Content-Type  "application/json"
                          :Authorization (str "bearer " token)}
                :body    (json/encode data)}]
    (-> (client/put (str service-location-url "/" service-location-id) params)
        :body
        (json/decode keyword))))

(defn get-eligible-sites
  [{:keys [indices client] :as _search}
   {:keys [city-codes type-codes owners] :as _criteria}]
  (let [idx-name (get-in indices [:sports-site :search])
        params   {:size             5000
                  :track_total_hits 50000
                  :_source          {:excludes ["location.geometries.*"
                                                "search-meta.location.geometries.*"
                                                "search-meta.location.simple-geoms.*"]}
                  :query
                  {:bool
                   {:must
                    (remove nil?
                            [{:terms {:status.keyword ["active" "out-of-service-temporarily"]}}
                             (when city-codes
                               {:terms {:location.city.city-code city-codes}})
                             (when owners
                               {:terms {:owner owners}})
                             (when type-codes
                               {:terms {:type.type-code type-codes}})])}}}]
    (-> (search/search client idx-name params)
        :body
        :hits
        :hits
        (->> (map :_source)))))
