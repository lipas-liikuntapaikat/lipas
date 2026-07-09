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
            [clojure.java.io :as io]
            [clojure.string :as str]
            [lipas.backend.kb :as kb]
            [lipas.backend.search :as search]
            [lipas.backend.llm :as llm]
            [lipas.data.cities :as cities]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.jobs.core :as jobs]
            [lipas.roles :as roles]
            [lipas.schema.assistant :as assistant-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [malli.core :as m]
            [malli.error :as me]
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
   [:search-filters {:optional true} [:string {:max 500}]]
   [:help {:optional true} ; open help-center page ("about this topic" questions)
    [:map {:closed true}
     [:section {:optional true} [:string {:max 200}]]
     [:page {:optional true} [:string {:max 200}]]
     [:title {:optional true} [:string {:max 300}]]]]
   [:ptv {:optional true} ; open PTV export dialog (covers the map view)
    [:map {:closed true}
     [:open? {:optional true} :boolean]
     [:org {:optional true} [:string {:max 200}]]
     [:tab {:optional true} [:string {:max 100}]]
     [:wizard-step {:optional true} [:string {:max 100}]]]]])

(def max-tool-iterations 6)
(def max-history-messages 12)
(def chat-rate-limit {:window-ms (* 60 60 1000) :max 30})
(def escalation-rate-limit {:window-ms (* 24 60 60 1000) :max 5})
(def support-email "lipasinfo@jyu.fi")

;;; ——— Names for codes ———————————————————————————————————————————————
;;
;; The model decorates bare codes with names from its world knowledge —
;; and gets them wrong (city 320 is Kemijärvi, not Muurame). Every code
;; that reaches the prompt or a tool result carries its name.

(def ^:private role-i18n
  "Official Finnish role names; single source: the UI translation file."
  (delay (-> (io/resource "lipas/i18n/fi/lipas_user_permissions_roles.edn")
             slurp
             read-string)))

(defn- city-label [code]
  (str code " " (get-in cities/by-city-code [code :name :fi] "?")))

(defn- type-label [code]
  (str code " " (get-in types/all [code :name :fi] "?")))

(defn- describe-roles
  "The user's roles with official names and resolved scopes."
  [user]
  (->> (get-in user [:permissions :roles])
       (mapv (fn [{:keys [role] :as r}]
               (let [role-kw (keyword role)]
                 (cond-> {:role (str (get-in @role-i18n [:role-names role-kw] (name role-kw))
                                     " (" (name role-kw) ")")}
                   (seq (:city-code r)) (assoc :cities (mapv city-label (sort (:city-code r))))
                   (seq (:type-code r)) (assoc :types (mapv type-label (sort (:type-code r))))
                   (seq (:lipas-id r)) (assoc :sites (vec (sort (:lipas-id r))))
                   (seq (:activity r)) (assoc :activities (vec (sort (:activity r))))
                   (seq (:org-id r)) (assoc :org-ids (vec (:org-id r)))))))))

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

   {:name "check_site_permission"
    :description "Authoritative check from the LIPAS permission engine: can the CURRENT user edit a given sports site? ALWAYS use this for questions like 'miksi en voi muokata tätä kohdetta' or 'saanko muokata kohteen X' — its verdict overrides any reasoning of your own. Also returns the user's roles with official names."
    :parameters {:type "object"
                 :properties {:lipas-id {:type "integer"}}
                 :required ["lipas-id"]}}

   {:name "apply_search"
    :description "Propose a UI action: run a sports-site search in the map view on the user's behalf. The call does NOT execute anything — the user gets a button and clicks to run it. Use when the user asks to find/see/list sites (e.g. 'mitä liikuntapaikkoja on Äänekoskella?', 'näytä kohteet joita voin muokata'). Resolve free-text type names to codes with lookup_type_code first. The map also pans to the results."
    :parameters {:type "object"
                 :properties {:label {:type "string"
                                      :description "Button text in the user's language, short and imperative, e.g. 'Hae: uimahallit Äänekoski'"}
                              :search-text {:type "string"
                                            :description "Free-text search words, only when searching by name/keyword"}
                              :city-names {:type "array" :items {:type "string"}
                                           :description "Municipality names, e.g. [\"Äänekoski\"] — resolved server-side"}
                              :type-codes {:type "array" :items {:type "integer"}
                                           :description "Facility type codes from lookup_type_code"}
                              :only-editable {:type "boolean"
                                              :description "true = only sites the user has rights to edit"}}
                 :required ["label"]}}

   {:name "show_site_on_map"
    :description "Propose a UI action: open one sports site on the map (button; the user clicks to run it). Use when the conversation concerns a specific site whose lipas-id you know from a tool result."
    :parameters {:type "object"
                 :properties {:label {:type "string"
                                      :description "Button text in the user's language, e.g. 'Näytä kartalla: Ounasvaaran latu'"}
                              :lipas-id {:type "integer"}}
                 :required ["label" "lipas-id"]}}

   {:name "pan_map_to_location"
    :description "Propose a UI action: pan/zoom the map to a named place — an address, area or municipality — without filtering the search (button; the user clicks to run it). For 'what sites are in X' prefer apply_search, which also fits the map to its results."
    :parameters {:type "object"
                 :properties {:label {:type "string"
                                      :description "Button text in the user's language"}
                              :location {:type "string"
                                         :description "Place name or address to geocode, e.g. 'Äänekoski' or 'Nallikari, Oulu'"}}
                 :required ["label" "location"]}}

   {:name "navigate_to_view"
    :description (str "Propose a UI action: open another view of the LIPAS app (button; the user clicks to run it). Use for 'missä näen/mistä löydän' navigation questions. Views: "
                      (str/join "; " (map (fn [[k v]] (str k " = " v))
                                          (sort assistant-schema/views))))
    :parameters {:type "object"
                 :properties {:label {:type "string"
                                      :description "Button text in the user's language, e.g. 'Avaa tilastot'"}
                              :view {:type "string"
                                     :enum (vec (sort (keys assistant-schema/views)))}}
                 :required ["label" "view"]}}

   {:name "escalate_to_support"
    :description "When you cannot answer from the knowledge base, or the user asks for something requiring human support (account issues, data corrections you cannot guide, bugs, wanting to talk to a human): draft a support request. The user confirms before anything is sent. ALWAYS write answer text alongside this call: tell the user an editable draft appears below your message and that pressing 'Lähetä tukipyyntö' sends it. If the user asked for a human without describing their issue, ask them to add what it concerns into the draft before sending."
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
     :type (type-label (-> src :type :type-code))
     :city (city-label (-> src :location :city :city-code))
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

(defn- ->client-action
  "Validate a candidate UI action against the closed vocabulary in
   lipas.schema.assistant. Valid → marker result the answer! loop
   collects into the response; invalid → error the model can act on."
  [action]
  (if (assistant-schema/valid? action)
    {:client-action action
     :note "Offered to the user as a button they can click. Refer to it briefly in your answer; never claim it already ran."}
    {:error (str "Invalid action: "
                 (pr-str (me/humanize (assistant-schema/explain action))))}))

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
         :type (type-label (-> src :type :type-code))
         :city (city-label (-> src :location :city :city-code))
         :status (:status src)
         :last-updated (:event-date src)
         :construction-year (:construction-year src)
         :www (:www src)
         :properties (:properties src)
         :link (str "https://www.lipas.fi/liikuntapaikat/" (:lipas-id args))}
        {:error "Site not found"}))

    "check_site_permission"
    (let [idx (get-in search [:indices :sports-site :search])
          src (try (-> (search/fetch-document (:client search) idx (:lipas-id args))
                       :body :_source)
                   (catch Exception _ nil))]
      (if src
        (let [;; DB-loaded roles may carry string role keys; the engine
              ;; looks roles up by keyword.
              user* (update-in user [:permissions :roles]
                               (fn [rs] (mapv #(update % :role keyword) rs)))
              can-edit? (boolean (roles/check-privilege
                                  user* (roles/site-roles-context src) :site/create-edit))]
          {:can-edit? can-edit?
           :site {:lipas-id (:lipas-id src)
                  :name (:name src)
                  :city (city-label (-> src :location :city :city-code))
                  :type (type-label (-> src :type :type-code))}
           :user-roles (describe-roles user)
           :note (if can-edit?
                   "Verdict from the permission engine: the user CAN edit this site."
                   "Verdict from the permission engine: the user CANNOT edit this site. They can request access via escalate_to_support.")})
        {:error "Site not found"}))

    "apply_search"
    (let [{:keys [label search-text city-names type-codes only-editable]} args
          city-codes (when (seq city-names) (resolve-city-names city-names))
          resolved (into #{} (map #(str/lower-case (get-in cities/by-city-code [% :name :fi] "")))
                         city-codes)
          unresolved (into [] (remove #(contains? resolved (str/lower-case %))) city-names)
          bad-types (into [] (remove #(m/validate types-schema/active-type-code %)) type-codes)]
      (cond
        (seq unresolved)
        {:error (str "Unknown municipalities: " (str/join ", " unresolved)
                     ". Use official Finnish municipality names.")}

        (seq bad-types)
        {:error (str "Unknown or retired type codes: " (str/join ", " bad-types)
                     ". Resolve them with lookup_type_code first.")}

        :else
        (->client-action
         (cond-> {:type "apply-search" :label (str label)}
           search-text (assoc :search-text search-text)
           (seq city-codes) (assoc :city-codes (vec city-codes))
           (seq type-codes) (assoc :type-codes (vec type-codes))
           only-editable (assoc :only-editable true)))))

    "show_site_on_map"
    (let [idx (get-in search [:indices :sports-site :search])
          exists? (try (some-> (search/fetch-document (:client search) idx (:lipas-id args))
                               :body :found)
                       ;; ES GET throws on 404 — a dead button is worse
                       ;; than telling the model the id is wrong.
                       (catch Exception _ false))]
      (if exists?
        (->client-action {:type "show-site"
                          :label (str (:label args))
                          :lipas-id (:lipas-id args)})
        {:error (str "No sports site with lipas-id " (:lipas-id args)
                     " — only offer this for ids seen in tool results.")}))

    "pan_map_to_location"
    (->client-action {:type "pan-to-location"
                      :label (str (:label args))
                      :location (str (:location args))})

    "navigate_to_view"
    (->client-action {:type "navigate-to-view"
                      :label (str (:label args))
                      :view (str (:view args))})

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
   "You are Lipastaja, the LIPAS assistant embedded in lipas.fi — Finland's national sports facility database. Users are registered maintainers (municipality employees and other data producers).

RULES:
- Answer ONLY from tool results. If the knowledge base has nothing relevant, say so honestly and offer to contact support with escalate_to_support. Never invent UI elements, menu names or type codes.
- search_kb returns TRUNCATED snippets. Before answering a how-to question, fetch the most relevant entries in full with get_kb_document — the snippet alone is not grounds to conclude the guides lack the answer.
- PARTIAL INFORMATION: when tool results cover the topic only partially, give exactly what they contain and link the source for the rest. Do NOT fill gaps from imagination — a numbered step, tab name, icon or button label that is not in a tool result must not appear in your answer.
- NO COVERAGE: when search_kb returns nothing relevant to the question, do not answer from general knowledge — say the guides don't cover it and propose escalate_to_support.
- Always answer in the user's language (see context; default Finnish).
- Cite sources: when your answer builds on a knowledge-base entry, link it in markdown using its deep-link and title, e.g. [Reitin lisääminen](?ohje=tallentajille/reitin-lisaaminen). Links starting with ?ohje= open the in-app help; full URLs open in a new tab.
- Scope: LIPAS usage, sports facility data maintenance, type codes, data model, LIPAS tools. Politely decline anything else.
- Keep answers short and task-focused; numbered steps for how-tos.
- ADAPT guides to the user's situation. The user is always logged in (this assistant requires it) — never include registration or login steps. USER CONTEXT below tells you where they are right now: omit steps they have already completed (e.g. skip \"avaa karttanäkymä\" when the route shows they are on the map; skip \"valitse kohde\" when a site is already selected) and start from the first step that is actually left to do. Adapting means OMITTING or briefly acknowledging completed steps — never adding steps or details that are not in the sources.
- For data-maintenance questions (\"mitkä kohteet kaipaavat päivitystä?\") use the listing tools; present results as a compact markdown list with links.
- PERMISSIONS: the user's roles (official names + scopes) are in USER CONTEXT. For any question about editing rights on a specific site (\"miksi en voi muokata\", \"saanko muokata\") call check_site_permission — its verdict comes from the real permission engine and overrides your own reasoning. Never guess what a role allows: if the KB has no entry about it, say so.
- NAMES FROM DATA ONLY: municipality, facility-type and role names must come from context or tool results — NEVER infer a name from a bare code using world knowledge.
- Never write a support email address into an answer — escalation via the confirmation card handles delivery.
- Help deep-links (?ohje=...) must be copied verbatim from tool results; never construct one yourself.
- WORK-SAVING FEATURES: when your answer requires the user to do repetitive manual work (e.g. creating several similar sites, entering the same data twice), run ONE extra search_kb asking whether a LIPAS feature eases that work, BEFORE writing your answer. Mention such a feature ONLY if a retrieved entry documents it, cite that entry, and base every detail of the tip on it. Found nothing → no tip.
- UI ACTIONS: apply_search, show_site_on_map, pan_map_to_location and navigate_to_view do NOT run anything — each successful call becomes a BUTTON the user may click. Offer one whenever the user asks to find/see sites, to locate a place on the map, or where something is in the app. Write label in the user's language, short and imperative. In your answer refer to the button briefly (\"paina alla olevaa painiketta\") — never claim the search/navigation already happened. The UI renders the button below your message: do not write the button label, any [bracketed pseudo-button], a link imitating a tool call, or a table of actions into the answer text — navigation/search happens ONLY by calling the action tools. At most 2 actions per answer. After your action tool calls return, ALWAYS still write a short normal answer — a button must never arrive with empty answer text.

USER CONTEXT:
"
   (json/encode
    {:name (-> user :user-data :firstname)
     :editing-scope (cond
                      (:all? scope) "all municipalities and types (admin)"
                      (:none? scope) "no editing rights"
                      :else (cond-> {}
                              (seq (:city-codes scope))
                              (assoc :cities (mapv city-label (sort (:city-codes scope))))
                              (seq (:type-codes scope))
                              (assoc :types (mapv type-label (sort (:type-codes scope))))))
     :roles (describe-roles user)
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

(def max-actions-per-answer 3)

(def ^:private action-tool-names
  "Propose-only tools: calling one produces a button, not information.
   A turn that pairs answer text with only these calls is a final answer."
  #{"apply_search" "show_site_on_map" "pan_map_to_location" "navigate_to_view"})

(defn- collect-actions
  "Validated UI actions proposed via the action tools this turn."
  [actions results]
  (into actions (keep :client-action) results))

(defn- sanitize-answer-links
  "Keep only link targets the widget can handle: ?ohje= deep-links the
   model actually retrieved this conversation, and normal web/mail
   links. Everything else — invented ?ohje= slugs, fabricated tool-call
   schemes like (navigate_to_view:profile) — degrades to plain text."
  [answer-md sources]
  (let [valid-ohje? (into #{} (keep :deep-link) sources)]
    (str/replace (str answer-md)
                 #"\[([^\]]*)\]\(([^)]*)\)"
                 (fn [[_ title link]]
                   (cond
                     (str/starts-with? link "?ohje=")
                     (if (valid-ohje? link) (str "[" title "](" link ")") title)

                     (re-matches #"(?i)(https?://|mailto:).*" link)
                     (str "[" title "](" link ")")

                     :else title)))))

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
           sources []
           actions []
           ;; Last non-blank text seen alongside tool calls. The model often
           ;; emits its final answer and an action-tool call in the SAME
           ;; turn; the follow-up turn then comes back empty. This keeps the
           ;; real answer from degrading to a canned fallback.
           pending-text ""]
      (let [resp (gemini-chat system contents mode)
            parts (or (-> resp :candidates first :content :parts) [])
            fcalls (keep :functionCall parts)
            text (->> parts (keep :text) (str/join))
            best-text (if (str/blank? text) pending-text text)]
        (cond
          ;; Escalation proposed: stop, let the user confirm in the UI.
          (some #(= "escalate_to_support" (:name %)) fcalls)
          (let [fc (first (filter #(= "escalate_to_support" (:name %)) fcalls))
                summary (-> fc :args :summary)]
            {:answer-md (if (str/blank? best-text)
                          "Voin lähettää kysymyksesi LIPAS-tuelle. Tarkista ja täydennä alla oleva tiivistelmä ja paina **Lähetä tukipyyntö** — saat vastauksen sähköpostiisi."
                          (sanitize-answer-links best-text sources))
             :sources (vec (distinct sources))
             :actions (->> actions distinct (take max-actions-per-answer) vec)
             ;; A blank draft renders a confusing empty card — fall back to
             ;; the user's own words.
             :escalation {:summary (if (str/blank? summary) message summary)}})

          (and (seq fcalls) (< iter max-tool-iterations))
          (let [results (mapv (fn [fc] (run-tool ctx (:name fc) (:args fc))) fcalls)
                sources' (reduce (fn [acc [fc r]] (collect-sources acc (:name fc) r))
                                 sources
                                 (map vector fcalls results))
                actions' (collect-actions actions results)]
            (if (and (not (str/blank? text))
                     (every? (comp action-tool-names :name) fcalls)
                     (every? :client-action results))
              ;; The model wrote its answer and proposed buttons in one
              ;; turn. Every action validated — asking again only yields an
              ;; empty follow-up, so this text IS the answer.
              {:answer-md (sanitize-answer-links text sources')
               :sources (vec (distinct sources'))
               :actions (->> actions' distinct (take max-actions-per-answer) vec)
               :escalation nil}
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
                     sources'
                     actions'
                     best-text)))

          :else
          (let [actions' (->> actions distinct (take max-actions-per-answer) vec)]
            {:answer-md (cond
                          (not (str/blank? best-text)) (sanitize-answer-links best-text sources)
                          ;; The model sometimes treats a proposed button as
                          ;; the whole answer and returns no text — an apology
                          ;; would contradict the working button below it.
                          (= 1 (count actions'))
                          "Voit jatkaa painamalla alla olevaa painiketta."

                          (seq actions')
                          "Voit jatkaa painamalla alla olevia painikkeita."

                          :else
                          "Pahoittelut — en osannut muodostaa vastausta. Yritä muotoilla kysymys uudelleen.")
             :sources (vec (distinct sources))
             :actions actions'
             :escalation nil}))))))

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
                      :actions (mapv :type (:actions result))
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
          body (str "Lipastajan (LIPAS-avustajan) välittämä tukipyyntö\n"
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
                          :subject (str "[Lipastaja] " (subs summary 0 (min 80 (count summary))))
                          :body body})
      {:status 200 :body {:sent true :conversation-id conversation-id}})))
