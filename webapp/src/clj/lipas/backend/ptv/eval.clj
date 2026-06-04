(ns lipas.backend.ptv.eval
  "REPL-driven evaluation harness for PTV description generation.

   Usage:
     (require '[lipas.backend.ptv.eval :as eval] :reload)

     ;; Discover diverse test IDs from local ES
     (def ids (eval/discover-test-ids (user/search)))

     ;; Run eval on a small batch
     (def results (eval/run-eval (user/search) {:lipas-ids (take 3 ids)}))

     ;; Inspect
     (eval/summary results)
     (eval/pp-result (first results))

     ;; Re-run with a modified prompt
     (def results2 (eval/run-eval (user/search)
                     {:lipas-ids      (take 3 ids)
                      :system-prompt  my-improved-prompt}))"
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [lipas.backend.ptv.ai :as ai]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-int]
            [lipas.backend.search :as search]
            [lipas.data.types :as types]
            [malli.json-schema :as json-schema]
            [malli.util :as mu]
            [taoensso.timbre :as log]))

;;; ——— Test data ————————————————————————————————————————————————————

(def test-lipas-ids
  "Fixed set of LIPAS IDs for reproducible evals.
   Populate from your local DB using (discover-test-ids search)."
  [])

(defn discover-test-ids
  "Query ES for diverse LIPAS IDs across different facility types.
   Returns a vector of lipas-ids covering `per-type` sites from
   up to `max-types` different type codes."
  [search-component & {:keys [per-type max-types]
                       :or   {per-type 1 max-types 20}}]
  (let [idx     (get-in search-component [:indices :sports-site :search])
        resp    (search/search
                  (:client search-component) idx
                  {:size 0
                   :aggs {:types {:terms {:field "type.type-code" :size max-types}
                                  :aggs  {:sample {:top_hits {:size    per-type
                                                              :_source ["lipas-id"]}}}}}})
        buckets (-> resp :body :aggregations :types :buckets)]
    (->> buckets
         (mapcat (fn [bucket]
                   (->> bucket :sample :hits :hits
                        (map #(-> % :_source :lipas-id)))))
         vec)))

;;; ——— Gemini helper ————————————————————————————————————————————————

(defn- gemini-call
  "Low-level Gemini API call with custom response schema.
   Returns {:content <parsed-json> :usage {...} :elapsed-ms <int> :model <str>}."
  [{:keys [model thinking-level top-p temperature max-tokens response-schema]
    :or   {model          (:model ai/gemini-default-params)
           thinking-level (:thinking-level ai/gemini-default-params)
           top-p          (:top-p ai/gemini-default-params)
           temperature    (:temperature ai/gemini-default-params)
           max-tokens     (:max-tokens ai/gemini-default-params)}}
   system-prompt
   user-prompt]
  (let [{:keys [base-url api-key]} ai/gemini-config
        url    (str base-url "/models/" model ":generateContent")
        body   {:systemInstruction {:parts [{:text system-prompt}]}
                :contents          [{:role "user" :parts [{:text user-prompt}]}]
                :generationConfig  (cond-> {:topP             top-p
                                            :temperature      temperature
                                            :maxOutputTokens  max-tokens
                                            :responseMimeType "application/json"
                                            :thinkingConfig   {:thinkingLevel thinking-level}}
                                     response-schema
                                     (assoc :responseSchema response-schema))}
        start  (System/currentTimeMillis)
        resp   (-> (client/post url {:headers      {"x-goog-api-key" api-key
                                                    "Content-Type"   "application/json"}
                                     :body         (json/encode body)
                                     :content-type :json})
                   :body
                   (json/decode keyword))
        elapsed (- (System/currentTimeMillis) start)
        usage   (:usageMetadata resp)
        text    (-> resp :candidates first :content :parts first :text)
        content (try
                  (json/decode text keyword)
                  (catch Exception e
                    (log/warnf "Failed to parse Gemini response: %s" (.getMessage e))
                    text))]
    {:content    content
     :usage      {:prompt_tokens     (:promptTokenCount usage 0)
                  :completion_tokens (:candidatesTokenCount usage 0)
                  :total_tokens      (:totalTokenCount usage 0)}
     :elapsed-ms elapsed
     :model      model}))

;;; ——— Prompt versions —————————————————————————————————————————————
;;
;; v3 = current production prompts (ai/ptv-system-instruction-v3 + ai/generate-utp-descriptions-prompt)
;; v4 = improved based on DVV auditor feedback + baseline eval findings
;; v5 = v4 + DVV audit instructions + few-shot corrections + unit formatting

(def system-prompt-v4
  "You are an assistant that creates strictly factual, administrative content for the Finnish Service Information Repository (Palvelutietovaranto / PTV) in Finnish, Swedish, and English.

CRITICAL RULES — violation of any of these results in automatic rejection:

1. CHECK THE STATUS FIELD FIRST
   - If status is \"out-of-service-permanently\": the description MUST clearly state that
     the facility is permanently out of service. Do NOT describe it as if it were active
     or maintained. Do not mention maintenance schedules or seasonal availability.
   - If status is \"out-of-service-temporarily\": mention that the facility is temporarily
     out of service.
   - Only describe the facility as active and usable if status is \"active\".

2. NO STREET ADDRESSES
   PTV has a separate address field. Never include street addresses, postal codes, or
   precise location identifiers (e.g. \"osoitteessa Tornitie 4\") in summaries or descriptions.
   Use only the municipality name and facility/area name for location.

3. ONLY VERIFIABLE FACTS FROM SOURCE DATA
   Write ONLY information that appears in the provided data. If the source does not
   mention changing rooms, toilets, season of use, or other details, do NOT add them.
   When uncertain, omit rather than invent. Never write meta-commentary about missing
   data (e.g. \"there is no information about winter availability\") — simply leave
   it out.

TONE AND STYLE:
- Neutral, administrative, unemotional, service-focused
- Present tense
- \"You\" form sparingly; prefer passive constructions
- No exclamation marks

PROHIBITED EXPRESSIONS (in any language):
× tervetuloa, välkommen, welcome
× nauttia, njuta, enjoy
× Marketing adjectives: upea, mahtava, erinomainen, wonderful, great, excellent
× Emotional appeals, inviting phrases, promotional language

FINNISH LANGUAGE REQUIREMENTS:
- Correct place name inflection: Raahe → Raahessa (not Raaheessa), Ii → Iissä
- Avoid overusing \"käytettävissä\" — vary with: \"voi käyttää\", \"on avoinna\",
  \"on tarkoitettu\", \"palvelee\"
- Prefer passive voice: \"latua ylläpidetään\" not \"ylläpito tapahtuu\"
- Compound place names: use en-dash without surrounding spaces
  (Ketunperäntie–Haapajoki, not Ketunperäntie - Haapajoki)
- Use common terms: \"tulentekopaikka\" or \"nuotiopaikka\" (not \"tulistelupaikka\")
- Include \"ja\" or \"sekä\" before the last item in lists

INFORMATION ORDER:
1. What: Service type and location (municipality name, not address)
2. Access: Who can use it — be explicit about school-time vs. public access
3. Facilities: Equipment, surfaces, dimensions (only from source data)
4. Conditions: Seasonal availability, weather dependencies (only if in source data)

FORMAT:
- Divide description into 2–4 paragraphs
- Maximum 4 sentences per paragraph
- One topic per paragraph
- Most important information first

Each language version may differ in phrasing but must convey the same factual content.
Different language versions must be grammatically correct and natural in that language.")

(def user-prompt-v4
  "Based on the JSON data below, produce a summary and description for the Service Information Repository (Palvelutietovaranto).

BEFORE WRITING, verify:
1. What is the \"status\" field? If not \"active\", the text must reflect that the facility is out of service.
2. What facts are explicitly present? Write ONLY those facts — do not infer or add details.
3. Is there a street address in the data? Do NOT include it in your output.

Summary: 1–2 sentences capturing the essential service information. Maximum 150 characters per language.

Description: 2–4 paragraphs following the information order (what → access → facilities → conditions). Maximum 2000 characters per language.

Source data:
%s")

(def system-prompt-v5
  "You are an assistant that creates strictly factual, administrative content for the Finnish Service Information Repository (Palvelutietovaranto / PTV) in Finnish, Swedish, and English.

You produce two texts per language:
- Summary (tiivistelmä): max 150 characters
- Description (kuvaus): max 2000 characters

CRITICAL RULES — violation causes automatic rejection:

1. CHECK THE STATUS FIELD FIRST
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

TONE AND STYLE:
- Neutral, administrative, unemotional, service-focused
- Present tense
- Prefer passive constructions; use \"you\" form sparingly
- No exclamation marks

PROHIBITED EXPRESSIONS (in any language):
× tervetuloa, välkommen, welcome
× nauttia, njuta, enjoy
× monipuolinen, mångsidig, versatile (evaluative)
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

INFORMATION ORDER:
1. What: Service type and location (municipality, not address)
2. Access: Who can use it, cost, how to access — be explicit about
   school-time vs. public access
3. Facilities: Equipment, surfaces, dimensions (only from source data)
4. Conditions: Seasonal availability, weather dependencies (only if in source data)

FORMAT:
- Divide description into 2–4 paragraphs
- Maximum 4 sentences per paragraph
- One topic per paragraph
- Most important information first

Each language version may differ in phrasing but must convey the same factual content.
All language versions must be grammatically correct and natural.")

(def user-prompt-v5
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
- \"Oijärven vesillelaskuluiska on ympäri vuoden käytössä oleva vesillelaskuluiska Iissä.\"

Source data:
%s")

;;; ——— Grading v2 (aligned with DVV audit instructions) ———————————

(def grading-system-prompt-v2
  "You are a strict quality evaluator for the Finnish Service Information Repository (Palvelutietovaranto / PTV). Grade AI-generated service descriptions against the official DVV content guidelines.

Grade each criterion on a 1-5 scale:
  1 = Completely fails
  2 = Major issues
  3 = Acceptable with notable issues
  4 = Good with minor issues
  5 = Excellent

CRITERIA:

1. TONE
   Must be neutral, administrative, unemotional, service-focused.
   No marketing language, promotional expressions, or evaluative adjectives
   (\"monipuolinen\", \"versatile\", \"mångsidig\" count as evaluative).

2. PROHIBITED ELEMENTS
   Must NOT contain: tervetuloa/välkommen/welcome, nauttia/njuta/enjoy,
   exclamation marks, marketing adjectives, emotional appeals.
   Must NOT contain street addresses, phone numbers, or URLs (PTV has separate fields).
   Must NOT repeat the organization/municipality name as subject.

3. STRUCTURE
   Information must follow: (1) What and where, (2) Access and cost,
   (3) Facilities/equipment, (4) Conditions/seasonal info.
   Must include a usage instruction (how to access the service).

4. LANGUAGE QUALITY
   Grammatically correct, readable, clear in ALL three languages (fi, se, en).
   Finnish-specific: correct place name inflection (Raahessa not Raaheessa),
   varied vocabulary (not overusing \"käytettävissä\"), formal terms (\"salibandy\"
   not \"säbä\"), reader-friendly units (\"290 metriä\" not \"0,29 km\").
   Swedish-specific: correct gender agreement (ett-ord vs en-ord).

5. CHARACTER LIMITS
   Summary ≤ 150 characters per language. Description ≤ 2000 characters per language.

6. CONTENT ACCURACY
   Must be factual based on source data. No invented details. No meta-commentary
   about missing data. Must correctly reflect the facility status field (active,
   out-of-service-permanently, etc.). Must not describe an out-of-service facility
   as active. Must not highlight mundane features as services (e.g. \"tarjoaa
   synteettisen pinnan\").

7. SUMMARY FORMAT
   Summary MUST be a complete sentence (virke), NOT a comma-separated list.
   Summary must not be a copy of the facility name.
   Summary must not contain information absent from the description.
   BAD: \"Jääkiekkokaukalo Haapajoella, avoin kaikille, valaistus ja pukuhuonetilat.\"
   GOOD: \"Haapajoen jääkiekkokaukalo on valaistu ulkokaukalo, joka on avoinna kaikille.\"

REFERENCE — DVV-approved corrections (generated → corrected):
- \"Ikosen koulun kaukalo Pyhäjärvellä, ilmaiseksi käytettävissä kaikille.\"
  → \"Ikosen koulun kaukalo Pyhäjärvellä on ilmaiseksi kaikkien käytettävissä.\"
- \"Lähiliikuntapaikka Iissä, kaksi kenttää, keinonurmi ja sorapinta, maksuton käyttö.\"
  → \"Aseman ala-asteen lähiliikuntapaikka tarjoaa ympäri vuoden maksutta kaksi urheilukenttää.\"
- \"Vesillelaskuluiska Iissä, kaupungin omistama veneilyn palvelupaikka, avoinna ympäri vuoden.\"
  → \"Oijärven vesillelaskuluiska on ympäri vuoden käytössä oleva vesillelaskuluiska Iissä.\"

Be strict. Cite exact problematic phrases. The goal is to identify areas for prompt improvement.")

(def grading-user-prompt-template-v2
  "Source data (facility information used to generate the descriptions):
%s

Generated output to evaluate:
%s

Grade this output against all DVV criteria. Check especially:
- Is the summary a complete sentence or a comma-separated list?
- Are there any street addresses, phone numbers, or URLs?
- Does the text correctly reflect the facility's status field?
- Is the Finnish grammatically correct with proper place name inflection?
- Are there any invented details not in the source data?
Be specific — cite exact problematic phrases.")

;;; ——— Generation ———————————————————————————————————————————————————

(def generation-schema
  "Gemini-compatible JSON Schema for the generation response."
  (json-schema/transform (mu/open-schema ai/response-schema)))

(def default-generator-config
  {:model          (:model ai/gemini-default-params)
   :thinking-level (:thinking-level ai/gemini-default-params)
   :response-schema generation-schema})

(defn- fetch-prompt-doc
  "Fetch a sports site from ES and prepare it as a prompt document."
  [search-component lipas-id]
  (let [idx (get-in search-component [:indices :sports-site :search])
        doc (-> (search/fetch-document (:client search-component) idx lipas-id)
                :body
                :_source)]
    {:source-doc doc
     :prompt-doc (ai/->prompt-doc doc)}))

;;; ——— Grading ——————————————————————————————————————————————————————

(def criterion-schema
  [:map
   [:grade :int]
   [:feedback :string]])

(def grading-response-schema
  [:map
   [:overall-grade :int]
   [:overall-reasoning :string]
   [:tone criterion-schema]
   [:prohibited-elements criterion-schema]
   [:structure criterion-schema]
   [:language-quality criterion-schema]
   [:character-limits criterion-schema]
   [:content-accuracy criterion-schema]
   [:format criterion-schema]])

(def GradingResponse
  "Gemini-compatible JSON Schema for the grading response."
  (json-schema/transform (mu/open-schema grading-response-schema)))

(def default-grader-config
  {:model          (:model ai/gemini-default-params)
   :thinking-level "high"
   :temperature    0.5
   :top-p          0.90
   :response-schema GradingResponse})

(def grading-system-prompt
  "You are a strict quality evaluator for the Finnish Service Information Repository (Palvelutietovaranto / PTV). Your task is to grade AI-generated service descriptions against DVV (Digital and Population Data Services Agency) content guidelines.

Grade each criterion on a 1-5 scale:
  1 = Completely fails the criterion
  2 = Major issues
  3 = Acceptable with notable issues
  4 = Good with minor issues
  5 = Excellent, fully meets the criterion

CRITERIA:

1. TONE
   Must be neutral, administrative, unemotional, service-focused.
   No marketing language or promotional expressions.

2. PROHIBITED ELEMENTS
   Must NOT contain:
   - Welcome/greeting phrases: tervetuloa, välkommen, welcome
   - Enjoyment words: nauttia, njuta, enjoy
   - Exclamation marks (!)
   - Marketing adjectives: wonderful, great, excellent, upea, mahtava
   - Emotional appeals or inviting phrases

3. STRUCTURE
   Information must follow this order:
   (1) What: service type and location
   (2) Access: who can use it and how
   (3) Conditions: any limitations or requirements
   (4) Practical details: operating hours, seasonal info

4. LANGUAGE QUALITY
   Must be grammatically correct, readable, and clear in ALL three languages (fi, se, en).
   Simple words, complete sentences. No convoluted participial constructions.
   Each language version can differ in phrasing but must convey the same information.

5. CHARACTER LIMITS
   Summary: maximum 150 characters per language.
   Description: maximum 2000 characters per language.

6. CONTENT ACCURACY
   Must be factual and based on the provided source data.
   No invented information. No street addresses in descriptions.
   Must not omit significant facts from the source data.

7. FORMAT
   Text divided into paragraphs. Maximum 4 sentences per paragraph.
   One topic per paragraph. Most important information first.

Be strict. Cite exact problematic phrases in your feedback. The goal is to identify specific areas for prompt improvement.")

(def grading-user-prompt-template
  "Source data (facility information used to generate the descriptions):
%s

Generated output to evaluate:
%s

Grade this output against all DVV criteria. Be specific — cite exact problematic phrases.")

;;; ——— Grading v2 schemas ——————————————————————————————————————————

(def criteria-keys-v2
  [:tone :prohibited-elements :structure :language-quality
   :character-limits :content-accuracy :summary-format])

(def grading-response-schema-v2
  [:map
   [:overall-grade :int]
   [:overall-reasoning :string]
   [:tone criterion-schema]
   [:prohibited-elements criterion-schema]
   [:structure criterion-schema]
   [:language-quality criterion-schema]
   [:character-limits criterion-schema]
   [:content-accuracy criterion-schema]
   [:summary-format criterion-schema]])

(def GradingResponseV2
  (json-schema/transform (mu/open-schema grading-response-schema-v2)))

(def grader-config-v2
  {:model          (:model ai/gemini-default-params)
   :thinking-level "high"
   :temperature    0.5
   :top-p          0.90
   :response-schema GradingResponseV2})

;;; ——— Eval runner ——————————————————————————————————————————————————

(def criteria-keys
  [:tone :prohibited-elements :structure :language-quality
   :character-limits :content-accuracy :format])

(defn run-eval
  "Run evaluation on a batch of LIPAS sites.

   Options:
     :lipas-ids            - vector of LIPAS IDs (default: test-lipas-ids)
     :batch-size           - how many to evaluate (default: all)
     :system-prompt        - override generation system prompt
     :user-prompt-template - override generation user prompt template
     :generator-config     - override generator model config
     :grader-config              - override grader model config
     :grader-system-prompt       - override grading system prompt
     :grader-user-prompt-template - override grading user prompt template

   Returns a vector of result maps."
  [search-component & {:keys [lipas-ids batch-size system-prompt user-prompt-template
                              generator-config grader-config
                              grader-system-prompt grader-user-prompt-template]
                       :or   {system-prompt               ai/ptv-system-instruction-v3
                              user-prompt-template        ai/generate-utp-descriptions-prompt
                              generator-config            default-generator-config
                              grader-config               default-grader-config
                              grader-system-prompt        grading-system-prompt
                              grader-user-prompt-template grading-user-prompt-template}}]
  (let [ids (cond-> (or lipas-ids test-lipas-ids)
              batch-size (#(vec (take batch-size %))))]
    (println (format "Running eval on %d sites..." (count ids)))
    (vec
      (for [[i lipas-id] (map-indexed vector ids)]
        (do
          (printf "[%d/%d] LIPAS %d — " (inc i) (count ids) lipas-id)
          (flush)
          (try
            (let [{:keys [prompt-doc]} (fetch-prompt-doc search-component lipas-id)
                  _ (print "generating... ")
                  _ (flush)
                  gen-result (gemini-call
                               generator-config
                               system-prompt
                               (format user-prompt-template (json/encode prompt-doc)))
                  _ (printf "%.1fs, " (/ (:elapsed-ms gen-result) 1000.0))
                  _ (print "grading... ")
                  _ (flush)
                  grade-result (gemini-call
                                 grader-config
                                 grader-system-prompt
                                 (format grader-user-prompt-template
                                         (json/encode prompt-doc)
                                         (json/encode (:content gen-result))))
                  overall (-> grade-result :content :overall-grade)]
              (printf "%.1fs — grade: %d/5\n" (/ (:elapsed-ms grade-result) 1000.0) (or overall 0))
              (flush)
              {:lipas-id    lipas-id
               :prompt-doc  prompt-doc
               :generation  gen-result
               :grading     grade-result})
            (catch Exception e
              (println (str "ERROR: " (.getMessage e)))
              {:lipas-id lipas-id
               :error    (.getMessage e)})))))))

;;; ——— Reporting ————————————————————————————————————————————————————

(defn summary
  "Print a summary of eval results. Pass :criteria-keys to use a different set."
  [results & {:keys [criteria] :or {criteria criteria-keys}}]
  (let [ok     (remove :error results)
        errors (filter :error results)
        grades (map #(-> % :grading :content) ok)
        avg-of (fn [k]
                 (let [vals (keep #(get-in % [k :grade]) grades)]
                   (when (seq vals)
                     (/ (reduce + vals) (double (count vals))))))]
    (println "\n=== Eval Summary ===")
    (printf "Sites: %d evaluated, %d errors\n" (count ok) (count errors))
    (println)
    (when (seq ok)
      (let [overall-vals (keep :overall-grade grades)]
        (when (seq overall-vals)
          (printf "Overall:                 %.1f / 5\n"
                  (/ (reduce + overall-vals) (double (count overall-vals))))))
      (doseq [k criteria]
        (when-let [a (avg-of k)]
          (printf "  %-23s %.1f / 5\n" (str (name k) ":") a)))
      (println))
    (when (seq errors)
      (println "Errors:")
      (doseq [e errors]
        (printf "  LIPAS %d: %s\n" (:lipas-id e) (:error e))))
    (println)))

(defn pp-result
  "Pretty-print a single eval result."
  [result]
  (if (:error result)
    (printf "LIPAS %d — ERROR: %s\n" (:lipas-id result) (:error result))
    (let [gen   (:content (:generation result))
          grade (:content (:grading result))]
      (println (str "\n=== LIPAS " (:lipas-id result) " ==="))
      (println "\n--- Generated Output ---")
      (println "Summary (FI):" (get-in gen [:summary :fi]))
      (println "Summary (SE):" (get-in gen [:summary :se]))
      (println "Summary (EN):" (get-in gen [:summary :en]))
      (println "\nDescription (FI):" (get-in gen [:description :fi]))
      (println "\nDescription (SE):" (get-in gen [:description :se]))
      (println "\nDescription (EN):" (get-in gen [:description :en]))
      (println "\n--- Grading ---")
      (printf "Overall: %d/5\n" (or (:overall-grade grade) 0))
      (println "Reasoning:" (:overall-reasoning grade))
      (doseq [k criteria-keys]
        (when-let [c (get grade k)]
          (printf "\n  %s: %d/5\n" (name k) (:grade c))
          (printf "  %s\n" (:feedback c)))))))

;;; ——— Service description eval ———————————————————————————————————

(def service-test-cases
  "Fixed set of {city-code, sub-category-id} pairs for reproducible service evals."
  [{:city-code 91   :sub-category-id 1100 :label "Lähiliikuntapaikat / Helsinki"}
   {:city-code 564  :sub-category-id 4400 :label "Ladut / Oulu"}
   {:city-code 92   :sub-category-id 3200 :label "Uimapaikat / Vantaa"}
   {:city-code 837  :sub-category-id 1500 :label "Jääurheilualueet / Tampere"}
   {:city-code 179  :sub-category-id 2100 :label "Sisäliikuntasalit / Jyväskylä"}
   {:city-code 49   :sub-category-id 1300 :label "Pallokentät / Espoo"}
   {:city-code 698  :sub-category-id 4400 :label "Ladut / Rovaniemi"}
   {:city-code 405  :sub-category-id 3200 :label "Uimapaikat / Lappeenranta"}])

(defn- build-service-prompt-doc
  "Build a service aggregate overview doc for a test case."
  [search-component {:keys [city-code sub-category-id]}]
  (let [type-codes (->> (types/by-sub-category sub-category-id) (map :type-code))
        sites      (ptv-int/get-eligible-sites search-component
                                               {:type-codes type-codes
                                                :city-codes [city-code]
                                                :owners     ["city" "city-main-owner"]})]
    (when (seq sites)
      (ptv-core/make-aggregate-overview sites
                                        {:free-use?          true
                                         :surface-materials? true
                                         :lighting?          true}))))

(defn run-service-eval
  "Run evaluation on service descriptions.

   Options:
     :test-cases              - vector of {:city-code :sub-category-id} maps
     :batch-size              - how many to evaluate
     :system-prompt           - override generation system prompt
     :user-prompt-template    - override generation user prompt template
     :generator-config        - override generator model config
     :grader-config           - override grader model config
     :grader-system-prompt    - override grading system prompt
     :grader-user-prompt-template - override grading user prompt template

   Returns a vector of result maps."
  [search-component & {:keys [test-cases batch-size system-prompt user-prompt-template
                              generator-config grader-config
                              grader-system-prompt grader-user-prompt-template]
                       :or   {test-cases              service-test-cases
                              system-prompt           ai/ptv-system-instruction-v3
                              user-prompt-template    ai/generate-utp-service-descriptions-prompt
                              generator-config        default-generator-config
                              grader-config           default-grader-config
                              grader-system-prompt    grading-system-prompt
                              grader-user-prompt-template grading-user-prompt-template}}]
  (let [cases (cond-> test-cases
                batch-size (#(vec (take batch-size %))))]
    (println (format "Running service eval on %d cases..." (count cases)))
    (vec
      (for [[i tc] (map-indexed vector cases)]
        (let [label (or (:label tc) (str (:sub-category-id tc) "/" (:city-code tc)))]
          (printf "[%d/%d] %s — " (inc i) (count cases) label)
          (flush)
          (try
            (let [prompt-doc (build-service-prompt-doc search-component tc)]
              (if-not prompt-doc
                (do (println "SKIP: no eligible sites")
                    {:test-case tc :error "no eligible sites"})
                (let [_ (print "generating... ")
                      _ (flush)
                      gen-result (gemini-call
                                   generator-config
                                   system-prompt
                                   (format user-prompt-template (json/encode prompt-doc)))
                      _ (printf "%.1fs, " (/ (:elapsed-ms gen-result) 1000.0))
                      _ (print "grading... ")
                      _ (flush)
                      grade-result (gemini-call
                                     grader-config
                                     grader-system-prompt
                                     (format grader-user-prompt-template
                                             (json/encode prompt-doc)
                                             (json/encode (:content gen-result))))
                      overall (-> grade-result :content :overall-grade)]
                  (printf "%.1fs — grade: %d/5\n" (/ (:elapsed-ms grade-result) 1000.0) (or overall 0))
                  (flush)
                  {:test-case   tc
                   :prompt-doc  prompt-doc
                   :generation  gen-result
                   :grading     grade-result})))
            (catch Exception e
              (println (str "ERROR: " (.getMessage e)))
              {:test-case tc :error (.getMessage e)})))))))

(defn pp-service-result
  "Pretty-print a single service eval result."
  [result]
  (if (:error result)
    (printf "%s — ERROR: %s\n" (or (-> result :test-case :label) "?") (:error result))
    (let [gen   (:content (:generation result))
          grade (:content (:grading result))
          label (-> result :test-case :label)]
      (println (str "\n=== " label " ==="))
      (println "\n--- Generated Output ---")
      (println "Summary (FI):" (get-in gen [:summary :fi]))
      (println "Summary (SE):" (get-in gen [:summary :se]))
      (println "Summary (EN):" (get-in gen [:summary :en]))
      (println "\nDescription (FI):" (get-in gen [:description :fi]))
      (println "\n--- Grading ---")
      (printf "Overall: %d/5\n" (or (:overall-grade grade) 0))
      (println "Reasoning:" (:overall-reasoning grade))
      (doseq [k criteria-keys-v2]
        (when-let [c (get grade k)]
          (when (< (:grade c) 5)
            (printf "  %s: %d/5 — %s\n" (name k) (:grade c) (:feedback c))))))))

(comment
  ;; Quick start
  (require '[lipas.backend.ptv.eval :as eval] :reload)
  (def ids (eval/discover-test-ids (user/search)))
  (def results (eval/run-eval (user/search) {:lipas-ids (take 3 ids)}))
  (eval/summary results)
  (eval/pp-result (first results))

  ;; With custom prompt
  (def results2
    (eval/run-eval (user/search)
                   {:lipas-ids      (take 3 ids)
                    :system-prompt  "Your improved prompt here..."}))
  (eval/summary results2))
