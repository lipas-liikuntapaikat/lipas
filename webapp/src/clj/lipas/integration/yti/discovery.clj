(ns lipas.integration.yti.discovery
  "API client for discovering existing models and concepts in Yhteentoimivuusalusta.

  This namespace provides functions to search and analyze:
  - Tietomallit (Data Models): Classes, attributes, associations
  - Koodistot (Code Lists): Existing code lists to link to
  - Sanastot (Vocabularies): Terminology and concepts

  ## API Endpoints

  Tietomallit (https://tietomallit.suomi.fi/datamodel-api/):
  - GET /v2/frontend/search-models - Search data models
  - GET /v2/frontend/search-internal-resources - Search classes/attributes
  - GET /v2/model/{prefix} - Get model details
  - GET /v2/export/{prefix} - Export model as JSON-LD/Turtle/RDF

  Sanastot (https://sanastot.suomi.fi/terminology-api/):
  - GET /api/v1/integration/resources - Search terminology concepts

  ## Authentication

  Some endpoints require API key authentication via the Authorization header.
  The API key should be set in the YTI_API_KEY environment variable.

  ## Usage

  ```clojure
  (require '[lipas.integration.yti.discovery :as discovery])

  ;; Search for models
  (discovery/search-models \"sijainti\")

  ;; Search for classes/attributes
  (discovery/search-resources \"osoite\")

  ;; Search for terminology concepts
  (discovery/search-concepts \"nimi\")

  ;; Find concept mappings for LIPAS attributes
  (discovery/find-concept-mappings)

  ;; Get full model details
  (discovery/get-model \"japy\")

  ;; Export model as JSON-LD
  (discovery/export-model \"japy\" :json-ld)
  ```"
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as str]))

;;; Configuration

(def ^:private tietomallit-url "https://tietomallit.suomi.fi/datamodel-api")
(def ^:private sanastot-url "https://sanastot.suomi.fi/terminology-api")

;; Legacy alias
(def ^:private base-url tietomallit-url)

(defn- api-key []
  (System/getenv "YTI_API_KEY"))

(defn- default-headers []
  (cond-> {"Accept" "application/json"
           "Content-Type" "application/json"}
    (api-key) (assoc "Authorization" (str "Bearer " (api-key)))))

;;; HTTP Helpers

(defn- get-json
  "Make GET request and parse JSON response."
  [url & [query-params]]
  (let [response (http/get url
                           {:headers (default-headers)
                            :query-params query-params
                            :throw-exceptions false})]
    (if (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword)
      (throw (ex-info "API request failed"
                      {:status (:status response)
                       :url url
                       :body (:body response)})))))

;;; Tietomallit API

(defn search-models
  "Search for data models matching the search term.

  Options:
    :status - Filter by status (e.g., \"VALID\", \"DRAFT\")
    :type - Filter by model type (e.g., \"LIBRARY\", \"PROFILE\")
    :language - Preferred language (default \"fi\")
    :page-size - Number of results (default 20)

  Returns a sequence of model summaries with :prefix, :label, :status, etc."
  [search-term & {:keys [status type language page-size]
                  :or {language "fi" page-size 20}}]
  (let [params (cond-> {:searchTerm search-term
                        :language language
                        :pageSize page-size}
                 status (assoc :status status)
                 type (assoc :type type))]
    (get-json (str base-url "/v2/frontend/search-models") params)))

(defn search-resources
  "Search for classes, attributes, and associations across all models.

  Options:
    :resource-type - Filter by type: \"CLASS\", \"ATTRIBUTE\", \"ASSOCIATION\"
    :language - Preferred language (default \"fi\")
    :page-size - Number of results (default 20)

  Returns a sequence of resource summaries."
  [search-term & {:keys [resource-type language page-size]
                  :or {language "fi" page-size 20}}]
  (let [params (cond-> {:searchTerm search-term
                        :language language
                        :pageSize page-size}
                 resource-type (assoc :type resource-type))]
    (get-json (str base-url "/v2/frontend/search-internal-resources") params)))

(defn get-model
  "Get detailed information about a specific model.

  Returns the full model definition including classes, attributes, etc."
  [prefix]
  (get-json (str base-url "/v2/model/" prefix)))

(defn get-class
  "Get details of a specific class from a model.

  Parameters:
    prefix - Model prefix (e.g., \"japy\")
    class-identifier - Class identifier within the model"
  [prefix class-identifier]
  (get-json (str base-url "/v2/class/library/" prefix "/" class-identifier)))

(defn export-model
  "Export a model in various formats.

  Format options:
    :json-ld - JSON-LD (default)
    :turtle - Turtle/TTL
    :rdf-xml - RDF/XML
    :openapi - OpenAPI specification

  Returns the serialized model as a string."
  [prefix & {:keys [format] :or {format :json-ld}}]
  (let [accept (case format
                 :json-ld "application/ld+json"
                 :turtle "text/turtle"
                 :rdf-xml "application/rdf+xml"
                 :openapi "application/x-yaml")
        response (http/get (str base-url "/v2/export/" prefix)
                           {:headers (assoc (default-headers) "Accept" accept)
                            :throw-exceptions false})]
    (if (= 200 (:status response))
      (:body response)
      (throw (ex-info "Export failed"
                      {:status (:status response)
                       :prefix prefix
                       :format format})))))

;;; Discovery Helpers

;;; Sanastot (Terminology) API

(defn search-concepts
  "Search for terminology concepts in Sanastot.

  Options:
    :language - Search language (default \"fi\")
    :page-size - Number of results (default 10)
    :status - Filter by status (e.g., \"VALID\", \"DRAFT\")

  Returns a sequence of concept summaries with :uri, :prefLabel, :status, etc."
  [search-term & {:keys [language page-size status]
                  :or {language "fi" page-size 10}}]
  (let [params (cond-> {:searchTerm search-term
                        :language language
                        :pageSize page-size}
                 status (assoc :status status))
        response (http/get (str sanastot-url "/api/v1/integration/resources")
                           {:headers (default-headers)
                            :query-params params
                            :throw-exceptions false})]
    (if (= 200 (:status response))
      (let [body (json/read-str (:body response) :key-fn keyword)]
        (:results body))
      [])))

(defn search-concepts-valid
  "Search for VALID terminology concepts only."
  [search-term & {:keys [language page-size] :or {language "fi" page-size 10}}]
  (->> (search-concepts search-term :language language :page-size page-size)
       (filter #(= "VALID" (:status %)))))

;;; LIPAS Attribute Concept Mapping

(def lipas-attribute-search-terms
  "Map of LIPAS attribute identifiers to Finnish search terms for finding
   matching terminology concepts.

   Format: {attribute-id [search-term-1 search-term-2 ...]}"
  {:lipas-id       ["tunniste" "tunnistenumero"]
   :name           ["nimi"]
   :marketing-name ["markkinointinimi" "kauppanimi" "brändinimi"]
   :construction-year ["rakennusvuosi" "valmistumisvuosi"]
   :renovation-years ["peruskorjaus" "saneeraus"]
   :address        ["osoite" "katuosoite"]
   :postal-code    ["postinumero"]
   :city           ["kunta" "kaupunki"]
   :owner          ["omistaja"]
   :admin          ["ylläpitäjä" "hallinnoija"]
   :www            ["verkko-osoite" "www-osoite" "kotisivu"]
   :email          ["sähköposti" "sähköpostiosoite"]
   :phone-number   ["puhelinnumero"]
   :location       ["sijainti"]
   :geometry       ["geometria" "sijaintitieto"]})

(defn find-concept-for-attribute
  "Search for matching terminology concepts for a LIPAS attribute.

   Returns a map with:
   - :attribute - The attribute identifier
   - :search-terms - Terms that were searched
   - :candidates - List of matching concepts (VALID ones first)
   - :recommended - The recommended concept (first VALID match, if any)"
  [attribute-id]
  (let [search-terms (get lipas-attribute-search-terms attribute-id [])
        all-results (mapcat (fn [term]
                              (map #(assoc % :search-term term)
                                   (search-concepts term :page-size 5)))
                            search-terms)
        ;; Deduplicate by URI
        unique-results (->> all-results
                            (group-by :uri)
                            (map (fn [[_ concepts]] (first concepts)))
                            (sort-by #(if (= "VALID" (:status %)) 0 1)))]
    {:attribute attribute-id
     :search-terms search-terms
     :candidates unique-results
     :recommended (first (filter #(= "VALID" (:status %)) unique-results))}))

(defn find-concept-mappings
  "Find terminology concept mappings for all known LIPAS attributes.

   Returns a map of attribute-id -> concept mapping result.

   Example output:
   {:name {:attribute :name
           :search-terms [\"nimi\"]
           :candidates [{:uri \"https://...\" :prefLabel {:fi \"nimi\"} ...}]
           :recommended {:uri \"https://...\" ...}}
    :address {...}}"
  []
  (->> (keys lipas-attribute-search-terms)
       (map (fn [attr] [attr (find-concept-for-attribute attr)]))
       (into {})))

(defn print-concept-mappings
  "Print a summary of concept mappings for review.

   Shows which attributes have recommended concepts and which need manual review."
  []
  (let [mappings (find-concept-mappings)]
    (println "\n=== LIPAS Attribute Concept Mappings ===\n")
    (doseq [[attr result] (sort-by first mappings)]
      (let [recommended (:recommended result)]
        (if recommended
          (println (format "✅ %-20s -> %s (%s)"
                           (name attr)
                           (get-in recommended [:prefLabel :fi])
                           (:uri recommended)))
          (println (format "❌ %-20s -> NO VALID CONCEPT FOUND (searched: %s)"
                           (name attr)
                           (str/join ", " (:search-terms result)))))))
    (println)))

(defn get-recommended-subjects
  "Get a map of attribute-id -> recommended subject URI.

   Only includes attributes that have a recommended VALID concept.
   Use this to get the URIs for updating attributes in Tietomallit.

   Example:
   {:name \"https://iri.suomi.fi/terminology/eucore-fi/c91\"
    :address \"https://iri.suomi.fi/terminology/rakymp/concept-3000\"
    ...}"
  []
  (->> (find-concept-mappings)
       (filter (fn [[_ result]] (:recommended result)))
       (map (fn [[attr result]] [attr (get-in result [:recommended :uri])]))
       (into {})))

;;; Discovery Helpers

(defn find-address-models
  "Search for address-related models and classes."
  []
  {:models (search-models "osoite")
   :classes (search-resources "osoite" :resource-type "CLASS")
   :attributes (search-resources "katuosoite" :resource-type "ATTRIBUTE")})

(defn find-location-models
  "Search for location/spatial-related models and classes."
  []
  {:models (search-models "sijainti")
   :classes (search-resources "sijainti" :resource-type "CLASS")
   :geometry (search-resources "geometria" :resource-type "ATTRIBUTE")})

(defn find-organization-models
  "Search for organization-related models."
  []
  {:models (search-models "organisaatio")
   :classes (search-resources "organisaatio" :resource-type "CLASS")})

(defn analyze-model
  "Analyze a model and extract key information for mapping decisions.

  Returns a summary with classes, attributes, and potential mapping targets."
  [prefix]
  (let [model (get-model prefix)
        ;; Extract classes from model
        classes (get-in model [:classes] [])
        attributes (get-in model [:attributes] [])]
    {:prefix prefix
     :label (:label model)
     :description (:description model)
     :class-count (count classes)
     :attribute-count (count attributes)
     :classes (mapv (fn [c] {:id (:identifier c)
                             :label (:label c)}) classes)
     :attributes (mapv (fn [a] {:id (:identifier a)
                                :label (:label a)
                                :range (:range a)}) attributes)}))

(defn run-discovery
  "Run full discovery workflow for LIPAS mapping.

  Searches for all relevant concepts and returns structured results
  for updating concept-mappings.edn."
  []
  {:address (find-address-models)
   :location (find-location-models)
   :organization (find-organization-models)
   :example-models {:japy (analyze-model "japy")}})

(comment
  ;; === Tietomallit API ===

  ;; Test API connection
  (search-models "liikunta")

  ;; Search for address concepts
  (search-resources "osoite" :resource-type "CLASS")

  ;; Get JaPy model details
  (get-model "japy")

  ;; Export as JSON-LD
  (export-model "japy" :format :json-ld)

  ;; Run full discovery
  (run-discovery)

  ;; Find address models
  (find-address-models)

  ;; === Sanastot API ===

  ;; Search for terminology concepts
  (search-concepts "nimi")
  ;; => [{:uri "https://iri.suomi.fi/terminology/eucore-fi/c91"
  ;;      :prefLabel {:fi "nimi" :en "name"}
  ;;      :status "VALID" ...} ...]

  ;; Search for VALID concepts only
  (search-concepts-valid "osoite")

  ;; === LIPAS Attribute Concept Mapping ===

  ;; Find concept for a single attribute
  (find-concept-for-attribute :name)
  ;; => {:attribute :name
  ;;     :search-terms ["nimi"]
  ;;     :candidates [...]
  ;;     :recommended {:uri "https://iri.suomi.fi/terminology/eucore-fi/c91" ...}}

  ;; Find concepts for all LIPAS attributes
  (find-concept-mappings)

  ;; Print summary for review
  (print-concept-mappings)
  ;; ✅ name                 -> nimi (https://iri.suomi.fi/terminology/eucore-fi/c91)
  ;; ❌ marketing-name       -> NO VALID CONCEPT FOUND (searched: markkinointinimi, ...)
  ;; ...
  )
