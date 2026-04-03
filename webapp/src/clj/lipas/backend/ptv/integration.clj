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
     :password "CLcPFgHQB3&"}

    ;; org 7
    "6745e341-be2a-45a4-b184-bbc2f8465615"
    {:username "API11@testi.fi"
     :password "yCfRXaNXg6+"}

    ;; org 9
    "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
    {:username "API15@testi.fi"
     :password "BadDPphJx4@"}

    ;; org 10
    "52e0f6dc-ec1f-48d5-a0a2-7a4d8b657d53"
    {:username "API17@testi.fi"
     :password "KWukVNxPa7@"}

    ;; Default fallback to Org 9 (Liminka) to prevent 500 errors
    ;; when testing with other organizations.
    {:username "API15@testi.fi"
     :password "BadDPphJx4@"}))

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
  "Fetch all service channels for an org with full entity data.
   Uses /list/organization (full entities with descriptions, sourceId,
   publishingStatus etc.) rather than /organization/{id} (summary only
   with {:id :name})."
  [ptv org-id]
  (ptv-data/get-all-pages (fn [page]
                            (let [params {:url (make-url ptv "/v11/ServiceChannel/list/organization")
                                          :method :get
                                          :query-params {:organizationId org-id
                                                         :page page}}]
                              (-> (http ptv org-id params)
                                  :body)))))

(defn get-org-service-channel
  [ptv auth-org-id service-channel-id]
  (let [params {:url (make-url ptv "/v11/ServiceChannel/" service-channel-id)
                :method :get}]
    (-> (http ptv auth-org-id params)
        :body)))

(defn get-active-service-channel
  "Fetch a service channel via the /active/ endpoint, which returns channels
   in ANY publishing state (Published, Modified, Draft) — unlike the regular
   endpoint which only returns Published versions.

   Background: PTV may assign a new UUID to a ServiceChannel when it's edited.
   The regular list endpoint only shows channels with the current Published UUID.
   If someone edits a channel directly in PTV, the UUID stored in LIPAS becomes
   stale — it won't appear in the regular list. But the /active/ endpoint still
   returns the channel by its old UUID, allowing us to detect what happened
   (e.g. it's now in 'Modified' state with unpublished changes).

   Returns the full channel data including :sourceId and :publishingStatus,
   or nil if the channel truly doesn't exist anymore."
  [ptv auth-org-id service-channel-id]
  (try
    (let [params {:url (make-url ptv "/v11/ServiceChannel/active/" service-channel-id)
                  :method :get}]
      (-> (http ptv auth-org-id params)
          :body))
    (catch clojure.lang.ExceptionInfo e
      (when-not (= 404 (:status (:resp (ex-data e))))
        (throw e)))))

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

(defn update-service-by-id
  "Update PTV service by its PTV UUID (not source-id).
   Used when adopting an existing service into LIPAS integration."
  [ptv service-id data]
  (log/info "Update PTV service by ID" service-id)
  (let [org-id (:mainResponsibleOrganization data)
        params {:url (make-url ptv "/v11/Service/" service-id)
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
                :track_total_hits 60000
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
                          [{:terms {:status ["active" "out-of-service-temporarily"]}}
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

;; Cleanup sequence for wiping a PTV test org and its LIPAS data.
;; Works in both lipas-dev and local dev environments.
(comment

  ;; --- Test org definitions ---

  (def org-6-utajarvi {:org-id "3d1759a2-e47a-4947-9a31-cab1c1e2512b"
                       :city-codes [889]
                       :label "Utajärvi / org 6"})

  (def org-7-raahe {:org-id "6745e341-be2a-45a4-b184-bbc2f8465615"
                    :city-codes [678]
                    :label "Raahe / org 7"})

  (def org-9-liminka {:org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
                      :city-codes [425]
                      :label "Liminka / org 9"})

  ;; --- Helpers (eval this block first) ---

  (do
    (require '[lipas.backend.core :as core])
    (require '[lipas.utils :as utils])

    (defn wipe-ptv-service-locations! [ptv org-id]
      (println "Removing Service Locations in PTV Test...")
      (doseq [x (:itemList (get-org-service-channels ptv org-id))]
        (update-service-location ptv (:id x) {:organizationId org-id
                                              :publishingStatus "Deleted"}))
      (println "Removing Service Locations in PTV Test... DONE!"))

    (defn wipe-ptv-services! [ptv org-id]
      (println "Removing Services in PTV Test...")
      (doseq [x (:itemList (get-org-services ptv org-id))]
        (when-let [source-id (:sourceId x)]
          (update-service ptv
                          source-id
                          {:mainResponsibleOrganization org-id
                           :publishingStatus "Deleted"})))
      (println "Removing Services in PTV Test... DONE!"))

    (defn wipe-lipas-ptv-data! [db search robot city-codes]
      (println "Removing PTV data from LIPAS for city-codes" city-codes)
      (doseq [search-site (get-eligible-sites search
                                              {:city-codes city-codes
                                               :owners ["city" "city-main-owner"]})
              :let [site (core/get-sports-site db (:lipas-id search-site))]]
        (println "Removing PTV data for site" (:lipas-id search-site))
        (let [resp (core/upsert-sports-site! db
                                             robot
                                             (-> (dissoc site :ptv)
                                                 (assoc :event-date (utils/timestamp)))
                                             false)]
          (core/index! search resp :sync)))
      (println "Removing PTV data from LIPAS... DONE!"))

    (defn wipe-all! [ptv db search robot {:keys [org-id city-codes label]}]
      (println "Wiping" label "...")
      (wipe-ptv-service-locations! ptv org-id)
      (wipe-ptv-services! ptv org-id)
      (wipe-lipas-ptv-data! db search robot city-codes)
      (println label "wiped out successfully.")))

  ;; --- lipas-dev (system via -main) ---
  (do
    (require '[lipas.backend.system :as system])
    (let [sys    @system/current-system
          ptv    (:lipas/ptv sys)
          db     (:lipas/db sys)
          search (:lipas/search sys)
          robot  (core/get-user db "robot@lipas.fi")]
      (wipe-all! ptv db search robot org-9-liminka)))

  ;; --- local dev (system from integrant.repl.state) ---
  (do
    (let [sys    integrant.repl.state/system
          ptv    (:lipas/ptv sys)
          db     (:lipas/db sys)
          search (:lipas/search sys)
          robot  (core/get-user db "robot@lipas.fi")]
      (wipe-all! ptv db search robot org-9-liminka))))
