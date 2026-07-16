---
name: help-video-ingest
description: Extract help-center (ohjeet) articles from LIPAS YouTube tutorial videos using Gemini's native video understanding, consolidate them into task-sized help pages, translate to sv/en, and publish to the help CMS (which feeds the AI-assistant knowledge base). Use when turning tutorial videos or guide PDFs into ohjeet content.
---

# Help video ingest

Turn LIPAS tutorial videos (and guide PDFs) into published, translated
help-center pages. The help CMS is the canonical target: published pages
become both human-readable ohjeet and AI-assistant KB docs
(`lipas.backend.kb/help-cms->docs` — one KB doc per page per language).

All heavy lifting lives in **`dev/lipas/help_authoring.clj`**
(`lipas.help-authoring`, self-contained: loads on any branch, can be
piped inline to remote nREPLs). This skill is the workflow around it.

## Prerequisites

- `GEMINI_API_KEY` in the backend JVM env (comes from `../.env.sh`)
- nREPL: local `clj-nrepl-eval -p 7888`; remote (e.g. lipas-dev) via an
  SSH tunnel the user opens, e.g. port 7887
- Load the util locally:
  `(load-file ".../webapp/dev/lipas/help_authoring.clj")`
  Remote REPLs can't `load-file` your disk — pipe the file inline:
  `clj-nrepl-eval -p 7887 "$(cat dev/lipas/help_authoring.clj)"`

## Workflow

### 1. Analyze the videos

One Gemini call per video, YouTube URL passed natively (`fileData.fileUri`
— no download; the videos are typically DRM-protected anyway, and agentic
CLIs like `agy`/`gemini` can NOT ingest YouTube natively, so don't try
them for this):

```clojure
(require '[lipas.help-authoring :as ha])
(def material (ha/analyze-video "https://www.youtube.com/watch?v=..."))
(spit "/tmp/extract_<id>.md" material)
```

~50 s and ~300 input tokens per video-second. The prompt
(`ha/extract-prompt`) asks for verbatim UI labels, exact mouse/keyboard
mechanics and `[MM:SS]` timestamps — the value over plain transcripts.
Save each extraction to a file for review; read them all before writing.

The full Lipasinfo video inventory lives in
`dev/lipas/kb_ingest.clj` (`video-sources`).

### 2. Ground against the current product — never skip

Videos age. Verify every UI label and mechanic before it goes into an
article:

- Labels: `grep -r "<label>" src/cljc/lipas/i18n/fi/`
- Map tool behavior: `src/cljs/lipas/ui/map/` (`events.cljs`,
  `editing.cljs`, `import.cljs`, `views.cljs`)
- When unsure, check the running UI in the browser.

Known drift examples found this way: simplify dialog retitled, import
now auto-simplifies GPX tracks, self-intersection warning text rewritten.
Record what you corrected — reviewers will ask.

### 3. Consolidate into task-sized pages

One page = one KB doc, so pages should answer one user task each
("Reittigeometrian tuonti tiedostosta"), not bundle five. Author the
result as an EDN content file under `dev/lipas/help_content/`:

```edn
{:section-slug "existing-or-new-section"
 :after-slug   "page-to-insert-after"        ; optional
 :pages [{:slug "..." :title "..." :summary "..."
          :blocks [{:type :text :content "markdown..."}
                   {:type :video :provider :youtube
                    :video-id "..." :title "..."}]}]}
```

Keep the source video embedded on its page. Keep existing slugs stable —
`?ohje=` deep links and KB citations resolve through them (renames need
`:aliases`). `:summary` feeds both the landing list and KB retrieval.

### 4. Publish fi

```clojure
(def content (clojure.edn/read-string (slurp "dev/lipas/help_content/<name>_fi.edn")))
(ha/upsert-pages! (user/db) :fi content)
```

Existing pages (matched by slug) are replaced in place keeping their id;
new pages are inserted after `:after-slug`. On v2 branches this publishes
via `lipas.backend.help/save-help-data`, which also enqueues the KB sync
job; on pre-v2 branches it writes `versioned_data` directly (check
`:saved-via` in the return value — `:versioned-data` means no KB sync
ran and the running UI may be v1).

### 5. Translate (LIPAS Swedish code is `se`, not `sv`)

Publish fi first — translation links resolve against the fi tree:

```clojure
(def se-pages (mapv #(-> (ha/translate-page ha/default-config % :se)
                         (assoc :translation-of-slug (:slug %)))
                    (:pages content)))
(ha/upsert-pages! (user/db) :se (assoc content
                                       :pages se-pages
                                       :section-title "<translated>"))
```

Same for `:en`. Persist translated content as
`dev/lipas/help_content/<name>_{se,en}.edn` for review/provenance.
Spot-check translations: UI labels must be translated (LIPAS UI is fully
localized), type codes and URLs unchanged.

### 6. Deploy to a remote env (e.g. lipas-dev)

Pipe the namespace, then run the same upserts reading the EDN files
inline (heredoc). Order: fi → se → en. Verify afterwards:

```clojure
(require '[lipas.backend.config :as config])
(def dbc (:db config/default-config))          ; remote REPL has no user/db
(mapv :slug (lipas.help-authoring/get-tree dbc :fi))
```

`save-help-data` enqueues `help-kb-sync`; the app's worker picks it up
from the shared DB queue. Confirm with
`(lipas.backend.kb/search-kb (search) {:query "..." :lang "fi"})` or by
checking the jobs table.

### 7. Verify rendering

Open the help center in the target env: `?ohje=<section-slug>/<page-slug>`
on `/liikuntapaikat`. Check: markdown renders (headers, lists, bold),
video embeds play, language switcher shows se/en counterparts, and the
assistant retrieves the new content.

## Cost & quality notes

- gemini-3.1-pro-preview reads 720p screencast UI labels reliably; a
  7-minute video ≈ 42k input tokens.
- Extraction fidelity rules live in `ha/extract-prompt`; keep the
  "only what's in the video / mark uncertain readings" contract if you
  edit it.
- PDFs: just `Read` them (the tool renders pages) and merge manually —
  no Gemini needed.
