(ns lipas.backend.help
  "Help content v2 storage: one versioned_data document per locale
   (help-v2-fi / help-v2-se / help-v2-en) so each language drafts,
   publishes and rolls back independently. See lipas.schema.help for
   the shape and the v1→v2 rationale."
  (:require [clojure.string :as str]
            [lipas.backend.db.db :as db]
            [lipas.jobs.core :as jobs]
            [lipas.schema.help :as help-schema]
            [lipas.utils :as utils]
            [malli.core :as m]))

(def locale->type
  {:fi "help-v2-fi"
   :se "help-v2-se"
   :en "help-v2-en"})

(defn get-help-data
  "Every locale's published tree; a locale with no published content
   maps to []."
  [db]
  (into {}
        (for [[locale type] locale->type]
          [locale (or (db/get-versioned-data db type "active") [])])))

(defn save-help-data
  "Publishes one locale's tree: becomes immediately visible to all
   users. Enqueues a knowledge-base sync so the AI assistant sees the
   new content."
  [db locale data]
  (let [result (db/add-versioned-data! db (locale->type locale) "active" data)]
    (jobs/enqueue-job! db "help-kb-sync" {})
    result))

(defn save-help-draft
  [db locale data]
  (db/add-versioned-data! db (locale->type locale) "draft" data))

(defn get-help-versions
  [db locale]
  (db/list-versioned-data db (locale->type locale) 100))

(defn get-help-version
  [db id]
  (db/get-versioned-data-by-id db id))

;;; ——— v1 → v2 migration ———————————————————————————————————————————————

(defn- unique-slug
  [title fallback taken]
  (let [base (let [s (utils/->slug title)]
               (if (str/blank? s) (utils/->slug fallback) s))
        base (if (str/blank? base) "osio" base)]
    (->> (cons base (map #(str base "-" %) (iterate inc 2)))
         (remove taken)
         first)))

(defn- v1-block->v2
  "Collapses a v1 block's {:fi :se :en} leaves to locale's plain
   strings. Text blocks with no content in the locale are dropped;
   media blocks are kept (the media itself is the content)."
  [locale block]
  (let [loc #(get % locale)]
    (case (:type block)
      :text (when-not (str/blank? (loc (:content block)))
              {:block-id (:block-id block)
               :type :text
               :content (loc (:content block))})
      :image (cond-> {:block-id (:block-id block)
                      :type :image
                      :url (:url block)
                      :alt (or (loc (:alt block)) "")}
               (not (str/blank? (loc (:caption block))))
               (assoc :caption (loc (:caption block))))
      :video (cond-> {:block-id (:block-id block)
                      :type :video
                      :provider (:provider block)
                      :video-id (:video-id block)}
               (not (str/blank? (loc (:title block))))
               (assoc :title (loc (:title block))))
      :pdf (cond-> {:block-id (:block-id block)
                    :type :pdf
                    :url (:url block)}
             (not (str/blank? (loc (:title block))))
             (assoc :title (loc (:title block)))
             (not (str/blank? (loc (:caption block))))
             (assoc :caption (loc (:caption block))))
      {:block-id (:block-id block)
       :type (:type block)})))

(defn v1->v2-tree
  "One locale's independent tree from the v1 shared-structure data.
   Slugs are regenerated from the locale's titles (v1 slugs are
   new-page-<timestamp> junk); the old slug is kept in :aliases so
   published ?ohje= links and KB citations keep resolving."
  [v1-data locale]
  (let [section-slugs (atom #{})]
    (vec
     (for [section v1-data
           :let [old-slug (name (:slug section))
                 title (get-in section [:title locale])
                 slug (unique-slug title old-slug @section-slugs)
                 _ (swap! section-slugs conj slug)
                 page-slugs (atom #{})]]
       {:id (str (random-uuid))
        :slug slug
        :title (or title "")
        :aliases (vec (distinct (remove #{slug} [old-slug])))
        :pages (vec
                (for [page (:pages section)
                      :let [old-page-slug (name (:slug page))
                            page-title (get-in page [:title locale])
                            page-slug (unique-slug page-title old-page-slug @page-slugs)
                            _ (swap! page-slugs conj page-slug)]]
                  {:id (str (random-uuid))
                   :slug page-slug
                   :title (or page-title "")
                   :aliases (vec (distinct (remove #{page-slug} [old-page-slug])))
                   :blocks (vec (keep (partial v1-block->v2 locale)
                                      (:blocks page)))}))}))))

(defn migrate-v1->v2!
  "One-shot migration: publishes the v1 active document as the fi v2
   tree and empty se/en trees (prod se/en content is placeholder junk —
   translations start from a clean slate). v1 history is untouched.
   Refuses to run when v2 content already exists unless :force? true."
  [db & {:keys [force?]}]
  (let [v1 (db/get-versioned-data db "help" "active")]
    (when (nil? v1)
      (throw (ex-info "No v1 help data to migrate" {})))
    (when (and (not force?)
               (some #(seq (db/get-versioned-data db % "active"))
                     (vals locale->type)))
      (throw (ex-info "help-v2 content already exists; pass :force? true to overwrite"
                      {})))
    (let [fi-tree (v1->v2-tree v1 :fi)]
      (when-not (m/validate help-schema/LocaleTree fi-tree)
        (throw (ex-info "Migrated fi tree does not validate"
                        {:explain (m/explain help-schema/LocaleTree fi-tree)})))
      (db/add-versioned-data! db "help-v2-fi" "active" fi-tree)
      (db/add-versioned-data! db "help-v2-se" "active" [])
      (db/add-versioned-data! db "help-v2-en" "active" [])
      {:fi (count fi-tree) :se 0 :en 0})))
