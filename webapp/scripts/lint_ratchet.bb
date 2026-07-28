#!/usr/bin/env bb
;; Warning ratchet for clj-kondo.
;;
;; The repository carries ~480 pre-existing clj-kondo warnings spread over 177
;; of 409 files. Gating changed files at --fail-level warning would therefore
;; fail roughly two PRs in five for warnings nobody in that PR introduced, and
;; the gate would be switched off within a week.
;;
;; So instead of an absolute threshold this compares per-file warning counts
;; against the merge base and fails only where a file got *worse*. Files that
;; are new at HEAD have a base count of zero, so they must be warning-clean.
;; Untouched legacy warnings never fail the build, but they can only ever go
;; down.
;;
;; Run from webapp/:  bb lint-ratchet     (or: bb lint-ratchet <base-ref>)

(require '[babashka.fs :as fs]
         '[babashka.process :as process]
         '[clojure.string :as str])

(def project-dir (str (fs/cwd)))

(defn git
  "Run git in dir, returning trimmed stdout (empty string on failure)."
  [dir & args]
  (let [{:keys [exit out]} (apply process/shell
                                  {:dir dir :out :string :err :string :continue true}
                                  "git" args)]
    (if (zero? exit) (str/trim out) "")))

(defn repo-root []
  (git project-dir "rev-parse" "--show-toplevel"))

(defn merge-base
  "Merge base of HEAD and base-ref, or nil when base-ref is unreachable
   (shallow clone, missing remote)."
  [base-ref]
  (let [mb (git project-dir "merge-base" "HEAD" base-ref)]
    (when-not (str/blank? mb) mb)))

(def source-re (re-pattern "\\.(clj[scdx]?|bb|edn)$"))

(defn changed-sources
  "Clojure files under webapp/ that differ from base, as webapp-relative paths.
   Deleted files are excluded — there is nothing left to lint."
  [base]
  (->> (str/split-lines (git project-dir "diff" "--name-only" "--diff-filter=d" base))
       (remove str/blank?)
       (filter (fn [p] (str/starts-with? p "webapp/")))
       (map (fn [p] (subs p (count "webapp/"))))
       (filter (fn [p] (re-find source-re p)))
       distinct
       sort))

(def lint-paths ["src" "test" "dev" "build.clj" "scripts"])

(defn warning-counts
  "Map of webapp-relative path -> clj-kondo warning count for a whole tree.
   Files with no warnings are simply absent, and callers treat that as 0.

   Two details here are load-bearing, and getting either wrong makes the
   ratchet report regressions that do not exist:

   - The WHOLE tree is linted, not just the changed files. Several of
     clj-kondo's checks are cross-namespace (it infers the return type of a
     function defined elsewhere), and those findings only appear when the
     defining namespace is analysed in the same run.

   - --cache false. Otherwise a warm .clj-kondo/.cache supplies cross-namespace
     information on one side while the freshly created base worktree, which has
     no cache, goes without — so the same code scores differently on each side.
     That asymmetry produced a phantom `gis_test.clj (0 -> 1)` regression on a
     whitespace-only commit. Disabling the cache on both sides costs nothing
     here, because a whole-tree run derives the same information anyway."
  [dir]
  (let [present (filter (fn [p] (fs/exists? (fs/file dir p))) lint-paths)]
    (if (empty? present)
      {}
      (let [{:keys [out]} (apply process/shell
                                 {:dir dir :out :string :err :string :continue true}
                                 "clj-kondo" "--cache" "false" "--lint" present)]
        (->> (str/split-lines out)
             (keep (fn [line]
                     (when-let [[_ path] (re-find (re-pattern "^(.+?):\\d+:\\d+: warning: ") line)]
                       path)))
             frequencies)))))

(defn warnings-for
  "The actual warning lines for one file at HEAD, for the failure report."
  [file]
  (let [{:keys [out]} (process/shell {:dir project-dir :out :string :err :string
                                      :continue true}
                                     "clj-kondo" "--lint" file)]
    (->> (str/split-lines out)
         (filter (fn [l] (str/includes? l ": warning: "))))))

(defn -main [& args]
  (let [base-ref (or (first args) (System/getenv "LINT_BASE") "origin/master")
        base     (merge-base base-ref)]
    (when-not base
      (println (str "⚠️  Cannot resolve " base-ref
                    " — skipping the warning ratchet."))
      (println "   In CI this means the checkout is shallow; use fetch-depth: 0.")
      (System/exit 0))

    (let [changed (changed-sources base)]
      (when (empty? changed)
        (println "No changed Clojure files — ratchet has nothing to compare.")
        (System/exit 0))

      (println (str "Comparing " (count changed) " changed file(s) against "
                    (subs base 0 (min 8 (count base))) " (" base-ref ")"))

      (let [head-counts (warning-counts project-dir)
            worktree    (str (fs/create-temp-dir {:prefix "lint-ratchet-"}))
            wt-path     (str worktree "/tree")
            ;; Compute the exit code inside try/finally and exit only after
            ;; cleanup — System/exit halts the JVM immediately and would skip
            ;; the finally, leaking a git worktree on every run.
            code
            (try
              (let [{:keys [exit err]} (process/shell
                                         {:dir (repo-root) :out :string :err :string
                                          :continue true}
                                         "git" "worktree" "add" "--detach" "--quiet"
                                         wt-path base)]
                (if-not (zero? exit)
                  (do (println "❌ Could not create a worktree for the base revision:")
                      (println err)
                      1)
                  (let [base-counts (warning-counts (str wt-path "/webapp"))
                        regressions (for [f changed
                                          :let [before (get base-counts f 0)
                                                after  (get head-counts f 0)]
                                          :when (> after before)]
                                      {:file f :before before :after after})]
                    (if (empty? regressions)
                      (do (println (str "✅ No new clj-kondo warnings in "
                                        (count changed) " changed file(s)."))
                          0)
                      (do
                        (println)
                        (println "❌ New clj-kondo warnings introduced:")
                        (println)
                        (doseq [{:keys [file before after]} regressions]
                          (println (str "  " file "  (" before " → " after ")"))
                          (doseq [w (warnings-for file)]
                            (println (str "      " w)))
                          (println))
                        (println "Pre-existing warnings elsewhere are tolerated; these files got worse.")
                        (println "Fix them, or run `bb clean-ns <file>` for unused/unsorted requires.")
                        1)))))
              (finally
                (process/shell {:dir (repo-root) :out :string :err :string :continue true}
                               "git" "worktree" "remove" "--force" wt-path)
                (fs/delete-tree worktree)))]
        (System/exit code)))))

(apply -main *command-line-args*)
