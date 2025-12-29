(ns ptv-benchmark
  "Benchmark tool for comparing PTV description generation across AI providers.

   Supports: OpenAI GPT, Anthropic Claude, Google Gemini

   Usage from REPL:
   (require '[ptv-benchmark :as bench] :reload)

   ;; Run benchmark with default models and sample sites
   (bench/run-benchmark!)

   ;; Compare specific models across providers
   (bench/run-benchmark! {:models [\"gpt-4.1-mini\"           ; OpenAI baseline
                                   \"claude-3-5-haiku-latest\" ; Anthropic fast
                                   \"gemini-2.0-flash\"]       ; Google fast
                          :lipas-ids sample-lipas-ids})

   ;; View results as HTML
   (bench/open-report!)  ; Opens in browser"
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [lipas.backend.ptv.ai :as ai]
            [lipas.backend.search :as search]
            [taoensso.timbre :as log]))

;;; Configuration

(def default-models
  "Models to compare across providers. OpenAI baseline first."
  ["gpt-4.1-mini"             ; OpenAI baseline (current production)
   "claude-3-5-haiku-latest"  ; Anthropic fast/cheap
   "gemini-2.0-flash"])       ; Google fast/cheap

(def openai-config
  {:api-key         (System/getenv "OPEN_AI_API_KEY")
   :completions-url "https://api.openai.com/v1/chat/completions"})

(def anthropic-config
  {:api-key      (System/getenv "ANTHROPIC_API_KEY")
   :messages-url "https://api.anthropic.com/v1/messages"
   :api-version  "2023-06-01"})

(def gemini-config
  {:api-key (System/getenv "GEMINI_API_KEY")
   :base-url "https://generativelanguage.googleapis.com/v1beta/models"})

;; Pricing per million tokens (input/output) - December 2024
(def model-pricing
  {"gpt-4.1-mini"             {:input 0.40 :output 1.60 :provider :openai}
   "gpt-4o-mini"              {:input 0.15 :output 0.60 :provider :openai}
   "gpt-4o"                   {:input 2.50 :output 10.00 :provider :openai}
   "gpt-4.1"                  {:input 2.00 :output 8.00 :provider :openai}
   "claude-3-5-haiku-latest"  {:input 0.80 :output 4.00 :provider :anthropic}
   "claude-3-5-sonnet-latest" {:input 3.00 :output 15.00 :provider :anthropic}
   "claude-sonnet-4-20250514" {:input 3.00 :output 15.00 :provider :anthropic}
   "gemini-2.0-flash"         {:input 0.10 :output 0.40 :provider :google}
   "gemini-2.0-flash-lite"    {:input 0.075 :output 0.30 :provider :google}
   "gemini-2.5-flash-preview-05-20" {:input 0.15 :output 0.60 :provider :google}
   "gemini-2.5-pro-preview-05-06" {:input 1.25 :output 10.00 :provider :google}
   "gemini-3-flash-preview"   {:input 0.50 :output 3.00 :provider :google}})

(def report-path
  "resources/public/ptv-benchmark-report.html")

;;; Provider detection

(defn detect-provider [model]
  (cond
    (str/starts-with? model "gpt-")    :openai
    (str/starts-with? model "o1")      :openai
    (str/starts-with? model "o3")      :openai
    (str/starts-with? model "claude-") :anthropic
    (str/starts-with? model "gemini-") :google
    :else                              :openai))

;;; Data fetching

(defn get-search-client
  "Get the ES client from the search component."
  []
  (let [search-component (-> integrant.repl.state/system :lipas/search)]
    {:client (:client search-component)
     :idx-name (get-in search-component [:indices :sports-site :search])}))

(defn get-sample-sports-sites
  "Fetch diverse sports sites for benchmarking.
   Returns sites with varying amounts of data."
  [n]
  (let [{:keys [client idx-name]} (get-search-client)
        results (search/search
                 client
                 idx-name
                 {:size (* n 3)
                  :query {:bool {:must [{:term {:status.keyword "active"}}
                                        {:exists {:field "properties"}}]}}
                  :sort [{:_score :desc}
                         {:lipas-id :asc}]})
        hits (get-in results [:body :hits :hits])]
    (->> hits
         (map :_source)
         (take n))))

(defn fetch-site-by-id
  "Fetch a specific sports site by lipas-id."
  [lipas-id]
  (let [{:keys [client idx-name]} (get-search-client)
        results (search/search
                 client
                 idx-name
                 {:size 1
                  :query {:term {:lipas-id lipas-id}}})]
    (-> results :body :hits :hits first :_source)))

(defn categorize-data-richness
  "Categorize how much data a sports site has."
  [site]
  (let [props (get site :properties {})
        prop-count (count props)
        has-comment? (not (str/blank? (:comment site)))
        has-www? (not (str/blank? (:www site)))]
    (cond
      (and (> prop-count 10) has-comment?) :rich
      (or (> prop-count 5) has-comment? has-www?) :medium
      :else :minimal)))

;;; AI completion with model override

(defn gpt5-model? [model]
  (or (str/starts-with? model "gpt-5")
      (str/starts-with? model "o1")
      (str/starts-with? model "o3")))

(defn calculate-cost
  "Calculate estimated cost in USD for a request."
  [model prompt-tokens completion-tokens]
  (when-let [pricing (get model-pricing model)]
    (+ (* (/ prompt-tokens 1000000.0) (:input pricing))
       (* (/ completion-tokens 1000000.0) (:output pricing)))))

;; OpenAI completion
(defn complete-openai
  "Run completion with OpenAI API."
  [model prompt system-instruction]
  (let [is-gpt5 (gpt5-model? model)
        body (cond-> {:model model
                      :n 1
                      :response_format {:type "json_schema"
                                        :json_schema {:name "Response"
                                                      :strict true
                                                      :schema ai/Response}}
                      :messages [{:role "system" :content system-instruction}
                                 {:role "user" :content prompt}]}
               is-gpt5 (assoc :max_completion_tokens 4096)
               (not is-gpt5) (assoc :max_tokens 4096)
               (not is-gpt5) (assoc :top_p 0.5 :presence_penalty -2))
        headers {:Authorization (str "Bearer " (:api-key openai-config))
                 :Content-Type "application/json"}
        start-time (System/currentTimeMillis)
        response (try
                   (-> (client/post (:completions-url openai-config)
                                    {:headers headers
                                     :body (json/encode body)})
                       :body
                       (json/decode keyword))
                   (catch Exception e
                     {:error (.getMessage e)}))
        end-time (System/currentTimeMillis)
        duration-ms (- end-time start-time)]
    (if (:error response)
      {:model model :provider :openai :error (:error response) :duration-ms duration-ms}
      (let [choice (first (:choices response))
            content (-> choice :message :content (json/decode keyword))
            usage (:usage response)
            prompt-tokens (:prompt_tokens usage)
            completion-tokens (:completion_tokens usage)]
        {:model model
         :provider :openai
         :duration-ms duration-ms
         :prompt-tokens prompt-tokens
         :completion-tokens completion-tokens
         :total-tokens (:total_tokens usage)
         :cost-usd (calculate-cost model prompt-tokens completion-tokens)
         :content content
         :finish-reason (:finish_reason choice)}))))

;; Anthropic Claude completion
(defn complete-anthropic
  "Run completion with Anthropic Claude API.
   Claude doesn't support JSON Schema directly, so we parse the response."
  [model prompt system-instruction]
  (let [;; Modify instruction to request JSON output
        json-instruction (str system-instruction
                              "\n\nIMPORTANT: Return your response as valid JSON with this exact structure:\n"
                              "{\"description\": {\"fi\": \"...\", \"se\": \"...\", \"en\": \"...\"},\n"
                              " \"summary\": {\"fi\": \"...\", \"se\": \"...\", \"en\": \"...\"}}")
        body {:model model
              :max_tokens 4096
              :system json-instruction
              :messages [{:role "user" :content prompt}]}
        headers {:x-api-key (:api-key anthropic-config)
                 :anthropic-version (:api-version anthropic-config)
                 :Content-Type "application/json"}
        start-time (System/currentTimeMillis)
        response (try
                   (-> (client/post (:messages-url anthropic-config)
                                    {:headers headers
                                     :body (json/encode body)})
                       :body
                       (json/decode keyword))
                   (catch Exception e
                     {:error (.getMessage e)}))
        end-time (System/currentTimeMillis)
        duration-ms (- end-time start-time)]
    (if (:error response)
      {:model model :provider :anthropic :error (:error response) :duration-ms duration-ms}
      (let [text-content (->> (:content response)
                              (filter #(= "text" (:type %)))
                              first
                              :text)
            ;; Try to extract JSON from the response (handle markdown code blocks)
            clean-text (some-> text-content
                               (str/replace #"```json\s*" "")
                               (str/replace #"```\s*$" ""))
            ;; Match JSON with either key order (description/summary or summary/description)
            json-match (or (re-find #"(?s)\{.*\"description\".*\"summary\".*\}" (or clean-text ""))
                           (re-find #"(?s)\{.*\"summary\".*\"description\".*\}" (or clean-text "")))
            content (when json-match
                      (try (json/decode json-match keyword) (catch Exception _ nil)))
            usage (:usage response)
            prompt-tokens (:input_tokens usage)
            completion-tokens (:output_tokens usage)]
        {:model model
         :provider :anthropic
         :duration-ms duration-ms
         :prompt-tokens prompt-tokens
         :completion-tokens completion-tokens
         :total-tokens (+ (or prompt-tokens 0) (or completion-tokens 0))
         :cost-usd (calculate-cost model prompt-tokens completion-tokens)
         :content content
         :finish-reason (:stop_reason response)
         :raw-text (when-not content text-content)}))))

;; Google Gemini completion
(defn complete-gemini
  "Run completion with Google Gemini API."
  [model prompt system-instruction]
  (let [url (str (:base-url gemini-config) "/" model ":generateContent?key=" (:api-key gemini-config))
        ;; Gemini uses different structure - system instruction as first message
        json-instruction (str system-instruction
                              "\n\nReturn your response as valid JSON with this exact structure:\n"
                              "{\"description\": {\"fi\": \"...\", \"se\": \"...\", \"en\": \"...\"},\n"
                              " \"summary\": {\"fi\": \"...\", \"se\": \"...\", \"en\": \"...\"}}")
        body {:contents [{:role "user"
                          :parts [{:text (str json-instruction "\n\n" prompt)}]}]
              :generationConfig {:maxOutputTokens 4096
                                 :temperature 0.3}}
        headers {:Content-Type "application/json"}
        start-time (System/currentTimeMillis)
        response (try
                   (-> (client/post url {:headers headers :body (json/encode body)})
                       :body
                       (json/decode keyword))
                   (catch Exception e
                     {:error (.getMessage e)}))
        end-time (System/currentTimeMillis)
        duration-ms (- end-time start-time)]
    (if (:error response)
      {:model model :provider :google :error (:error response) :duration-ms duration-ms}
      (let [text-content (-> response :candidates first :content :parts first :text)
            ;; Try to extract JSON from the response
            json-match (re-find #"(?s)\{.*\"description\".*\"summary\".*\}" (or text-content ""))
            content (when json-match
                      (try (json/decode json-match keyword) (catch Exception _ nil)))
            usage (:usageMetadata response)
            prompt-tokens (:promptTokenCount usage)
            completion-tokens (:candidatesTokenCount usage)]
        {:model model
         :provider :google
         :duration-ms duration-ms
         :prompt-tokens prompt-tokens
         :completion-tokens completion-tokens
         :total-tokens (:totalTokenCount usage)
         :cost-usd (calculate-cost model prompt-tokens completion-tokens)
         :content content
         :finish-reason (-> response :candidates first :finishReason)
         :raw-text (when-not content text-content)}))))

;; Unified completion dispatcher
(defn complete-with-model
  "Run AI completion with a specific model, returning timing and usage info."
  [model sports-site]
  (let [prompt-doc (ai/->prompt-doc sports-site)
        prompt (format ai/generate-utp-descriptions-prompt (json/encode prompt-doc))
        provider (detect-provider model)]
    (case provider
      :openai    (complete-openai model prompt ai/ptv-system-instruction-v3)
      :anthropic (complete-anthropic model prompt ai/ptv-system-instruction-v3)
      :google    (complete-gemini model prompt ai/ptv-system-instruction-v3))))

;;; HTML Report Generation

(defn format-duration [ms]
  (if ms
    (format "%.1fs" (/ ms 1000.0))
    "-"))

(defn format-tokens [tokens]
  (if tokens
    (format "%,d" (long tokens))
    "-"))

(defn format-cost [cost-usd]
  (if cost-usd
    (format "$%.5f" cost-usd)
    "-"))

(defn provider-label [provider]
  (case provider
    :openai "OpenAI"
    :anthropic "Anthropic"
    :google "Google"
    "Unknown"))

(defn truncate-text [s max-len]
  (if (and s (> (count s) max-len))
    (str (subs s 0 max-len) "...")
    s))

(defn escape-html [s]
  (when s
    (-> (str s)
        (str/replace "&" "&amp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;")
        (str/replace "\"" "&quot;"))))

(def css-styles
  "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; background: #f5f5f5; }
h1 { color: #333; }
h3 { color: #666; margin: 0; }
h4 { margin: 10px 0; }
.site-card { background: white; border-radius: 8px; padding: 20px; margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.site-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
.site-info { font-size: 14px; color: #666; margin-top: 5px; }
.richness-badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
.richness-rich { background: #c8e6c9; color: #2e7d32; }
.richness-medium { background: #fff9c4; color: #f57f17; }
.richness-minimal { background: #ffcdd2; color: #c62828; }
.comparison-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
.comparison-table th, .comparison-table td { padding: 12px; text-align: left; border: 1px solid #ddd; vertical-align: top; }
.comparison-table th { background: #f0f0f0; font-weight: 600; }
.comparison-table tr:nth-child(even) td { background: #fafafa; }
.model-name { font-weight: bold; white-space: nowrap; }
.stats { font-size: 12px; color: #888; }
.text-fi { color: #1565c0; }
.text-se { color: #6d4c00; }
.text-en { color: #d32f2f; }
.lang-label { font-size: 10px; font-weight: bold; text-transform: uppercase; margin-right: 5px; display: inline-block; width: 22px; }
.description-text { font-size: 13px; line-height: 1.5; margin: 8px 0; }
.summary-text { font-size: 14px; font-weight: 500; margin: 8px 0; }
.input-section { background: #e3f2fd; padding: 15px; border-radius: 4px; margin: 15px 0; }
.input-json { font-family: monospace; font-size: 11px; max-height: 300px; overflow: auto; white-space: pre-wrap; background: white; padding: 10px; border-radius: 4px; }
.error { color: #c62828; font-style: italic; }
.toggle-btn { background: #1976d2; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; }
.toggle-btn:hover { background: #1565c0; }
.hidden { display: none; }
.metrics-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }
.metric-card { background: white; padding: 15px; border-radius: 8px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.metric-value { font-size: 24px; font-weight: bold; color: #1976d2; }
.metric-label { font-size: 14px; color: #333; margin-top: 5px; }
.metric-stats { font-size: 12px; color: #666; margin-top: 5px; }
.provider-openai { border-left: 4px solid #10a37f; }
.provider-anthropic { border-left: 4px solid #d97706; }
.provider-google { border-left: 4px solid #4285f4; }
.provider-badge { display: inline-block; padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: bold; margin-left: 8px; }
.provider-badge.openai { background: #d1fae5; color: #065f46; }
.provider-badge.anthropic { background: #fef3c7; color: #92400e; }
.provider-badge.google { background: #dbeafe; color: #1e40af; }
.cost-info { color: #059669; font-weight: 500; }")

(defn generate-html-report
  "Generate an HTML comparison report."
  [benchmark-results models]
  (let [metric-cards
        (str/join "\n"
                  (for [model models]
                    (let [all-results (mapcat :results benchmark-results)
                          model-results (filter #(= model (:model %)) all-results)
                          provider (detect-provider model)
                          avg-duration (when (seq model-results)
                                         (/ (reduce + (map :duration-ms model-results))
                                            (count model-results)))
                          avg-tokens (when (seq model-results)
                                       (/ (reduce + (keep :total-tokens model-results))
                                          (max 1 (count (filter :total-tokens model-results)))))
                          total-cost (reduce + (keep :cost-usd model-results))]
                      (format "<div class='metric-card provider-%s'>
                                <div class='metric-value'>%s</div>
                                <div class='metric-label'>%s<span class='provider-badge %s'>%s</span></div>
                                <div class='metric-stats'>Avg tokens: %s</div>
                                <div class='metric-stats cost-info'>Total cost: %s</div>
                              </div>"
                              (name provider)
                              (format-duration avg-duration)
                              (escape-html model)
                              (name provider)
                              (provider-label provider)
                              (format-tokens (int (or avg-tokens 0)))
                              (format-cost total-cost)))))

        site-cards
        (str/join "\n"
                  (for [site benchmark-results]
                    (let [result-rows
                          (str/join "\n"
                                    (for [result (:results site)]
                                      (let [content (:content result)]
                                        (format "<tr>
                                                  <td class='model-name'>%s</td>
                                                  <td>%s</td>
                                                  <td>%s</td>
                                                  <td class='stats'>%s</td>
                                                </tr>"
                                                (escape-html (:model result))
                                                (if (:error result)
                                                  (format "<span class='error'>%s</span>" (escape-html (:error result)))
                                                  (format "<div class='summary-text text-fi'><span class='lang-label'>FI</span>%s</div>
                                                           <div class='summary-text text-se'><span class='lang-label'>SE</span>%s</div>
                                                           <div class='summary-text text-en'><span class='lang-label'>EN</span>%s</div>"
                                                          (escape-html (get-in content [:summary :fi]))
                                                          (escape-html (get-in content [:summary :se]))
                                                          (escape-html (get-in content [:summary :en]))))
                                                (if (:error result)
                                                  ""
                                                  (format "<div class='description-text text-fi'><span class='lang-label'>FI</span>%s</div>
                                                           <div class='description-text text-se'><span class='lang-label'>SE</span>%s</div>
                                                           <div class='description-text text-en'><span class='lang-label'>EN</span>%s</div>"
                                                          (escape-html (truncate-text (get-in content [:description :fi]) 500))
                                                          (escape-html (truncate-text (get-in content [:description :se]) 500))
                                                          (escape-html (truncate-text (get-in content [:description :en]) 500))))
                                                (if (:error result)
                                                  ""
                                                  (format "<div>Time: %s</div><div>Tokens: %s</div><div class='cost-info'>Cost: %s</div>"
                                                          (format-duration (:duration-ms result))
                                                          (format-tokens (:total-tokens result))
                                                          (format-cost (:cost-usd result))))))))]
                      (format "<div class='site-card'>
                                <div class='site-header'>
                                  <div>
                                    <h3>%s (LIPAS ID: %s)</h3>
                                    <div class='site-info'>%s — %s</div>
                                  </div>
                                  <span class='richness-badge richness-%s'>%s DATA</span>
                                </div>
                                <button class='toggle-btn' onclick=\"document.getElementById('input-%s').classList.toggle('hidden')\">Toggle Input Data</button>
                                <div id='input-%s' class='input-section hidden'>
                                  <h4>Input Data (sent to AI)</h4>
                                  <pre class='input-json'>%s</pre>
                                </div>
                                <table class='comparison-table'>
                                  <thead>
                                    <tr><th>Model</th><th>Summary (FI/SE/EN)</th><th>Description (FI/SE/EN)</th><th>Stats</th></tr>
                                  </thead>
                                  <tbody>%s</tbody>
                                </table>
                              </div>"
                              (escape-html (:name site))
                              (:lipas-id site)
                              (escape-html (:type site))
                              (escape-html (:city site))
                              (name (:richness site))
                              (str/upper-case (name (:richness site)))
                              (:lipas-id site)
                              (:lipas-id site)
                              (escape-html (json/encode (:input-data site) {:pretty true}))
                              result-rows))))]

    (format "<!DOCTYPE html>
<html>
<head>
  <meta charset='UTF-8'>
  <title>PTV AI Model Benchmark</title>
  <style>%s</style>
</head>
<body>
  <h1>PTV AI Model Benchmark Report</h1>
  <p>Generated: %s</p>
  <p>Models tested: %s</p>
  <div class='metrics-summary'>%s</div>
  %s
</body>
</html>"
            css-styles
            (str (java.time.LocalDateTime/now))
            (str/join ", " models)
            metric-cards
            site-cards)))

(defn open-report!
  "Open the benchmark report in the default browser."
  []
  (let [path (str "file://" (.getAbsolutePath (io/file report-path)))]
    (sh "open" path)
    (println "Opened:" path)))

;;; Benchmark execution

(defn benchmark-site
  "Run benchmark for a single site across all models."
  [models site]
  (let [lipas-id (:lipas-id site)
        ;; Name can be a string or nested in search-meta
        site-name (or (:name site)
                      (get-in site [:search-meta :name])
                      "Unknown")
        ;; Type name is in search-meta
        type-name (or (get-in site [:search-meta :type :name :fi])
                      (get-in site [:type :name :fi])
                      "Unknown")
        ;; City name is in search-meta
        city-name (or (get-in site [:search-meta :location :city :name :fi])
                      (get-in site [:location :city :name :fi])
                      "Unknown")
        richness (categorize-data-richness site)]
    (log/infof "Benchmarking site %s: %s (%s) [%s data]"
               lipas-id site-name type-name (name richness))
    {:lipas-id lipas-id
     :name site-name
     :type type-name
     :city city-name
     :richness richness
     :input-data (ai/->prompt-doc site)
     :results (vec (for [model models]
                     (do
                       (log/infof "  Testing model: %s" model)
                       (let [result (complete-with-model model site)]
                         (log/infof "    Done in %dms (%d tokens)"
                                    (:duration-ms result)
                                    (:total-tokens result 0))
                         result))))}))

(defn run-benchmark!
  "Run the full benchmark.

   Options:
   - :models - Vector of model names to test (default: default-models)
   - :lipas-ids - Specific lipas-ids to test (default: auto-select diverse sites)
   - :n - Number of sites to test if auto-selecting (default: 3)"
  ([] (run-benchmark! {}))
  ([{:keys [models lipas-ids n]
     :or {models default-models
          n 3}}]
   (let [sites (if lipas-ids
                 (mapv fetch-site-by-id lipas-ids)
                 (get-sample-sports-sites n))
         _ (log/infof "Running benchmark with %d models on %d sites"
                      (count models) (count sites))
         results (mapv #(benchmark-site models %) sites)
         report-html (generate-html-report results models)]
     (spit report-path report-html)
     (log/infof "Report saved to %s" report-path)
     {:results results
      :report-path report-path})))

;;; Utilities

(defn list-available-models
  "List all available GPT models from OpenAI."
  []
  (let [headers {:Authorization (str "Bearer " (:api-key openai-config))
                 :Content-Type "application/json"}]
    (->> (client/get "https://api.openai.com/v1/models" {:headers headers})
         :body
         (#(json/decode % keyword))
         :data
         (map :id)
         (filter #(re-find #"gpt" %))
         sort
         vec)))

;; Default sample sites with varying data richness
(def sample-lipas-ids
  [505789   ; Rich: Epitihian uimahalli (swimming hall, 12 properties, comment)
   72269    ; Medium: Keuruun Koulukeskuksen lähiliikunta-alue (5 properties, comment)
   72292])  ; Minimal: Haminan kesäpuisto (no properties, no comment, no www)

(comment
  ;; List all available GPT models
  (list-available-models)

  ;; Run benchmark with defaults (OpenAI + Anthropic + Google)
  (run-benchmark!)

  ;; Run benchmark with sample sites (recommended)
  (run-benchmark! {:lipas-ids sample-lipas-ids})

  ;; Compare fast/cheap models across all providers
  (run-benchmark! {:models ["gpt-4.1-mini"             ; OpenAI baseline
                            "claude-3-5-haiku-latest"  ; Anthropic fast
                            "gemini-2.0-flash"]        ; Google fast
                   :lipas-ids sample-lipas-ids})

  ;; Compare quality models
  (run-benchmark! {:models ["gpt-4.1-mini"              ; OpenAI baseline
                            "claude-3-5-sonnet-latest"  ; Anthropic quality
                            "gemini-2.5-pro-preview-05-06"]  ; Google quality
                   :lipas-ids sample-lipas-ids})

  ;; Test single provider
  (run-benchmark! {:models ["gpt-4.1-mini" "gpt-4o-mini"]
                   :lipas-ids sample-lipas-ids})

  ;; Open the report
  (open-report!))
