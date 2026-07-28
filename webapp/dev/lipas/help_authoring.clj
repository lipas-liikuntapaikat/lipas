(ns lipas.help-authoring
  "Authoring tooling for help center (ohjeet) v2 content:

   - extract article material from YouTube tutorial videos with Gemini's
     native video understanding (fileData/fileUri — no download needed)
   - translate finished pages between locales (fi/se/en)
   - upsert pages into the per-locale help trees in versioned_data

   Self-contained on purpose: only clj-http, cheshire, malli and
   lipas.backend.db.db, so the namespace loads into any LIPAS REPL
   regardless of branch, and the whole file can be piped inline to a
   remote nREPL (e.g. lipas-dev) where load-file paths don't exist.

   Driven by the help-video-ingest skill; see
   .claude/skills/help-video-ingest/SKILL.md for the workflow."
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [lipas.backend.db.db :as db]))

;;; ——— Gemini REST ———————————————————————————————————————————————————

(def default-config
  "Reads the key from the JVM environment; override per call if needed.
   gemini-3.1-pro-preview is the cheapest model verified to read UI
   labels reliably from 720p screencasts."
  {:api-key  (System/getenv "GEMINI_API_KEY")
   :base-url "https://generativelanguage.googleapis.com/v1beta"
   :model    "gemini-3.1-pro-preview"})

(defn gemini-generate
  "Low-level generateContent. parts is a vector of Gemini part maps,
   e.g. {:text ...} or {:fileData {:fileUri ...}}. Media parts must come
   before the text prompt (Google's video best practice). Returns the
   response text, or the parsed JSON map when :response-schema is given."
  [{:keys [api-key base-url model]} parts
   & {:keys [response-schema temperature max-tokens]
      :or   {temperature 0.2 max-tokens 65536}}]
  (let [gen-config (cond-> {:maxOutputTokens max-tokens
                            :temperature     temperature}
                     response-schema (assoc :responseMimeType "application/json"
                                            :responseSchema response-schema))
        resp (-> (http/post (str base-url "/models/" model ":generateContent")
                            {:headers            {"x-goog-api-key" api-key
                                                  "Content-Type"   "application/json"}
                             :body               (json/encode
                                                   {:contents [{:role "user" :parts parts}]
                                                    :generationConfig gen-config})
                             :socket-timeout     600000
                             :connection-timeout 10000})
                 :body
                 (json/decode keyword))
        text (-> resp :candidates first :content :parts last :text)]
    (when-not text
      (throw (ex-info "Gemini returned no text" {:resp resp})))
    (if response-schema (json/decode text keyword) text)))

;;; ——— Video → article material ——————————————————————————————————————

(def extract-prompt
  "Video analysis prompt. Asks for verbatim UI labels, mouse/keyboard
   mechanics and per-step timestamps — the things audio transcripts miss.
   Output language parameterized; source videos are Finnish."
  "You are analyzing a Finnish screencast tutorial video about LIPAS (lipas.fi), Finland's national database of sports and outdoor recreation facilities. The video demonstrates how to use the LIPAS map view (Liikuntapaikat) to maintain facility data. The audience is municipality employees and other data maintainers.

Your job: extract EVERYTHING instructional from this video, so precisely that a technical writer can produce help-center articles without ever watching the video. You have native video understanding — use both the narration (audio) and the visible UI (frames). The visible UI details are the most valuable part: transcripts already exist, pixels do not.

Write the output in %s, as markdown, using exactly this structure:

# Video: <title as shown or spoken>

## Yleiskuvaus
2-4 sentences: what the video teaches, which LIPAS views and tools appear.

## UI-havainnot
Bullet list of what the UI looks like: layout landmarks, panel names, notable buttons and icons with their exact on-screen labels, toolbars and their locations. Purpose: let the writer check whether the current LIPAS UI still matches the video's UI.

## Tehtävät
One section per distinct user task demonstrated:

### <task title, phrased the way a user would search for it>
- **Esitiedot:** prerequisites stated or clearly implied (login, edit permission, a selected facility, a file of a certain format, etc.)
- **Vaiheet:** numbered steps. Every step: `[MM:SS]` what the user does — exact UI element labels verbatim in quotes, precise mouse/keyboard mechanics (single click, double-click, drag, right-click, keyboard modifiers, which map tool is active) — and what the system visibly does in response (e.g. a vertex appears, the line highlights red, a dialog opens).
- **Vinkit ja varoitukset:** tips, warnings, common mistakes and edge cases the narrator mentions or the video shows.

## Sanasto
Domain terms used in the video, each with the meaning it carries in the video.

## Epäselvät kohdat
Anything you could not read or hear confidently.

Fidelity rules — these override everything else:
- ONLY content that is actually in the video. Never invent UI elements, labels, menu paths or type codes.
- Transcribe on-screen UI labels VERBATIM, exactly as rendered in Finnish.
- When the narration and the visible UI differ, report both and flag the difference.
- If a label or detail is unreadable or uncertain, mark it `[epävarma: <best guess>]`.
- Mouse and keyboard mechanics are critical: drawing, editing, splitting and importing geometries involve clicks, double-clicks, drags, modifier keys and map tool buttons — capture these exactly as demonstrated, step by step.
- Give a `[MM:SS]` timestamp on every step so claims can be verified against the video.")

(defn analyze-video
  "Native Gemini video analysis of a public YouTube URL. Returns markdown
   article material (see extract-prompt). ~50s and ~300 input tokens per
   video-second at default resolution. lang = output language, e.g.
   \"Finnish\"."
  ([url] (analyze-video default-config url "Finnish"))
  ([config url lang]
   (gemini-generate config
                    [{:fileData {:fileUri url}}
                     {:text (format extract-prompt lang)}])))

;;; ——— Page translation ———————————————————————————————————————————————

(def ^:private locale->language
  {:fi "Finnish" :se "Swedish" :en "English"})

(defn- translatable-paths
  "Paths (for get-in/assoc-in) of every human-readable string in a page."
  [page]
  (into (filterv #(get-in page %) [[:title] [:summary]])
        (for [[i b] (map-indexed vector (:blocks page))
              k     [:content :title :caption :alt]
              :when (string? (get b k))]
          [:blocks i k])))

(defn translate-page
  "Translates a v2 help page's strings from fi to target-locale (:se/:en),
   preserving structure, slugs, ids and media references. Markdown inside
   text blocks is kept as markdown; verbatim UI labels are handled by the
   prompt: LIPAS UI is fully localized, so labels are translated too."
  [config page target-locale]
  (let [paths (translatable-paths page)
        texts (mapv #(get-in page %) paths)
        out   (gemini-generate
                config
                [{:text (str "Translate the following LIPAS help-center strings from Finnish to "
                             (locale->language target-locale)
                             ". They belong to one help article about maintaining sports-facility "
                             "data on a map. Rules:\n"
                             "- Preserve markdown structure exactly (headers, lists, bold, quotes).\n"
                             "- LIPAS's user interface is fully localized, so translate quoted UI "
                             "labels naturally into the target language (e.g. \"Lisää kartalle\" → "
                             (case target-locale :se "\"Lägg till på kartan\"" "\"Add to map\"")
                             " style), keeping them **bold**/quoted as in the source.\n"
                             "- Keep LIPAS type codes (e.g. 4402), URLs and file-format names unchanged.\n"
                             "- Finnish domain terms may be glossed once in parentheses when helpful.\n"
                             "- Return JSON: {\"translations\": [...]} with exactly "
                             (count texts) " strings, same order as the input.\n\n"
                             "Input strings as JSON:\n" (json/encode texts))}]
                :response-schema {:type "object"
                                  :properties {:translations {:type "array"
                                                              :items {:type "string"}}}
                                  :required ["translations"]}
                :max-tokens 32768)
        translations (:translations out)]
    (when (not= (count texts) (count translations))
      (throw (ex-info "Translation count mismatch"
                      {:expected (count texts) :got (count translations)})))
    (reduce (fn [p [path t]] (assoc-in p path t))
            page
            (map vector paths translations))))

;;; ——— Tree upsert ————————————————————————————————————————————————————

(defn- ensure-ids
  [page]
  (-> page
      (update :id #(or % (str (random-uuid))))
      (update :blocks (fn [blocks]
                        (mapv #(update % :block-id (fn [id] (or id (str (random-uuid)))))
                              blocks)))))

(defn- locale->type [locale] (str "help-v2-" (name locale)))

(defn get-tree [db locale]
  (or (db/get-versioned-data db (locale->type locale) "active") []))

(defn slug->page-id
  "Map of page slug → page id over every section of a locale tree."
  [tree]
  (into {} (for [s tree, p (:pages s)] [(:slug p) (:id p)])))

(defn- upsert-page
  "Replaces a same-slug page in place (keeping its id), or inserts the
   new page at insert-idx. Returns [pages next-insert-idx]."
  [pages page insert-idx]
  (if-let [idx (first (keep-indexed (fn [i p] (when (= (:slug p) (:slug page)) i)) pages))]
    [(assoc pages idx (assoc page :id (:id (get pages idx)))) insert-idx]
    (let [idx (min insert-idx (count pages))]
      [(vec (concat (subvec pages 0 idx) [page] (subvec pages idx)))
       (inc idx)])))

(defn- validate-tree!
  "Best-effort: validates against lipas.schema.help/LocaleTree when the
   v2 schema is on the classpath (it isn't on pre-v2 branches). Stored
   trees carry string enum values, so decode with the string transformer
   before validating."
  [tree]
  (when-let [schema (try (requiring-resolve 'lipas.schema.help/LocaleTree)
                         (catch Exception _ nil))]
    (let [m-validate (requiring-resolve 'malli.core/validate)
          m-decode   (requiring-resolve 'malli.core/decode)
          m-explain  (requiring-resolve 'malli.core/explain)
          str-trans  ((requiring-resolve 'malli.transform/string-transformer))
          decoded    (m-decode @schema tree str-trans)]
      (when-not (m-validate @schema decoded)
        (throw (ex-info "Tree does not validate against LocaleTree"
                        {:explain (m-explain @schema decoded)})))
      decoded)))

(defn upsert-pages!
  "Merges pages into one section of a locale's help tree and publishes.

   content = {:section-slug ... :section-title ... :section-summary ...
              :after-slug ... :pages [...]}

   - Pages are matched by :slug: existing pages are replaced in place
     (keeping their :id, so translation-of links stay valid), new pages
     are inserted after :after-slug (or appended).
   - The section is created (title/summary required then) if missing.
   - Page maps may carry :translation-of-slug — resolved to the fi
     page's id and stored as :translation-of.
   - Missing :id/:block-id are filled with fresh uuids.
   - Publishes via lipas.backend.help/save-help-data when available
     (v2 branches: also enqueues the KB sync job), otherwise writes
     versioned_data directly.

   Returns {:locale ... :section ... :pages [slugs] :saved-via ...}."
  [db locale {:keys [section-slug section-title section-summary after-slug pages]}]
  (let [tree        (vec (get-tree db locale))
        fi-ids      (when (not= :fi locale) (slug->page-id (get-tree db :fi)))
        resolve-tr  (fn [p]
                      (if-let [slug (:translation-of-slug p)]
                        (-> p
                            (dissoc :translation-of-slug)
                            (assoc :translation-of
                                   (or (get fi-ids slug)
                                       (throw (ex-info "translation-of-slug not found in fi tree"
                                                       {:slug slug})))))
                        (dissoc p :translation-of-slug)))
        pages       (mapv (comp ensure-ids resolve-tr) pages)
        sec-idx     (or (first (keep-indexed
                                 (fn [i s] (when (= (:slug s) section-slug) i)) tree))
                        (count tree))
        section     (or (get tree sec-idx)
                        {:id      (str (random-uuid))
                         :slug    section-slug
                         :title   (or section-title section-slug)
                         :pages   []})
        section     (cond-> section
                      (and section-summary (not (:summary section)))
                      (assoc :summary section-summary))
        insert-idx  (let [ps (:pages section)]
                      (or (some->> ps
                                   (keep-indexed (fn [i p] (when (= (:slug p) after-slug) i)))
                                   first
                                   inc)
                          (count ps)))
        new-pages   (first
                      (reduce (fn [[ps idx] page]
                                (upsert-page ps page idx))
                              [(vec (:pages section)) insert-idx]
                              pages))
        tree        (assoc tree sec-idx (assoc section :pages new-pages))
        _           (validate-tree! tree)
        ;; v1 branches also have a save-help-data (different type/arity) —
        ;; only trust it when the v2 marker var is present too.
        save-fn     (try (when (requiring-resolve 'lipas.backend.help/locale->type)
                           (requiring-resolve 'lipas.backend.help/save-help-data))
                         (catch Exception _ nil))]
    (if save-fn
      (save-fn db locale tree)
      (db/add-versioned-data! db (locale->type locale) "active" tree))
    {:locale    locale
     :section   section-slug
     :pages     (mapv :slug pages)
     :saved-via (if save-fn :help/save-help-data :versioned-data)}))

(comment
  ;; Analyze one video (≈50 s):
  (def material (analyze-video "https://www.youtube.com/watch?v=XcZrIepjYe0"))

  ;; Publish authored pages (EDN under dev/lipas/help_content/):
  (def content (clojure.edn/read-string (slurp "dev/lipas/help_content/reitit_fi.edn")))
  (upsert-pages! (user/db) :fi content)

  ;; Translate the pages and publish per locale:
  (def se-pages (mapv #(-> (translate-page default-config % :se)
                           (assoc :translation-of-slug (:slug %)))
                      (:pages content)))
  (upsert-pages! (user/db) :se (assoc content :pages se-pages)))
