(ns lipas.backend.assistant
  "AI help assistant: a Gemini function-calling loop grounded in the
   knowledge base (lipas.backend.kb) plus read-only data-maintenance
   tools over the sports-site search index.

   Design constraints:
   - Registered users only (:ai-assistant/use privilege, checked at the
     route). The Gemini key never leaves the backend.
   - The first model turn is FORCED to call a tool (toolConfig ANY), so
     answers are grounded in retrieval, not vibes.
   - All tools are read-only. escalate_to_support only *drafts* a
     support request — the send happens via a separate endpoint after
     the user confirms in the UI.
   - Sports-site data is public open data; the user's editing scope is
     used as a relevance default for listing tools, not as a security
     boundary."
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [lipas.backend.kb :as kb]
            [lipas.backend.search :as search]
            [lipas.backend.llm :as llm]
            [lipas.data.cities :as cities]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.jobs.core :as jobs]
            [lipas.roles :as roles]
            [taoensso.timbre :as log]))

(def context-schema
  "The app-db snapshot the widget sends with each message. Closed map so
   junk can't flow into the prompt; server-side scope derivation never
   trusts any of this."
  [:map {:closed true}
   [:route {:optional true} [:string {:max 200}]]
   [:locale {:optional true} [:enum "fi" "se" "en"]]
   [:view {:optional true} [:string {:max 100}]]
   [:site {:optional true}
    [:map {:closed true}
     [:lipas-id {:optional true} :int]
     [:name {:optional true} [:string {:max 200}]]
     [:type-code {:optional true} :int]
     [:status {:optional true} [:string {:max 50}]]]]
   [:edit-mode? {:optional true} :boolean]
   [:active-tool {:optional true} [:string {:max 100}]]
   [:search-filters {:optional true} [:string {:max 500}]]])

(def max-tool-iterations 6)
(def max-history-messages 12)
(def chat-rate-limit {:window-ms (* 60 60 1000) :max 30})
(def escalation-rate-limit {:window-ms (* 24 60 60 1000) :max 5})
(def support-email "lipasinfo@jyu.fi")

;;; ——— User scope ————————————————————————————————————————————————————

(defn editable-scope
  "Union of city/type codes the user's roles allow editing. Approximate
   by design (union across roles): this drives the *default* filters of
   listing tools, not authorization — site data is public."
  [user]
  (let [entries (->> (get-in user [:permissions :roles])
                     (map #(update % :role keyword))
                     (filter #(contains? (get-in roles/roles [(:role %) :privileges] #{})
                                         :site/create-edit)))]
    (cond
      (empty? entries)
      {:none? true}

      (some #(and (empty? (:city-code %)) (empty? (:type-code %))) entries)
      {:all? true}

      :else
      {:city-codes (into #{} (mapcat :city-code) entries)
       :type-codes (into #{} (mapcat :type-code) entries)})))

;;; ——— Tools ————————————————————————————————————————————————————————

(def tool-declarations
  [{:name "search_kb"
    :description "Search the LIPAS help knowledge base (user guides, tool instructions, facility type codes, data-model field definitions). ALWAYS use this before answering a how-to or what-is question."
    :parameters {:type "object"
                 :properties {:query {:type "string"
                                      :description "Natural-language search query, in the user's language"}
                              :lang {:type "string" :enum ["fi" "se" "en"]
                                     :description "User's UI language"}}
                 :required ["query"]}}

   {:name "get_kb_document"
    :description "Fetch the FULL text of a knowledge-base entry by its id. search_kb returns truncated snippets — always fetch the full entry before answering a how-to question based on it."
    :parameters {:type "object"
                 :properties {:id {:type "string"
                                   :description "Entry id from search_kb results"}}
                 :required ["id"]}}

   {:name "lookup_type_code"
    :description "Resolve a sports facility type: by numeric type code or by free-text name (colloquial names work). Returns the official name, description, geometry and allowed properties."
    :parameters {:type "object"
                 :properties {:query {:type "string"
                                      :description "Type code (e.g. '1390') or name (e.g. 'padel')"}}
                 :required ["query"]}}

   {:name "explain_field"
    :description "Explain a sports-site data-model field/property: what it means and which facility types use it."
    :parameters {:type "object"
                 :properties {:query {:type "string"
                                      :description "Field name in Finnish or English, e.g. 'pintamateriaali' or 'surface-material'"}}
                 :required ["query"]}}

   {:name "list_stale_sites"
    :description "List sports sites that have not been updated since a given date — the user's data-maintenance backlog. Defaults to the user's own municipalities/types when no filters given."
    :parameters {:type "object"
                 :properties {:not-updated-since {:type "string"
                                                  :description "ISO date, e.g. '2023-01-01'"}
                              :city-names {:type "array" :items {:type "string"}
                                           :description "Municipality names, e.g. [\"Utajärvi\"] — resolved server-side"}
                              :city-codes {:type "array" :items {:type "integer"}}
                              :type-codes {:type "array" :items {:type "integer"}}
                              :limit {:type "integer"}}
                 :required ["not-updated-since"]}}

   {:name "list_sites_missing_data"
    :description "List sports sites missing given data fields (e.g. no www, no surface-material). Defaults to the user's own municipalities/types."
    :parameters {:type "object"
                 :properties {:fields {:type "array" :items {:type "string"}
                                       :description "Field keys, e.g. [\"www\", \"surface-material\"]"}
                              :city-names {:type "array" :items {:type "string"}
                                           :description "Municipality names — resolved server-side"}
                              :city-codes {:type "array" :items {:type "integer"}}
                              :type-codes {:type "array" :items {:type "integer"}}
                              :limit {:type "integer"}}
                 :required ["fields"]}}

   {:name "get_site_summary"
    :description "Current key data of one sports site by lipas-id."
    :parameters {:type "object"
                 :properties {:lipas-id {:type "integer"}}
                 :required ["lipas-id"]}}

   {:name "escalate_to_support"
    :description "When you cannot answer from the knowledge base, or the user asks for something requiring human support (account issues, data corrections you cannot guide, bugs): draft a support request. The user confirms before anything is sent."
    :parameters {:type "object"
                 :properties {:summary {:type "string"
                                        :description "Concise summary of the user's problem, in the user's language"}}
                 :required ["summary"]}}])

;;; — Tool handlers —

(defn- top-level-field? [f]
  (contains? #{"www" "email" "phone-number" "reservations-link"
               "construction-year" "renovation-years"} f))

(defn- resolve-city-names
  [names]
  (let [wanted (into #{} (map str/lower-case) names)]
    (->> cities/by-city-code
         (filter (fn [[_ m]] (contains? wanted (str/lower-case (get-in m [:name :fi] "")))))
         (mapv first))))

(defn- scope-filters
  "Explicit request filters win; otherwise fall back to the user's scope."
  [{:keys [city-codes city-names type-codes]} scope]
  (let [cities (or (seq (concat city-codes (when (seq city-names)
                                             (resolve-city-names city-names))))
                   (seq (:city-codes scope)))
        types* (or (seq type-codes)
                   (seq (:type-codes scope)))]
    (cond-> []
      cities (conj {:terms {:location.city.city-code (vec cities)}})
      types* (conj {:terms {:type.type-code (vec types*)}}))))

(defn- site-hit->summary [hit]
  (let [src (:_source hit)]
    {:lipas-id (:lipas-id src)
     :name (:name src)
     :type (str (-> src :type :type-code) " "
                (get-in types/all [(-> src :type :type-code) :name :fi]))
     :city-code (-> src :location :city :city-code)
     :status (:status src)
     :last-updated (:event-date src)
     :link (str "https://www.lipas.fi/liikuntapaikat/" (:lipas-id src))}))

(defn- list-sites [search query-filters extra-clauses limit]
  (let [idx (get-in search [:indices :sports-site :search])
        resp (search/search
              (:client search) idx
              {:size (min (or limit 20) 50)
               :sort [{:event-date {:order "asc"}}]
               :query {:bool (merge {:filter (into [{:terms {:status ["active" "out-of-service-temporarily"]}}]
                                                   query-filters)}
                                    extra-clauses)}
               :_source [:lipas-id :name :type.type-code :location.city.city-code
                         :status :event-date]})]
    {:total (-> resp :body :hits :total :value)
     :sites (mapv site-hit->summary (-> resp :body :hits :hits))}))

(defn- run-tool*
  [{:keys [db search user scope]} tool-name args]
  (case tool-name
    "search_kb"
    (kb/search-kb search {:query (:query args)
                          :lang (or (:lang args) "fi")
                          :limit 5})

    "get_kb_document"
    (or (kb/get-doc search (:id args))
        {:error "No entry with that id"})

    "lookup_type_code"
    (let [q (str/trim (str (:query args)))
          code (parse-long q)]
      (if-let [m (and code (get types/all code))]
        {:type-code code
         :name (:name m)
         :description (:description m)
         :geometry (:geometry-type m)
         :status (:status m)
         :properties (->> (:props m) keys
                          (keep #(get-in prop-types/all [% :name :fi]))
                          sort vec)}
        {:candidates (kb/search-kb search {:query q :lang "fi" :limit 5})}))

    "explain_field"
    (let [q (str/trim (str (:query args)))
          k (keyword q)]
      (if-let [m (get prop-types/all k)]
        {:field q :name (:name m) :description (:description m)
         :data-type (:data-type m)}
        {:candidates (kb/search-kb search {:query q :lang "fi" :limit 5})}))

    "list_stale_sites"
    (if (:none? scope)
      {:error "User has no site editing rights; ask which municipality they are interested in and pass explicit city-codes."}
      (list-sites search
                  (scope-filters args scope)
                  {:must_not [{:range {:event-date {:gte (:not-updated-since args)}}}]}
                  (:limit args)))

    "list_sites_missing_data"
    (if (:none? scope)
      {:error "User has no site editing rights; ask which municipality they are interested in and pass explicit city-codes."}
      (let [paths (map (fn [f] (if (top-level-field? f) f (str "properties." f)))
                       (:fields args))]
        (list-sites search
                    (scope-filters args scope)
                    {:must_not (mapv (fn [p] {:exists {:field p}}) paths)}
                    (:limit args))))

    "get_site_summary"
    (let [idx (get-in search [:indices :sports-site :search])
          src (-> (search/fetch-document (:client search) idx (:lipas-id args))
                  :body :_source)]
      (if src
        {:lipas-id (:lipas-id src)
         :name (:name src)
         :type (str (-> src :type :type-code) " "
                    (get-in types/all [(-> src :type :type-code) :name :fi]))
         :city-code (-> src :location :city :city-code)
         :status (:status src)
         :last-updated (:event-date src)
         :construction-year (:construction-year src)
         :www (:www src)
         :properties (:properties src)
         :link (str "https://www.lipas.fi/liikuntapaikat/" (:lipas-id args))}
        {:error "Site not found"}))

    "escalate_to_support"
    ;; Marker result only: answer! short-circuits on this tool and the
    ;; widget renders a confirmation card. Nothing is sent here.
    {:escalation-proposed true :summary (:summary args)}

    {:error (str "Unknown tool " tool-name)}))

(defn- run-tool [ctx tool-name args]
  (try
    (run-tool* ctx tool-name args)
    (catch Exception e
      (log/warn e "Assistant tool failed" {:tool tool-name :args args})
      {:error (str "Tool failed: " (.getMessage e))})))

;;; ——— Prompt ————————————————————————————————————————————————————————

(defn- system-prompt
  [{:keys [user scope context]}]
  (str
   "You are the LIPAS assistant (LIPAS-avustaja), embedded in lipas.fi — Finland's national sports facility database. Users are registered maintainers (municipality employees and other data producers).

RULES:
- Answer ONLY from tool results. If the knowledge base has nothing relevant, say so honestly and offer to contact support with escalate_to_support. Never invent UI elements, menu names or type codes.
- search_kb returns TRUNCATED snippets. Before answering a how-to question, fetch the most relevant entries in full with get_kb_document — the snippet alone is not grounds to conclude the guides lack the answer.
- PARTIAL INFORMATION: when tool results cover the topic only partially, give exactly what they contain and link the source for the rest. Do NOT fill gaps from imagination — a numbered step, tab name, icon or button label that is not in a tool result must not appear in your answer.
- NO COVERAGE: when search_kb returns nothing relevant to the question, do not answer from general knowledge — say the guides don't cover it and propose escalate_to_support.
- Always answer in the user's language (see context; default Finnish).
- Cite sources: when your answer builds on a knowledge-base entry, link it in markdown using its deep-link and title, e.g. [Reitin lisääminen](?ohje=tallentajille/reitin-lisaaminen). Links starting with ?ohje= open the in-app help; full URLs open in a new tab.
- Scope: LIPAS usage, sports facility data maintenance, type codes, data model, LIPAS tools. Politely decline anything else.
- Keep answers short and task-focused; numbered steps for how-tos.
- For data-maintenance questions (\"mitkä kohteet kaipaavat päivitystä?\") use the listing tools; present results as a compact markdown list with links.

USER CONTEXT:
"
   (json/encode
    {:name (-> user :user-data :firstname)
     :editing-scope (cond
                      (:all? scope) "all municipalities and types (admin)"
                      (:none? scope) "no editing rights"
                      :else scope)
     :app-context context})))

;;; ——— Gemini chat with tools ———————————————————————————————————————

(defn- gemini-chat
  [system contents tool-mode]
  (let [{:keys [base-url api-key]} llm/gemini-config
        model (:model llm/gemini-default-params)
        body {:systemInstruction {:parts [{:text system}]}
              :contents contents
              :tools [{:functionDeclarations tool-declarations}]
              :toolConfig {:functionCallingConfig {:mode tool-mode}}
              :generationConfig {:temperature 0.2
                                 :maxOutputTokens 4096
                                 :thinkingConfig {:thinkingLevel "minimal"}}}]
    (-> (http/post (str base-url "/models/" model ":generateContent")
                   {:headers {"x-goog-api-key" api-key
                              "Content-Type" "application/json"}
                    :body (json/encode body)
                    :content-type :json
                    :socket-timeout 120000
                    :connection-timeout 10000})
        :body
        (json/decode keyword))))

(defn- history->contents [history]
  (mapv (fn [{:keys [role text]}]
          {:role (if (= role "assistant") "model" "user")
           :parts [{:text text}]})
        (take-last max-history-messages history)))

(defn- collect-sources
  "KB docs retrieved during the conversation → citation candidates."
  [sources tool-name result]
  (if (and (= tool-name "search_kb") (sequential? result))
    (into sources (map #(select-keys % [:id :title :deep-link :source-type :lang]) result))
    sources))

(defn answer!
  "Run the assistant loop for one user message. Returns
   {:answer-md ... :sources [...] :escalation nil|{:summary ...}}."
  [{:keys [db search user message history context] :as _req}]
  (let [scope (editable-scope user)
        ctx {:db db :search search :user user :scope scope}
        system (system-prompt {:user user :scope scope :context context})]
    (loop [contents (conj (history->contents history)
                          {:role "user" :parts [{:text message}]})
           iter 0
           ;; First turn must ground itself in a tool call.
           mode "ANY"
           sources []]
      (let [resp (gemini-chat system contents mode)
            parts (or (-> resp :candidates first :content :parts) [])
            fcalls (keep :functionCall parts)
            text (->> parts (keep :text) (str/join))]
        (cond
          ;; Escalation proposed: stop, let the user confirm in the UI.
          (some #(= "escalate_to_support" (:name %)) fcalls)
          (let [fc (first (filter #(= "escalate_to_support" (:name %)) fcalls))]
            {:answer-md (if (str/blank? text)
                          "Voin lähettää kysymyksesi LIPAS-tuelle. Saat vastauksen sähköpostiisi."
                          text)
             :sources (vec (distinct sources))
             :escalation {:summary (-> fc :args :summary)}})

          (and (seq fcalls) (< iter max-tool-iterations))
          (let [results (mapv (fn [fc] (run-tool ctx (:name fc) (:args fc))) fcalls)
                sources' (reduce (fn [acc [fc r]] (collect-sources acc (:name fc) r))
                                 sources
                                 (map vector fcalls results))]
            (recur (into contents
                         [{:role "model" :parts (vec parts)}
                          {:role "user"
                           :parts (mapv (fn [fc r]
                                          {:functionResponse
                                           {:name (:name fc)
                                            :response {:result r}}})
                                        fcalls results)}])
                   (inc iter)
                   "AUTO"
                   sources'))

          :else
          {:answer-md (if (str/blank? text)
                        "Pahoittelut — en osannut muodostaa vastausta. Yritä muotoilla kysymys uudelleen."
                        text)
           :sources (vec (distinct sources))
           :escalation nil})))))

;;; ——— Rate limiting ————————————————————————————————————————————————

(defonce ^:private rate-state (atom {}))

(defn rate-limited?
  "Sliding-window limiter keyed by [kind user-id]. Mutates state."
  [kind user-id {:keys [window-ms max]}]
  (let [now (System/currentTimeMillis)
        k [kind user-id]
        stamps (->> (get @rate-state k [])
                    (filterv #(> % (- now window-ms))))]
    (if (>= (count stamps) max)
      (do (swap! rate-state assoc k stamps) true)
      (do (swap! rate-state assoc k (conj stamps now)) false))))

(defn- sha-256 [^String s]
  (->> (.digest (java.security.MessageDigest/getInstance "SHA-256")
                (.getBytes s "UTF-8"))
       (map #(format "%02x" %))
       (apply str)))

;;; ——— Logging ———————————————————————————————————————————————————————

(defn- log-exchange!
  [search doc]
  (future
    (try
      (search/index! (:client search)
                     (get-in search [:indices :assistant :logs])
                     (fn [_] (str (random-uuid)))
                     doc)
      (catch Exception e
        (log/warn e "Failed to index assistant log")))))

;;; ——— Public entrypoints (called from handler) —————————————————————

(defn chat!
  [{:keys [db search user] :as req}]
  (if (rate-limited? :chat (:id user) chat-rate-limit)
    {:status 429
     :body {:error "Viestiraja täynnä. Yritä myöhemmin uudelleen."}}
    (let [start (System/currentTimeMillis)
          result (answer! req)]
      (log-exchange! search
                     {:user-hash (sha-256 (str (:id user)))
                      :created-at (str (java.time.Instant/now))
                      :question (:message req)
                      :answer (:answer-md result)
                      :sources (mapv :id (:sources result))
                      :escalated? (some? (:escalation result))
                      :context (json/encode (:context req))
                      :took-ms (- (System/currentTimeMillis) start)})
      {:status 200 :body result})))

(defn escalate!
  "Send a user-confirmed support request to lipasinfo via the email job
   queue. The user's address goes into the body — support replies
   directly to them."
  [{:keys [db user summary transcript context]}]
  (if (rate-limited? :escalation (:id user) escalation-rate-limit)
    {:status 429
     :body {:error "Tukipyyntöraja täynnä tälle päivälle."}}
    (let [conversation-id (str (random-uuid))
          body (str "LIPAS-avustajan välittämä tukipyyntö\n"
                    "Tunniste: " conversation-id "\n"
                    "Käyttäjä: " (:email user)
                    " (" (-> user :user-data :firstname) " "
                    (-> user :user-data :lastname) ")\n"
                    "Vastaa suoraan käyttäjän osoitteeseen.\n\n"
                    "ONGELMA:\n" summary "\n\n"
                    "SOVELLUSKONTEKSTI:\n" (json/encode context) "\n\n"
                    "KESKUSTELU:\n"
                    (->> (take-last 10 transcript)
                         (map (fn [{:keys [role text]}] (str role ": " text)))
                         (str/join "\n\n")))]
      (jobs/enqueue-job! db "email"
                         {:to support-email
                          :subject (str "[LIPAS-avustaja] " (subs summary 0 (min 80 (count summary))))
                          :body body})
      {:status 200 :body {:sent true :conversation-id conversation-id}})))
