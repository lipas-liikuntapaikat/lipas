(ns lipas.build.cache-bust
  "shadow-cljs build hook that renders resources/public/index.html from a
   template, substituting the actual hashed bundle filename produced by the
   build. In dev builds the filename is `app.js`; in release builds with
   `:module-hash-names` it is e.g. `app.A1B2C3D4.js`.

   With code splitting only the base :app module goes in the script tag —
   shadow.loader fetches the lazy modules (hashed names embedded in the
   base module) on demand. The :geo and :map modules additionally get
   `<link rel=prefetch>` hints: they're by far the likeliest next fetch
   (the map is the main view) and prefetching warms the cache without
   delaying first paint."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private template-path "resources/public/index.html.tmpl")
(def ^:private output-path   "resources/public/index.html")

(def ^:private prefetch-modules #{:geo :map})

(defn- read-manifest
  [state]
  (let [output-dir    (or (get-in state [:shadow.build/config :output-dir])
                          "resources/public/js/compiled")
        manifest-file (io/file output-dir "manifest.edn")]
    (when (.exists manifest-file)
      (edn/read-string (slurp manifest-file)))))

(defn- prefetch-links
  [manifest]
  (->> manifest
       (filter #(prefetch-modules (:module-id %)))
       (map #(str "<link rel=\"prefetch\" as=\"script\" href=\"/js/compiled/"
                  (:output-name %) "\">"))
       (str/join "\n    ")))

(defn render-index
  {:shadow.build/stage :flush}
  [state]
  (let [manifest    (read-manifest state)
        output-name (:output-name (first (filter #(= :app (:module-id %)) manifest)))
        tmpl-file   (io/file template-path)]
    (cond
      (not output-name)
      (println "[cache-bust] WARN: could not resolve :app output-name; skipping index.html render")

      (not (.exists tmpl-file))
      (println (str "[cache-bust] WARN: template not found at " template-path "; skipping"))

      :else
      (let [rendered (-> (slurp tmpl-file)
                         (str/replace "{{APP_JS}}" output-name)
                         (str/replace "{{PREFETCH_LINKS}}" (prefetch-links manifest)))]
        (spit output-path rendered)
        (println (str "[cache-bust] Wrote " output-path " referencing " output-name)))))
  state)
