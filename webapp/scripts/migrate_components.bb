#!/usr/bin/env bb
;; scripts/migrate_components.bb — Replace lipas.ui.components barrel imports
;; with direct sub-namespace imports.
;;
;; Usage:
;;   bb scripts/migrate_components.bb [--dry-run] file1.cljs [file2.cljs ...]
;;   bb scripts/migrate_components.bb --all [--dry-run]

(require '[clojure.string :as str]
         '[clojure.java.io :as io])

;; ============================================================================
;; Barrel alias → [sub-ns-suffix var-name]
;; Derived directly from src/cljs/lipas/ui/components.cljs
;; ============================================================================

(def barrel-map
  {;; buttons
   "email-button"                    ["buttons" "email-button"]
   "download-button"                 ["buttons" "download-button"]
   "login-button"                    ["buttons" "login-button"]
   "register-button"                 ["buttons" "register-button"]
   "save-button"                     ["buttons" "save-button"]
   "discard-button"                  ["buttons" "discard-button"]
   "locator-button"                  ["buttons" "locator-button"]
   ;; text-fields (barrel uses text-field-controlled, but text-fields has def text-field = text-field-controlled)
   "text-field"                      ["text-fields" "text-field"]
   ;; selects
   "select"                          ["selects" "select"]
   "multi-select"                    ["selects" "multi-select"]
   "years-selector"                  ["selects" "years-selector"]
   "year-selector"                   ["selects" "year-selector"]
   "number-selector"                 ["selects" "number-selector"]
   "region-selector"                 ["selects" "region-selector"]
   "type-selector"                   ["selects" "type-selector"]
   "type-selector-single"            ["selects" "type-selector-single"]
   "type-category-selector"          ["selects" "type-category-selector"]
   "city-selector-single"            ["selects" "city-selector-single"]
   "admin-selector"                  ["selects" "admin-selector"]
   "admin-selector-single"           ["selects" "admin-selector-single"]
   "owner-selector"                  ["selects" "owner-selector"]
   "owner-selector-single"           ["selects" "owner-selector-single"]
   "surface-material-selector"       ["selects" "surface-material-selector"]
   "search-results-column-selector"  ["selects" "search-results-column-selector"]
   "status-selector"                 ["selects" "status-selector"]
   "status-selector-single"          ["selects" "status-selector-single"]
   "date-picker"                     ["selects" "date-picker"]
   ;; autocompletes
   "autocomplete"                    ["autocompletes" "autocomplete"]
   "year-selector2"                  ["autocompletes" "year-selector"]
   ;; tables
   "table"                           ["tables" "table"]
   "table-v2"                        ["tables" "table-v2"]
   "form-table"                      ["tables" "form-table"]
   ;; dialogs
   "dialog"                          ["dialogs" "dialog"]
   "full-screen-dialog"              ["dialogs" "full-screen-dialog"]
   "confirmation-dialog"             ["dialogs" "confirmation-dialog"]
   ;; forms
   "form"                            ["forms" "form"]
   ;; checkboxes
   "checkbox"                        ["checkboxes" "checkbox"]
   "switch"                          ["checkboxes" "switch"]
   ;; notifications
   "notification"                    ["notifications" "notification"]
   ;; layouts
   "floating-container"              ["layouts" "floating-container"]
   "form-card"                       ["layouts" "card"]
   "expansion-panel"                 ["layouts" "expansion-panel"]
   ;; misc
   "icon-text"                       ["misc" "icon-text"]
   "li"                              ["misc" "li"]
   "sub-heading"                     ["misc" "sub-heading"]})

;; Canonical aliases for sub-namespaces
(def canonical-aliases
  {"autocompletes" "autocompletes"
   "buttons"       "buttons"
   "checkboxes"    "checkboxes"
   "dialogs"       "dialogs"
   "forms"         "forms"
   "layouts"       "layouts"
   "misc"          "misc"
   "notifications" "notifications"
   "selects"       "selects"
   "tables"        "tables"
   "text-fields"   "text-fields"})

;; ============================================================================
;; Helpers
;; ============================================================================

(defn find-barrel-refs
  "Find all unique prefix/xxx references in content."
  [content prefix]
  (let [escaped (java.util.regex.Pattern/quote prefix)]
    (->> (re-seq (re-pattern (str "(?<![:\\w])" escaped "/([^\\s()\\[\\]{}\",;@/]+)")) content)
         (map second)
         set)))

(defn find-existing-sub-ns-as-imports
  "Find existing [lipas.ui.components.xxx :as alias] imports.
   Returns {sub-ns-suffix alias-name}"
  [content]
  (->> (re-seq #"\[lipas\.ui\.components\.([^\s\]]+)\s+:as\s+([^\s\]]+)" content)
       (map (fn [[_ sub-ns alias-name]]
              [(str/replace sub-ns "_" "-") alias-name]))
       (into {})))

(defn find-refer-only-sub-ns
  "Find sub-namespaces imported with :refer but without :as.
   Returns #{sub-ns-suffix}"
  [content]
  (let [;; Find all sub-ns that have :refer
        with-refer (->> (re-seq #"\[lipas\.ui\.components\.([^\s\]]+)\s+:refer\s+" content)
                        (map (fn [[_ sub-ns]] (str/replace sub-ns "_" "-")))
                        set)
        ;; Find all sub-ns that have :as
        with-as (->> (re-seq #"\[lipas\.ui\.components\.([^\s\]]+)\s+:as\s+" content)
                     (map (fn [[_ sub-ns]] (str/replace sub-ns "_" "-")))
                     set)]
    (clojure.set/difference with-refer with-as)))

(defn detect-barrel-alias
  "Detect what alias is used for lipas.ui.components."
  [content]
  (second (re-find #"\[lipas\.ui\.components\s+:as\s+([^\s\]]+)" content)))

(defn replace-refs
  "Replace all barrel-prefix/name references with sub-ns-alias/var-name in content.
   replacement-map: {barrel-name [sub-alias var-name]}"
  [content barrel-alias replacement-map]
  (let [;; Sort by name length descending to avoid substring matches
        sorted-names (sort-by #(- (count %)) (keys replacement-map))]
    (reduce
      (fn [s barrel-name]
        (let [[sub-alias var-name] (get replacement-map barrel-name)
              escaped-prefix (java.util.regex.Pattern/quote barrel-alias)
              escaped-name (java.util.regex.Pattern/quote barrel-name)
              target (str sub-alias "/" var-name)]
          (str/replace s
                       (re-pattern (str "(?<![:\\w])" escaped-prefix "/" escaped-name "(?![\\w-])"))
                       target)))
      content
      sorted-names)))

(defn find-barrel-require-idx
  "Find line index of [lipas.ui.components :as xxx]."
  [lines]
  (first (keep-indexed
           (fn [i line]
             (when (re-find #"\[lipas\.ui\.components\s+:as\s+" line) i))
           lines)))

(defn add-as-to-refer-line
  "Add :as alias to a line that has :refer but no :as.
   '[lipas.ui.components.xxx :refer [y]]' → '[lipas.ui.components.xxx :as alias :refer [y]]'"
  [line sub-ns alias-name]
  (let [;; Handle both _ and - in file path vs namespace name
        fs-sub-ns (str/replace sub-ns "-" "[_-]")]
    (str/replace line
                 (re-pattern (str "(\\[lipas\\.ui\\.components\\." fs-sub-ns "\\s+)(:refer)"))
                 (str "$1:as " alias-name " $2"))))

(defn find-refer-only-line-idx
  "Find line index of [lipas.ui.components.sub-ns :refer [...]] (no :as)."
  [lines sub-ns]
  (let [fs-sub-ns (str/replace sub-ns "-" "[_-]")
        refer-pat (re-pattern (str "\\[lipas\\.ui\\.components\\." fs-sub-ns "\\s+:refer\\s+"))
        as-pat (re-pattern (str "\\[lipas\\.ui\\.components\\." fs-sub-ns "\\s+:as\\s+"))]
    (first (keep-indexed
             (fn [i line]
               (when (and (re-find refer-pat line)
                          (not (re-find as-pat line)))
                 i))
             lines))))

(defn update-ns-requires
  "Update ns :require block. Remove barrel require, add sub-ns requires,
   merge :as into :refer-only lines."
  [lines needed-sub-ns-aliases existing-aliases refer-only-sns]
  (let [;; First, merge :as into existing :refer-only lines
        lines (reduce
                (fn [ls [sub-ns alias-name]]
                  (if (contains? refer-only-sns sub-ns)
                    (let [idx (find-refer-only-line-idx ls sub-ns)]
                      (if idx
                        (do
                          (println (str "  Merging :as " alias-name " into :refer line for " sub-ns))
                          (assoc ls idx (add-as-to-refer-line (nth ls idx) sub-ns alias-name)))
                        ls))
                    ls))
                (vec lines)
                needed-sub-ns-aliases)

        barrel-idx (find-barrel-require-idx lines)
        _ (when-not barrel-idx
            (println "  ERROR: Could not find [lipas.ui.components :as ...] in ns form")
            (System/exit 1))

        barrel-line (nth lines barrel-idx)

        ;; Extract prefix (everything before [lipas.ui.components)
        prefix (second (re-find #"^(.*?)\[lipas\.ui\.components\s" barrel-line))

        ;; Column where [ starts
        bracket-col (count prefix)

        ;; Continuation indent (spaces to bracket column)
        cont-indent (apply str (repeat bracket-col \space))

        ;; Extract suffix after the closing ] of the barrel require (e.g., "))" )
        suffix (or (second (re-find #"\[lipas\.ui\.components\s+:as\s+\S+\](.*)" barrel-line)) "")

        ;; Which sub-ns need brand new require lines?
        ;; Exclude those already imported (:as or :refer) and those we just merged :as into
        new-requires (->> needed-sub-ns-aliases
                          (remove (fn [[sub-ns _]] (contains? existing-aliases sub-ns)))
                          (remove (fn [[sub-ns _]] (contains? refer-only-sns sub-ns)))
                          (sort-by first)
                          vec)

        ;; Build new require lines to replace the barrel line
        require-lines (mapv
                        (fn [[sub-ns alias-name]]
                          (str "[lipas.ui.components." sub-ns " :as " alias-name "]"))
                        new-requires)

        ;; Assemble replacement lines
        replacement-lines (if (empty? require-lines)
                            ;; No new requires needed — just remove the barrel line
                            []
                            ;; First line gets original prefix, rest get cont-indent
                            (into [(str prefix (first require-lines))]
                                  (map #(str cont-indent %) (rest require-lines))))

        ;; Append suffix to the last replacement line (or to previous line if no replacements)
        replacement-lines (if (and (seq suffix) (not (str/blank? suffix)))
                            (if (seq replacement-lines)
                              (update replacement-lines (dec (count replacement-lines)) str suffix)
                              ;; No new lines: append suffix to line before barrel
                              ;; This handles the case where barrel was last require with ))
                              :transfer-suffix)
                            replacement-lines)

        before (subvec lines 0 barrel-idx)
        after (subvec lines (inc barrel-idx))]

    (if (= replacement-lines :transfer-suffix)
      ;; Transfer closing parens to the line above
      (let [before (if (and (seq suffix) (seq before))
                     (update before (dec (count before)) str suffix)
                     before)]
        (vec (concat before after)))
      (vec (concat before replacement-lines after)))))

;; ============================================================================
;; Main migration
;; ============================================================================

(defn migrate-file!
  [path {:keys [dry-run]}]
  (let [content (slurp path)
        trailing-newline? (str/ends-with? content "\n")
        barrel-alias (detect-barrel-alias content)]
    (if-not barrel-alias
      (do (println "  SKIP: no barrel import found")
          nil)
      (let [all-refs (find-barrel-refs content barrel-alias)
            known-refs (filterv #(contains? barrel-map %) all-refs)
            unknown-refs (vec (remove #(contains? barrel-map %) all-refs))]

        (when (seq unknown-refs)
          (println (str "  WARN: Unknown " barrel-alias "/ refs: "
                        (str/join ", " (sort unknown-refs)))))

        (if (and (empty? known-refs) (empty? unknown-refs))
          (do (println "  SKIP: no barrel refs found")
              nil)
          (let [existing-aliases (find-existing-sub-ns-as-imports content)
                refer-only-sns (find-refer-only-sub-ns content)

                ;; For each barrel ref, determine target sub-ns and resolve alias
                needed-sub-ns (into {}
                                    (for [ref-name known-refs
                                          :let [[sub-ns _] (barrel-map ref-name)
                                                alias (or (get existing-aliases sub-ns)
                                                          (canonical-aliases sub-ns))]]
                                      [sub-ns alias]))

                ;; Build replacement map: barrel-name → [resolved-alias var-name]
                replacement-map (into {}
                                      (for [ref-name known-refs
                                            :let [[sub-ns var-name] (barrel-map ref-name)
                                                  alias (get needed-sub-ns sub-ns)]]
                                        [ref-name [alias var-name]]))

                ;; Replace refs in body
                new-content (replace-refs content barrel-alias replacement-map)

                ;; Update ns requires
                new-lines (update-ns-requires
                            (vec (str/split-lines new-content))
                            needed-sub-ns
                            existing-aliases
                            refer-only-sns)

                final-content (cond-> (str/join "\n" new-lines)
                                trailing-newline? (str "\n"))]

            (if dry-run
              (do
                (println (str "  Would migrate " (count known-refs) " refs: "
                              (str/join ", " (sort known-refs))))
                (doseq [[sub-ns alias] (sort-by first needed-sub-ns)]
                  (if (contains? existing-aliases sub-ns)
                    (println (str "    ~ " sub-ns " (already imported as " (existing-aliases sub-ns) ")"))
                    (if (contains? refer-only-sns sub-ns)
                      (println (str "    + :as " alias " merged into existing :refer for " sub-ns))
                      (println (str "    + [lipas.ui.components." sub-ns " :as " alias "]"))))))
              (do
                (spit path final-content)
                (println (str "  Migrated " (count known-refs) " refs"))))

            {:path path
             :refs (count known-refs)}))))))

;; ============================================================================
;; CLI
;; ============================================================================

(let [args *command-line-args*
      dry-run? (some #{"--dry-run"} args)
      all? (some #{"--all"} args)
      files (if all?
              (->> (file-seq (io/file "src/cljs/lipas/ui"))
                   (filter #(.isFile %))
                   (filter #(str/ends-with? (.getName %) ".cljs"))
                   (map #(.getPath %))
                   (filter (fn [p] (str/includes? (slurp p) "[lipas.ui.components :as ")))
                   sort
                   vec)
              (->> args (remove #(str/starts-with? % "--")) vec))]
  (when (empty? files)
    (println "Usage: bb scripts/migrate_components.bb [--dry-run] file1.cljs [file2.cljs ...]")
    (println "       bb scripts/migrate_components.bb --all [--dry-run]")
    (System/exit 1))
  (println (str "Processing " (count files) " files..."
                (when dry-run? " (DRY RUN)")
                "\n"))
  (let [results (doall
                  (for [f files]
                    (do
                      (println (str "Processing " f "..."))
                      (when-not (.exists (java.io.File. f))
                        (println (str "  ERROR: File not found: " f))
                        (System/exit 1))
                      (migrate-file! f {:dry-run dry-run?}))))]
    (println (str "\nDone. Migrated " (count (remove nil? results)) " files."))))
