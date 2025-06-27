(ns lipas.backend.ptv.ai
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [lipas.backend.config :as config]
            [malli.json-schema :as json-schema]
            [taoensso.timbre :as log]))

(def openai-config
  (get-in config/default-config [:open-ai]))

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

(defn complete
  [{:keys [completions-url model n #_temperature top-p presence-penalty message-format max-tokens]
    :or   {n                1
           #_#_temperature  0
           top-p            0.5
           presence-penalty -2
           max-tokens       4096}}
   system-instruction
   prompt]
  (let [;; Response format with JSON Schema should ensure
        ;; the response is valid JSON and according to the schema,
        ;; without specfying this in the prompts.
        message-format (or message-format
                           {:type "json_schema"
                            :json_schema {:name "Response"
                                          ;; This is probably needed? Providing an unsupported Schema,
                                          ;; like with maxLength, without this doesn't throw an error,
                                          ;; but with this enabled it does.
                                          :strict true
                                          :schema Response}})
        body   {:model            model
                :n                n
                :max_tokens       max-tokens
                #_#_:temperature  temperature
                :top_p            top-p
                :presence_penalty presence-penalty
                :response_format  message-format
                :messages         [{:role "system" :content system-instruction}
                                   {:role "user" :content prompt}]}
        _ (log/infof "AI Prompt sent: %s" prompt)
        params {:headers default-headers
                :body    (json/encode body)}
        result (-> (client/post completions-url params)
                   :body
                   (json/decode keyword)
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
