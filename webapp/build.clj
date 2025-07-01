(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.edn :as edn]))

;; Read project info from deps.edn (using neil format if available)
(def deps-edn (edn/read-string (slurp "deps.edn")))
(def project (-> deps-edn :aliases :neil :project))

;; Project configuration matching your Leiningen setup
(def lib (or (:name project) 'lipas/lipas))
(def version (or (:version project) "0.1.0-SNAPSHOT"))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

;; Match Leiningen uberjar naming
(def uber-file "target/backend.jar")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; Main class for uberjar (matching Leiningen :main)
;; Main class for uberjar - no AOT needed for simple main functions
;; Main class for uberjar - minimal launcher namespace
;; No main class needed - we'll use clojure.main
;; (commented out to be explicit about this choice)
;; (def main-class 'clojure.main)

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src/clj" "src/cljc"]})
  (b/copy-dir {:src-dirs ["src/clj" "src/cljc" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (jar {})
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn uber [_]
  (println "Building uberjar...")
  (clean nil)

  ;; Copy source and resources
  (println "Copying source files...")
  (b/copy-dir {:src-dirs ["src/clj" "src/cljc" "resources"]
               :target-dir class-dir})

  ;; Create uberjar with clojure.main as default entry point
  (println "Creating uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'clojure.main})

  (println (str "Uberjar created: " uber-file))
  (println "Usage:")
  (println "  Web server: java -jar " uber-file " -m lipas.backend.system server")
  (println "  Worker:     java -jar " uber-file " -m lipas.backend.system worker"))

(defn deploy [opts]
  (jar opts)
  ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
   (merge {:installer :remote
           :artifact jar-file
           :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
          opts))
  opts)

