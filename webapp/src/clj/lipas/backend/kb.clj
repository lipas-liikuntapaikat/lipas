(ns lipas.backend.kb
  "Knowledge base for the AI help assistant.

   One ES document = one task/question-sized entry in one language.
   Sources: the help CMS (published content, synced continuously) and
   code-derived docs regenerated from lipas.data.* on every sync — those
   can never go stale. Embeddings via lipas.backend.llm; documents carry
   a content hash so unchanged entries are never re-embedded.

   Index mapping lives in lipas.backend.search/kb-mapping and the index
   is created by system startup like every other index."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [lipas.backend.db.db :as db]
            [lipas.backend.llm :as llm]
            [lipas.backend.search :as search]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [taoensso.timbre :as log]))

(def langs [:fi :se :en])

(def embedding-model (:model llm/embedding-defaults))

(defn- sha-256
  [^String s]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256")
                        (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(defn- content-hash
  [{:keys [id title body lang source-type deep-link type-codes]}]
  (sha-256 (str/join "\n" [id title body lang source-type deep-link
                           (str/join "," (sort type-codes))])))

;;; ——— Help CMS → docs ———————————————————————————————————————————————

(defn- block->text
  [lang block]
  (case (:type block)
    :text  (get-in block [:content lang])
    :video (str "Video: " (get-in block [:title lang] "")
                " https://www.youtube.com/watch?v=" (:video-id block))
    :image (get-in block [:caption lang])
    :pdf   (when-let [title (get-in block [:title lang])]
             (str "PDF: " title " " (:url block)))
    nil))

(defn help-cms->docs
  "One doc per page per language. Languages whose title or entire body is
   blank are skipped — retrieval should never surface an empty entry."
  [help-data]
  (for [section help-data
        page    (:pages section)
        lang    langs
        :let [slug-path (str (name (:slug section)) "/" (name (:slug page)))
              title     (get-in page [:title lang])
              body      (->> (:blocks page)
                             (keep (partial block->text lang))
                             (map str/trim)
                             (remove str/blank?)
                             (str/join "\n\n"))]
        :when (and (not (str/blank? title))
                   (not (str/blank? body)))]
    {:id            (str "help-cms:" slug-path ":" (name lang))
     :title         title
     :body          body
     :lang          (name lang)
     :source-type   "help-cms"
     :source-ref    slug-path
     :deep-link     (str "?ohje=" slug-path)
     :type-codes    []
     :review-status "reviewed"}))

;;; ——— Code-derived docs ————————————————————————————————————————————

(def ^:private labels
  {:main-category {:fi "Pääluokka" :se "Huvudkategori" :en "Main category"}
   :sub-category  {:fi "Alaluokka" :se "Underkategori" :en "Sub category"}
   :geometry      {:fi "Geometria kartalla" :se "Geometri på kartan" :en "Geometry on map"}
   :props         {:fi "Ominaisuustiedot" :se "Egenskaper" :en "Properties"}
   :data-type     {:fi "Tietotyyppi" :se "Datatyp" :en "Data type"}
   :used-by       {:fi "Käytössä liikuntapaikkatyypeillä"
                   :se "Används av idrottsplatstyper"
                   :en "Used by sports facility types"}})

(def ^:private geometry-names
  {"Point"      {:fi "piste" :se "punkt" :en "point"}
   "LineString" {:fi "reitti" :se "rutt" :en "route"}
   "Polygon"    {:fi "alue" :se "område" :en "area"}})

(defn- type->doc
  [lang [type-code m]]
  (let [type-name (get-in m [:name lang])
        prop-names (->> (:props m)
                        keys
                        (keep #(get-in prop-types/all [% :name lang]))
                        sort)
        body (->> [(get-in m [:description lang])
                   (str (get-in labels [:main-category lang]) ": "
                        (get-in types/main-categories [(:main-category m) :name lang])
                        ". " (get-in labels [:sub-category lang]) ": "
                        (get-in types/sub-categories [(:sub-category m) :name lang]) ".")
                   (str (get-in labels [:geometry lang]) ": "
                        (get-in geometry-names [(:geometry-type m) lang]
                                (:geometry-type m)) ".")
                   (when (seq prop-names)
                     (str (get-in labels [:props lang]) ": "
                          (str/join ", " prop-names) "."))]
                  (remove str/blank?)
                  (str/join "\n\n"))]
    (when-not (str/blank? type-name)
      {:id            (str "type:" type-code ":" (name lang))
       :title         (str type-code " — " type-name)
       :body          body
       :lang          (name lang)
       :source-type   "code-data"
       :source-ref    (str "type:" type-code)
       :deep-link     nil
       :type-codes    [type-code]
       :review-status "reviewed"})))

(defn- prop->doc
  [lang [prop-k m]]
  (let [prop-name (get-in m [:name lang])
        used-by (->> types/active
                     (filter (fn [[_ t]] (contains? (:props t) prop-k)))
                     (keep (fn [[tc t]]
                             (when-let [n (get-in t [:name lang])]
                               (str tc " " n))))
                     sort)
        body (->> [(get-in m [:description lang])
                   (str (get-in labels [:data-type lang]) ": " (:data-type m) ".")
                   (when (seq used-by)
                     (str (get-in labels [:used-by lang]) ": "
                          (str/join ", " used-by) "."))]
                  (remove str/blank?)
                  (str/join "\n\n"))]
    (when-not (str/blank? prop-name)
      {:id            (str "prop:" (name prop-k) ":" (name lang))
       :title         prop-name
       :body          body
       :lang          (name lang)
       :source-type   "code-data"
       :source-ref    (str "prop:" (name prop-k))
       :deep-link     nil
       :type-codes    (->> types/active
                           (filter (fn [[_ t]] (contains? (:props t) prop-k)))
                           (mapv first))
       :review-status "reviewed"})))

(defn code-data->docs
  []
  (concat
   (for [lang langs, entry types/active
         :let [doc (type->doc lang entry)] :when doc]
     doc)
   (for [lang langs, entry prop-types/all
         :let [doc (prop->doc lang entry)] :when doc]
     doc)))

;;; ——— Sync —————————————————————————————————————————————————————————

(defn- existing-doc-hashes
  "Map of doc id → {:content-hash ... :embedding-model ...} for everything
   currently in the index."
  [client idx]
  (->> (search/search client idx
                      {:size    10000
                       :query   {:match_all {}}
                       :_source [:content-hash :embedding-model]})
       :body :hits :hits
       (map (fn [hit] [(:_id hit) (:_source hit)]))
       (into {})))

(defn- unchanged?
  [existing doc]
  (when-let [e (get existing (:id doc))]
    (and (= (:content-hash e) (:content-hash doc))
         (= (:embedding-model e) (:embedding-model doc)))))

(defn sync!
  "Rebuild the knowledge base: published help CMS content + code-derived
   docs. Embeds only new/changed docs (content-hash diff), deletes docs
   whose source entry no longer exists. Idempotent; safe to run any time."
  [db search]
  (let [client    (:client search)
        idx       (get-in search [:indices :kb :kb])
        now       (str (java.time.Instant/now))
        docs      (->> (concat (help-cms->docs (db/get-versioned-data db "help" "active"))
                               (code-data->docs))
                       (map #(assoc %
                                    :content-hash (content-hash %)
                                    :embedding-model embedding-model
                                    :updated-at now)))
        existing  (existing-doc-hashes client idx)
        changed   (remove (partial unchanged? existing) docs)
        orphans   (set/difference (set (keys existing))
                                  (set (map :id docs)))]
    (when (seq changed)
      (let [vectors (llm/embed llm/gemini-config
                               (mapv :body changed)
                               :titles (mapv :title changed))
            with-embeddings (mapv (fn [doc v] (assoc doc :embedding v))
                                  changed vectors)]
        (search/bulk-index-sync!
         client (search/->bulk idx :id with-embeddings))))
    (doseq [id orphans]
      (search/delete! client idx id))
    (let [result {:total    (count docs)
                  :embedded (count changed)
                  :deleted  (count orphans)}]
      (log/info "KB sync complete" result)
      result)))

(comment
  (require '[integrant.repl.state :as state])
  (def db* (:lipas/db state/system))
  (def search* (:lipas/search state/system))
  (sync! db* search*)
  (count (code-data->docs))
  (take 2 (help-cms->docs (db/get-versioned-data db* "help" "active"))))
