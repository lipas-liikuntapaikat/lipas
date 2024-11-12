(ns lipas.backend.ptv.integration
  (:require [buddy.core.codecs.base64 :as b64]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.string :as str]
            [lipas.backend.search :as search]
            [lipas.data.ptv :as ptv-data]
            [taoensso.timbre :as log]))

;; ptv component, :lipas/ptv has the config and :tokens atom for org-id -> auth token storage

;; In test the credential to create API tokens is dependant on the org-id,
;; so use this fn to hardcode the credentials.
;; These keys are public so can be leaked out...
(defn get-test-credentials [org-id]
  (case org-id
    ;; org 6
    "3d1759a2-e47a-4947-9a31-cab1c1e2512b"
    {:username "API9@testi.fi"
     :password "APIinterfaceUser9-1009*"}

    ;; org 9
    "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
    {:username "API15@testi.fi"
     :password "APIinterfaceUser15-1015*"}

    ;; org 10
    "52e0f6dc-ec1f-48d5-a0a2-7a4d8b657d53"
    {:username "API17@testi.fi"
     :password "APIinterfaceUser17-1017*"}

    nil))

(defn make-url [ptv & parts]
  (apply str (:api-url ptv) parts))

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
  [ptv org-id]
  ;; NOTE: deref + swap
  (let [x (get @(:tokens ptv) org-id)]
    (if (or (not x) (expired? (:payload x)))
      (let [new-token (authenticate (merge {:token-url (:token-url ptv)
                                            :username  (get-in ptv [:creds :api :username])
                                            :password  (get-in ptv [:creds :api :password])
                                            :org-id    org-id}
                                           (when (= "test" (:env ptv))
                                             (get-test-credentials org-id))))
            x {:token   new-token
               :payload (parse-payload new-token)}]
        (log/infof "Create token %s => %s" org-id new-token)
        (swap! (:tokens ptv) assoc org-id x)
        (:token x))
      (:token x))))

(defn http
  ([ptv auth-org-id req] (http ptv auth-org-id req false))
  ([ptv auth-org-id req retried?]
   (let [token (get-token ptv auth-org-id)
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
                       (get (:headers d) "WWW-Authenticate")))
             (do
               (log/infof "Invalid token, trying to get a new token and retry")
               (swap! (:tokens ptv) dissoc (:auth-org-id req))
               (http ptv auth-org-id req true))
             (throw (ex-info (format "HTTP Error: %s %s" (:status d) (:body d))
                             {:resp d
                              :req req*}
                             nil)))))))))

(defn get-org
  [ptv org-id]
  (let [params {:url (make-url ptv "/v11/Organization/" org-id)
                :method :get}]
    (-> (http ptv org-id params)
        :body)))

;; Need to proxy this with auth because otherwise the API doesn't
;; return :sourceId (wtf)
(defn get-org-services
  [ptv org-id]
  (let [params {:url (make-url ptv "/v11/Service/list/organization")
                :method :get
                :query-params {:organizationId org-id}}]
    (-> (http ptv org-id params)
        :body)))

(defn get-org-service-collections
  [ptv org-id]
  (let [params {:url (make-url ptv "/v11/ServiceCollection/organization")
                :method :get
                :query-params {:organizationId org-id}}]
    (-> (http ptv org-id params)
        :body)))

(defn get-org-service-channels
  [ptv org-id]
  ;; TODO: Solve paginations, if multiple pages, lazy seq and make multiple requests?
  ;; Or should we handle pagination from FE?
  ;; 500 should be fine in one response, what if we have 2000-5000 for some city/org?
  (let [params {:url (make-url ptv "/v11/ServiceChannel/organization/" org-id)
                :method       :get}]
    (-> (http ptv org-id params)
        :body)))

(defn create-service
  [ptv
   {:keys [org-id] :as service}]
  (let [params {:url (make-url ptv "/v11/Service")
                :method :post
                :form-params service}]
    (log/infof "Create PTV service %s" service)
    (-> (http ptv org-id params)
        :body)))

(defn get-service
  [ptv org-id service-id]
  (let [params {:url (make-url ptv "/v11/Service/" service-id)
                :method :get}]
    (-> (http ptv org-id params)
        :body)))

(defn update-service
  [ptv
   service-id
   {:keys [org-id] :as data}]
  (log/info "Update PTV service with id " service-id "and data" data)
  (let [params {:url (make-url ptv "/v11/Service/" service-id)
                :method :put
                :form-params data}]
    (-> (http ptv org-id params)
        :body)))

(defn create-service-location
  [ptv service-location]
  (let [org-id (-> service-location :organizationId)
        params {:url (make-url ptv "/v11/ServiceChannel/ServiceLocation")
                :auth-org-id org-id
                :method :post
                :form-params service-location}]
    (log/info "Create PTV service location" service-location)
    (-> (http ptv org-id params)
        :body)))

(defn update-service-location
  [ptv service-location-id data]
  (let [org-id (-> data :organization :id)
        params {:url (make-url ptv "/v11/ServiceChannel/ServiceLocation/" service-location-id)
                :method :put
                :form-params data}]
    (log/infof "req %s" params)
    (-> (http ptv org-id params)
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
  (def ptv* (:lipas/ptv integrant.repl.state/system))

  (get-org-services ptv* ptv-data/liminka-org-id-test)

  ;; Delete all org services
  (doseq [x (:itemList (get-org-services {} ptv-data/liminka-org-id-test))]
    (update-service ptv*
                    (:id x)
                    {:org-id ptv-data/liminka-org-id-test
                     :publishingStatus "Deleted"}))

  (get-service ptv*
               ptv-data/liminka-org-id-test
               (-> (get-org-services {} ptv-data/liminka-org-id-test)
                   :itemList
                   first
                   :id))

  (get-org-service-channels ptv* ptv-data/liminka-org-id-test)

  ;; Delete all org service locations
  (doseq [x (:itemList (get-org-service-channels ptv* ptv-data/liminka-org-id-test))]
    (update-service-location {:org-id ptv-data/liminka-org-id-test} (:id x) {:publishingStatus "Deleted"}))

  (update-service-location ptv*
                           "fc768bb4-268c-4054-9b88-9ecc9a943452"
                           {:org-id ptv-data/liminka-org-id-test
                            :publishingStatus "Deleted"}))
