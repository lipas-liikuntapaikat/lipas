(ns lipas.backend.ptv.integration
  (:require [buddy.core.codecs.base64 :as b64]
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
     {:main-user
      ;; unused?
      {:username "paakayttaja41.testi@testi.fi"
       :password "Paatestaaja41-1041*"}
      :maintainer
      ;; unused?
      {:username ""
       :password ""}
      ;; FIXME: The current test env needs different credentials per org
      :api
      ;; org 10
      #_{:username "API17@testi.fi"
         :password "APIinterfaceUser17-1017*"}
      ;; org 6
      #_{:username "API9@testi.fi"
         :password "APIinterfaceUser9-1009*"}
      ;; org 9
      {:username "API15@testi.fi"
       :password "APIinterfaceUser15-1015*"}}}))

#_(def test-config
    {:api-url              "https://api.palvelutietovaranto.trn.suomi.fi/api"
     :token-url            "https://palvelutietovaranto.trn.suomi.fi/api/auth/api-login"
     :service-url          "https://api.palvelutietovaranto.trn.suomi.fi/api/v11/Service"
     :service-location-url "https://api.palvelutietovaranto.trn.suomi.fi/api/v11/ServiceChannel/ServiceLocation"
     :creds
     {:main-user
      {:username "paakayttaja35.testi@testi.fi"
       :password "Paatestaaja35-1035*"}
      :maintainer
      {:username "yllapitaja35.testi@testi.fi"
       :password "Yllapitajatestaaja35-1035*"}
      :api
      {:username "API14@testi.fi"
       :password "APIinterfaceUser14-1014*"}}})

;; Org-id => {:token ... :payload ...}
;; TODO: Move this to IG component state?
(def current-token (atom {}))

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
        req       {:url token-url
                   :method :post
                   :as :json
                   :accept :json
                   :content-type :json
                   :form-params (merge {:username username
                                        :password password}
                                       (when org-id
                                         {:apiUserOrganisation org-id}))}]
    (-> (client/request req)
        :body
        token-key)))

(defn parse-payload
  [token]
  (-> token (str/split #"\.") second b64/decode (String.) (json/decode keyword)))

(defn get-token
  [org-id]
  ;; NOTE: deref + swap
  (let [x (get @current-token org-id)]
    (if (or (not x) (expired? (:payload x)))
      (let [new-token (authenticate {:token-url (:token-url test-config)
                                     :username  (get-in test-config [:creds :api :username])
                                     :password  (get-in test-config [:creds :api :password])
                                     :org-id    org-id})
            x {:token   new-token
               :payload (parse-payload new-token)}]
        (log/infof "Create token %s => %s" org-id new-token)
        (swap! current-token assoc org-id x)
        (:token x))
      (:token x))))

(defn http
  ([req] (http req false))
  ([req retried?]
   (let [token (get-token (:auth-org-id req))
         req* (-> req
                  (dissoc :auth-org-id)
                  (assoc :accept :json
                         :as :json)
                  (assoc-in [:headers :Authorization] (str "bearer " token)))
         req* (if (:form-params req*)
                (assoc req* :content-type :json)
                req*)]
     (try
       (client/request req*)
       (catch clojure.lang.ExceptionInfo e
         (let [d (ex-data e)]
           ;; NOTE: Looks like tokens from yesterday aren't valid the next day even if they haven't "expired" yet?
           (if (and (not retried?)
                    (= 401 (:status d))
                    (= "Bearer error=\"invalid_token\", error_description=\"The access token is not valid.\""
                       (get (:headers d) "rWW-Authenticate")))
             (do
               (log/infof "Invalid token, trying to get a new token and retry")
               (swap! current-token dissoc (:auth-org-id req))
               (http req true))
             (throw (ex-info (format "HTTP Error: %s %s" (:status d) (:body d))
                             {:resp d
                              :req req*}
                             nil)))))))))

;; Need to proxy this with auth because otherwise the API doesn't
;; return :sourceId (wtf)
(defn get-org-services
  [{:keys [service-url]
    :or   {service-url (:service-url test-config)}}
   org-id]
  (let [params {:url (str service-url "/list/organization")
                :auth-org-id org-id
                :method :get
                :query-params {:organizationId org-id}}]
    (-> (http params)
        :body)))

(defn get-org-service-channels
  [{:keys [api-url]
    :or   {api-url (:api-url test-config)}}
   org-id]
  ;; TODO: Solve paginations, if multiple pages, lazy seq and make multiple requests?
  ;; Or should we handle pagination from FE?
  ;; 500 should be fine in one response, what if we have 2000-5000 for some city/org?
  (let [params {:url (str api-url "/v11/ServiceChannel/organization/" org-id)
                :auth-org-id org-id
                :method       :get}]
    (-> (http params)
        :body)))

(defn create-service
  [{:keys [service-url]
    :or   {service-url (:service-url test-config)}
    :as _config}
   {:keys [org-id] :as service}]
  (let [params {:url service-url
                :auth-org-id org-id
                :method :post
                :form-params service}]
    (log/infof "Create PTV service %s" service)
    (-> (http params)
        :body)))

(defn get-service
  [{:keys [service-url org-id]
    :or   {service-url (:service-url test-config)}}
   service-id]
  (let [params {:url (str service-url "/" service-id)
                :auth-org-id org-id
                :method :get}]
    (-> (http params)
        :body)))

(defn update-service
  [{:keys [service-url]
    :or   {service-url (:service-url test-config)}}
   service-id
   {:keys [org-id] :as data}]
  (log/info "Update PTV service with id " service-id "and data" data)
  (let [params {:url (str service-url "/" service-id)
                :auth-org-id org-id
                :method :put
                :form-params data}]
    (-> (http params)
        :body)))

(defn create-service-location
  [{:keys [service-location-url]
    :or   {service-location-url (:service-location-url test-config)}}
   service-location]
  (let [org-id (-> service-location :organizationId)
        params {:url service-location-url
                :auth-org-id org-id
                :method :post
                :form-params service-location}]
    (log/info "Create PTV service location" service-location)
    (-> (http params)
        :body)))

(defn update-service-location
  [{:keys [service-location-url]
    :or   {service-location-url (:service-location-url test-config)}}
   service-location-id
   data]
  (let [org-id (-> data :organization :id)
        params {:url (str service-location-url "/" service-location-id)
                :auth-org-id org-id
                :method :put
                :form-params data}]
    (log/infof "req %s" params)
    (-> (http params)
        :body)))

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
                            [;; Include all statuses - this is also used to remove the sites from PTV
                             ; {:terms {:status.keyword ["active"
                             ;                           "out-of-service-temporarily"]}}
                             (when city-codes
                               {:terms {:location.city.city-code city-codes}})
                             (when owners
                               {:terms {:owner owners}})
                             (when type-codes
                               {:terms {:type.type-code type-codes}})])}}}]
    ;; TODO: Remove 7000 - huoltorakennukset
    (-> (search/search client idx-name params)
        :body
        :hits
        :hits
        (->> (map :_source)))))

(comment
  (get-org-services {} ptv-data/liminka-org-id-test)

  ;; Delete all org services
  (doseq [x (:itemList (get-org-services {} ptv-data/liminka-org-id-test))]
    (update-service {:org-id ptv-data/liminka-org-id-test}
                    (:id x)
                    {:publishingStatus "Deleted"}))

  (get-service {}
               (-> (get-org-services {} ptv-data/liminka-org-id-test)
                   :itemList
                   first
                   :id))

  (get-org-service-channels {} ptv-data/liminka-org-id-test)

  ;; Delete all org service locations
  (doseq [x (:itemList (get-org-service-channels {} ptv-data/liminka-org-id-test))]
    (update-service-location {:org-id ptv-data/liminka-org-id-test} (:id x) {:publishingStatus "Deleted"}))

  (update-service-location {:org-id ptv-data/liminka-org-id-test} "fc768bb4-268c-4054-9b88-9ecc9a943452" {:publishingStatus "Deleted"}))
