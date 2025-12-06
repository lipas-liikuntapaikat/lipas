(ns lipas.integration.yti.tietomallit
  "API client for creating LIPAS data model in Tietomallit.

   Creates a Library (Ydintietomalli) with 4-level class hierarchy:
   1. LiikuntaPaikka (base)
   2. Main categories (8)
   3. Sub categories (28)
   4. Type codes (149)

   Each type class has only its relevant properties from lipas.data.prop-types.

   ## API Documentation

   Base URL: https://tietomallit.suomi.fi/datamodel-api
   Swagger UI: https://tietomallit.suomi.fi/datamodel-api/swagger-ui/index.html
   OpenAPI Spec: https://tietomallit.suomi.fi/datamodel-api/v3/api-docs

   ## Authentication

   Requires YTI_API_KEY environment variable with Bearer token.

   ## Usage

   ```clojure
   (require '[lipas.integration.yti.tietomallit :as tietomallit])

   ;; Dry run - see what would be created
   (tietomallit/create-lipas-model! {:dry-run true})

   ;; Create minimal test model
   (tietomallit/create-test-model!)

   ;; Create full model
   (tietomallit/create-lipas-model!)
   ```"
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [lipas.data.prop-types :as prop-types]
   [lipas.data.types :as types]))

;;; Forward declarations

(declare generate-all-classes)

;;; Configuration

(def ^:private base-url "https://tietomallit.suomi.fi/datamodel-api")

(defn- api-key []
  (System/getenv "YTI_API_KEY"))

(defn- auth-headers []
  (if-let [key (api-key)]
    {"Accept" "application/json"
     "Content-Type" "application/json"
     "Authorization" (str "Bearer " key)}
    (throw (ex-info "YTI_API_KEY environment variable not set" {}))))

;;; Progress Tracking

(def ^:dynamic *progress-callback*
  "Dynamic var for progress reporting callback.
   When bound, called with progress maps during sync operations.

   Progress map shape:
   {:phase :creating-classes | :creating-attributes | ...
    :current 42
    :total 186
    :item \"Uimahalli\"
    :status :ok | :error
    :message \"...\"}"
  nil)

(def ^:dynamic *stop-on-error*
  "When true, stop sync operation on first error."
  false)

(defn- report-progress!
  "Report progress if callback is bound."
  [progress-map]
  (when *progress-callback*
    (*progress-callback* progress-map)))

(defn default-progress-callback
  "Default progress reporter that prints to stdout.

   Usage:
   (binding [*progress-callback* default-progress-callback]
     (sync-model!))"
  [{:keys [phase current total item status message elapsed-ms]}]
  (let [pct (when (and current total (pos? total))
              (int (* 100 (/ current total))))
        status-icon (case status
                      :ok "✓"
                      :error "✗"
                      :skipped "~"
                      :start "→"
                      :done "✓"
                      " ")]
    (cond
      ;; Phase start
      (= status :start)
      (println (format "\n[%s] %s (%d items)..." status-icon (name phase) total))

      ;; Phase done
      (= status :done)
      (println (format "[%s] %s completed in %.1fs" status-icon (name phase) (/ (or elapsed-ms 0) 1000.0)))

      ;; Individual item
      (and current total)
      (do
        (print (format "\r  [%3d%%] %d/%d %s %s" pct current total status-icon (or item "")))
        (when (= status :error)
          (println)  ; newline after error
          (println (format "    ERROR: %s" message)))
        (flush))

      ;; Other messages
      :else
      (println (format "[%s] %s" status-icon (or message (name phase)))))))

(defn- process-items-with-progress
  "Process a collection of items with progress reporting.

   Parameters:
     phase - Keyword identifying this phase (e.g., :creating-classes)
     items - Collection of items to process
     process-fn - Function (fn [item] result) that processes each item
     item-name-fn - Function (fn [item] string) that extracts display name

   Returns:
     {:results [...] :ok-count N :error-count N :stopped? bool}

   Respects *stop-on-error* dynamic var."
  [phase items process-fn item-name-fn]
  (let [total (count items)
        start-time (System/currentTimeMillis)]
    (report-progress! {:phase phase :status :start :total total})
    (loop [remaining items
           idx 0
           results []
           error-count 0]
      (if (empty? remaining)
        ;; Done - report completion
        (let [elapsed (- (System/currentTimeMillis) start-time)]
          (report-progress! {:phase phase :status :done :elapsed-ms elapsed})
          (println)  ; newline after progress line
          {:results results
           :ok-count (- (count results) error-count)
           :error-count error-count
           :stopped? false})
        ;; Process next item
        (let [item (first remaining)
              item-name (item-name-fn item)
              result (process-fn item)
              is-error? (= :error (:status result))
              new-error-count (if is-error? (inc error-count) error-count)]
          (report-progress! {:phase phase
                             :current (inc idx)
                             :total total
                             :item item-name
                             :status (if is-error? :error :ok)
                             :message (when is-error?
                                        (str (get-in result [:response :body])))})
          (if (and *stop-on-error* is-error?)
            ;; Stop on error
            (let [elapsed (- (System/currentTimeMillis) start-time)]
              (report-progress! {:phase phase
                                 :status :error
                                 :message (format "Stopped after error at item %d/%d" (inc idx) total)})
              {:results (conj results result)
               :ok-count (- (inc idx) new-error-count)
               :error-count new-error-count
               :stopped? true})
            ;; Continue
            (recur (rest remaining)
                   (inc idx)
                   (conj results result)
                   new-error-count)))))))

;;; HTTP Helpers

(defn- post-json
  "Make POST request with JSON body."
  [url body]
  (let [response (http/post url
                            {:headers (auth-headers)
                             :body (json/write-str body)
                             :throw-exceptions false})]
    {:status (:status response)
     :body (when (seq (:body response))
             (try
               (json/read-str (:body response) :key-fn keyword)
               (catch Exception _
                 (:body response))))}))

(defn- put-json
  "Make PUT request with JSON body."
  [url body]
  (let [response (http/put url
                           {:headers (auth-headers)
                            :body (json/write-str body)
                            :throw-exceptions false})]
    {:status (:status response)
     :body (when (seq (:body response))
             (try
               (json/read-str (:body response) :key-fn keyword)
               (catch Exception _
                 (:body response))))}))

(defn- delete-resource
  "Make DELETE request."
  [url]
  (let [response (http/delete url
                              {:headers (auth-headers)
                               :throw-exceptions false})]
    {:status (:status response)
     :body (:body response)}))

(defn- success? [response]
  (<= 200 (:status response) 299))

;;; Model Metadata

(def organization-id
  "Jyväskylän yliopisto organization UUID in YTI."
  "4a7795bb-c165-486e-96ff-0e3258527d71")

(def service-category-sports
  "P27 = Liikunta ja ulkoilu (Sports and outdoor activities).
   Note: The API expects just the identifier 'P27', not the full URN."
  "P27")

(def model-metadata
  "LIPAS data model metadata for creating the library."
  {:prefix "lipas"
   :label {:fi "LIPAS Liikuntapaikkatietomalli"
           :en "LIPAS Sports Facility Data Model"
           :sv "LIPAS Datamodell för idrottsanläggningar"}
   :description {:fi "Suomen liikuntapaikkojen kansallinen tietomalli. Sisältää liikuntapaikkatyypit ja niiden ominaisuudet."
                 :en "National data model for Finnish sports facilities. Contains facility types and their properties."
                 :sv "Nationell datamodell för finländska idrottsanläggningar."}
   :languages ["fi" "en" "sv"]
   :status "DRAFT"
   :organizations [organization-id]
   :groups [service-category-sports]
   :contact "lipasinfo@jyu.fi"
   :externalNamespaces []
   :terminologies []})

;; Code lists to reference (added after model creation via PUT)
(def code-list-uris
  ["http://uri.suomi.fi/codelist/educ/lipas-type-codes"
   "http://uri.suomi.fi/codelist/educ/lipas-admin-types"
   "http://uri.suomi.fi/codelist/educ/lipas-owner-types"
   "http://uri.suomi.fi/codelist/educ/lipas-statuses"
   "http://uri.suomi.fi/codelist/educ/lipas-surface-materials"
   "http://uri.suomi.fi/codelist/jhs/kunta"])

;;; Class Name Generation

(defn ->class-identifier
  "Convert Finnish name to valid class identifier (PascalCase, ASCII only).

   Examples:
     'Uimahalli' -> 'Uimahalli'
     'Ulkokentät ja liikuntapuistot' -> 'UlkokentatJaLiikuntapuistot'
     'Virkistyskohteet ja palvelut' -> 'VirkistyskohteetJaPalvelut'"
  [name-fi]
  (-> name-fi
      (str/replace #"[äÄ]" "a")
      (str/replace #"[öÖ]" "o")
      (str/replace #"[åÅ]" "a")
      (str/replace #"[^a-zA-Z0-9]+" " ")
      (str/trim)
      (str/split #"\s+")
      (->> (map str/capitalize)
           (str/join ""))))

;;; Language Normalization

(defn normalize-lang-map
  "Normalize language keys in a map.
   LIPAS uses :se for Swedish but YTI requires :sv (ISO 639-1).
   Also ensures all required languages (fi, en, sv) are present,
   falling back to Finnish if a translation is missing."
  [m]
  (when m
    (let [normalized (cond-> m
                       (contains? m :se) (-> (assoc :sv (:se m))
                                             (dissoc :se)))
          fi-value (:fi normalized)]
      ;; Ensure all languages exist, falling back to Finnish
      (merge {:fi fi-value
              :en (or (:en normalized) fi-value)
              :sv (or (:sv normalized) fi-value)}
             normalized))))

;;; DTO Generators

(defn ->model-dto
  "Generate DataModelDTO for creating the LIPAS library.

   Options:
     :prefix - Override the prefix (default \"lipas\")"
  [& {:keys [prefix] :or {prefix "lipas"}}]
  (assoc model-metadata :prefix prefix))

(defn ->class-uri
  "Generate full class URI for referencing within the model.
   Uses the full https://iri.suomi.fi/model/{prefix}/{identifier} format
   which is required for internal class references."
  [prefix identifier]
  (str "https://iri.suomi.fi/model/" prefix "/" identifier))

(defn ->class-dto
  "Generate ClassDTO from a type/category definition.

   Parameters:
     name-map - {:fi \"...\" :en \"...\" :sv \"...\"}
     description-map - {:fi \"...\" :en \"...\"} or nil
     parent-identifier - Parent class identifier (e.g., \"LiikuntaPaikka\") or nil for base class
     prefix - Model prefix for subClassOf URIs (default \"lipas\")"
  [name-map description-map parent-identifier & {:keys [prefix] :or {prefix "lipas"}}]
  (let [identifier (->class-identifier (:fi name-map))]
    (cond-> {:identifier identifier
             :label (normalize-lang-map name-map)}
      description-map (assoc :note (normalize-lang-map description-map))
      parent-identifier (assoc :subClassOf [(->class-uri prefix parent-identifier)]))))

(defn ->base-class-dto
  "Generate the base LiikuntaPaikka class DTO."
  []
  {:identifier "LiikuntaPaikka"
   :label {:fi "Liikuntapaikka"
           :en "Sports Facility"
           :sv "Idrottsanläggning"}
   :note {:fi "Liikuntaan tai ulkoiluun tarkoitettu paikka, alue tai reitti."
          :en "A place, area, or route intended for sports or outdoor recreation."
          :sv "En plats, ett område eller en led avsedd för idrott eller friluftsliv."}
   :subClassOf []})

(defn ->main-category-dto
  "Generate ClassDTO for a main category (e.g., 3000 Vesiliikuntapaikat)."
  [type-code category-data & {:keys [prefix] :or {prefix "lipas"}}]
  (->class-dto (:name category-data)
               {:fi (str "Pääluokka " type-code)}
               "LiikuntaPaikka"
               :prefix prefix))

(defn ->sub-category-dto
  "Generate ClassDTO for a sub-category (e.g., 3100 Uimahallit)."
  [type-code sub-data main-category-code & {:keys [prefix] :or {prefix "lipas"}}]
  (let [main-name (get-in types/main-categories [main-category-code :name :fi])]
    (->class-dto (:name sub-data)
                 {:fi (str "Alaluokka " type-code)}
                 (->class-identifier main-name)
                 :prefix prefix)))

(defn ->type-dto
  "Generate ClassDTO for a specific type (e.g., 3110 Uimahalli)."
  [type-code type-data & {:keys [prefix] :or {prefix "lipas"}}]
  (let [sub-code (:sub-category type-data)
        sub-name (get-in types/sub-categories [sub-code :name :fi])]
    (->class-dto (:name type-data)
                 (:description type-data)
                 (->class-identifier sub-name)
                 :prefix prefix)))

;;; Attribute DTO Generation

(def xsd-ns "http://www.w3.org/2001/XMLSchema#")

(def lipas->xsd-type
  "Map LIPAS data types to XSD types.
   Uses full URIs as required by the Tietomallit API.

   NOTE: Not all XSD types are supported. xsd:gYear is not allowed,
   use xsd:integer for years instead."
  {"string"    (str xsd-ns "string")
   "numeric"   (str xsd-ns "decimal")
   "boolean"   (str xsd-ns "boolean")
   "year"      (str xsd-ns "integer")     ; gYear not supported, use integer
   "datetime"  (str xsd-ns "dateTime")
   "uri"       (str xsd-ns "anyURI")
   "enum"      (str xsd-ns "string")      ; Enums are strings, link to code list separately
   "enum-coll" (str xsd-ns "string")})

(defn ->attribute-identifier
  "Convert property key to valid attribute identifier.
   Uses camelCase convention for attributes.

   Examples:
     :height-m -> 'heightM'
     :heating? -> 'heating'
     :pool-tracks-count -> 'poolTracksCount'"
  [prop-key]
  (-> (name prop-key)
      (str/replace #"\?" "")
      (str/replace #"-(\w)" (fn [[_ c]] (str/upper-case c)))))

(defn ->attribute-dto
  "Generate AttributeDTO from a LIPAS property definition.

   Parameters:
     prop-key - Property keyword (e.g., :height-m)
     prop-def - Property definition map from prop-types
     domain-class - Class identifier the attribute belongs to (e.g., 'LiikuntaPaikka')
     prefix - Model prefix (default 'lipas')"
  [prop-key prop-def domain-class & {:keys [prefix] :or {prefix "lipas"}}]
  (let [identifier (->attribute-identifier prop-key)
        xsd-type (get lipas->xsd-type (:data-type prop-def) "xsd:string")]
    (cond-> {:identifier identifier
             :label (normalize-lang-map (:name prop-def))
             :range xsd-type}
      ;; Add domain if class specified
      domain-class
      (assoc :domain (->class-uri prefix domain-class))
      ;; Add description if available
      (seq (get-in prop-def [:description :fi]))
      (assoc :note (normalize-lang-map (:description prop-def))))))

;;; Core Attribute Definitions (for base LiikuntaPaikka class)

(def core-attributes
  "Core attributes that apply to all sports facilities (LiikuntaPaikka).
   These are the most fundamental properties that every facility has."
  {:lipas-id
   {:name {:fi "LIPAS-tunniste"
           :en "LIPAS ID"
           :sv "LIPAS-identifierare"}
    :data-type "numeric"
    :description {:fi "Liikuntapaikan yksilöivä LIPAS-tunniste"
                  :en "Unique LIPAS identifier for the sports facility"
                  :sv "Unik LIPAS-identifierare för idrottsanläggningen"}}

   :name
   {:name {:fi "Nimi"
           :en "Name"
           :sv "Namn"}
    :data-type "string"
    :description {:fi "Liikuntapaikan nimi"
                  :en "Name of the sports facility"
                  :sv "Idrottsanläggningens namn"}}

   :marketing-name
   {:name {:fi "Markkinointinimi"
           :en "Marketing name"
           :sv "Marknadsföringsnamn"}
    :data-type "string"
    :description {:fi "Liikuntapaikan markkinointinimi"
                  :en "Marketing name of the facility"
                  :sv "Anläggningens marknadsföringsnamn"}}

   :construction-year
   {:name {:fi "Rakennusvuosi"
           :en "Construction year"
           :sv "Byggnadsår"}
    :data-type "year"
    :description {:fi "Liikuntapaikan rakentamisvuosi"
                  :en "Year the facility was constructed"
                  :sv "Året då anläggningen byggdes"}}

   :renovation-years
   {:name {:fi "Peruskorjausvuodet"
           :en "Renovation years"
           :sv "Renoveringsår"}
    :data-type "string"
    :description {:fi "Liikuntapaikan peruskorjausvuodet (pilkulla erotettuna)"
                  :en "Renovation years (comma-separated)"
                  :sv "Renoveringsår (kommaseparerade)"}}})

;;; API Operations

(defn create-library!
  "Create a new library (core data model) in Tietomallit.

   Returns {:status :created :uri \"...\"} on success,
           {:status :error :response {...}} on failure."
  [model-dto]
  (let [url (str base-url "/v2/model/library")
        response (post-json url model-dto)]
    (if (success? response)
      {:status :created
       :uri (str "https://tietomallit.suomi.fi/model/" (:prefix model-dto))
       :response response}
      {:status :error
       :response response})))

(defn add-class!
  "Add a class to an existing library.

   Parameters:
     prefix - Model prefix (e.g., \"lipas\")
     class-dto - ClassDTO map

   Returns {:status :created} on success."
  [prefix class-dto]
  (let [url (str base-url "/v2/class/library/" prefix)
        response (post-json url class-dto)]
    (if (success? response)
      {:status :created
       :identifier (:identifier class-dto)
       :response response}
      {:status :error
       :identifier (:identifier class-dto)
       :response response})))

(defn add-attribute!
  "Add an attribute to an existing library.

   Parameters:
     prefix - Model prefix (e.g., \"lipas\")
     attribute-dto - ResourceDTO for attribute

   Returns {:status :created} on success."
  [prefix attribute-dto]
  (let [url (str base-url "/v2/resource/library/" prefix "/attribute")
        response (post-json url attribute-dto)]
    (if (success? response)
      {:status :created
       :identifier (:identifier attribute-dto)
       :response response}
      {:status :error
       :identifier (:identifier attribute-dto)
       :response response})))

(defn update-attribute!
  "Update an existing attribute in a library.

   Parameters:
     prefix - Model prefix (e.g., \"lipas\")
     identifier - Attribute identifier (e.g., \"name\")
     attribute-dto - ResourceDTO with updated fields

   Returns {:status :updated} on success."
  [prefix identifier attribute-dto]
  (let [url (str base-url "/v2/resource/library/" prefix "/attribute/" identifier)
        response (put-json url attribute-dto)]
    (if (success? response)
      {:status :updated
       :identifier identifier
       :response response}
      {:status :error
       :identifier identifier
       :response response})))

(defn add-association!
  "Add an association between classes.

   Parameters:
     prefix - Model prefix (e.g., \"lipas\")
     association-dto - ResourceDTO for association

   Returns {:status :created} on success."
  [prefix association-dto]
  (let [url (str base-url "/v2/resource/library/" prefix "/association")
        response (post-json url association-dto)]
    (if (success? response)
      {:status :created
       :identifier (:identifier association-dto)
       :response response}
      {:status :error
       :identifier (:identifier association-dto)
       :response response})))

(defn add-property-to-class!
  "Add an existing attribute or association to a class.

   In YTI, attributes are created at the model level and then must be
   explicitly added to classes as property references.

   Parameters:
     prefix - Model prefix (e.g., \"lipasdev6\")
     class-identifier - Class to add property to (e.g., \"Sijainti\")
     property-uri - Full URI of the attribute/association

   Returns {:status :updated} on success."
  [prefix class-identifier property-uri]
  (let [url (str base-url "/v2/class/library/" prefix "/" class-identifier "/properties")
        response (http/put url
                           {:headers (auth-headers)
                            :query-params {:uri property-uri}
                            :throw-exceptions false})]
    (if (<= 200 (:status response) 299)
      {:status :updated
       :class class-identifier
       :property property-uri}
      {:status :error
       :class class-identifier
       :property property-uri
       :response {:status (:status response)
                  :body (when (seq (:body response))
                          (try
                            (json/read-str (:body response) :key-fn keyword)
                            (catch Exception _
                              (:body response))))}})))

(defn add-code-list!
  "Add code list to a library class attribute.

   Code lists must be added via a separate endpoint after the attribute is created
   AND added to the class as a property reference.

   Parameters:
     prefix - Model prefix (e.g., \"lipasdev6\")
     class-identifier - Class containing the attribute (e.g., \"Sijainti\")
     attribute-uri - Full URI of the attribute (e.g., \"https://iri.suomi.fi/model/lipasdev6/cityCode\")
     code-list-uris - Vector of code list URIs to link

   Returns {:status :updated} on success."
  [prefix class-identifier attribute-uri code-list-uris]
  (let [url (str base-url "/v2/class/library/" prefix "/" class-identifier "/codeList")
        payload {:attributeUri attribute-uri
                 :codeLists code-list-uris}
        response (put-json url payload)]
    (if (success? response)
      {:status :updated
       :attribute-uri attribute-uri
       :code-lists code-list-uris
       :response response}
      {:status :error
       :attribute-uri attribute-uri
       :response response})))

(defn delete-model!
  "Delete a draft model. Only works for DRAFT status models.

   Parameters:
     prefix - Model prefix (e.g., \"lipas\")"
  [prefix]
  (let [url (str base-url "/v2/model/" prefix)
        response (delete-resource url)]
    (if (success? response)
      {:status :deleted :prefix prefix}
      {:status :error :response response})))

;;; Orchestration

(defn create-test-model!
  "Create a minimal test model to verify API workflow.

   Creates:
   - lipas library
   - LiikuntaPaikka base class
   - Vesiliikuntapaikat (main category 3000)
   - Uimahallit (sub category 3100)
   - Uimahalli (type 3110)

   Options:
     :prefix - Model prefix (default \"lipas\")
     :dry-run - If true, just return the DTOs without calling API"
  [& {:keys [prefix dry-run] :or {prefix "lipas" dry-run false}}]
  (let [model-dto (->model-dto :prefix prefix)
        base-class (->base-class-dto)
        main-category (->main-category-dto 3000 (types/main-categories 3000) :prefix prefix)
        sub-category (->sub-category-dto 3100 (types/sub-categories 3100) 3000 :prefix prefix)
        type-class (->type-dto 3110 (types/all 3110) :prefix prefix)]

    (if dry-run
      {:prefix prefix
       :model model-dto
       :classes [base-class main-category sub-category type-class]}

      ;; Execute API calls
      (let [results {:prefix prefix
                     :model (create-library! model-dto)}]
        (if (= :created (:status (:model results)))
          ;; Model created, add classes
          (assoc results
                 :base-class (add-class! prefix base-class)
                 :main-category (add-class! prefix main-category)
                 :sub-category (add-class! prefix sub-category)
                 :type-class (add-class! prefix type-class))
          ;; Model creation failed
          results)))))

(defn create-lipas-model!
  "Create the complete LIPAS data model in Tietomallit.

   Creates all 186 classes (1 base + 8 main + 28 sub + 149 types).

   Options:
     :dry-run - If true, just return the DTOs without calling API

   Returns summary of created resources."
  [& {:keys [dry-run] :or {dry-run false}}]
  ;; TODO: Implement full model creation
  ;; For now, delegate to test model
  (create-test-model! :dry-run dry-run))

(defn add-core-attributes!
  "Add core attributes to an existing model's base class.

   Parameters:
     prefix - Model prefix (e.g., 'lipasdev3')
     domain-class - Class to attach attributes to (default 'LiikuntaPaikka')
     dry-run - If true, just return the DTOs without calling API

   Returns summary of created attributes."
  [prefix & {:keys [domain-class dry-run] :or {domain-class "LiikuntaPaikka" dry-run false}}]
  (let [attribute-dtos (mapv (fn [[prop-key prop-def]]
                               (->attribute-dto prop-key prop-def domain-class :prefix prefix))
                             core-attributes)]
    (if dry-run
      {:prefix prefix
       :domain-class domain-class
       :attributes attribute-dtos}

      ;; Execute API calls
      {:prefix prefix
       :domain-class domain-class
       :results (mapv (fn [attr-dto]
                        (let [result (add-attribute! prefix attr-dto)]
                          (assoc result :identifier (:identifier attr-dto))))
                      attribute-dtos)})))

;;; Model Spec Loading

(def model-spec-path
  "Path to the model specification EDN file."
  "lipas/integration/yti/data/model-spec.edn")

(defn load-model-spec
  "Load the model specification from EDN file.

   Returns the parsed spec map, or nil if file doesn't exist."
  []
  (when-let [resource (io/resource model-spec-path)]
    (edn/read-string (slurp resource))))

;;; XSD Type Resolution

(def xsd-type-uris
  "Map of XSD type keywords to full URIs."
  {:xsd/string   "http://www.w3.org/2001/XMLSchema#string"
   :xsd/integer  "http://www.w3.org/2001/XMLSchema#integer"
   :xsd/decimal  "http://www.w3.org/2001/XMLSchema#decimal"
   :xsd/boolean  "http://www.w3.org/2001/XMLSchema#boolean"
   :xsd/dateTime "http://www.w3.org/2001/XMLSchema#dateTime"
   :xsd/anyURI   "http://www.w3.org/2001/XMLSchema#anyURI"})

(defn resolve-xsd-type
  "Resolve XSD type keyword to full URI, or pass through if already a URI string."
  [type-spec]
  (if (keyword? type-spec)
    (get xsd-type-uris type-spec (str type-spec))
    type-spec))

;;; Sync Functions

(defn spec->model-dto
  "Convert model spec to API DataModelDTO."
  [spec & {:keys [use-dev-prefix] :or {use-dev-prefix true}}]
  (let [model (:model spec)
        prefix (if (and use-dev-prefix (:dev-prefix model))
                 (:dev-prefix model)
                 (:prefix model))]
    {:prefix prefix
     :label (normalize-lang-map (:label model))
     :description (normalize-lang-map (:description model))
     :languages (:languages model)
     :status (:status model)
     :organizations (:organizations model)
     :groups (:groups model)
     :contact (:contact model)
     :externalNamespaces (:external-namespaces spec)
     :terminologies []}))

(defn spec->class-dto
  "Convert a class spec to API ClassDTO."
  [class-spec prefix]
  (cond-> {:identifier (:identifier class-spec)
           :label (normalize-lang-map (:label class-spec))}
    (:note class-spec)
    (assoc :note (normalize-lang-map (:note class-spec)))

    (seq (:sub-class-of class-spec))
    (assoc :subClassOf (mapv #(if (str/starts-with? % "http")
                                %
                                (->class-uri prefix %))
                             (:sub-class-of class-spec)))

    (seq (:equivalent-class class-spec))
    (assoc :equivalentClass (:equivalent-class class-spec))))

(defn spec->attribute-dto
  "Convert an attribute spec to API ResourceDTO."
  [attr-spec prefix terminology-subjects code-lists]
  (let [subject-key (:subject-key attr-spec)
        subject-uri (when subject-key (get terminology-subjects subject-key))
        code-list-key (:code-list-key attr-spec)
        code-list-uri (when code-list-key (get code-lists code-list-key))]
    (cond-> {:identifier (:identifier attr-spec)
             :label (normalize-lang-map (:label attr-spec))
             :domain (->class-uri prefix (:domain attr-spec))
             :range (resolve-xsd-type (:range attr-spec))}
      (:note attr-spec)
      (assoc :note (normalize-lang-map (:note attr-spec)))

      subject-uri
      (assoc :subject subject-uri)

      code-list-uri
      (assoc :codeLists [code-list-uri]))))

(defn spec->association-dto
  "Convert an association spec to API ResourceDTO."
  [assoc-spec prefix]
  (cond-> {:identifier (:identifier assoc-spec)
           :label (normalize-lang-map (:label assoc-spec))
           :domain (->class-uri prefix (:domain assoc-spec))
           :range (->class-uri prefix (:range assoc-spec))}
    (:note assoc-spec)
    (assoc :note (normalize-lang-map (:note assoc-spec)))))

(defn add-properties-to-classes!
  "Add all attributes to their domain classes.

   In YTI, attributes are created at the model level and must be
   explicitly added to classes as property references before code lists
   can be linked.

   Parameters:
     prefix - Model prefix
     attributes - Attribute specs from model-spec.edn

   Returns vector of results for each property addition."
  [prefix attributes]
  (mapv (fn [attr]
          (let [attr-uri (str "https://iri.suomi.fi/model/" prefix "/" (:identifier attr))
                domain (:domain attr)]
            (add-property-to-class! prefix domain attr-uri)))
        attributes))

(defn add-code-lists-for-attributes!
  "Add code list links for all attributes that have code-list-key defined.

   This must be called after attributes are created AND added to classes,
   as code lists are managed through a separate endpoint.

   Parameters:
     prefix - Model prefix
     attributes - Attribute specs from model-spec.edn
     code-lists - Map of code list keys to URIs

   Returns vector of results for each code list link operation."
  [prefix attributes code-lists]
  (let [attrs-with-code-lists (filter :code-list-key attributes)]
    (mapv (fn [attr]
            (let [attr-uri (str "https://iri.suomi.fi/model/" prefix "/" (:identifier attr))
                  code-list-key (:code-list-key attr)
                  code-list-uri (get code-lists code-list-key)
                  domain (:domain attr)]
              (if code-list-uri
                (add-code-list! prefix domain attr-uri [code-list-uri])
                {:status :skipped
                 :identifier (:identifier attr)
                 :reason (str "No URI found for code-list-key: " code-list-key)})))
          attrs-with-code-lists)))

(defn- should-generate-type-hierarchy?
  "Check if the spec contains a :generate-from-types directive."
  [spec]
  (some :generate-from-types (:classes spec)))

(defn- get-type-hierarchy-config
  "Get the type hierarchy configuration from the spec."
  [spec]
  (first (filter :generate-from-types (:classes spec))))

(defn- should-generate-type-specific-attrs?
  "Check if the spec indicates type-specific attributes should be generated."
  [spec]
  (get-in spec [:type-specific-attributes :generate?]))

;;; Type-Specific Attribute Generation
;;; (Must be defined before sync-model! which uses these functions)

(defn get-all-used-props
  "Get all unique property keys used across all type codes."
  []
  (set (mapcat (comp keys :props) (vals types/all))))

(defn prop-def->attribute-dto
  "Convert a prop-types definition to an attribute DTO.

   Parameters:
     prop-key - Property key (e.g., :height-m)
     prefix - Model prefix

   Note: These attributes are created without a domain - they will be
   added to specific type classes via property references."
  [prop-key prefix]
  (let [prop-def (prop-types/all prop-key)
        identifier (->attribute-identifier prop-key)
        xsd-type (get lipas->xsd-type (:data-type prop-def) (str xsd-ns "string"))]
    (cond-> {:identifier identifier
             :label (normalize-lang-map (:name prop-def))
             :range xsd-type}
      ;; Add description if available
      (seq (get-in prop-def [:description :fi]))
      (assoc :note (normalize-lang-map (:description prop-def))))))

(defn generate-type-specific-attributes
  "Generate attribute DTOs for all props used across all types.

   Returns a vector of attribute DTOs (without domain - will be added
   to types via property references)."
  [prefix]
  (let [used-props (get-all-used-props)]
    (mapv #(prop-def->attribute-dto % prefix) used-props)))

(defn type-code->class-identifier
  "Get the class identifier for a type code."
  [type-code]
  (when-let [type-data (types/all type-code)]
    (->class-identifier (get-in type-data [:name :fi]))))

(defn get-prop-to-types-mapping
  "Build a map of prop-key -> [type-codes that use it].

   This is used to know which types each attribute should be added to."
  []
  (reduce (fn [acc [type-code type-data]]
            (reduce (fn [inner-acc prop-key]
                      (update inner-acc prop-key (fnil conj []) type-code))
                    acc
                    (keys (:props type-data))))
          {}
          types/all))

(defn generate-type-attribute-assignments
  "Generate a list of {attribute-identifier, class-identifier} pairs
   showing which attributes should be assigned to which type classes.

   Returns a vector of maps:
   [{:attribute \"heightM\" :class \"Uimahalli\" :type-code 3110}]"
  []
  (let [prop-to-types (get-prop-to-types-mapping)]
    (vec
     (for [[prop-key type-codes] prop-to-types
           type-code type-codes
           :let [class-id (type-code->class-identifier type-code)]
           :when class-id]
       {:attribute (->attribute-identifier prop-key)
        :class class-id
        :type-code type-code}))))

(defn add-type-specific-attributes!
  "Create type-specific attributes and assign them to their type classes.

   This is a two-step process:
   1. Create all unique attributes at model level
   2. Add property references from each type class to its attributes

   Parameters:
     prefix - Model prefix
     dry-run - If true, return plan without executing

   Returns summary of created attributes and property references."
  [prefix & {:keys [dry-run] :or {dry-run false}}]
  (let [attr-dtos (generate-type-specific-attributes prefix)
        assignments (generate-type-attribute-assignments)]
    (if dry-run
      {:prefix prefix
       :attributes attr-dtos
       :attribute-count (count attr-dtos)
       :assignments assignments
       :assignment-count (count assignments)
       :sample-assignments (take 10 assignments)}

      ;; Execute with progress: first create attributes, then add to classes
      (let [;; Step 1: Create all attributes with progress
            attr-progress (process-items-with-progress
                           :creating-type-specific-attributes
                           (vec attr-dtos)
                           (fn [attr-dto]
                             (let [result (add-attribute! prefix attr-dto)]
                               (assoc result :identifier (:identifier attr-dto))))
                           #(:identifier %))
            attr-results (:results attr-progress)

            created-attrs (set (map :identifier (filter #(= :created (:status %)) attr-results)))

            ;; Step 2: Add property references with progress (only for created attrs)
            property-progress (when-not (:stopped? attr-progress)
                                (process-items-with-progress
                                 :assigning-type-attributes
                                 (vec assignments)
                                 (fn [{:keys [attribute class]}]
                                   (if (contains? created-attrs attribute)
                                     (let [attr-uri (str "https://iri.suomi.fi/model/" prefix "/" attribute)]
                                       (add-property-to-class! prefix class attr-uri))
                                     {:status :skipped
                                      :attribute attribute
                                      :class class
                                      :reason "Attribute not created"}))
                                 #(str (:class %) "." (:attribute %))))
            property-results (or (:results property-progress) [])]
        {:prefix prefix
         :attribute-results attr-results
         :attribute-counts {:total (count attr-results)
                            :created (:ok-count attr-progress)
                            :failed (:error-count attr-progress)}
         :property-results property-results
         :property-counts {:total (count property-results)
                           :added (or (:ok-count property-progress) 0)
                           :skipped (count (filter #(= :skipped (:status %)) property-results))
                           :failed (or (:error-count property-progress) 0)}
         :stopped? (or (:stopped? attr-progress)
                       (:stopped? property-progress))}))))

;;; Sync Functions

(defn sync-model!
  "Sync model specification to YTI.

   This function:
   1. Creates the model (library) if it doesn't exist
   2. Creates all classes defined in the spec (including generated type hierarchy)
   3. Creates all attributes with terminology subject links
   4. Creates all associations
   5. Adds attributes to their domain classes (required for code list linking)
   6. Links code lists to attributes (separate API call)
   7. Creates type-specific attributes and assigns them to type classes

   The :generate-from-types directive in :classes triggers automatic generation
   of the 186-class type hierarchy (1 base + 8 main + 28 sub + 149 types).

   ## Progress Tracking

   Bind *progress-callback* to receive progress updates during sync:

     (binding [tietomallit/*progress-callback* tietomallit/default-progress-callback]
       (sync-model!))

   Or use the convenience wrapper:

     (sync-model-with-progress!)
     (sync-model-with-progress! :stop-on-error true)

   Bind *stop-on-error* to true to abort on first error.

   ## Options

     :use-dev-prefix - Use dev-prefix instead of production prefix (default true)
     :dry-run - Return DTOs without making API calls (default false)
     :skip-existing - Skip resources that already exist (default true)
     :include-types - Include all 149 type classes in hierarchy (default true)

   Returns a summary of created resources and any errors."
  [& {:keys [use-dev-prefix dry-run skip-existing include-types]
      :or {use-dev-prefix true dry-run false skip-existing true include-types true}}]
  (let [spec (load-model-spec)
        model-dto (spec->model-dto spec :use-dev-prefix use-dev-prefix)
        prefix (:prefix model-dto)
        terminology-subjects (:terminology-subjects spec)
        code-lists (:code-lists spec)

        ;; Filter to only core classes (not generated ones)
        core-classes (filter #(and (:identifier %)
                                   (not (:generate-from-types %)))
                             (:classes spec))
        core-class-dtos (mapv #(spec->class-dto % prefix) core-classes)

        ;; Check if we need to generate type hierarchy
        generate-hierarchy? (should-generate-type-hierarchy? spec)
        hierarchy-classes (when generate-hierarchy?
                            (let [all-classes (generate-all-classes prefix)]
                              (if include-types
                                (:all all-classes)
                                (concat (:base all-classes)
                                        (:main-categories all-classes)
                                        (:sub-categories all-classes)))))

        ;; Combine core classes with generated hierarchy
        ;; Note: LiikuntaPaikka is in both core and hierarchy, hierarchy takes precedence
        ;; In lipasdev9+, Sijainti is removed (location embedded in LiikuntaPaikka)
        all-class-dtos (if generate-hierarchy?
                         ;; Just use the hierarchy (LiikuntaPaikka -> main -> sub -> types)
                         ;; Core classes that aren't in hierarchy would be added separately
                         hierarchy-classes
                         core-class-dtos)

        ;; Core attributes from spec
        core-attr-dtos (mapv #(spec->attribute-dto % prefix terminology-subjects code-lists)
                             (:attributes spec))

        ;; Check if we need to generate type-specific attributes
        generate-type-attrs? (and include-types
                                  generate-hierarchy?
                                  (should-generate-type-specific-attrs? spec))
        type-specific-attr-dtos (when generate-type-attrs?
                                  (generate-type-specific-attributes prefix))
        type-attr-assignments (when generate-type-attrs?
                                (generate-type-attribute-assignments))

        ;; Combine core and type-specific attributes
        all-attr-dtos (if generate-type-attrs?
                        (into (vec core-attr-dtos) type-specific-attr-dtos)
                        core-attr-dtos)

        assoc-dtos (mapv #(spec->association-dto % prefix)
                         (:associations spec))]

    (if dry-run
      ;; Return what would be created
      {:prefix prefix
       :model model-dto
       :classes all-class-dtos
       :class-counts {:core (count core-class-dtos)
                      :hierarchy (count hierarchy-classes)
                      :total (count all-class-dtos)}
       :attributes all-attr-dtos
       :attribute-counts {:core (count core-attr-dtos)
                          :type-specific (count type-specific-attr-dtos)
                          :total (count all-attr-dtos)}
       :associations assoc-dtos
       :code-list-attrs (filter :code-list-key (:attributes spec))
       :type-attr-assignments (when generate-type-attrs?
                                {:total (count type-attr-assignments)
                                 :sample (take 10 type-attr-assignments)})
       :generate-hierarchy? generate-hierarchy?
       :generate-type-attrs? generate-type-attrs?}

      ;; Execute sync with progress tracking
      (let [sync-start-time (System/currentTimeMillis)
            _ (report-progress! {:phase :creating-model :status :start :total 1})
            model-result (create-library! model-dto)
            _ (report-progress! {:phase :creating-model
                                 :current 1 :total 1
                                 :item (:prefix model-dto)
                                 :status (if (= :created (:status model-result)) :ok :error)
                                 :message (when (= :error (:status model-result))
                                            (str (get-in model-result [:response :body])))})
            results {:prefix prefix
                     :model-result model-result
                     :class-results []
                     :attribute-results []
                     :association-results []
                     :property-results []
                     :code-list-results []
                     :type-attr-results nil
                     :hierarchy-generated? generate-hierarchy?
                     :type-attrs-generated? generate-type-attrs?}]

        (if (= :created (:status model-result))
          ;; Model created successfully
          ;; Order matters: classes -> core attributes -> associations -> properties -> code lists -> type attrs
          (let [;; 1. Create classes with progress
                class-progress (process-items-with-progress
                                :creating-classes
                                (vec all-class-dtos)
                                #(add-class! prefix %)
                                #(:identifier %))
                class-results (:results class-progress)

                ;; Check for stop-on-error
                _ (when (:stopped? class-progress)
                    (report-progress! {:phase :sync :status :error
                                       :message "Stopped due to class creation error"}))

                ;; 2. Create core attributes with progress
                attr-progress (when-not (:stopped? class-progress)
                                (process-items-with-progress
                                 :creating-core-attributes
                                 (vec core-attr-dtos)
                                 #(add-attribute! prefix %)
                                 #(:identifier %)))
                attr-results (or (:results attr-progress) [])

                ;; 3. Create associations with progress
                assoc-progress (when-not (or (:stopped? class-progress)
                                             (:stopped? attr-progress))
                                 (process-items-with-progress
                                  :creating-associations
                                  (vec assoc-dtos)
                                  #(add-association! prefix %)
                                  #(:identifier %)))
                assoc-results (or (:results assoc-progress) [])

                ;; 4. Add properties to classes with progress
                property-progress (when-not (or (:stopped? class-progress)
                                                (:stopped? attr-progress)
                                                (:stopped? assoc-progress))
                                    (process-items-with-progress
                                     :adding-properties-to-classes
                                     (vec (:attributes spec))
                                     (fn [attr]
                                       (let [attr-uri (str "https://iri.suomi.fi/model/" prefix "/" (:identifier attr))
                                             domain (:domain attr)]
                                         (add-property-to-class! prefix domain attr-uri)))
                                     #(:identifier %)))
                property-results (or (:results property-progress) [])

                ;; 5. Link code lists with progress
                attrs-with-code-lists (filter :code-list-key (:attributes spec))
                code-list-progress (when-not (or (:stopped? class-progress)
                                                 (:stopped? attr-progress)
                                                 (:stopped? assoc-progress)
                                                 (:stopped? property-progress))
                                     (process-items-with-progress
                                      :linking-code-lists
                                      (vec attrs-with-code-lists)
                                      (fn [attr]
                                        (let [attr-uri (str "https://iri.suomi.fi/model/" prefix "/" (:identifier attr))
                                              code-list-key (:code-list-key attr)
                                              code-list-uri (get code-lists code-list-key)
                                              domain (:domain attr)]
                                          (if code-list-uri
                                            (add-code-list! prefix domain attr-uri [code-list-uri])
                                            {:status :skipped
                                             :identifier (:identifier attr)
                                             :reason (str "No URI for " code-list-key)})))
                                      #(:identifier %)))
                code-list-results (or (:results code-list-progress) [])

                ;; 6. Type-specific attributes (if enabled)
                type-attr-results (when (and generate-type-attrs?
                                             (not (:stopped? class-progress))
                                             (not (:stopped? attr-progress)))
                                    (add-type-specific-attributes! prefix))

                ;; Calculate total time
                total-elapsed (- (System/currentTimeMillis) sync-start-time)]

            (report-progress! {:phase :sync-complete
                               :status :done
                               :elapsed-ms total-elapsed
                               :message (format "Total time: %.1fs" (/ total-elapsed 1000.0))})

            (assoc results
                   :class-results class-results
                   :class-counts {:total (count class-results)
                                  :created (:ok-count class-progress)
                                  :failed (:error-count class-progress)}
                   :attribute-results attr-results
                   :association-results assoc-results
                   :property-results property-results
                   :code-list-results code-list-results
                   :type-attr-results type-attr-results
                   :total-elapsed-ms total-elapsed
                   :stopped? (or (:stopped? class-progress)
                                 (:stopped? attr-progress)
                                 (:stopped? assoc-progress))))
          ;; Model creation failed
          (do
            (report-progress! {:phase :sync :status :error
                               :message "Model creation failed, sync aborted"})
            results))))))

(defn sync-model-with-progress!
  "Convenience wrapper for sync-model! that enables progress reporting.

   Binds *progress-callback* to default-progress-callback and optionally
   enables stop-on-error behavior.

   Options:
     :stop-on-error - Stop on first error (default false)
     :use-dev-prefix - Use dev-prefix from spec (default true)
     :include-types - Include all 149 type classes (default true)

   Example:
     (sync-model-with-progress!)
     (sync-model-with-progress! :stop-on-error true)

   Returns the same result map as sync-model!"
  [& {:keys [stop-on-error use-dev-prefix include-types]
      :or {stop-on-error false use-dev-prefix true include-types true}}]
  (binding [*progress-callback* default-progress-callback
            *stop-on-error* stop-on-error]
    (println "\n╔══════════════════════════════════════════════════════════════╗")
    (println "║           LIPAS YTI Model Sync                               ║")
    (println "╚══════════════════════════════════════════════════════════════╝")
    (println)
    (println "Options:")
    (println "  stop-on-error:" stop-on-error)
    (println "  use-dev-prefix:" use-dev-prefix)
    (println "  include-types:" include-types)
    (sync-model! :use-dev-prefix use-dev-prefix
                 :include-types include-types)))

(defn print-sync-summary
  "Print a human-readable summary of sync results."
  [results]
  (println "\n=== Sync Summary ===\n")
  (println "Prefix:" (:prefix results))
  (println "Model:" (name (get-in results [:model-result :status] :unknown)))

  (when-let [uri (get-in results [:model-result :uri])]
    (println "URL:" uri))

  (when (:hierarchy-generated? results)
    (println "Type hierarchy:" "generated"))

  (println "\nClasses:" (when-let [counts (:class-counts results)]
                          (str "(" (:created counts) "/" (:total counts) " created)")))
  (let [class-results (:class-results results)
        failed (filter #(= :error (:status %)) class-results)]
    ;; Only show first 10 classes + failed ones to avoid flooding output
    (doseq [r (take 10 class-results)]
      (println " " (if (= :created (:status r)) "+" "-")
               (:identifier r)
               (when (= :error (:status r))
                 (str "(" (get-in r [:response :body]) ")"))))
    (when (> (count class-results) 10)
      (println "  ... and" (- (count class-results) 10) "more classes"))
    (when (seq failed)
      (println "\n  Failed classes:")
      (doseq [r failed]
        (println "   -" (:identifier r) ":" (get-in r [:response :body])))))

  (println "\nAttributes:")
  (doseq [r (:attribute-results results)]
    (println " " (if (= :created (:status r)) "+" "-")
             (:identifier r)
             (when (= :error (:status r))
               (str "(" (get-in r [:response :body]) ")"))))

  (println "\nAssociations:")
  (doseq [r (:association-results results)]
    (println " " (if (= :created (:status r)) "+" "-")
             (:identifier r)
             (when (= :error (:status r))
               (str "(" (get-in r [:response :body]) ")"))))

  (println "\nProperties added to classes:")
  (doseq [r (:property-results results)]
    (println " " (if (= :updated (:status r)) "+" "-")
             (:class r) "<-" (last (str/split (or (:property r) "") #"/"))
             (when (= :error (:status r))
               (str "(" (get-in r [:response :body]) ")"))))

  (println "\nCode Lists:")
  (doseq [r (:code-list-results results)]
    (println " " (case (:status r)
                   :updated "+"
                   :skipped "~"
                   "-")
             (or (:identifier r) (:attribute-uri r))
             (cond
               (= :error (:status r)) (str "(" (get-in r [:response :body]) ")")
               (= :skipped (:status r)) (str "(" (:reason r) ")")
               :else nil)))

  ;; Type-specific attributes (if generated)
  (when-let [type-attr-results (:type-attr-results results)]
    (println "\nType-Specific Attributes:")
    (println "  Attributes created:" (get-in type-attr-results [:attribute-counts :created])
             "/" (get-in type-attr-results [:attribute-counts :total]))
    (println "  Property assignments:" (get-in type-attr-results [:property-counts :added])
             "/" (get-in type-attr-results [:property-counts :total]))
    (when (pos? (get-in type-attr-results [:attribute-counts :failed] 0))
      (println "  Failed attributes:" (get-in type-attr-results [:attribute-counts :failed])))
    (when (pos? (get-in type-attr-results [:property-counts :failed] 0))
      (println "  Failed assignments:" (get-in type-attr-results [:property-counts :failed]))))

  (println))

;;; Type Hierarchy Generation

(defn generate-main-category-classes
  "Generate ClassDTOs for all 8 main categories.

   Main categories (1000, 2000, ..., 7000, 0) are children of LiikuntaPaikka.

   Returns a vector of ClassDTO maps."
  [prefix]
  (mapv (fn [[code data]]
          (->main-category-dto code data :prefix prefix))
        types/main-categories))

(defn generate-sub-category-classes
  "Generate ClassDTOs for all 28 sub-categories.

   Sub-categories are children of their respective main categories.

   Returns a vector of ClassDTO maps."
  [prefix]
  (mapv (fn [[code data]]
          (let [main-code (if (string? (:main-category data))
                            (parse-long (:main-category data))
                            (:main-category data))]
            (->sub-category-dto code data main-code :prefix prefix)))
        types/sub-categories))

(defn generate-type-classes
  "Generate ClassDTOs for all 149 type codes.

   Types are children of their respective sub-categories.
   Each type includes:
   - Name in fi/en/sv
   - Description
   - Geometry type as note
   - Type code reference (for semantic linking, workaround for skos:exactMatch)

   Options:
     :include-type-code-in-note? - Add type code reference to notes (default true)

   Returns a vector of ClassDTO maps."
  [prefix & {:keys [include-type-code-in-note?] :or {include-type-code-in-note? true}}]
  (mapv (fn [[code data]]
          (let [base-dto (->type-dto code data :prefix prefix)
                ;; Build note with geometry type and optionally type code
                geometry-type (:geometry-type data)
                note-fi (or (get-in base-dto [:note :fi]) "")
                note-with-geometry (if geometry-type
                                     (str note-fi "\n\nGeometriatyyppi: " geometry-type)
                                     note-fi)
                ;; Add type code reference for semantic linking (skos:exactMatch workaround)
                note-with-code (if include-type-code-in-note?
                                 (str note-with-geometry
                                      "\n\nTyyppikoodi: " code
                                      "\nKoodistolinkki: http://uri.suomi.fi/codelist/educ/lipas-type-codes/code/" code)
                                 note-with-geometry)]
            (assoc-in base-dto [:note :fi] note-with-code)))
        types/all))

(defn generate-all-classes
  "Generate all classes for the type hierarchy.

   Returns a map with:
   - :base - Base LiikuntaPaikka class
   - :main-categories - 8 main category classes
   - :sub-categories - 28 sub-category classes
   - :types - 149 type classes
   - :all - All classes in creation order (parent before child)"
  [prefix]
  (let [base [(->base-class-dto)]
        main-cats (generate-main-category-classes prefix)
        sub-cats (generate-sub-category-classes prefix)
        type-classes (generate-type-classes prefix)]
    {:base base
     :main-categories main-cats
     :sub-categories sub-cats
     :types type-classes
     :all (concat base main-cats sub-cats type-classes)}))

(defn create-hierarchy!
  "Create the complete type hierarchy in an existing model.

   This creates all 186 classes in the correct order (parents first).

   Options:
     :dry-run - If true, return DTOs without calling API
     :include-types - If true, create all 149 type classes (default true)

   Returns {:success true :created [...] :failed [...]}"
  [prefix & {:keys [dry-run include-types] :or {dry-run false include-types true}}]
  (let [all-classes (generate-all-classes prefix)
        classes-to-create (if include-types
                            (:all all-classes)
                            (concat (:base all-classes)
                                    (:main-categories all-classes)
                                    (:sub-categories all-classes)))]
    (if dry-run
      {:prefix prefix
       :counts {:base (count (:base all-classes))
                :main-categories (count (:main-categories all-classes))
                :sub-categories (count (:sub-categories all-classes))
                :types (count (:types all-classes))}
       :classes classes-to-create}

      ;; Execute API calls sequentially (order matters for hierarchy)
      (let [results (reduce
                     (fn [acc class-dto]
                       (let [result (add-class! prefix class-dto)]
                         (if (= :created (:status result))
                           (update acc :created conj (:identifier class-dto))
                           (update acc :failed conj {:identifier (:identifier class-dto)
                                                     :error (:response result)}))))
                     {:created [] :failed []}
                     classes-to-create)]
        (assoc results
               :prefix prefix
               :success (empty? (:failed results))
               :total-created (count (:created results)))))))

;;; Full Model Creation with Hierarchy

(defn create-model-with-hierarchy!
  "Create a complete model with the type hierarchy.

   This is an all-in-one function that:
   1. Creates the library (model)
   2. Creates Sijainti class for location
   3. Creates LiikuntaPaikka -> Main Categories -> Sub Categories -> Types
   4. Adds core attributes
   5. Adds location attributes
   6. Adds the sijainti association

   Options:
     :prefix - Model prefix (default 'lipasdev7')
     :dry-run - If true, return DTOs without calling API
     :include-types - If true, create all 149 type classes (default true)

   Returns summary of all operations."
  [& {:keys [prefix dry-run include-types]
      :or {prefix "lipasdev7" dry-run false include-types true}}]
  (let [model-dto (->model-dto :prefix prefix)
        sijainti-dto {:identifier "Sijainti"
                      :label {:fi "Sijainti"
                              :en "Location"
                              :sv "Plats"}
                      :note {:fi "Liikuntapaikan sijaintitiedot."
                             :en "Location information for a sports facility."
                             :sv "Platsinformation för en idrottsanläggning."}
                      :subClassOf []}]
    (if dry-run
      ;; Return what would be created
      (let [hierarchy (generate-all-classes prefix)]
        {:prefix prefix
         :model model-dto
         :sijainti sijainti-dto
         :hierarchy {:base (count (:base hierarchy))
                     :main-categories (count (:main-categories hierarchy))
                     :sub-categories (count (:sub-categories hierarchy))
                     :types (count (:types hierarchy))
                     :total (count (:all hierarchy))}
         :sample-classes (take 5 (:all hierarchy))})

      ;; Execute all operations
      (let [results {:prefix prefix
                     :steps []}

            ;; Step 1: Create model
            model-result (create-library! model-dto)
            results (update results :steps conj {:step "create-model" :result model-result})]

        (if (not= :created (:status model-result))
          (assoc results :success false :error "Failed to create model")

          (let [;; Step 2: Create Sijainti class first
                sijainti-result (add-class! prefix sijainti-dto)
                results (update results :steps conj {:step "create-sijainti" :result sijainti-result})

                ;; Step 3: Create hierarchy (LiikuntaPaikka -> Main -> Sub -> Types)
                hierarchy-result (create-hierarchy! prefix :include-types include-types)
                results (update results :steps conj {:step "create-hierarchy" :result hierarchy-result})]

            (assoc results
                   :success (:success hierarchy-result)
                   :url (str "https://tietomallit.suomi.fi/model/" prefix)
                   :stats {:sijainti-created (= :created (:status sijainti-result))
                           :hierarchy-created (:total-created hierarchy-result)
                           :hierarchy-failed (count (:failed hierarchy-result))})))))))

(defn print-creation-summary
  "Print a human-readable summary of model creation results."
  [results]
  (println "\n=== Model Creation Summary ===\n")
  (println "Prefix:" (:prefix results))
  (println "Success:" (:success results))
  (when (:url results)
    (println "URL:" (:url results)))
  (println)

  (doseq [{:keys [step result]} (:steps results)]
    (println (str "Step: " step))
    (case step
      "create-model"
      (println "  Status:" (name (or (:status result) :unknown)))

      "create-sijainti"
      (println "  Status:" (name (or (:status result) :unknown)))

      "create-hierarchy"
      (do
        (println "  Created:" (:total-created result) "classes")
        (when (seq (:failed result))
          (println "  Failed:" (count (:failed result)))))

      (println "  Result:" result))
    (println))

  (when-let [stats (:stats results)]
    (println "Statistics:")
    (println "  - Sijainti:" (if (:sijainti-created stats) "created" "failed"))
    (println "  - Classes created:" (:hierarchy-created stats))
    (println "  - Classes failed:" (:hierarchy-failed stats)))
  (println))

(comment
  ;; Test class identifier generation
  (->class-identifier "Uimahalli")
  ;; => "Uimahalli"

  (->class-identifier "Ulkokentät ja liikuntapuistot")
  ;; => "UlkokentatJaLiikuntapuistot"

  (->class-identifier "Virkistyskohteet ja palvelut")
  ;; => "VirkistyskohteetJaPalvelut"

  ;; Test attribute identifier generation
  (->attribute-identifier :height-m)
  ;; => "heightM"

  (->attribute-identifier :heating?)
  ;; => "heating"

  (->attribute-identifier :pool-tracks-count)
  ;; => "poolTracksCount"

  ;; Generate attribute DTO
  (->attribute-dto :lipas-id (core-attributes :lipas-id) "LiikuntaPaikka" :prefix "lipasdev3")

  ;; Generate DTOs for test
  (->model-dto)
  (->base-class-dto)
  (->main-category-dto 3000 (types/main-categories 3000))
  (->sub-category-dto 3100 (types/sub-categories 3100) 3000)
  (->type-dto 3110 (types/all 3110))

  ;; Dry run to see what would be created
  (create-test-model! :dry-run true)

  ;; Actually create the test model (requires YTI_API_KEY)
  (create-test-model!)

  ;; Delete model if needed to start over
  (delete-model! "lipas")

  ;; === Attribute testing ===

  ;; Dry run - see what attributes would be created
  (add-core-attributes! "lipasdev3" :dry-run true)

  ;; Add core attributes to lipasdev3 model
  (add-core-attributes! "lipasdev3")

  ;; === Model Spec Sync ===

  ;; Load and inspect the model spec
  (def spec (load-model-spec))
  (:model spec)
  (:attributes spec)

  ;; Dry run - see what would be created
  (sync-model! :dry-run true)

  ;; Sync to YTI (uses dev-prefix by default)
  (def results (sync-model!))
  (print-sync-summary results)

  ;; Sync with production prefix (requires DVV approval for "lipas")
  ;; (sync-model! :use-dev-prefix false)

  ;; Delete test model and try again
  (delete-model! "lipasdev4")
  (sync-model!))
