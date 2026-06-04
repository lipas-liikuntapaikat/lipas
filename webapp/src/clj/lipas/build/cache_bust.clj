(ns lipas.build.cache-bust
  "shadow-cljs build hook that renders resources/public/index.html from a
   template, substituting the actual hashed bundle filename produced by the
   build. In dev builds the filename is `app.js`; in release builds with
   `:module-hash-names` it is e.g. `app.A1B2C3D4.js`."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private template-path "resources/public/index.html.tmpl")
(def ^:private output-path   "resources/public/index.html")

(defn- resolve-output-name
  "Reads the post-flush manifest.edn for this build and returns the resolved
   :output-name of the :app module (post-hash-names in release builds)."
  [state]
  (let [output-dir    (or (get-in state [:shadow.build/config :output-dir])
                          "resources/public/js/compiled")
        manifest-file (io/file output-dir "manifest.edn")]
    (when (.exists manifest-file)
      (let [manifest (edn/read-string (slurp manifest-file))
            app-mod  (first (filter #(= :app (:module-id %)) manifest))]
        (:output-name app-mod)))))

(defn render-index
  {:shadow.build/stage :flush}
  [state]
  (let [output-name (resolve-output-name state)
        tmpl-file   (io/file template-path)]
    (cond
      (not output-name)
      (println "[cache-bust] WARN: could not resolve :app output-name; skipping index.html render")

      (not (.exists tmpl-file))
      (println (str "[cache-bust] WARN: template not found at " template-path "; skipping"))

      :else
      (let [rendered (str/replace (slurp tmpl-file) "{{APP_JS}}" output-name)]
        (spit output-path rendered)
        (println (str "[cache-bust] Wrote " output-path " referencing " output-name)))))
  state)
