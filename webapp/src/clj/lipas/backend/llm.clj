(ns lipas.backend.llm
  "Generic LLM provider plumbing: provider/model registry, chat completion
   for OpenAI and Gemini (with retry and cross-provider fallback), and
   Gemini embeddings.

   Prompt content, response schemas and domain defaults belong to feature
   namespaces (e.g. lipas.backend.ptv.ai); this namespace stays
   feature-agnostic."
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [lipas.backend.config :as config]
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

(def gemini-default-params
  "Default Gemini model params. Use this instead of hardcoding model names."
  (-> providers :gemini :default-params))

;;; ——— API credentials ——————————————————————————————————————————————————

(def openai-config
  (get-in config/default-config [:open-ai]))

(def gemini-config
  (get-in config/default-config [:gemini]))

(def default-headers
  {:Authorization (str "Bearer " (:api-key openai-config))
   :Content-Type  "application/json"})

;;; ——— Schema helpers ———————————————————————————————————————————————————

(defn localized-string-schema
  "Malli schema for a closed {:fi :se :en} string map — the standard shape
   for multilingual LLM output fields."
  [string-props]
  [:map
   {:closed true}
   [:fi [:string string-props]]
   [:se [:string string-props]]
   [:en [:string string-props]]])

;;; ——— Chat completion ——————————————————————————————————————————————————

(defn complete-raw
  "Calls OpenAI chat completions. Returns the full response including
   :choices, :usage, and :model. When :message-format is absent no
   response_format is sent and the model returns plain text."
  [{:keys [completions-url model n temperature top-p presence-penalty message-format max-tokens]
    :or   {n                1
           top-p            0.5
           presence-penalty -2
           max-tokens       4096}}
   system-instruction
   prompt]
  (let [{:keys [new-api-models no-sampling]} (providers :openai)
        new-api? (new-api-models model)
        body   (cond-> {:model    model
                        :n        n
                        :messages [{:role "system" :content system-instruction}
                                   {:role "user" :content prompt}]}
                 message-format       (assoc :response_format message-format)
                 new-api?             (assoc :max_completion_tokens max-tokens)
                 (not new-api?)       (assoc :max_tokens max-tokens)
                 (not (no-sampling model))
                 (-> (assoc :top_p top-p)
                     (assoc :presence_penalty presence-penalty))
                 temperature (assoc :temperature temperature))
        _ (log/debugf "OpenAI prompt (%s, %d chars): %s" model (count prompt) prompt)
        params {:headers default-headers
                :body    (json/encode body)}]
    (-> (client/post completions-url params)
        :body
        (json/decode keyword))))

(defn gemini-complete-raw
  "Calls Gemini API and normalizes the response to match OpenAI's shape.
   When :response-schema is absent the model returns plain text instead of
   structured JSON."
  [{:keys [base-url api-key model n temperature top-p max-tokens thinking-level response-schema]
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
              :generationConfig  (cond-> {:topP            top-p
                                          :temperature     temperature
                                          :maxOutputTokens max-tokens
                                          :candidateCount  n
                                          :thinkingConfig  {:thinkingLevel thinking-level}}
                                   response-schema
                                   (assoc :responseMimeType "application/json"
                                          :responseSchema response-schema))}
        _      (log/debugf "Gemini prompt (%s, %d chars): %s" model (count prompt) prompt)
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
                            ;; Parse JSON content server-side so callers receive
                            ;; a proper nested map. Falls back to raw string if
                            ;; the model returned plain text or invalid JSON.
                            content (if response-schema
                                      (try
                                        (json/decode text keyword)
                                        (catch Exception e
                                          (log/warnf "Failed to parse Gemini JSON content: %s" (.getMessage e))
                                          text))
                                      text)]
                        {:message {:content content}}))
                    (:candidates resp))
     :usage   {:prompt_tokens     (:promptTokenCount usage 0)
               :completion_tokens (:candidatesTokenCount usage 0)
               :total_tokens      (:totalTokenCount usage 0)}}))

(defn complete
  [{:keys [model message-format] :as config-map}
   system-instruction
   prompt]
  (let [result (-> (complete-raw config-map system-instruction prompt)
                   :choices
                   first
                   (update-in [:message :content]
                              (fn [content]
                                (if message-format
                                  (try
                                    (json/decode content keyword)
                                    (catch Exception e
                                      (log/warnf "Failed to parse OpenAI JSON content: %s" (.getMessage e))
                                      content))
                                  content))))]
    (log/infof "OpenAI complete (%s): %d tokens" model (get-in result [:usage :total_tokens] 0))
    (log/debugf "OpenAI result: %s" result)
    result))

(defn- gemini-unavailable?
  "True if the exception is a 503 (or 429) from Gemini indicating capacity issues."
  [e]
  (when-let [status (:status (ex-data e))]
    (contains? #{503 429} status)))

(defn gemini-complete
  "Like `complete` but uses Gemini. Returns {:message {:content <map>}}.
   Merges provider defaults for any missing params.
   On 503/429 errors, retries once after 2s, then falls back to OpenAI using
   :fallback-message-format as the response_format when provided."
  [config system-instruction prompt]
  (let [config (merge gemini-default-params config)
        openai-fallback
        (fn []
          (let [fallback-model (:model default-params)]
            (log/warnf "Gemini still unavailable, falling back to OpenAI %s" fallback-model)
            (let [openai-cfg (cond-> (merge default-params openai-config)
                               (:fallback-message-format config)
                               (assoc :message-format (:fallback-message-format config)))
                  result (complete openai-cfg system-instruction prompt)]
              (log/infof "OpenAI fallback complete (%s)" fallback-model)
              result)))]
    (try
      (let [result (-> (gemini-complete-raw config system-instruction prompt)
                       :choices
                       first)]
        (log/infof "Gemini complete (%s)" (:model config))
        (log/debugf "Gemini result: %s" result)
        result)
      (catch Exception e
        (if (gemini-unavailable? e)
          (do
            (log/warnf "Gemini unavailable (%s), retrying in 2s..." (.getMessage e))
            (Thread/sleep 2000)
            (try
              (let [result (-> (gemini-complete-raw config system-instruction prompt)
                               :choices
                               first)]
                (log/infof "Gemini retry succeeded (%s)" (:model config))
                (log/debugf "Gemini result: %s" result)
                result)
              (catch Exception e2
                (if (gemini-unavailable? e2)
                  (openai-fallback)
                  (throw e2)))))
          (throw e))))))

;;; ——— Embeddings ———————————————————————————————————————————————————————

(def embedding-defaults
  {:model "gemini-embedding-001"
   :dims  768})

(def ^:private embed-batch-size
  "Gemini batchEmbedContents accepts at most 100 requests per call."
  100)

(defn- l2-normalize
  "MRL-truncated vectors (dims < the model's native 3072) are not
   unit-length; cosine kNN quality degrades without re-normalization."
  [values]
  (let [norm (Math/sqrt (double (reduce (fn [acc v] (+ acc (* v v))) 0.0 values)))]
    (if (zero? norm)
      values
      (mapv #(/ % norm) values))))

(defn embed
  "Embed a collection of texts with Gemini. Returns a vector of L2-normalized
   float vectors aligned with the input order.

   config — gemini-config, optionally with :model / :dims overrides
   opts:
   :task-type — \"RETRIEVAL_DOCUMENT\" (default), \"RETRIEVAL_QUERY\",
                \"SEMANTIC_SIMILARITY\", ...
   :titles    — optional coll aligned with texts; only meaningful with
                RETRIEVAL_DOCUMENT"
  [{:keys [base-url api-key model dims]
    :or   {model (:model embedding-defaults)
           dims  (:dims embedding-defaults)}}
   texts
   & {:keys [task-type titles] :or {task-type "RETRIEVAL_DOCUMENT"}}]
  (let [url (str base-url "/models/" model ":batchEmbedContents")]
    (->> (map vector texts (concat (or titles []) (repeat nil)))
         (partition-all embed-batch-size)
         (mapcat (fn [chunk]
                   (let [requests (mapv (fn [[text title]]
                                          (cond-> {:model                (str "models/" model)
                                                   :content              {:parts [{:text text}]}
                                                   :taskType             task-type
                                                   :outputDimensionality dims}
                                            title (assoc :title title)))
                                        chunk)
                         resp (-> (client/post url {:headers      {"x-goog-api-key" api-key
                                                                   "Content-Type"   "application/json"}
                                                    :body         (json/encode {:requests requests})
                                                    :content-type :json})
                                  :body
                                  (json/decode keyword))]
                     (log/debugf "Gemini embed (%s): %d texts" model (count chunk))
                     (mapv (comp l2-normalize :values) (:embeddings resp)))))
         vec)))

(defn embed-one
  "Embed a single text — convenience for query-time embedding.
   Returns one L2-normalized float vector."
  [config text & {:keys [task-type] :or {task-type "RETRIEVAL_QUERY"}}]
  (first (embed config [text] :task-type task-type)))

(defn get-models
  [{:keys [_api-key models-url]}]
  (let [params {:headers default-headers}]
    (-> (client/get models-url params)
        :body
        (json/decode keyword))))

(comment
  (get-models openai-config)
  (embed gemini-config ["uimahalli" "jäähalli"] :task-type "SEMANTIC_SIMILARITY")
  (embed-one gemini-config "miten lisään reitin?"))
