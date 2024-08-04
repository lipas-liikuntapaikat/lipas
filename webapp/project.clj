(defproject lipas "0.1.0-SNAPSHOT"
  :dependencies
  [;;; Common ;;;
   [org.clojure/clojure "1.11.4"]
   [camel-snake-kebab "0.4.0"]
   [hiposfer/geojson.specs "0.2.0"]
   [com.taoensso/timbre "4.10.0"]
   [com.cemerick/url "0.1.1"]
   [metosin/reitit "0.7.1"]
   [metosin/spec-tools "0.10.7"]
   [metosin/malli "0.16.2"]

   ;;; Frontend ;;;
   [thheller/shadow-cljs "2.28.11"]
   [org.clojure/clojurescript "1.11.132"]
   [reagent "1.2.0"]
   [re-frame "1.4.3"]
   [tongue "0.4.4"]
   [day8.re-frame/http-fx "0.2.4"]

   ;;; Backend ;;;
   [metosin/ring-http-response "0.9.0"]
   [cheshire "5.13.0"]
   [buddy "2.0.0"]
   [com.layerware/hugsql "0.4.8"]
   [org.postgresql/postgresql  "42.2.2"]
   [nrepl "1.2.0"]
   [hikari-cp "2.7.0"]
   [cc.qbits/spandex "0.8.2"]
   [integrant "0.10.0"]
   [migratus "1.0.6"]
   [environ "1.1.0"]
   [com.draines/postal "2.0.2"]
   [ring/ring-jetty-adapter "1.6.3"]
   [org.clojure/data.csv "0.1.4"]
   [dk.ative/docjure "1.12.0"]
   [tea-time "1.0.0"]
   [clj-http "3.13.0"]
   [factual/geo "3.0.1"]
   #_[com.amazonaws/aws-java-sdk-core "1.12.538"]
   [software.amazon.awssdk/regions "2.20.135"]
   [software.amazon.awssdk/auth "2.20.135"]
   [software.amazon.awssdk/s3 "2.20.135"]
   [org.apache.commons/commons-math3 "3.6.1"]
   [org.apache.commons/commons-lang3 "3.12.0"]
   [org.apache.commons/commons-math "2.2"]
   [org.locationtech.geowave/geowave-analytic-api "1.2.0"
    :exclusions
    [[org.slf4j/slf4j-log4j12]
     [log4j]
     [org.locationtech.geowave/geowave-adapter-raster]
     [org.locationtech.geowave/geowave-adapter-vector]]]]

  :plugins [[lein-ring "0.12.5"]
            [migratus-lein "0.7.0"]]

  :ring {:handler      lipas.dev/dev-handler
         :port         8091
         :auto-reload? true
         :reload-paths ["src/clj" "src/cljc"]}

  :min-lein-version "2.5.3"

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test/clj"]

  :test-selectors
  {:default     (complement :integration)
   :integration :integration}

  :clean-targets ^{:protect false}
  ["target"]

  :jvm-opts ["-Duser.timezone=UTC" "-Xmx4g"]

  :migratus
  {:store         :database
   :migration-dir "migrations"
   :db            {:dbtype   "postgresql"
                   :dbname   ~(get (System/getenv) "DB_NAME")
                   :host     ~(get (System/getenv) "DB_HOST")
                   :user     ~(get (System/getenv) "DB_USER")
                   :port     ~(get (System/getenv) "DB_PORT")
                   :password ~(get (System/getenv) "DB_PASSWORD")}}

  :profiles
  {:dev
   {:repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
    :source-paths ["src/cljs" "src/cljc" "src/js"]
    :dependencies
    [;;; Frontend ;;;
     [binaryage/devtools "1.0.7"]
     [day8.re-frame/re-frame-10x "1.9.9"]
     [cider/piggieback "0.5.3"]

     ;;; Backend ;;;
     [ring/ring-mock "0.3.2"]
     [org.clojure/test.check "0.9.0"]
     [etaoin "1.0.40"]]}
   :uberjar
   {:main         lipas.aot
    :aot          [lipas.aot]
    ;; Hack to speed up build with docker. Writing to mounted volumes
    ;; is slow (at least in OSX) so it's better to build using
    ;; non-mounted path and copy backend.jar to mounted location
    ;; afterwards.
    :target-path  "/tmp/%s"
    :compile-path "%s/classy-files"
    :uberjar-name "backend.jar"}})
