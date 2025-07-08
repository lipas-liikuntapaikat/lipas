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

(defn get-org-ptv-config-with-fallback
  "Get organization PTV configuration with fallback to hard-coded data.
   This is a temporary function during migration."
  [ptv ptv-org-id]
  (if-let [get-config-fn (:get-config-by-ptv-org-id-fn ptv)]
    (or (get-config-fn ptv-org-id)
        (do
          (log/warn "Using hard-coded PTV config for PTV org" ptv-org-id)
          (get ptv-data/org-id->params ptv-org-id)))
    ;; No get-config-fn available (e.g., in tests), use hard-coded data
    (get ptv-data/org-id->params ptv-org-id)))

(defn authenticate
  "If API account is connected to multiple organisations, user should
  define Palveluhallinta organisation ID by using apiUserOrganisation parameter.

  If parameter is not given, then token return authentication (token)
  for active organization (can be check from Palveluhallinta UI).

  In test-env token seems to be valid for 24h."
  [{:keys [token-url username password org-id ptv]}]
  (let [token-key (if (test-env? token-url) :ptvToken :serviceToken) ; wtf
        ;; Prod needs a different type of ID for apiUserOrganisation value
        org-config (get-org-ptv-config-with-fallback ptv org-id)
        user-org-id (or (:prod-org-id org-config)
                        org-id)
        req {:url token-url
             :method :post
             :as :json
             :accept :json
             :content-type :json
             :form-params (merge {:username username
                                  :password password}
                                 (when user-org-id
                                   {:apiUserOrganisation user-org-id}))}]
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
      (let [token-props (merge {:token-url (:token-url ptv)
                                :username (get-in ptv [:creds :api :username])
                                :password (get-in ptv [:creds :api :password])
                                :org-id org-id
                                :ptv ptv}
                               (when (= "test" (:env ptv))
                                 (get-test-credentials org-id)))
            new-token (authenticate token-props)
            payload (parse-payload new-token)
            x {:token new-token
               :payload payload}]
        (log/infof "Create token %s => %s (%s)" org-id new-token payload)
        (swap! (:tokens ptv) assoc org-id x)
        (:token x))
      (:token x))))

(defn http
  ([ptv auth-org-id req] (http ptv auth-org-id req false))
  ([ptv auth-org-id req retried?]
   (let [token (get-token ptv auth-org-id)
         req* (-> req
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
                    ;; Just retry once for every 401
                    #_(= "Bearer error=\"invalid_token\", error_description=\"The access token is not valid.\""
                         (get (:headers d) "WWW-Authenticate")))
             (do
               (log/infof "Invalid token, trying to get a new token and retry")
               (swap! (:tokens ptv) dissoc auth-org-id)
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
  (ptv-data/get-all-pages (fn [page]
                            (let [params {:url (make-url ptv "/v11/Service/list/organization")
                                          :method :get
                                          :query-params {:organizationId org-id
                                                         :page page}}]
                              (-> (http ptv org-id params)
                                  :body)))))

(defn get-org-service-collections
  [ptv org-id]
  (ptv-data/get-all-pages (fn [page]
                            (let [params {:url (make-url ptv "/v11/ServiceCollection/organization")
                                          :method :get
                                          :query-params {:organizationId org-id
                                                         :page page}}]
                              (-> (http ptv org-id params)
                                  :body)))))

(defn get-org-service-channels
  [ptv org-id]
  (ptv-data/get-all-pages (fn [page]
                            (let [params {:url (make-url ptv "/v11/ServiceChannel/organization/" org-id)
                                          :method :get
                                          :query-params {:page page}}]
                              (-> (http ptv org-id params)
                                  :body)))))

(defn get-org-service-channel
  [ptv auth-org-id service-channel-id]
  (let [params {:url (make-url ptv "/v11/ServiceChannel/" service-channel-id)
                :method :get}]
    (-> (http ptv auth-org-id params)
        :body)))

(defn create-service
  [ptv
   service]
  (let [org-id (:mainResponsibleOrganization service)
        params {:url (make-url ptv "/v11/Service")
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
   source-id
   data]
  (log/info "Update PTV service with id " source-id "and data" data)
  (let [org-id (:mainResponsibleOrganization data)
        params {:url (make-url ptv "/v11/Service/SourceId/" source-id)
                :method :put
                :form-params data}]
    (-> (http ptv org-id params)
        :body)))

(defn create-service-location
  [ptv service-location]
  (let [org-id (-> service-location :organizationId)
        params {:url (make-url ptv "/v11/ServiceChannel/ServiceLocation")
                :method :post
                :form-params service-location}]
    (log/info "Create PTV service location" service-location)
    (-> (http ptv org-id params)
        :body)))

(defn update-service-location
  [ptv service-location-id data]
  (let [org-id (-> data :organizationId)
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
        params {:size 5000
                :track_total_hits 50000
                :_source {:excludes ["location.geometries.*"
                                     "search-meta.location.geometries.*"
                                     "search-meta.location.simple-geoms.*"]}
                :query
                {:bool
                 {;; Remove these, they aren't PTV candidates
                    ;; Huoltorakennus
                    ;; Opastuspiste
                  :must_not [{:terms {:type.type-code [207 7000]}}]
                  :must
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

(defn update-service-connections [ptv org-id service-id f]
  (let [service-resp (:body (http ptv org-id {:url (make-url ptv "/v11/Service/" service-id)
                                              :method :get}))
        ;; Map Service data to just set of current service-channel-ids
        current-services (->> service-resp
                              :serviceChannels
                              (map (comp :id :serviceChannel))
                              set)
        updated-services (->> (f current-services)
                              (map (fn [id]
                                     {:serviceChannelId id}))
                              vec)]
    (log/infof "Update service %s connections, %s => %s" service-id current-services updated-services)
    ;; NOTE: Hopefully there weren't connection changes to this service between the API calls.
    (http ptv org-id {:url (make-url ptv "/v11/Connection/serviceId/" service-id)
                      :method :put
                      ;; NOTE: Even if the relations have some metadata set in PTV, this doesn't
                      ;; remove the metadata for existing relations.
                      :form-params (cond-> {:channelRelations updated-services}
                                     ;; If channelRelations is empty, the empty list alone isn't enough to
                                     ;; remove rest of the relations, instead we need this property:
                                     (empty? updated-services)
                                     (assoc :deleteAllChannelRelations true))})))

(comment
  (require '[clojure.java.jdbc :as sql]
           '[integrant.repl.state :as state]
           '[lipas.backend.core :as core]
           '[lipas.utils :as utils])

  (def ptv* (:lipas/ptv state/system))

  ;; "Liminka" test / org 9
  (def org-id* "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")

  (get-org-services ptv* org-id*)

  ;; Create extra services for testing pagination
  (dotimes [i 20]
    (let [i (+ 80 i)]
      (create-service ptv* {:sourceId (str "lipas-random-0-" i)
                            :ontologyTerms ["http://www.yso.fi/onto/koko/p37350" "http://www.yso.fi/onto/koko/p33303"]
                            :serviceClasses ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"]
                            :type "Service"
                            :fundingType "PubliclyFunded"
                            :serviceNames [{:type "Name"
                                            :language "fi"
                                            :value (str "Lipas " i)}]
                            :targetGroups ["http://uri.suomi.fi/codelist/ptv/ptvkohderyhmat/code/KR1"] ;; Kansalaiset
                            :areaType "LimitedType"
                            :areas [{:type "Municipality"
                                     :areaCodes [837]}]
                            :languages ["fi"]
                            :serviceDescriptions [{:type "Description"
                                                   :language "fi"
                                                   :value "Kuvaus"}
                                                  {:type "Summary"
                                                   :language "fi"
                                                   :value "Kuvaus 2"}]
                            :publishingStatus "Published"
                            :serviceProducers [{;; SelfProducedServices | ProcuredServices | Other
                                                :provisionType "SelfProducedServices"
                                                :organizations [org-id*]}]
                            :mainResponsibleOrganization org-id*})))

  ;; Delete all org services
  (doseq [x (:itemList (get-org-services ptv* org-id*))]
    (update-service ptv*
                    (:sourceId x)
                    {:mainResponsibleOrganization org-id*
                     :publishingStatus "Deleted"}))

  (get-service ptv*
               org-id*
               (-> (get-org-services {} org-id*)
                   :itemList
                   first
                   :id))

  (require 'user)

  ;; Remove :ptv key
  (doseq [search-site (get-eligible-sites (user/search)
                                          {:city-codes [425]
                                           :owners ["city" "city-main-owner"]})
          :let [site (core/get-sports-site (user/db) (:lipas-id search-site))]]
    (let [resp (core/upsert-sports-site! (user/db)
                                         user/robot
                                         (-> (dissoc site :ptv)
                                             (assoc :event-date (utils/timestamp)))
                                         false)]
      (core/index! (user/search) resp :sync)))

  (doseq [search-site (get-eligible-sites (user/search)
                                          {:city-codes [425]
                                           :owners ["city" "city-main-owner"]})
          :let [site (core/get-sports-site (user/db) (:lipas-id search-site))]]
    (core/index! (user/search) site :sync))

  (get-org-service-channels ptv* org-id*)

  (http ptv* org-id* {:url (make-url ptv* "/v11/ServiceChannel/b4abd13e-0d36-4ff9-a6c9-94f2f5aee036")
                      :method :get})

  ;; Delete all org service locations
  (doseq [x (:itemList (get-org-service-channels ptv* org-id*))]
    (update-service-location ptv* (:id x) {:organizationId org-id*
                                           :publishingStatus "Deleted"}))

  ptv*

  (update-service-location ptv*
                           "fc768bb4-268c-4054-9b88-9ecc9a943452"
                           {:org-id org-id*
                            :publishingStatus "Deleted"})

;; Get all prod orgs
  (def ptv-prod-orgs
    (ptv-data/get-all-pages
     (fn [page]
       (let [params {:url "https://api.palvelutietovaranto.suomi.fi/api/v11/Organization"
                     :method :get
                     :as :json
                     :query-params {:page page :status "Published"}}]
         (:body (client/request params)))))))
