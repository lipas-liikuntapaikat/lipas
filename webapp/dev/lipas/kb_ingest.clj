(ns lipas.kb-ingest
  "One-off ingestion of jyu.fi PDF guides and Lipasinfo YouTube
   transcripts into the knowledge base. REPL-driven; results are written
   to versioned_data (type \"kb-ingest\") so lipas.backend.kb/sync! can
   rebuild the index deterministically at any time.

   Pipeline per source: extract (LLM: source text → task-shaped entries)
   → grounding (separate LLM call verifies every claim against source)
   → mechanical checks (type codes exist) → doc assembly. Everything
   enters as review-status \"machine\"; a human pass flips to
   \"reviewed\".

   Usage:
     (require '[lipas.kb-ingest :as ingest])
     (def result (ingest/ingest-all! \"/path/to/kb-ingest\"))
     (ingest/report result)
     (ingest/save-ingested! (user/db) (user/search) (:docs result))"
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [lipas.backend.db.db :as db]
            [lipas.backend.kb :as kb]
            [lipas.backend.llm :as llm]
            [lipas.data.types :as types]
            [malli.json-schema :as json-schema]
            [malli.util :as mu]
            [taoensso.timbre :as log]))

;;; ——— Corpus ————————————————————————————————————————————————————————

(def pdf-sources
  "jyu.fi guide PDFs; :file under <dir>/txt/, extracted with pdftotext."
  [{:slug "lipas-liikuntapaikan-lisaaminen-ja-muokkaaminen-2023" :lang "fi"
    :doc "Pistemäisen liikuntapaikan lisääminen ja muokkaaminen"}
   {:slug "lipas-reitin-lisaaminen-2023" :lang "fi"
    :doc "Reittimäisen liikuntapaikan tallennus ja muokkaus"}
   {:slug "lipas-saavutettavuustyokalu-2023" :lang "fi"
    :doc "Saavutettavuustyökalun käyttö"}
   {:slug "lipas-monipuolisuustyokalu-2023" :lang "fi"
    :doc "Monipuolisuustyökalun käyttö"}
   {:slug "lipas-utp-sisaltoohje-2026" :lang "fi"
    :doc "Luontoon.fi-sisällönsyötön yleisohje"}
   {:slug "lipas-utp-kalastusohje" :lang "fi"
    :doc "Kalastuskohteiden sisällönsyöttö Luontoon.fi-palveluun"}
   {:slug "lipas-tilaohje-2023" :lang "fi"
    :doc "Liikuntapaikan tila/status-tiedon merkitys"}
   {:slug "lipas-avustukset-2023" :lang "fi"
    :doc "Liikuntapaikkarakentamisen avustustiedot"}
   {:slug "lipas-taloustiedot-2023" :lang "fi"
    :doc "Kuntien liikunta- ja nuorisotoimen taloustiedot"}
   ;; lipas-utp-pyorailyohjeistus.pdf has a corrupt xref table -
   ;; pdftotext can't read it. Re-add when a clean copy exists.
   ])

(defn pdf-url [slug]
  (str "https://www.jyu.fi/fi/file/" slug))

(def video-sources
  "Lipasinfo channel (@lipasinfo7382), full inventory 2026-07;
   corpus boundary confirmed by the team. Subtitles under <dir>/subs/."
  [{:id "gMK-uOm6a7k" :lang "fi" :title "LIPAS-palvelun esittely"}
   {:id "cGHg7C5FSos" :lang "fi" :title "LIPAS intro"}
   {:id "cDaGW3EOrsI" :lang "fi" :title "Pistemäisen liikuntapaikan lisääminen"}
   {:id "XcZrIepjYe0" :lang "fi" :title "Reittimäisen liikuntapaikan lisääminen"}
   {:id "DUa_aMbg9k0" :lang "fi" :title "Reittigeometrian tuonti tiedostosta"}
   {:id "M3tmvCVyoD4" :lang "fi" :title "Reitin katkaisu ja reittijäljen yksinkertaistaminen"}
   {:id "dbBfReS_RFs" :lang "fi" :title "Aluemaisen kohteen lisääminen ja muokkaaminen"}
   {:id "CALwXxFKAAA" :lang "fi" :title "Alueen tallennus"}
   {:id "6MOxSpd1fUg" :lang "fi" :title "Raporttityökalun käyttö"}
   {:id "X5qZHzOc5JY" :lang "fi" :title "Lipas.fi/tilastot"}
   {:id "uz9BKFg2bl8" :lang "fi" :title "Aluehallintoviraston Lipas-käyttö"}
   {:id "UiKjGeS4Yyg" :lang "fi" :title "Monipuolisuustyökalun käyttöönotto"}
   {:id "MovCvE0yuKU" :lang "fi" :title "Liikuntapaikkojen monipuolisuusvertailut"}
   {:id "FADGfqvjyY4" :lang "fi" :title "Kestävää liikuntaa: saavutettavuus ja monipuolisuus (webinaari)"}
   {:id "rFuvGwqjASI" :lang "fi" :title "Luontoon.fi-tiedonsyöttö: muut kohteet"}
   {:id "LBnm9mES8GY" :lang "fi" :title "Luontoon.fi-tiedonsyöttö: kalastus"}
   {:id "TxkbYn91WW8" :lang "fi" :title "Luontoon.fi-tiedonsyöttö: pyöräily"}
   {:id "UMCrs_T8OLg" :lang "fi" :title "Luontoon.fi-tiedonsyöttö: melonta"}
   {:id "Ge1pxWPs3_s" :lang "fi" :title "Luontoon.fi-tiedonsyöttö: retkeily ja ulkoilu"}
   {:id "MASE23FjsiI" :lang "en" :title "Analysing accessibility of sport facilities"}
   {:id "XoGxJfolAPY" :lang "en" :title "Starting to use LIPAS accessibility tool"}])

(defn video-url
  ([id] (str "https://www.youtube.com/watch?v=" id))
  ([id t] (str (video-url id) "&t=" t)))

;;; ——— VTT → timestamped transcript —————————————————————————————————

(defn- vtt-ts->seconds [ts]
  (let [[h m s] (map #(Double/parseDouble %) (str/split ts #":"))]
    (long (+ (* 3600 h) (* 60 m) s))))

(defn vtt->transcript
  "YouTube auto-caption VTT → plain text with [s=N] markers roughly every
   30 seconds so the extract pass can attach start times to entries.
   Strips inline word-timing tags and the rolling-window duplicates."
  [vtt-str]
  (let [cue-re #"(?m)^(\d{2}:\d{2}:\d{2})\.\d{3} --> .*$"
        lines (str/split-lines vtt-str)]
    (loop [lines lines, cur-ts 0, last-mark -100, seen-prev "", out []]
      (if-let [line (first lines)]
        (if-let [[_ ts] (re-find cue-re line)]
          (recur (rest lines) (vtt-ts->seconds ts) last-mark seen-prev out)
          (let [text (-> line
                         (str/replace #"<[^>]*>" "")
                         str/trim)]
            (if (or (str/blank? text)
                    (str/starts-with? text "WEBVTT")
                    (str/starts-with? text "Kind:")
                    (str/starts-with? text "Language:")
                    (= text seen-prev))
              (recur (rest lines) cur-ts last-mark seen-prev out)
              (let [mark? (>= (- cur-ts last-mark) 30)
                    out (conj out (if mark? (str "[s=" cur-ts "] " text) text))]
                (recur (rest lines) cur-ts (if mark? cur-ts last-mark) text out)))))
        (str/join "\n" out)))))

(defn gemini-video-transcript
  "Transcribe a public YouTube video via Gemini video input — the fallback
   for videos without auto-captions. Returns plain text with [s=N] topic
   markers, matching vtt->transcript's output shape."
  [url lang]
  (let [{:keys [base-url api-key]} llm/gemini-config
        model (:model llm/gemini-default-params)
        body  {:contents
               [{:role "user"
                 :parts [{:fileData {:fileUri url}}
                         {:text (str "Transcribe this video's narration in its original language (" lang "). "
                                     "Clean up filler words. Insert a [s=N] marker (N = seconds from video start) "
                                     "at each topic change and roughly every 30 seconds. When the narration alone "
                                     "is unclear, describe the on-screen UI action concisely in [UI: ...] brackets. "
                                     "Output only the transcript.")}]}]
               :generationConfig {:maxOutputTokens 65536
                                  :temperature 0.2}}
        resp  (-> (http/post (str base-url "/models/" model ":generateContent")
                             {:headers            {"x-goog-api-key" api-key
                                                   "Content-Type"   "application/json"}
                              :body               (json/encode body)
                              :content-type       :json
                              :socket-timeout     600000
                              :connection-timeout 10000})
                  :body
                  (json/decode keyword))]
    (-> resp :candidates first :content :parts first :text)))

;;; ——— LLM passes ———————————————————————————————————————————————————

(def ^:private extract-schema
  (json-schema/transform
   (mu/open-schema
    [:map {:closed true}
     [:entries
      [:vector
       [:map {:closed true}
        [:title :string]
        [:body :string]
        [:start-seconds :int]]]]])))

(def ^:private extract-system-prompt
  "You convert LIPAS user-guide source material (PDF text or video transcript) into knowledge-base entries for an AI help assistant.

LIPAS (lipas.fi) is Finland's national sports facility GIS. Users are municipality employees who maintain facility data.

Produce one entry per distinct user task or question the source answers. Rules:

- Each entry must be SELF-CONTAINED: understandable without the other entries or the source. No \"kuten edellä\", no unresolved references.
- title: the task or question phrased the way a user would ask it (e.g. \"Reitin tuonti GPX-tiedostosta\").
- body: 100-400 words of markdown. Concrete steps as numbered lists. Prerequisites (login, edit rights) stated when the source states them.
- Write in the SAME LANGUAGE as the source.
- ONLY facts from the source. Do not invent UI elements, menu names or type codes not present in the source. Auto-generated transcripts contain recognition errors — fix obvious ones from context, and OMIT anything you cannot confidently reconstruct.
- Skip marketing, greetings, presenter introductions and anything that is not actionable guidance. A short intro video may yield just one overview entry; a dense guide may yield many.
- start-seconds: for video transcripts, the [s=N] marker closest to where the entry's topic starts; 0 for PDFs or if unsure.")

(def ^:private ground-schema
  (json-schema/transform
   (mu/open-schema
    [:map {:closed true}
     [:verdicts
      [:vector
       [:map {:closed true}
        [:title :string]
        [:grounded :boolean]
        [:issues :string]]]]])))

(def ^:private ground-system-prompt
  "You are a strict fact-checker. You get SOURCE MATERIAL and ENTRIES derived from it. For each entry decide whether every substantive claim (steps, UI elements, names, codes, constraints) is supported by the source. Transcript wording may be paraphrased or cleaned up — that is fine. Adding facts that are not in the source is not. Report grounded=false with the offending claims in issues when the entry invents anything.")

(defn- gemini-json
  [system-prompt prompt schema]
  (-> (llm/gemini-complete (assoc llm/gemini-config
                                  :response-schema schema
                                  :max-tokens 32768)
                           system-prompt
                           prompt)
      (get-in [:message :content])))

(defn extract-entries
  [{:keys [doc title lang]} source-text]
  (let [header (str "Source: " (or doc title) " (language: " lang ")\n\n")]
    (:entries (gemini-json extract-system-prompt
                           (str header source-text)
                           extract-schema))))

(defn ground-entries
  "Returns entries with :grounded / :issues merged in from the verifier."
  [source-text entries]
  (let [verdicts (:verdicts
                  (gemini-json ground-system-prompt
                               (str "SOURCE MATERIAL:\n\n" source-text
                                    "\n\nENTRIES:\n\n"
                                    (json/encode (map #(select-keys % [:title :body]) entries)))
                               ground-schema))
        by-title (into {} (map (juxt :title identity)) verdicts)]
    (mapv (fn [e]
            (let [v (get by-title (:title e))]
              (assoc e
                     :grounded (:grounded v false)
                     :issues (:issues v "no verdict returned"))))
          entries)))

;;; ——— Mechanical checks & doc assembly —————————————————————————————

(defn- mentioned-type-codes
  "Type codes the entry text mentions that actually exist. Codes that look
   like type codes but don't exist are returned separately for review."
  [text]
  (let [candidates (->> (re-seq #"\b(\d{3,4})\b" text)
                        (map (comp parse-long second))
                        distinct)]
    {:valid (filterv #(contains? types/all %) candidates)
     :unknown (vec (remove #(contains? types/all %) candidates))}))

(defn- ->doc
  [{:keys [kind slug lang] :as source} idx {:keys [title body start-seconds] :as entry}]
  (let [{:keys [valid unknown]} (mentioned-type-codes (str title " " body))]
    {:id            (str (name kind) ":" slug ":" idx)
     :title         title
     :body          body
     :lang          lang
     :source-type   (name kind)
     :source-ref    slug
     :deep-link     (case kind
                      :jyu (pdf-url slug)
                      :youtube (if (pos? (or start-seconds 0))
                                 (video-url slug start-seconds)
                                 (video-url slug)))
     :type-codes    valid
     :review-status "machine"
     ;; review metadata — stripped before indexing by kb/ingested->docs
     :grounded      (:grounded entry)
     :issues        (:issues entry)
     :unknown-codes unknown}))

(defn ingest-source!
  "Full pipeline for one source. Returns {:docs [...] :rejected [...]}."
  [{:keys [kind slug] :as source} source-text]
  (log/info "Ingesting" (name kind) slug)
  (let [entries (extract-entries source source-text)
        graded  (ground-entries source-text entries)
        docs    (map-indexed (partial ->doc source) graded)
        {grounded true rejected false} (group-by (comp boolean :grounded) docs)]
    (log/info "Ingested" slug {:entries (count docs) :rejected (count rejected)})
    {:docs (vec grounded) :rejected (vec rejected)}))

;;; ——— Drivers ———————————————————————————————————————————————————————

(defn ingest-all!
  "dir = directory with txt/<slug>.txt and subs/<id>.<lang>.vtt.
   Runs the whole corpus; returns {:docs [...] :rejected [...]}."
  [dir]
  (let [pdf-results
        (for [{:keys [slug lang] :as src} pdf-sources
              :let [f (io/file dir "txt" (str slug ".txt"))]
              :when (.exists f)]
          (ingest-source! (assoc src :kind :jyu :slug slug :lang lang)
                          (slurp f)))
        video-results
        (for [{:keys [id lang title] :as src} video-sources
              :let [f (io/file dir "subs" (str id "." lang ".vtt"))
                    transcript (if (.exists f)
                                 (vtt->transcript (slurp f))
                                 (gemini-video-transcript (video-url id) lang))]]
          (ingest-source! (assoc src :kind :youtube :slug id :lang lang
                                 :doc title)
                          (str "Video title: " title "\n\n" transcript)))
        results (concat pdf-results video-results)]
    {:docs (vec (mapcat :docs results))
     :rejected (vec (mapcat :rejected results))}))

(defn report
  [{:keys [docs rejected]}]
  {:accepted (count docs)
   :rejected (mapv #(select-keys % [:id :title :issues]) rejected)
   :unknown-type-codes (->> docs
                            (filter (comp seq :unknown-codes))
                            (mapv #(select-keys % [:id :unknown-codes])))
   :by-source (->> docs (map :source-ref) frequencies)})

(defn save-ingested!
  "Persists accepted docs to versioned_data and resyncs the KB index."
  [db search docs]
  (db/add-versioned-data! db "kb-ingest" "active"
                          (mapv #(dissoc % :grounded :issues :unknown-codes) docs))
  (kb/sync! db search))

(comment
  (def dir "/private/tmp/claude-501/-Users-tipo-lipas-lipas/8045ae23-5da4-4056-ba5c-c74d5956ff56/scratchpad/kb-ingest")
  (def result (ingest-all! dir))
  (report result)
  (save-ingested! (user/db) (user/search) (:docs result)))
