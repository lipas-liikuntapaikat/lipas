#!/usr/bin/env bb
;; scripts/unused_translations.bb — Find and optionally remove unused translation keys
;;
;; Usage:
;;   bb scripts/unused_translations.bb                     # Report unused keys
;;   bb scripts/unused_translations.bb --edn               # Report as EDN
;;   bb scripts/unused_translations.bb --remove --dry-run  # Preview removal
;;   bb scripts/unused_translations.bb --remove            # Remove unused keys

(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[rewrite-clj.zip :as z])

;; === Configuration ===

(def i18n-base "src/cljc/lipas/i18n")
(def cljs-src "src/cljs")
(def utils-file "src/cljc/lipas/i18n/utils.cljc")
(def langs ["fi" "se" "en"])
(def ref-lang "fi")

;; Namespaces where keys are known to be constructed dynamically at runtime.
;; All keys in these namespaces are considered "used" regardless of static analysis.
;; Additional dynamic namespaces are auto-detected by scanning for (keyword :ns expr)
;; patterns where the key part is not a literal.
;; Keep this set for namespaces that are hard to detect automatically (e.g. the key
;; construction happens in data, not in a (keyword ...) call visible to regex).
(def hardcoded-exempt-namespaces
  #{"pool-types"
    "pool-structures"})

;; Will be set! after scanning source files
(def ^:dynamic *all-exempt-namespaces* hardcoded-exempt-namespaces)

(defn exempt-ns?
  "Check if a namespace is exempt (exact match or sub-namespace of an exempt ns)"
  [ns-name]
  (some #(or (= ns-name %)
             (str/starts-with? ns-name (str % ".")))
        *all-exempt-namespaces*))

;; === CLI ===

(def cli-args (set *command-line-args*))
(def remove? (contains? cli-args "--remove"))
(def dry-run? (or (contains? cli-args "--dry-run")
                  (contains? cli-args "-n")))
(def edn-output? (contains? cli-args "--edn"))

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

(defn find-cljs-files []
  (->> (file-seq (io/file cljs-src))
       (filter #(str/ends-with? (.getName %) ".cljs"))
       vec))

(defn extract-dynamic-namespaces
  "Extract namespaces where keywords are constructed dynamically at runtime.
   These namespaces should be treated as fully used since we can't statically
   determine which keys will be generated.
   Matches patterns like:
     (keyword \"ns\" (str ...))  (keyword :ns some-var)  (keyword :ns (name ...))"
  [content]
  ;; Collapse whitespace so multiline (keyword ...) calls become single-line for regex
  (let [flat (str/replace content #"\s+" " ")]
    (into #{}
          (concat
            ;; (keyword "ns" <non-literal>) — string namespace with dynamic key
            ;; Catches: (keyword "lipas.sports-site" (str "name-localized-" (name l)))
            (->> (re-seq #"\(keyword\s+\"([^\"]+)\"\s+(?!\")" flat)
                 (map second))
            ;; (keyword :ns <non-literal>) — keyword namespace with dynamic key
            ;; Matches :ns followed by something that is NOT a literal string or keyword
            ;; Catches: (keyword :accessibility f)
            (->> (re-seq #"\(keyword\s+:([\w.\-]+)\s+(?![:\"])[a-z(]" flat)
                 (map second))
            ;; (keyword (str "ns/" <expr>)) — fully dynamic with string concat
            ;; Catches: (keyword (str "ptv.audit.status/" status))
            (->> (re-seq #"\(keyword\s+\(str\s+\"([^\"]+)/" flat)
                 (map second))))))

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
  "Scan .cljs files, return map of keyword -> #{files}"
  []
  (reduce
    (fn [acc f]
      (let [content (slurp f)
            path (.getPath f)]
        (reduce (fn [a k] (update a k (fnil conj #{}) path))
                acc (extract-used-keys content))))
    {} (find-cljs-files)))

(defn collect-dynamic-namespaces
  "Scan .cljs files for namespaces where keywords are constructed dynamically."
  []
  (reduce
    (fn [acc f]
      (let [content (slurp f)]
        (into acc (extract-dynamic-namespaces content))))
    #{} (find-cljs-files)))

;; === Phase 3 & 4: Analysis & Reporting ===

(defn analyze [defined used-map]
  (let [used-set (set (keys used-map))
        exempt (into {} (filter #(exempt-ns? (namespace (key %)))) defined)
        analyzable (into {} (remove #(exempt-ns? (namespace (key %)))) defined)
        unused (into {} (remove #(contains? used-set (key %))) analyzable)
        defined-set (set (keys defined))
        undefined (into {}
                        (remove #(or (contains? defined-set (key %))
                                     (exempt-ns? (namespace (key %)))))
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
  (prn {:summary {:defined defined-count :namespaces ns-count
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

(let [_ (binding [*out* *err*] (println "Scanning translations..."))
      dynamic-nss (collect-dynamic-namespaces)
      _ (when (seq dynamic-nss)
          (binding [*out* *err*]
            (println "Auto-detected dynamic namespaces:" (str/join ", " (sort dynamic-nss)))))]
  (binding [*all-exempt-namespaces* (into hardcoded-exempt-namespaces dynamic-nss)]
    (let [defined (collect-defined-keys)
          used (collect-used-keys)
          results (analyze defined used)]
      (if edn-output?
        (edn-report results)
        (print-report results))
      (when (or remove? dry-run?)
        (if edn-output?
          (binding [*out* *err*] (perform-removal results))
          (perform-removal results))))))
