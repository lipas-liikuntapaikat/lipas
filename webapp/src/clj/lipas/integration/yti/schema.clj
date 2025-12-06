(ns lipas.integration.yti.schema
  "Malli schemas for YTI concept mappings and API responses.

  These schemas ensure the concept-mappings.edn file remains valid and
  consistent as we discover and document mappings to external models."
  (:require
   [malli.core :as m]
   [malli.transform]
   [malli.util :as mu]))

;;; Enums

(def RelationshipType
  "OWL/SKOS relationship types for concept mappings."
  [:enum
   :owl/sameAs           ; Identical meaning
   :owl/equivalentClass  ; Same class, different models
   :rdfs/subClassOf      ; LIPAS is more specific
   :rdfs/seeAlso         ; Related but not equivalent
   :skos/exactMatch      ; Exact concept match (vocabularies)
   :skos/closeMatch      ; Close but not exact
   :lipas-specific])     ; No external equivalent exists

(def ConfidenceLevel
  "Confidence in the mapping accuracy."
  [:enum :high :medium :low :unvalidated])

(def MappingStatus
  "Status of the overall mappings document."
  [:enum :draft :review :validated :published])

(def YtiSource
  "Which YTI tool the external resource comes from."
  [:enum :koodistot :sanastot :tietomallit :external])

(def ModelStatus
  "Publication status of an external model in YTI.
   - :valid      Published and officially approved
   - :draft      Work in progress, not published
   - :suggested  Proposed but not officially adopted
   - :superseded Replaced by newer version
   - :retired    No longer maintained"
  [:enum :valid :draft :suggested :superseded :retired])

;;; Field Mapping (for complex nested structures)

(def FieldMapping
  "Mapping between a LIPAS field and an external field.
   The :external value can be nil when no external equivalent exists."
  [:map
   [:lipas :keyword]
   [:external [:maybe [:or :string :keyword]]]
   [:notes {:optional true} :string]])

;;; External Namespace

(def ExternalNamespace
  "Reference to an external namespace/model.
   For tietomallit sources, status is required to track VALID vs DRAFT models."
  [:map
   [:uri :string]
   [:source YtiSource]
   [:status ModelStatus]
   [:description :string]
   [:prefix {:optional true} :string]
   [:version {:optional true} :string]])

;;; Concept Mapping

(def ConceptMapping
  "A mapping from a LIPAS concept to an external concept."
  [:map
   [:lipas-concept :keyword]
   [:lipas-path [:vector :keyword]]
   [:external-uri [:maybe :string]]
   [:relationship RelationshipType]
   [:confidence ConfidenceLevel]
   [:notes {:optional true} :string]
   [:field-mappings {:optional true} [:vector FieldMapping]]
   [:validated-by {:optional true} :string]
   [:validated-at {:optional true} :string]])

;;; Discovered Model

(def DiscoveredModel
  "A model discovered during YTI exploration."
  [:map
   [:prefix :string]
   [:name :string]
   [:status ModelStatus]
   [:relevance [:enum :high :medium :low :none]]
   [:useful-concepts [:vector :keyword]]
   [:analyzed? :boolean]
   [:url :string]
   [:notes {:optional true} :string]])

;;; Metadata

(def MappingsMetadata
  "Metadata about the mappings document."
  [:map
   [:status MappingStatus]
   [:created {:optional true} :string]
   [:updated {:optional true} :string]
   [:author {:optional true} :string]
   [:notes {:optional true} :string]])

;;; Top-level Schema

(def ConceptMappings
  "Complete concept mappings document schema."
  [:map
   [:metadata MappingsMetadata]
   [:external-namespaces [:map-of :string ExternalNamespace]]
   [:mappings [:vector ConceptMapping]]
   [:discovered-models [:vector DiscoveredModel]]])

;;; Validation Helpers

(defn valid?
  "Check if data matches the ConceptMappings schema."
  [data]
  (m/validate ConceptMappings data))

(defn explain
  "Explain why data doesn't match the schema."
  [data]
  (m/explain ConceptMappings data))

(defn coerce
  "Attempt to coerce data to match the schema."
  [data]
  (m/coerce ConceptMappings data (malli.transform/default-value-transformer)))

;;; API Response Schemas (for discovery.clj)

(def TietomallitModelSummary
  "Summary of a model from Tietomallit search."
  [:map
   [:prefix :string]
   [:label [:map-of :keyword :string]]
   [:status {:optional true} :string]
   [:type {:optional true} :string]
   [:organizations {:optional true} [:vector :string]]])

(def TietomallitResourceSummary
  "Summary of a resource (class/attribute) from Tietomallit search."
  [:map
   [:identifier :string]
   [:label [:map-of :keyword :string]]
   [:type [:enum "CLASS" "ATTRIBUTE" "ASSOCIATION"]]
   [:modelPrefix {:optional true} :string]
   [:uri {:optional true} :string]])

;;; Model Spec Schemas (for model-spec.edn)

(def XsdType
  "XSD data types supported by Tietomallit API."
  [:enum
   :xsd/string
   :xsd/integer
   :xsd/decimal
   :xsd/boolean
   :xsd/dateTime
   :xsd/anyURI])

(def LangMap
  "Multilingual string map."
  [:map
   [:fi :string]
   [:en {:optional true} :string]
   [:sv {:optional true} :string]])

(def ModelMetadata
  "Metadata about the model spec document."
  [:map
   [:version :string]
   [:status [:enum :development :testing :production]]
   [:created :string]
   [:updated :string]
   [:author {:optional true} :string]
   [:notes {:optional true} :string]])

(def ModelDefinition
  "Core model properties."
  [:map
   [:prefix :string]
   [:dev-prefix {:optional true} :string]
   [:label LangMap]
   [:description LangMap]
   [:languages [:vector :string]]
   [:status :string]
   [:organizations [:vector :string]]
   [:groups [:vector :string]]
   [:contact :string]])

(def ExternalNamespaceSpec
  "External namespace reference."
  [:map
   [:prefix :string]
   [:namespace :string]
   [:name LangMap]])

(def ClassSpec
  "Class definition in the model."
  [:or
   ;; Regular class definition
   [:map
    [:identifier :string]
    [:label LangMap]
    [:note {:optional true} LangMap]
    [:sub-class-of {:optional true} [:vector :string]]
    [:equivalent-class {:optional true} [:vector :string]]
    [:core-class? {:optional true} :boolean]]
   ;; Generated from types
   [:map
    [:generate-from-types :boolean]
    [:parent-class :string]
    [:source :map]]])

(def AttributeSpec
  "Attribute definition."
  [:map
   [:identifier :string]
   [:label LangMap]
   [:note {:optional true} LangMap]
   [:domain :string]
   [:range [:or XsdType :string]]  ; XSD type or full URI
   [:subject-key {:optional true} [:maybe :keyword]]
   [:code-list-key {:optional true} :keyword]])

(def AssociationSpec
  "Association between classes."
  [:map
   [:identifier :string]
   [:label LangMap]
   [:note {:optional true} LangMap]
   [:domain :string]
   [:range :string]])

(def SyncStatus
  "Sync status tracking."
  [:map
   [:last-sync [:maybe :string]]
   [:synced-prefix [:maybe :string]]
   [:classes-created [:vector :string]]
   [:attributes-created [:vector :string]]
   [:associations-created [:vector :string]]
   [:errors [:vector :any]]])

(def ModelSpec
  "Complete model specification document."
  [:map
   [:metadata ModelMetadata]
   [:model ModelDefinition]
   [:external-namespaces [:vector ExternalNamespaceSpec]]
   [:code-lists [:map-of :keyword :string]]
   [:terminology-subjects [:map-of :keyword [:maybe :string]]]
   [:classes [:vector ClassSpec]]
   [:attributes [:vector AttributeSpec]]
   [:associations [:vector AssociationSpec]]
   [:sync-status SyncStatus]])

;;; Model Spec Validation Helpers

(defn valid-model-spec?
  "Check if data matches the ModelSpec schema."
  [data]
  (m/validate ModelSpec data))

(defn explain-model-spec
  "Explain why data doesn't match the ModelSpec schema."
  [data]
  (m/explain ModelSpec data))

(comment
  ;; Example valid mappings
  (def example-mappings
    {:metadata {:status :draft
                :created "2025-11-29"
                :updated "2025-11-29"}
     :external-namespaces
     {"kunta" {:uri "http://uri.suomi.fi/codelist/jhs/kunta"
               :source :koodistot
               :status :valid
               :description "Finnish municipalities"}}
     :mappings
     [{:lipas-concept :location/kunta
       :lipas-path [:location :city :city-code]
       :external-uri "http://uri.suomi.fi/codelist/jhs/kunta"
       :relationship :owl/sameAs
       :confidence :high
       :notes "Direct code match"}]
     :discovered-models
     [{:prefix "japy"
       :name "Jalankulku- ja pyöräilyväylät"
       :status :valid
       :relevance :medium
       :useful-concepts [:sijainti :geometria]
       :analyzed? true
       :url "https://tietomallit.suomi.fi/model/japy"}]})

  ;; Validate
  (valid? example-mappings)

  ;; Explain errors
  (explain {:metadata {:status :invalid}})

  ;; Check relationship types
  (m/validate RelationshipType :owl/sameAs)
  (m/validate RelationshipType :invalid)

  ;; Check model status
  (m/validate ModelStatus :valid)
  (m/validate ModelStatus :suggested))
