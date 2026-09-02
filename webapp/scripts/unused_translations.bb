#!/usr/bin/env bb
;; scripts/unused_translations.bb — Find and optionally remove unused translation keys
;;
;; Usage:
;;   bb scripts/unused_translations.bb                     # Report unused keys
;;   bb scripts/unused_translations.bb --lang en           # Compare against the en dictionary
;;   bb scripts/unused_translations.bb --edn               # Report as EDN
;;   bb scripts/unused_translations.bb --remove --dry-run  # Preview removal
;;   bb scripts/unused_translations.bb --remove            # Remove unused keys
;;
;; The reference language decides which keys exist at all: only keys defined in
;; that language's EDN files are analysed. fi is the default because it is the
;; fallback locale and the one that is always complete. Run it against en or se
;; to catch keys that were added to one locale and never to fi — those are
;; invisible to a fi-based run, and being absent from the fallback they cannot
;; render for anyone.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[rewrite-clj.zip :as z])

;; === Configuration ===

(def i18n-base "src/cljc/lipas/i18n")
(def src-dirs ["src/cljs" "src/cljc"])
(def utils-file "src/cljc/lipas/i18n/utils.cljc")
(def langs ["fi" "se" "en"])

;; Keys constructed dynamically at runtime are considered "used" regardless of
;; static analysis. The exemption map is ns -> :all | #{"key-prefix" ...}:
;;
;;   :all        the whole namespace is unanalysable, because some call builds a
;;               key from an opaque expression — (keyword :accessibility f)
;;   #{"pfx-"}   only keys starting with one of these prefixes are unanalysable,
;;               because every call for that ns has a literal prefix —
;;               (keyword "ptv" (str "filter-" (name status-filter))) can only
;;               ever produce :ptv/filter-*, so the rest of :ptv stays checkable
;;
;; Both are auto-detected. Keep entries below only for namespaces that are hard
;; to detect automatically (key construction happens in data, not in a
;; (keyword ...) call visible to regex).
(def hardcoded-exempt-namespaces
  {"pool-types" :all
   "pool-structures" :all})

;; Will be bound after scanning source files
(def ^:dynamic *all-exempt-namespaces* hardcoded-exempt-namespaces)

(defn exempt-key?
  "Is this qualified key unreachable by static analysis? True when its namespace
  is exempt wholesale, when it matches one of its namespace's dynamic prefixes,
  or when it lives under a wholesale-exempt parent namespace (nested EDN maps
  produce ns.sub-keys, which the parent's dynamic construction can reach)."
  [k]
  (let [ns-name (namespace k)
        spec (get *all-exempt-namespaces* ns-name)]
    (boolean
      (or (= :all spec)
          (and (set? spec) (some #(str/starts-with? (name k) %) spec))
          (some (fn [[n sp]]
                  (and (= :all sp) (str/starts-with? ns-name (str n "."))))
                *all-exempt-namespaces*)))))

;; === CLI ===

(def cli-args (set *command-line-args*))
(def remove? (contains? cli-args "--remove"))
(def dry-run? (or (contains? cli-args "--dry-run")
                  (contains? cli-args "-n")))
(def edn-output? (contains? cli-args "--edn"))

(defn- parse-ref-lang
  "--lang <code> or --lang=<code>, defaulting to fi."
  [args]
  (let [v (or (second (drop-while #(not= "--lang" %) args))
              (some #(second (re-matches #"--lang=(.+)" %)) args))]
    (cond
      (nil? v) "fi"
      (some #{v} langs) v
      :else (binding [*out* *err*]
              (println (format "Unknown --lang %s, expected one of %s"
                               (pr-str v) (str/join ", " langs)))
              (System/exit 1)))))

(def ref-lang (parse-ref-lang *command-line-args*))

;; === Phase 1: Collect Defined Keys ===

(defn safe-filename [s]
  (str/replace s "." "_"))

(defn parse-top-level-keys
  "Extract keywords from top-level-keys vector in utils.cljc using rewrite-clj"
  []
  (let [content (slurp utils-file)
        zloc (z/of-string content)
        vec-loc (-> zloc
                    (z/find-value z/next 'top-level-keys)
                    z/right)]
    (when vec-loc
      (z/sexpr vec-loc))))

(defn flatten-edn-keys
  "Produce qualified keywords from an EDN map.
   {:save 'Tallenna'} in 'actions' -> {:actions/save {:file ... :edn-key :save}}
   Nested maps recurse one level deeper."
  [ns-name edn-data]
  (reduce-kv
    (fn [acc k v]
      (let [qk (keyword ns-name (name k))
            entry {:file (str (safe-filename ns-name) ".edn")
                   :edn-key k}
            acc (assoc acc qk entry)]
        (if (map? v)
          (let [sub-ns (str ns-name "." (name k))]
            (reduce-kv
              (fn [a k2 _]
                (assoc a (keyword sub-ns (name k2))
                       {:file (:file entry) :edn-key k :sub-key k2}))
              acc v))
          acc)))
    {} edn-data))

(defn collect-defined-keys
  "Read all fi/ EDN files, return map of qualified-keyword -> metadata"
  []
  (let [top-keys (parse-top-level-keys)]
    (reduce
      (fn [acc kw]
        (let [ns-name (name kw)
              filename (str (safe-filename ns-name) ".edn")
              path (str i18n-base "/" ref-lang "/" filename)]
          (if (.exists (io/file path))
            (merge acc (flatten-edn-keys ns-name (edn/read-string (slurp path))))
            (do (binding [*out* *err*]
                  (println "WARNING: Missing" path))
                acc))))
      {} top-keys)))

;; === Phase 2: Collect Used Keys ===

(defn find-cljs-files
  "Frontend sources that may reference translation keys. .cljc is included
  because shared namespaces reference them too; the i18n dictionaries
  themselves are .edn and so never match."
  []
  (->> (mapcat #(file-seq (io/file %)) src-dirs)
       (filter #(let [n (.getName ^java.io.File %)]
                  (or (str/ends-with? n ".cljs") (str/ends-with? n ".cljc"))))
       vec))

(defn- merge-exempt-spec
  "Combine two exemption specs for the same namespace. :all wins over prefixes —
  one opaque call site makes the whole namespace unanalysable."
  [a b]
  (cond (or (= :all a) (= :all b)) :all
        (nil? a) b
        (nil? b) a
        :else (into a b)))

(defn extract-dynamic-namespaces
  "Extract namespaces where keywords are constructed dynamically at runtime,
   as ns -> :all | #{\"prefix\" ...}. See *all-exempt-namespaces*.
   Matches patterns like:
     (keyword \"ns\" (str \"pfx-\" ...))  (keyword :ns some-var)  (keyword :ns (name ...))"
  [content]
  ;; Collapse whitespace so multiline (keyword ...) calls become single-line for regex
  (let [flat (str/replace content #"\s+" " ")
        add (fn [m [ns spec]] (update m ns merge-exempt-spec spec))]
    (reduce add {}
            (concat
              ;; (keyword "ns" (str "literal-prefix" <expr>)) — only that prefix
              ;; is unanalysable. Catches:
              ;;   (keyword "ptv" (str "filter-" (name status-filter)))
              ;;   (keyword "lipas.sports-site" (str "name-localized-" (name l)))
              (->> (re-seq #"\(keyword\s+\"([^\"]+)\"\s+\(str\s+\"([^\"]+)\"" flat)
                   (map (fn [[_ ns pfx]] [ns #{pfx}])))
              ;; (keyword "ns" <non-literal, no literal prefix>) — whole namespace
              (->> (re-seq #"\(keyword\s+\"([^\"]+)\"\s+(?!\"|\(str\s+\")" flat)
                   (map (fn [[_ ns]] [ns :all])))
              ;; (keyword :ns <non-literal>) — keyword namespace with dynamic key
              ;; Matches :ns followed by something that is NOT a literal string or keyword
              ;; Catches: (keyword :accessibility f)
              (->> (re-seq #"\(keyword\s+:([\w.\-]+)\s+(?![:\"])[a-z(]" flat)
                   (map (fn [[_ ns]] [ns :all])))
              ;; (keyword (str "ns/" <expr>)) — fully dynamic with string concat
              ;; Catches: (keyword (str "ptv.audit.status/" status))
              (->> (re-seq #"\(keyword\s+\(str\s+\"([^\"]+)/" flat)
                   (map (fn [[_ ns]] [ns :all])))))))

(defn extract-used-keys
  "Extract translation keys referenced in a ClojureScript source string."
  [content]
  (into #{}
        (concat
     ;; (tr :ns/key)
          (->> (re-seq #"\(tr\s+(:[^\s()\[\]\{\},]+)" content)
               (keep (fn [[_ s]] (when (str/includes? s "/") (read-string s)))))
     ;; :tr-key :ns/key
          (->> (re-seq #":tr-key\s+(:[^\s()\[\]\{\},]+)" content)
               (keep (fn [[_ s]] (when (str/includes? s "/") (read-string s)))))
     ;; (keyword :ns "literal")
          (->> (re-seq #"\(keyword\s+:([\w.\-]+)\s+\"([^\"]+)\"\)" content)
               (map (fn [[_ ns-part val-part]] (keyword ns-part val-part))))
     ;; (keyword :ns :key)
          (->> (re-seq #"\(keyword\s+:([\w.\-]+)\s+:([\w.\-]+)\)" content)
               (map (fn [[_ ns-part key-part]] (keyword ns-part key-part)))))))

(defn collect-used-keys
  "Scan sources, return map of keyword -> #{files}"
  []
  (reduce
    (fn [acc f]
      (let [content (slurp f)
            path (.getPath f)]
        (reduce (fn [a k] (update a k (fnil conj #{}) path))
                acc (extract-used-keys content))))
    {} (find-cljs-files)))

(defn extract-mentioned-keys
  "Every qualified keyword literal in a source string. A translation key can be
  referenced without sitting in (tr ...) position — it may be held in a lookup
  map and translated later:

    (def dialog-body-keys {:modified :ptv/sync-failed-body-ptv-modified ...})

  Treating any literal mention as a use keeps such keys out of the unused
  report. It errs towards under-reporting, which is the right direction for a
  tool that can delete translations."
  [content]
  (into #{}
        (comp (map second)
              (keep #(try (read-string %) (catch Exception _ nil)))
              (filter keyword?))
        (re-seq #"(?<![\w:/.-])(:[\w.<>?*+!=-]+/[\w.<>?*+!=-]+)" content)))

(defn collect-mentioned-keys
  "Union of every qualified keyword literal across the frontend sources."
  []
  (reduce (fn [acc f] (into acc (extract-mentioned-keys (slurp f))))
          #{} (find-cljs-files)))

(defn collect-dynamic-namespaces
  "Scan .cljs files for namespaces where keywords are constructed dynamically."
  []
  (reduce
    (fn [acc f]
      (merge-with merge-exempt-spec acc (extract-dynamic-namespaces (slurp f))))
    {} (find-cljs-files)))

;; === Phase 3 & 4: Analysis & Reporting ===

(defn analyze [defined used-map mentioned]
  (let [;; `used-map` is (tr ...) call sites and drives the undefined-reference
        ;; report; `mentioned` additionally covers keys referenced as plain data.
        used-set (into (set (keys used-map)) mentioned)
        exempt (into {} (filter #(exempt-key? (key %))) defined)
        analyzable (into {} (remove #(exempt-key? (key %))) defined)
        unused (into {} (remove #(contains? used-set (key %))) analyzable)
        defined-set (set (keys defined))
        undefined (into {}
                        (remove #(or (contains? defined-set (key %))
                                     (exempt-key? (key %))))
                        used-map)
        unused-by-ns (group-by (comp namespace key) unused)
        analyzable-by-ns (group-by (comp namespace key) analyzable)
        fully-unused (into {}
                           (filter (fn [[ns entries]]
                                     (= (count entries)
                                        (count (get analyzable-by-ns ns)))))
                           unused-by-ns)
        partially-unused (into {}
                               (remove #(contains? fully-unused (key %)))
                               unused-by-ns)]
    {:defined-count (count defined)
     :ns-count (count (distinct (map (comp namespace key) defined)))
     :used-count (count (filter #(contains? used-set (key %)) analyzable))
     :exempt-count (count exempt)
     :exempt-by-ns (into {} (map (fn [[ns es]] [ns (count es)]))
                         (group-by (comp namespace key) exempt))
     :unused-count (count unused)
     :unused unused
     :unused-by-ns unused-by-ns
     :analyzable-by-ns analyzable-by-ns
     :fully-unused fully-unused
     :partially-unused partially-unused
     :undefined undefined}))

(defn print-report [{:keys [defined-count ns-count used-count exempt-count
                            exempt-by-ns unused-count unused-by-ns
                            fully-unused partially-unused
                            analyzable-by-ns undefined]}]
  (println "=== LIPAS Unused Translation Keys ===")
  (println)
  (println "Summary:")
  (printf "  Reference language: %s%n" ref-lang)
  (printf "  Defined:  %,d keys across %d namespaces%n" defined-count ns-count)
  (printf "  Used:     %,d keys (static analysis)%n" used-count)
  (printf "  Exempt:   %,d keys (%d dynamic namespaces)%n"
          exempt-count (count exempt-by-ns))
  (printf "  Unused:   %,d keys across %d namespaces%n"
          unused-count (count unused-by-ns))
  (println)

  (println "Dynamic-exempt namespaces (skipped):")
  (doseq [[ns cnt] (sort-by key exempt-by-ns)]
    (printf "  %s (%d keys)%n" ns cnt))
  (println)

  (when (seq fully-unused)
    (println "Fully unused namespaces (candidates for file deletion):")
    (doseq [[ns entries] (sort-by key fully-unused)]
      (let [{:keys [file]} (val (first entries))]
        (printf "  %s (%d keys) -> %s%n" ns (count entries) file)))
    (println))

  (when (seq partially-unused)
    (println "Partially unused namespaces:")
    (doseq [[ns entries] (sort-by key partially-unused)]
      (let [total (count (get analyzable-by-ns ns))]
        (printf "  %s: %d unused of %d total%n" ns (count entries) total)
        (doseq [qk (sort (map key entries))]
          (printf "    %s%n" qk))))
    (println))

  (when (seq undefined)
    (println "Undefined references (tr calls with no matching key):")
    (doseq [[qk files] (sort-by key undefined)]
      (doseq [f (sort files)]
        (printf "  %s in %s%n" qk f)))
    (println)))

(defn edn-report [{:keys [defined-count ns-count used-count exempt-count
                          unused-count exempt-by-ns fully-unused
                          partially-unused undefined]}]
  (prn {:summary {:ref-lang ref-lang
                  :defined defined-count :namespaces ns-count
                  :used used-count :exempt exempt-count :unused unused-count}
        :exempt-namespaces exempt-by-ns
        :fully-unused (update-vals fully-unused #(sort (map key %)))
        :partially-unused (update-vals partially-unused #(sort (map key %)))
        :undefined (update-vals undefined #(vec (sort %)))}))

;; === Phase 5: Removal ===

(defn remove-key-from-edn
  "Remove a key-value pair from an EDN map string using rewrite-clj.
   Returns modified string, or original if key not found."
  [s k]
  (let [zloc (z/of-string s)]
    (loop [loc (z/down zloc)]
      (cond
        (nil? loc) s
        (and (z/sexpr-able? loc) (= (z/sexpr loc) k))
        (-> loc z/right z/remove z/remove z/root-string)
        :else
        (recur (some-> loc z/right z/right))))))

(defn remove-keys-from-edn
  "Remove multiple keys from an EDN map string."
  [s ks]
  (reduce remove-key-from-edn s ks))

(defn remove-from-top-level-keys
  "Remove a keyword from the top-level-keys vector in utils.cljc content."
  [content kw]
  (let [zloc (z/of-string content)
        vec-loc (-> zloc
                    (z/find-value z/next 'top-level-keys)
                    z/right)]
    (loop [loc (z/down vec-loc)]
      (cond
        (nil? loc) content
        (= (z/sexpr loc) kw)
        (-> loc z/remove z/root-string)
        :else
        (recur (z/right loc))))))

(defn perform-removal [{:keys [fully-unused partially-unused]}]
  (println)
  (when dry-run?
    (println "=== DRY RUN — No changes will be made ==="))
  (println)

  ;; Fully unused → delete files + remove from top-level-keys
  (doseq [[ns-name entries] (sort-by key fully-unused)]
    (let [{:keys [file]} (val (first entries))
          top-key (keyword ns-name)]
      (printf "Namespace %s (%d keys):%n" ns-name (count entries))
      (doseq [lang langs]
        (let [path (str i18n-base "/" lang "/" file)]
          (if dry-run?
            (printf "  Would delete %s%n" path)
            (when (.exists (io/file path))
              (io/delete-file (io/file path))
              (printf "  Deleted %s%n" path)))))
      (if dry-run?
        (printf "  Would remove %s from top-level-keys%n" top-key)
        (let [content (slurp utils-file)
              updated (remove-from-top-level-keys content top-key)]
          (spit utils-file updated)
          (printf "  Removed %s from top-level-keys%n" top-key)))))

  ;; Partially unused → remove individual keys
  (doseq [[ns-name entries] (sort-by key partially-unused)]
    (let [{:keys [file]} (val (first entries))
          ks (mapv (comp :edn-key val) entries)]
      (printf "Namespace %s (%d keys to remove):%n" ns-name (count ks))
      (doseq [lang langs]
        (let [path (str i18n-base "/" lang "/" file)]
          (if dry-run?
            (printf "  Would remove from %s: %s%n" path (str/join ", " (map str ks)))
            (when (.exists (io/file path))
              (let [content (slurp path)
                    updated (remove-keys-from-edn content ks)]
                (spit path updated)
                (printf "  Removed %d keys from %s%n" (count ks) path))))))))

  (println)
  (if dry-run?
    (println "Run with --remove (without --dry-run) to apply changes.")
    (println "Done. Review changes with: git diff")))

;; === Main ===

(let [_ (binding [*out* *err*]
          (println (format "Scanning translations (reference language: %s)..." ref-lang)))
      dynamic-nss (collect-dynamic-namespaces)
      _ (when (seq dynamic-nss)
          (binding [*out* *err*]
            (println "Auto-detected dynamic namespaces:"
                     (str/join ", " (for [[ns spec] (sort-by key dynamic-nss)]
                                      (if (= :all spec)
                                        ns
                                        (str ns "/" (str/join "|" (sort spec)) "*")))))))]
  (binding [*all-exempt-namespaces*
            (merge-with merge-exempt-spec hardcoded-exempt-namespaces dynamic-nss)]
    (let [defined (collect-defined-keys)
          used (collect-used-keys)
          mentioned (collect-mentioned-keys)
          results (analyze defined used mentioned)]
      (if edn-output?
        (edn-report results)
        (print-report results))
      (when (or remove? dry-run?)
        (if edn-output?
          (binding [*out* *err*] (perform-removal results))
          (perform-removal results))))))
