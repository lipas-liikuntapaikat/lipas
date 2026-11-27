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

    ;; org 8
    "92374b0f-7d3c-4017-858e-666ee3ca2761"
    {:username "API13@testi.fi"
     :password "EfWjACLNS8+"}

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
        (log/debugf "Create token for org %s (expires %s)" org-id (:exp payload))
        (swap! (:tokens ptv) assoc org-id x)
        (:token x))
      (:token x))))

(defn- printable-body
  "The request body for a log line. Must never throw: it runs while
  reporting a PTV error, and an exception here would replace that error.
  Non-ASCII is escaped (\\u00e4) because the prod log pipeline turns
  ä/ö into '?'; anything cheshire can't encode falls back to pr-str."
  [form-params]
  (try
    (json/generate-string form-params {:escape-non-ascii true})
    (catch Exception _
      (pr-str form-params))))

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
               (log/debug "Invalid token, retrying with new token")
               (swap! (:tokens ptv) dissoc auth-org-id)
               (http ptv auth-org-id req true))
             (do
               ;; PTV's 5xx bodies only say "An unexpected error occurred.
               ;; Trace id: …", and the exception's printed :req is
               ;; truncated, so log exactly what we sent: the trace id plus
               ;; this payload is what PTV support needs.
               (when (and (:status d) (>= (:status d) 500))
                 (log/warnf "PTV %s %s -> %s %s\nRequest body: %s"
                            (some-> (:method req*) name str/upper-case)
                            (:url req*)
                            (:status d)
                            (:body d)
                            (some-> (:form-params req*) printable-body)))
               (throw (ex-info (format "HTTP Error: %s %s" (:status d) (:body d))
                               {:resp d
                                ;; No bearer token: this ex-data is printed
                                ;; into the logs with the stack trace.
                                :req (update req* :headers dissoc :Authorization)}
                               nil))))))))))

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
    (log/infof "Create PTV service (sourceId: %s)" (:sourceId service))
    (-> (http ptv org-id params)
        :body)))

(defn get-service
  [ptv org-id service-id]
  (let [params {:url (make-url ptv "/v11/Service/" service-id)
                :method :get}]
    (-> (http ptv org-id params)
        :body)))

(def ^:private archived-page-size
  "PTV's most per page of its archived lists."
  100)

(defn get-org-archived-services
  "The services of the org that PTV has archived, by hand or automatically
  (ArchivingType 0 and 1), each {:id :archivingDate :serviceNames}. A
  service archived in PTV answers 404 to every GET by its id; only these
  lists still know it. Paged by Skip/Take, a request per 100 per type."
  [ptv org-id]
  (vec (for [archiving-type [0 1]
             service (loop [skip 0
                            acc []]
                       (let [page (-> (http ptv org-id {:url (make-url ptv "/v11/Service/archived/list")
                                                        :method :get
                                                        :query-params {:OrganizationId org-id
                                                                       :ArchivingType archiving-type
                                                                       :Skip skip
                                                                       :Take archived-page-size}})
                                      :body)
                             acc (into acc page)]
                         (if (< (count page) archived-page-size)
                           acc
                           (recur (+ skip archived-page-size) acc))))]
         service)))

(defn update-service
  [ptv
   source-id
   data]
  (log/infof "Update PTV service (sourceId: %s)" source-id)
  (let [org-id (:mainResponsibleOrganization data)
        params {:url (make-url ptv "/v11/Service/SourceId/" source-id)
                :method :put
                :form-params data}]
    (-> (http ptv org-id params)
        :body)))

(defn update-service-by-id
  "Update PTV service by its PTV UUID (not source-id), as `org-id`. The org is
   explicit, not the body's :mainResponsibleOrganization: a patch of a
   service made in PTV doesn't send that, as the org writing may be only one
   of its other responsible organisations."
  [ptv org-id service-id data]
  (log/infof "Update PTV service by ID %s" service-id)
  (let [params {:url (make-url ptv "/v11/Service/" service-id)
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
    (log/infof "Create PTV service-location (sourceId: %s, org: %s)" (:sourceId service-location) org-id)
    (-> (http ptv org-id params)
        :body)))

(defn update-service-location
  [ptv service-location-id data]
  (let [org-id (-> data :organizationId)
        params {:url (make-url ptv "/v11/ServiceChannel/ServiceLocation/" service-location-id)
                :method :put
                :form-params data}]
    (log/infof "Update PTV service-location %s" service-location-id)
    (-> (http ptv org-id params)
        :body)))

(defn archive-service-location
  "Archive a service-location by changing only its publishingStatus.

  A full update body would also be validated as a whole, so an unrelated
  problem in it blocks the archive. In particular, PTV's unique-name check
  rejects archiving a duplicate whose name clashes with another channel in
  the org. Everything else on the channel (sourceId, names, descriptions,
  addresses) is kept, so a later full update can re-publish it."
  [ptv org-id service-location-id]
  (let [params {:url (make-url ptv "/v11/ServiceChannel/ServiceLocation/" service-location-id)
                :method :put
                :form-params {:publishingStatus "Deleted"}}]
    (log/infof "Archive PTV service-location %s" service-location-id)
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
    (log/infof "Update service %s connections: %d channels" service-id (count updated-services))
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

;; Resetting a PTV test org (both sides) to its pre-integration state:
;; see lipas.backend.ptv.test-env-reset.
