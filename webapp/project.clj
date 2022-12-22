x(defproject lipas "0.1.0-SNAPSHOT"
  :dependencies
  [;;; Common ;;;
   [org.clojure/clojure "1.11.1"]
   [camel-snake-kebab "0.4.0"]
   [hiposfer/geojson.specs "0.2.0"]
   [com.taoensso/timbre "4.10.0"]
   [com.cemerick/url "0.1.1"]
   [metosin/reitit "0.5.18"]

   ;;; Frontend ;;;
   [org.clojure/clojurescript "1.11.60"]
   [cljsjs/create-react-class "15.6.0-2"]
   [reagent "0.8.1" :exclusions [[cljsjs/react]
                                 [cljsjs/react-dom]
                                 [cljsjs/create-react-class]
                                 [cljsjs/react-dom-server]]]
   [re-frame "0.10.6"]
   ;; [ns-tracker "0.3.0"]
   [tongue "0.2.6"]
   [day8.re-frame/http-fx "0.1.6"]
   [cljsjs/google-analytics "2015.04.13-0"]
   [district0x.re-frame/google-analytics-fx "1.0.0"]
   [cljsjs/babel-polyfill "6.20.0-2"]

   ;;; Backend ;;;
   [metosin/ring-http-response "0.9.0"]
   [cheshire "5.8.0"]
   [buddy "2.0.0"]
   [com.layerware/hugsql "0.4.8"]
   [org.postgresql/postgresql  "42.2.2"]
   [hikari-cp "2.7.0"]
   [cc.qbits/spandex "0.6.4"]
   [integrant "0.6.3"]
   [migratus "1.0.6"]
   [environ "1.1.0"]
   [com.draines/postal "2.0.2"]
   [ring/ring-jetty-adapter "1.6.3"]
   [org.clojure/data.csv "0.1.4"]
   [dk.ative/docjure "1.12.0"]
   [tea-time "1.0.0"]
   [etaoin "0.2.8-SNAPSHOT"]
   [clj-http "3.9.1"]
   [factual/geo "3.0.1"]
   [org.apache.commons/commons-math3 "3.6.1"]
   [org.apache.commons/commons-lang3 "3.12.0"]
   [org.apache.commons/commons-math "2.2"]
   [org.locationtech.geowave/geowave-analytic-api "1.2.0"
    :exclusions
    [[org.slf4j/slf4j-log4j12]
     [log4j]
     [org.locationtech.geowave/geowave-adapter-raster]
     [org.locationtech.geowave/geowave-adapter-vector]]]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.7"]
            [lein-ring "0.12.5"]
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
  ["resources/public/js/compiled" "target" "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

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
    :dependencies
    [;;; Frontend ;;;
     [binaryage/devtools "0.9.10"]
     [day8.re-frame/re-frame-10x "0.3.7-react16"]
     [re-frisk "0.5.3"]
     [figwheel-sidecar "0.5.18"]
     [cider/piggieback "0.4.0"]

     ;;; Backend ;;;
     [ring/ring-mock "0.3.2"]
     [org.clojure/test.check "0.9.0"]
     [metasoarous/oz "2.0.0-alpha5"]]

    :plugins [[lein-figwheel "0.5.20"]
              [lein-doo "0.1.10"]]}
   :uberjar
   {:main         lipas.aot
    :aot          [lipas.aot]
    ;; Hack to speed up build with docker. Writing to mounted volumes
    ;; is slow (at least in OSX) so it's better to build using
    ;; non-mounted path and copy backend.jar to mounted location
    ;; afterwards.
    :target-path  "/tmp/%s"
    :compile-path "%s/classy-files"
    :uberjar-name "backend.jar"}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs" "src/cljc"]
     :figwheel     {:on-jsload "lipas.ui.core/mount-root"}
     :compiler
     {:main                 lipas.ui.core
      :npm-deps             false
      :infer-externs        true
      :foreign-libs
      [{:file           "dist/index.bundle.js"
        :provides       ["ol" "zipcelx" "filesaver" "react" "react-dom"
                         "react-dom/server" "mui" "cljsjs.react" "cljsjs.react.dom"
                         "recharts" "proj4" "cljsjs.react-autosuggest"
                         "turf" "react-select" "rcslider" "shp"]
        :global-exports {ol        ol        zipcelx            zipcelx
                         filesaver filesaver react              React
                         react-dom ReactDOM  "react-dom/server" ReactDOMServer
                         mui       mui       proj4              proj4
                         recharts  recharts  react-autosuggest  "cljsjs.react-autosuggest"
                         turf      turf      react-select       ReactSelect
                         rcslider  rcslider  shp                shp }}]
      :output-to            "resources/public/js/compiled/app.js"
      :output-dir           "resources/public/js/compiled/out"
      :asset-path           "/js/compiled/out"
      :source-map-timestamp true
      :preloads             [devtools.preload day8.re-frame-10x.preload
                                        ;Re-frisk.preload
                             ]
      :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true}
      :external-config      {:devtools/config {:features-to-install :all}}}}

    {:id           "min"
     :source-paths ["src/cljs" "src/cljc"]
     :compiler
     {:main            lipas.ui.core
      :verbose         false
      :npm-deps        false
      :infer-externs   true
      :externs         ["src/js/ol_externs.js"]
      :parallel-build  true
      :foreign-libs
      [{:file           "dist/index.bundle.js"
        :provides       ["ol" "zipcelx" "filesaver" "react" "react-dom"
                         "react-dom/server" "mui" "cljsjs.react" "cljsjs.react.dom"
                         "recharts" "proj4" "cljsjs.react-autosuggest"
                         "turf" "react-select" "shp"]
        :global-exports {ol        ol        zipcelx            zipcelx
                         filesaver filesaver react              React
                         react-dom ReactDOM  "react-dom/server" ReactDOMServer
                         mui       mui       proj4              proj4
                         recharts  recharts  react-autosuggest  "cljsjs.react-autosuggest"
                         turf      turf      react-select       ReactSelect
                         shp       shp}}]
      :output-to       "resources/public/js/compiled/app.js"
      :optimizations   :advanced
      :closure-defines {goog.DEBUG false}
      :pretty-print    false}}

    {:id           "test"
     :source-paths ["src/cljs" "src/cljc" "test/cljs"]
     :compiler
     {:main          lipas.ui.runner
      :output-to     "resources/public/js/compiled/test.js"
      :output-dir    "resources/public/js/compiled/test/out"
      :optimizations :none}}]})
