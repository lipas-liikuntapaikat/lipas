(ns lipas.backend.ptv.ai
  "PTV-specific AI content generation: prompts, response schemas and
   sports-site → prompt-doc shaping. Generic LLM plumbing (providers,
   completion calls, retry/fallback, embeddings) lives in lipas.backend.llm."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [lipas.backend.llm :as llm]
            [malli.json-schema :as json-schema]
            [malli.util :as mu]))

(def ptv-system-instruction-v2
  "You are an assistant who helps users produce content for the Service Information Repository (Palvelutietovaranto). You will be asked questions and should primarily use source material and secondarily your own knowledge to provide answers. Follow these style guidelines in your responses:

- Use a neutral tone in your responses.
- Avoid promotional language.
- The texts are not marketing communications.
- Address and target the content to the user in the \"you\" form.
- Describe the service offered to the customer, not the organizing entity or its tasks.
- Avoid promotional expressions. Focus on describing the service and its usage.
- Use simple expressions and familiar words.
- Explain abbreviations and difficult terms if they must be used.
- Avoid vagueness and uninformative sentences.
- Form complete sentences and use both main and subordinate clauses. Avoid convoluted participial constructions and replace them with subordinate clauses.
- Present the most important information at the beginning of the text in the first paragraph. Get straight to the point and provide background information at the end.
- Consider what information the reader needs to get a comprehensive view of the service and available service channels and to start using the service.
- Present only one topic per paragraph.
- Divide the text into paragraphs.
- A paragraph should contain a maximum of four sentences.

Provide answers in English, Finnish, and Swedish. Different language versions can differ in their phrasing. It is important that the language is grammatically correct, readable and clear.")

(def ptv-system-instruction-v3
  "You are an assistant that creates strictly factual, administrative content for the Service Information Repository (Palvelutietovaranto) in Finnish, Swedish, and English.

CRITICAL RULES:
  - NO promotional or welcoming phrases (\"tervetuloa\", \"nauttia\", \"welcome\", \"enjoy\")
- NO marketing language or emotional appeals
- NO exclamation marks

Content Requirements:
- Start with location and basic service type
- State only verifiable facts
- Use administrative, neutral tone throughout
- Write in present tense
- Address users with \"you\" form, but minimize its use

Language Structure:
- Maximum 4 sentences per paragraph
- One topic per paragraph
- Main information first, details after
- Use complete sentences
- Simple words and clear structure

Required Information Order:
1. What: Service type and location
2. Access: Who can use it and how
3. Conditions: Any limitations or requirements
4. Practical details: Operating hours, seasonal information

Prohibited Elements:
× Tervetuloa, nauttia, enjoy, welcome
× Exclamation marks (!)
× Marketing adjectives (wonderful, great, excellent)
× Emotional appeals
× Inviting phrases

Each response must be:
- Factual
- Administrative
- Unemotional
- Service-focused
")

(def ptv-system-instruction-v5
  "You are an assistant that creates strictly factual, administrative content for the Finnish Service Information Repository (Palvelutietovaranto / PTV) in Finnish, Swedish, and English.

CONTEXT: PTV uses two concepts:
- Palvelu (Service): describes what a municipality offers from the citizen's perspective
- Palvelupaikka (ServiceLocation): a specific place where the citizen accesses the service
You will be asked to write descriptions for one or the other. In both cases, write
from the CITIZEN'S perspective: what can they do, and how do they access it.

You produce two texts per language:
- Summary (tiivistelmä): max 150 characters
- Description (kuvaus): max 2000 characters

CRITICAL RULES — violation causes automatic rejection:

1. CHECK THE STATUS FIELD (ServiceLocation only)
   - \"out-of-service-permanently\": state clearly that the facility is permanently
     out of service. Do NOT describe it as active or maintained.
   - \"out-of-service-temporarily\": state that the facility is temporarily out of service.
   - Only describe as active and usable if status is \"active\".

2. NO CONTACT INFORMATION
   PTV has separate fields for address, phone, and URL. Never include street addresses,
   postal codes, phone numbers, or web addresses in summaries or descriptions.
   Use only municipality name and facility/area name for location.

3. ONLY VERIFIABLE FACTS FROM SOURCE DATA
   Write ONLY information present in the provided data. If the source does not mention
   changing rooms, toilets, season, or other details — do NOT add them.
   Never write meta-commentary about missing data — simply omit it.

4. NO ORGANIZATION NAME REPETITION
   PTV has a separate field for the organization name. Do not repeat the municipality
   or organization name as the subject. Write \"Nuorisotalon kuntosali\", not
   \"Utajärven kunnan nuorisotalon kuntosali\".

SUMMARY RULES:
- Must be a complete sentence (virke), not a comma-separated list
- Must not be a copy of the facility name
- Must not contain information absent from the description
- BAD:  \"Jääkiekkokaukalo Haapajoella, avoin kaikille, valaistus ja pukuhuonetilat.\"
- GOOD: \"Haapajoen jääkiekkokaukalo on valaistu ulkokaukalo, joka on avoinna kaikille.\"
- BAD:  \"Lähiliikuntapaikka Iissä, kaksi kenttää, keinonurmi ja sorapinta, maksuton käyttö.\"
- GOOD: \"Aseman ala-asteen lähiliikuntapaikka tarjoaa ympäri vuoden maksutta kaksi urheilukenttää.\"

DESCRIPTION RULES:
- Must consist of complete sentences, never lists or fragments
- Must also cover everything mentioned in the summary
- Include a usage instruction (toimintaohje): how to access or start using the service
- Use clear everyday language (yleiskieli) that any citizen can understand
- Avoid unintentionally comical phrasing — e.g. do not highlight a floor surface
  as a feature (\"tarjoaa synteettisen pinnan\")
- For Services: describe the service from the citizen's perspective, not as an
  inventory of facilities. Use the data as background context, not as a list to
  transcribe. Direct citizens to individual ServiceLocations for specifics.

TONE AND STYLE:
- Neutral, administrative, unemotional, service-focused
- Present tense
- Prefer passive constructions; use \"you\" form sparingly
- No exclamation marks

PROHIBITED EXPRESSIONS (in any language):
× tervetuloa, välkommen, welcome
× nauttia, njuta, enjoy
× monipuolinen, mångsidig, versatile (evaluative)
× kattava, omfattande, extensive, comprehensive (evaluative)
× upea, mahtava, erinomainen, wonderful, great, excellent
× Emotional appeals, inviting phrases, promotional language

FINNISH LANGUAGE REQUIREMENTS:
- Correct place name inflection: Raahe → Raahessa (not Raaheessa), Ii → Iissä
- Avoid overusing \"käytettävissä\" — vary with: \"voi käyttää\", \"on avoinna\",
  \"on tarkoitettu\", \"palvelee\"
- Prefer passive voice: \"latua ylläpidetään\" not \"ylläpito tapahtuu\"
- Compound place names: use en-dash without surrounding spaces
  (Ketunperäntie–Haapajoki, not Ketunperäntie - Haapajoki)
- Use common terms: \"tulentekopaikka\" or \"nuotiopaikka\" (not \"tulistelupaikka\"),
  \"salibandy\" (not \"säbä\")
- Include \"ja\" or \"sekä\" before the last item in lists
- Use reader-friendly units: write \"290 metriä\" not \"0,29 km\",
  write \"kilometriä\" not \"km\" in running text

FORMAT:
- Divide description into 2–4 paragraphs
- Maximum 4 sentences per paragraph
- One topic per paragraph
- Most important information first

Each language version may differ in phrasing but must convey the same factual content.
All language versions must be grammatically correct and natural.")

(def generate-utp-descriptions-prompt-v5
  "Create a summary and description for the Service Information Repository (Palvelutietovaranto).

BEFORE WRITING, verify:
1. STATUS — is it \"active\", \"out-of-service-permanently\", or other? Reflect this.
2. FACTS — write ONLY what is explicitly in the data. Do not infer or invent.
3. ADDRESS — do NOT include any street address, phone number, or URL.
4. ORGANIZATION — do NOT repeat the organization/municipality name as subject.

Summary: A complete sentence (not a list!) capturing the essential service. Max 150 chars/language.

Description: 2–4 paragraphs in the order: what → access → facilities → conditions. Include a brief usage instruction. Max 2000 chars/language.

EXAMPLES of DVV-approved summaries (Finnish):
- \"Ikosen koulun kaukalo Pyhäjärvellä on ilmaiseksi kaikkien käytettävissä.\"
- \"Haminan ala-asteen sali on koulun liikuntasali, jossa on yksi koripallo-, käsipallo- ja lentopallokenttä.\"
- \"Aseman ala-asteen lähiliikuntapaikka tarjoaa ympäri vuoden maksutta kaksi urheilukenttää.\"
- \"Oijärven vesillelaskuluiska on ympäri vuoden käytössä oleva vesillelaskuluiska Iissä.\"%s

Source data:
%s")

(comment
  (println ptv-system-instruction-v2))

(def response-schema
  [:map
   {:closed true}
   [:description (llm/localized-string-schema nil)]
   ;; Structured Outputs doesn't support maxLength
   ;; https://platform.openai.com/docs/guides/structured-outputs#some-type-specific-keywords-are-not-yet-supported
   ;; The prompt mentions summary should be max 150 chars
   [:summary (llm/localized-string-schema nil #_{:max 150})]
   [:user-instruction (llm/localized-string-schema nil)]])

(def Response
  (json-schema/transform response-schema))

(def GeminiResponse
  "JSON Schema for Gemini — same structure but without additionalProperties
   which Gemini's API does not support."
  (json-schema/transform (mu/open-schema response-schema)))

(def openai-response-format
  "OpenAI response_format equivalent of GeminiResponse, used both by the
   workbench and as the Gemini→OpenAI fallback format."
  {:type "json_schema"
   :json_schema {:name   "Response"
                 :strict true
                 :schema Response}})

(defn gemini-complete
  "PTV-flavored llm/gemini-complete: defaults to the PTV response schema and
   keeps the OpenAI fallback on the equivalent JSON schema."
  [config system-instruction prompt]
  (llm/gemini-complete (merge {:response-schema         GeminiResponse
                               :fallback-message-format openai-response-format}
                              config)
                       system-instruction
                       prompt))

(def generate-utp-descriptions-prompt
  "Based on the JSON structure provided, create two multilingual descriptions (in Finnish, Swedish, and English) of the sports facility:
        1.	A concise summary (max 150 characters per language) that captures the essential service information
        2.	A structured description divided into clear paragraphs. (max 2000 characters per language)
Follow these requirements:
        •	Focus on information relevant to service users
        •	Use \"you\" form when addressing users
        •	Exclude detailed technical specifications and condition reports
        •	Start with the most essential information
        •	Use clear, everyday language
        •	Maintain neutral, non-promotional tone
        •	Exclude street address from descriptions
%s")

;;; ——— Prompt input shaping —————————————————————————————————————————
;;
;; ->prompt-doc reduces a full sports-site document down to the subset of
;; fields the AI should actually consider when writing a citizen-facing
;; description. Everything not declared in the allowlists below is dropped.
;;
;; Principles driving the allowlist:
;; - Citizen utility first: include only facts a resident reading a
;;   municipality's service page would act on.
;; - No contact information: addresses, phone numbers, websites belong in
;;   PTV's structured fields, never inlined into descriptions.
;; - No noisy technical dimensions: surface area, ball-field length/width,
;;   ceiling height etc. clutter descriptions without helping users.
;; - Keep dimensions citizens actually use: route-length, pool-length,
;;   track-length, beach-length are directly actionable.
;; - Safe-by-default: any property added to prop-types in the future is
;;   invisible to the AI until explicitly listed here.

(defn- blank?
  [v]
  (or (nil? v)
      (and (string? v) (str/blank? v))
      (and (coll? v) (empty? v))))

(defn- select+transform
  "Build a map from `source` using `field->transformer`. Each key is looked
   up, the transformer applied, and the pair kept only if the result is
   non-blank. New entries do not appear on the output when the source lacks
   the key, so callers don't need to clean up nils."
  [field->transformer source]
  (reduce-kv (fn [acc k transformer]
               (if-some [v (get source k)]
                 (let [v' (transformer v)]
                   (cond-> acc (not (blank? v')) (assoc k v')))
                 acc))
             {}
             field->transformer))

(defn- encode-stand-capacity
  [n]
  (cond (< n 100) :small (< n 500) :medium :else :large))

(defn- trim-location
  "Keep only neighborhood. Address, postal-code, postal-office are excluded
   by the no-contact-info policy; city name is sourced from :search-meta."
  [loc]
  (when-let [nbh (get-in loc [:city :neighborhood])]
    {:neighborhood nbh}))

(defn- trim-search-meta
  "Keep the resolved, multilingual type and city names the AI relies on.
   Drops coordinates, administrative hierarchy, audit timestamps, etc."
  [sm]
  (let [type-info {:name         (get-in sm [:type :name])
                   :sub-category (get-in sm [:type :sub-category :name])}
        city-name (get-in sm [:location :city :name])]
    (cond-> {}
      (seq type-info) (assoc :type type-info)
      (seq city-name) (assoc-in [:location :city :name] city-name))))

(def ^:private top-level-ai-fields
  {:name              identity
   :marketing-name    identity
   :comment           identity
   :status            identity
   :reservations-link identity
   :lipas-id          identity
   :location          trim-location
   :search-meta       trim-search-meta})

(def ^:private property-ai-fields
  {;; Access & use
   :free-use?               identity
   :school-use?             identity
   :year-round-use?         identity

   ;; Amenities
   :toilet?                 identity
   :changing-rooms?         identity
   :shower?                 identity
   :sauna?                  identity
   :kiosk?                  identity
   :pier?                   identity

   ;; Surface & lighting
   :surface-material        identity
   :surface-material-info   identity
   :ligthing?               identity
   :lighting-info           identity

   ;; Accessibility
   :accessibility-info      identity

   ;; Citizen-meaningful linear dimensions
   :route-length-km         identity
   :lit-route-length-km     identity
   :track-length-m          identity
   :pool-length-m           identity
   :beach-length-m          identity

   ;; Activity-indicator counts
   :swimming-pool-count     identity
   :ice-rinks-count         identity
   :holes-count             identity
   :basketball-fields-count identity
   :volleyball-fields-count identity
   :tennis-courts-count     identity
   :badminton-courts-count  identity
   :floorball-fields-count  identity
   :handball-fields-count   identity
   :football-fields-count   identity

   ;; Encoded: exact count is misleading, buckets convey the signal
   :stand-capacity-person   encode-stand-capacity})

(defn ->prompt-doc
  "Shape a sports-site document for AI prompting: include only
   citizen-relevant, non-noisy fields as defined by `top-level-ai-fields`
   and `property-ai-fields`."
  [sports-site]
  (let [top   (select+transform top-level-ai-fields sports-site)
        props (select+transform property-ai-fields (:properties sports-site))]
    (cond-> top
      (seq props) (assoc :properties props))))

(def batch-reference-section
  "

STYLE REFERENCE — an approved description of a previous facility of the same type.
Match its tone, topic ordering, sentence patterns, and vocabulary. DO NOT copy its
paragraph count — let each facility's description length follow its own data.
Omit topics that are not supported by a given facility's data.

Reference (Finnish):
Summary: %s
Description: %s")

(defn generate-ptv-descriptions
  "Generate PTV descriptions for a single sports site.
   Optional `reference` {:summary :description} in Finnish anchors the style
   to an approved description of a peer facility (same type, same org)."
  [sports-site & [{:keys [reference]}]]
  (let [prompt-doc  (->prompt-doc sports-site)
        ref-section (if reference
                      (format batch-reference-section
                              (:summary reference)
                              (:description reference))
                      "")]
    (gemini-complete llm/gemini-config
                     ptv-system-instruction-v5
                     (format generate-utp-descriptions-prompt-v5
                             ref-section
                             (json/encode prompt-doc)))))

;;; ——— Batch generation ———————————————————————————————————————————————
;;
;; Multiple same-type facilities generated in one Gemini call. The model
;; naturally aligns structure, vocabulary, and sentence patterns across
;; facilities, producing uniform output for display on municipality pages.
;;
;; Gemini reliably handles ~10–20 items per batch; beyond that it tends
;; to bail early with a stub response. Callers partition larger groups
;; and use :reference to anchor style across batches.

(def batch-response-schema
  [:map
   {:closed true}
   [:sites
    [:vector
     [:map
      {:closed true}
      ;; NOT the canonical sports-sites-schema/lipas-id: a var ref
      ;; transforms to a JSON-schema $ref, which Gemini structured output
      ;; doesn't support. LLM only echoes the id, so plain :int is fine.
      [:lipas-id :int]
      [:summary (llm/localized-string-schema nil)]
      [:description (llm/localized-string-schema nil)]
      [:user-instruction (llm/localized-string-schema nil)]]]]])

(def BatchGeminiResponse
  (json-schema/transform (mu/open-schema batch-response-schema)))

(def generate-utp-descriptions-batch-prompt-v1
  "Create summaries and descriptions for %d sports facilities of the SAME TYPE in the same municipality, for the Service Information Repository (Palvelutietovaranto).

CONSISTENCY — the descriptions will be shown side-by-side on municipality pages, so:
- Use the same TOPIC ORDER across all facilities (what → access → facilities → conditions)
- Use matching SENTENCE PATTERNS and VOCABULARY between facilities
- The set should read as if written by one person in one session

ADAPT TO DATA DENSITY — a facility with sparse data gets a SHORTER description, not a padded one:
- Omit any paragraph whose topic has no supporting data — do NOT write a paragraph about missing information
- A facility with only a few facts may warrant 2 paragraphs; a rich one may warrant 4
- NEVER write sentences like \"Kentällä ei ole mainittu pukeutumistiloja\" / \"inga uppgifter om X\" / \"no amenities are listed\" — these are meta-commentary about the data, not about the facility. Silently drop the topic.

Per facility, produce:
- summary: A complete sentence (not a list). Max 150 chars/language.
- description: 2–4 paragraphs, topic order as above. Include a brief usage instruction. Max 2000 chars/language.
- user-instruction: 1–3 sentences on how to access. Max 5000 chars/language.

BEFORE WRITING, verify for EACH facility:
1. STATUS — is it \"active\", \"out-of-service-permanently\", or other? Reflect this.
2. FACTS — write ONLY what is explicitly in the data. Do not infer or invent.
3. ADDRESS — do NOT include any street address, phone number, or URL.
4. ORGANIZATION — do NOT repeat the organization/municipality name as subject.

Return a \"sites\" array. Each entry's lipas-id MUST match the source data exactly (as an integer).%s

Source data:
%s")

(defn generate-ptv-descriptions-batch
  "Generate PTV descriptions for a batch of same-type sports sites in one call.

  sports-sites : seq of enriched sports site maps (like single-site input)
  reference    : optional {:summary string :description string} in Finnish.
                 When provided, anchors the style for continuity across partitioned batches."
  [sports-sites & [{:keys [reference]}]]
  (let [prompt-docs (mapv ->prompt-doc sports-sites)
        ref-section (if reference
                      (format batch-reference-section
                              (:summary reference)
                              (:description reference))
                      "")
        prompt      (format generate-utp-descriptions-batch-prompt-v1
                            (count prompt-docs)
                            ref-section
                            (json/encode prompt-docs))
        config      (assoc llm/gemini-config
                           :response-schema BatchGeminiResponse
                           :max-tokens 32768)]
    (gemini-complete config ptv-system-instruction-v5 prompt)))

(def translate-to-other-langs-prompt
  "Translate the following Service Information Repository descriptions from %s to %s:

1) Administrative summary (maximum 150 characters)
2) Service description (2-3 paragraphs)
3) User instruction (toimintaohje) - how to access the service (max 5000 characters)

Requirements:
- Maintain administrative tone in target languages
- Keep the same information hierarchy
- Preserve factual content without adding promotional elements
- Use appropriate administrative language for each target language
- Keep the same paragraph structure
- Maintain character limits for summary

Prohibited in all languages:
- Welcome phrases and greetings
- Marketing words (great, excellent, wonderful, etc.)
- Exclamation marks
- Opinion statements
- Inviting phrases
- Street addresses
- Emotional language
- Enjoyment references

Source text:
%s
")

(defn translate-to-other-langs
  [{:keys [from to summary description user-instruction]}]
  (gemini-complete llm/gemini-config
                   ptv-system-instruction-v5
                   (format translate-to-other-langs-prompt
                           from
                           (str/join ", " to)
                           (json/encode (cond-> {:summary summary
                                                 :description description}
                                          user-instruction (assoc :user-instruction user-instruction))))))

(comment
  (translate-to-other-langs
    {:from "fi"
     :to ["en" "se"]
     :summary "Limingan yleisurheilupyhättö, monipuolinen ulkoilma-alue."
     :description "Limingan yleisurheilupyhättö on monipuolinen ulkoilma-alue, jossa on useita yleisurheilulajeja varten varustettuja kenttiä."}))

(def generate-utp-service-descriptions-prompt
  "Based on the provided JSON structure, create two administrative descriptions of the sports service for the Service Information Repository:

1) Factual summary (maximum 150 characters)
2) Service description (2-4 paragraphs)

Requirements:
- Present only verifiable facts
- Use administrative language
- Avoid marketing language
- No greetings or welcoming phrases
- No exclamation marks

Prohibited elements:
- Welcome, enjoy, cozy
- Marketing words (great, excellent, wonderful)
- Exclamation marks
- Opinion statements
- Inviting phrases
- Street addresses

%s")

(def generate-utp-service-descriptions-prompt-v5
  "You are writing a PTV SERVICE (Palvelu) description — this describes a municipal
sports service from the CITIZEN'S perspective. Individual facilities (ServiceLocations)
have their own separate descriptions; this text describes the service as a whole.

The source data contains aggregate statistics. Use this as BACKGROUND CONTEXT to
inform your writing. Do NOT transcribe it as an inventory or enumerate counts per type.

BEFORE WRITING, verify:
1. FACTS — write ONLY what is supported by the data. Do not infer details.
2. NO CONTACT INFO — no addresses, phone numbers, or URLs.
3. NO EVALUATIVE ADJECTIVES — do not use monipuolinen, kattava, mångsidig, versatile,
   lukuisia, numerous, or similar words that emphasize abundance.
4. NO META-COMMENTARY — if data is missing, omit it silently.
5. FREE-USE DATA — the \"free-use\" field uses a checkbox model:
   - \"true\" count = confirmed free to use without reservation or fee
   - \"unknown\" count = not confirmed (the checkbox was not checked, NOT necessarily paid)
   Only state \"maksuton\" / \"free\" if a significant share is confirmed true.
   If mostly unknown, do not make claims about cost — simply omit.

THE TEXT SHOULD ANSWER (from the citizen's perspective):
- What kind of sports/recreation activity is this service about?
- Where is it available? (municipality, general scope)
- Who can use it and is it free? (only if confirmed by the data)
- How does a citizen access it? (go directly, check individual facility details, etc.)

Summary: A complete sentence describing the service. Max 150 chars/language.
- GOOD: \"Vantaalla voi uida kymmenellä yleisellä uimarannalla ja uimapaikalla.\"
- GOOD: \"Tampereella on talvikaudella käytössä luistelukenttiä ja jääkiekkokaukaloita.\"
- BAD:  \"Tampereella on 144 luistelukenttää, 10 kaukaloa, 7 tekojääkenttää...\" (inventory)
- BAD:  \"Tampere tarjoaa jääurheilualueita.\" (municipality as subject)

Description: 2–4 paragraphs covering:
1. What this service is about and where (use inessive case: \"Oulussa\", \"Espoossa\")
2. What a citizen can do — describe the activity, not the asset count
3. Access and cost — who can use, is it free, any general conditions
4. How to find a specific location — direct to individual ServiceLocation descriptions

You may mention the approximate scale (\"useita\", \"kymmeniä\", \"yli sata\") but
do NOT list exact counts per facility type. The detailed breakdown belongs in
individual ServiceLocation descriptions.

User instruction (Toimintaohje): 1–3 sentences telling the citizen how to access
or start using the service. What concrete steps should they take? Can they go
directly to a facility, or do they need to book or register first? Keep it
actionable and written for the citizen. Do not include addresses or phone numbers.
Max 5000 chars/language.
- GOOD: \"Liikuntapaikat ovat vapaasti käytettävissä. Tarkista aukioloajat ja yhteystiedot palvelupaikkojen kuvauksista.\"
- GOOD: \"Uimahallin käyttö edellyttää pääsylipun ostamista. Tarkista aukioloajat ja hinnat palvelupaikan kuvauksesta.\"

Source data:
%s")

(defn generate-ptv-service-descriptions
  [doc]
  (let [prompt-doc doc]
    (gemini-complete llm/gemini-config
                     ptv-system-instruction-v5
                     (format generate-utp-service-descriptions-prompt-v5 (json/encode prompt-doc)))))
