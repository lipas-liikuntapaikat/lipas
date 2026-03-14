(ns lipas.backend.ptv.ai
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [lipas.backend.config :as config]
            [malli.json-schema :as json-schema]
            [malli.util :as mu]
            [taoensso.timbre :as log]))

;;; ——— Provider & model registry ———————————————————————————————————————
;;
;; Single source of truth for all provider/model configuration.
;;
;; :models          — set of model IDs this provider supports
;; :default-params  — workbench defaults sent to the frontend
;; :new-api-models  — (OpenAI) models using max_completion_tokens
;; :no-sampling     — (OpenAI) models that reject top_p / presence_penalty

(def providers
  {:openai
   {:models         #{"gpt-4.1-nano" "gpt-4.1-mini" "gpt-4.1" "gpt-4o-mini" "gpt-4o"
                      "gpt-5-nano" "gpt-5-mini" "gpt-5" "gpt-5.4"
                      "o3-mini" "o4-mini" "o3" "o1" "o1-pro"}
    :new-api-models #{"gpt-5-nano" "gpt-5-mini" "gpt-5" "gpt-5.4"
                      "o3-mini" "o4-mini" "o3" "o1" "o1-pro"}
    :no-sampling    #{"gpt-5-nano" "gpt-5-mini" "gpt-5" "gpt-5.4"
                      "o3-mini" "o4-mini" "o3" "o1" "o1-pro"}
    :default-params {:model            "gpt-4.1-mini"
                     :top-p            0.5
                     :presence-penalty -2
                     :max-tokens       4096
                     :temperature      nil}}
   :gemini
   {:models         #{"gemini-3-flash-preview"}
    :default-params {:model          "gemini-3-flash-preview"
                     :top-p          0.90
                     :temperature    1.0
                     :max-tokens     8192
                     :thinking-level "minimal"}}})

(def model->provider
  "Reverse lookup: model-id → provider keyword. Derived from `providers`."
  (into {}
        (for [[provider {:keys [models]}] providers
              model models]
          [model provider])))

(def default-params
  "Default workbench params (OpenAI). Sent to frontend on preview-data load."
  (-> providers :openai :default-params))

;;; ——— API credentials ——————————————————————————————————————————————————

(def openai-config
  (get-in config/default-config [:open-ai]))

(def gemini-config
  (get-in config/default-config [:gemini]))

(def default-headers
  {:Authorization (str "Bearer " (:api-key openai-config))
   :Content-Type  "application/json"})

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

(comment
  (println ptv-system-instruction-v2))

(defn localized-string-schema [string-props]
  [:map
   {:closed true}
   [:fi [:string string-props]]
   [:se [:string string-props]]
   [:en [:string string-props]]])

(def response-schema
  [:map
   {:closed true}
   [:description (localized-string-schema nil)]
   ;; Structured Outputs doesn't support maxLength
   ;; https://platform.openai.com/docs/guides/structured-outputs#some-type-specific-keywords-are-not-yet-supported
   ;; The prompt mentions summary should be max 150 chars
   [:summary (localized-string-schema nil #_{:max 150})]])

(def Response
  (json-schema/transform response-schema))

(def GeminiResponse
  "JSON Schema for Gemini — same structure but without additionalProperties
   which Gemini's API does not support."
  (json-schema/transform (mu/open-schema response-schema)))

(def default-params
  {:model            (:model openai-config)
   :top-p            0.5
   :presence-penalty -2
   :max-tokens       4096})

(defn complete-raw
  "Returns the full OpenAI response including :choices, :usage, and :model."
  [{:keys [completions-url model n temperature top-p presence-penalty message-format max-tokens]
    :or   {n                1
           top-p            0.5
           presence-penalty -2
           max-tokens       4096}}
   system-instruction
   prompt]
  (let [message-format (or message-format
                           {:type "json_schema"
                            :json_schema {:name "Response"
                                          :strict true
                                          :schema Response}})
        {:keys [new-api-models no-sampling]} (providers :openai)
        new-api? (new-api-models model)
        body   (cond-> {:model            model
                        :n                n
                        :response_format  message-format
                        :messages         [{:role "system" :content system-instruction}
                                           {:role "user" :content prompt}]}
                 new-api?             (assoc :max_completion_tokens max-tokens)
                 (not new-api?)       (assoc :max_tokens max-tokens)
                 (not (no-sampling model))
                 (-> (assoc :top_p top-p)
                     (assoc :presence_penalty presence-penalty))
                 temperature (assoc :temperature temperature))
        _ (log/infof "AI Prompt sent: %s" prompt)
        params {:headers default-headers
                :body    (json/encode body)}]
    (-> (client/post completions-url params)
        :body
        (json/decode keyword))))

(defn gemini-complete-raw
  "Calls Gemini API and normalizes the response to match OpenAI's shape."
  [{:keys [base-url api-key model n temperature top-p max-tokens thinking-level]
    :or   {n              1
           top-p          0.90
           temperature    1.0
           max-tokens     8192
           thinking-level "minimal"}}
   system-instruction
   prompt]
  (let [url  (str base-url "/models/" model ":generateContent")
        body {:systemInstruction {:parts [{:text system-instruction}]}
              :contents          [{:role "user" :parts [{:text prompt}]}]
              :generationConfig  {:topP             top-p
                                  :temperature      temperature
                                  :maxOutputTokens  max-tokens
                                  :candidateCount   n
                                  :responseMimeType "application/json"
                                  :responseSchema   GeminiResponse
                                  :thinkingConfig   {:thinkingLevel thinking-level}}}
        _      (log/infof "Gemini prompt sent: %s" prompt)
        params {:headers      {"x-goog-api-key" api-key
                               "Content-Type"   "application/json"}
                :body         (json/encode body)
                :content-type :json}
        resp   (-> (client/post url params)
                   :body
                   (json/decode keyword))
        usage  (:usageMetadata resp)]
    ;; Normalize to OpenAI shape
    {:model   model
     :choices (mapv (fn [candidate]
                      (let [text (-> candidate :content :parts first :text)
                            ;; Parse JSON content server-side so the frontend receives
                            ;; a proper nested map. Falls back to raw string if Gemini
                            ;; truncated the output or returned invalid JSON.
                            content (try
                                      (json/decode text keyword)
                                      (catch Exception e
                                        (log/warnf "Failed to parse Gemini JSON content: %s" (.getMessage e))
                                        text))]
                        {:message {:content content}}))
                    (:candidates resp))
     :usage   {:prompt_tokens     (:promptTokenCount usage 0)
               :completion_tokens (:candidatesTokenCount usage 0)
               :total_tokens      (:totalTokenCount usage 0)}}))

(defn complete
  [{:keys [completions-url model n #_temperature top-p presence-penalty message-format max-tokens]
    :as config-map}
   system-instruction
   prompt]
  (let [result (-> (complete-raw config-map system-instruction prompt)
                   :choices
                   first
                   (update-in [:message :content] #(json/decode % keyword)))]
    (log/infof "AI Result (%s): %s" model result)
    result))

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

(defn ->prompt-doc
  [sports-site]
  ;; Might include (some) of the UTP data now?
  ;; Could be a good thing, but might make the prompt data too large?
  (walk/postwalk (fn [x]
                   (if (map? x)
                     (dissoc x :geoms :geometries :simple-geoms :images :videos :id :fids :event-date
                             ;; This includes the already generated summary/description, important that that isn't
                             ;; passed back into the AI.
                             :ptv)
                     x))
                 sports-site))

(defn generate-ptv-descriptions
  [sports-site]
  (let [prompt-doc (->prompt-doc sports-site)]
    (complete openai-config
              ptv-system-instruction-v3
              (format generate-utp-descriptions-prompt (json/encode prompt-doc)))))

(def translate-to-other-langs-prompt
  "Translate the following Service Information Repository descriptions from %s to %s:

1) Administrative summary (maximum 150 characters)
2) Service description (2-3 paragraphs)

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
  [{:keys [from to summary description]}]
  (complete openai-config
            ptv-system-instruction-v3
            (format translate-to-other-langs-prompt
                    from
                    (str/join ", " to)
                    (json/encode {:summary summary
                                  :description description}))))

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

(defn generate-ptv-service-descriptions
  [doc]
  (let [prompt-doc doc]
    (complete openai-config
              ptv-system-instruction-v3
              (format generate-utp-service-descriptions-prompt (json/encode prompt-doc)))))

(defn get-models
  [{:keys [_api-key models-url]}]
  (let [params {:headers default-headers}]
    (-> (client/get models-url params)
        :body
        (json/decode keyword))))

(comment
  (get-models openai-config)
  (complete openai-config ptv-system-instruction "Why volcanoes erupt?"))
