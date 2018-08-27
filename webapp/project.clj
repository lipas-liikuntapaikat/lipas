(defproject lipas "0.1.0-SNAPSHOT"
  :dependencies [
                 ;;; Common ;;;
                 [org.clojure/clojure "1.9.0"]
                 [camel-snake-kebab "0.4.0"]
                 [etaoin "0.2.8-SNAPSHOT"]
                 [hiposfer/geojson.specs "0.2.0"]
                 [metosin/spec-tools "0.7.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.cemerick/url "0.1.1"]

                 ;;; Frontend ;;;
                 [org.clojure/clojurescript "1.10.312"]
                 [cljsjs/react "16.4.0-0"]
                 [cljsjs/react-dom "16.4.0-0"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.5"]
                 [secretary "1.2.3"]
                 [ns-tracker "0.3.0"]
                 [cljsjs/material-ui "1.4.0-0"]
                 [tongue "0.2.4"]
                 [day8.re-frame/http-fx "0.1.6"]
                 [cljsjs/filesaverjs "1.3.3-0"]
                 [testdouble/clojurescript.csv "0.3.0"]
                 [cljsjs/google-analytics "2015.04.13-0"]
                 [district0x.re-frame/google-analytics-fx "1.0.0"]
                 [cljsjs/babel-polyfill "6.20.0-2"]
                 [cljsjs/date-fns "1.29.0-0"]
                 [cljsjs/recharts "1.1.0-3"]

                 ;;; Backend ;;;
                 [metosin/reitit "0.2.0-SNAPSHOT"]
                 [metosin/ring-http-response "0.9.0"]
                 [cheshire "5.8.0"]
                 [buddy "2.0.0"]
                 [com.layerware/hugsql "0.4.8"]
                 [org.postgresql/postgresql  "42.2.2"]
                 [cc.qbits/spandex "0.6.2"]
                 [integrant "0.6.3"]
                 [migratus "1.0.6"]
                 [environ "1.1.0"]
                 [com.draines/postal "2.0.2"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [org.clojure/data.csv "0.1.4"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.5"]
            [lein-ring "0.12.4"]
            [migratus-lein "0.5.7"]]

  :ring {:handler      lipas.dev/dev-handler
         :port         8091
         :auto-reload? true
         :reload-paths ["src/clj" "src/cljc"]}

  :min-lein-version "2.5.3"

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test/clj"]

  :test-selectors {:default     (complement :integration)
                   :integration :integration}

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  :jvm-opts ["-Duser.timezone=UTC"]

  :migratus {:store         :database
             :migration-dir "migrations"
             :db            {:dbtype   "postgresql"
                             :dbname   ~(get (System/getenv) "DB_NAME")
                             :host     ~(get (System/getenv) "DB_HOST")
                             :user     ~(get (System/getenv) "DB_USER")
                             :port     ~(get (System/getenv) "DB_PORT")
                             :password ~(get (System/getenv) "DB_PASSWORD")}}

  :profiles
  {:dev
   {:dependencies [;;; Frontend ;;;
                   [binaryage/devtools "0.9.10"]
                   [day8.re-frame/re-frame-10x "0.3.3"]
                   [figwheel-sidecar "0.5.16"]
                   [cider/piggieback "0.3.6"]

                    ;;; Backend ;;;
                   [ring/ring-mock "0.3.2"]
                   [org.clojure/test.check "0.9.0"]]

    :plugins [[lein-figwheel "0.5.16"]
              [lein-doo "0.1.8"]]}
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
     :compiler     {:main                 lipas.ui.core
                    :npm-deps             false
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload
                                           day8.re-frame-10x.preload]
                    :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true}
                    :external-config      {:devtools/config {:features-to-install :all}}
                    }}

    {:id           "min"
     :source-paths ["src/cljs" "src/cljc"]
     :compiler     {:main            lipas.ui.core
                    :npm-deps        false
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}

    {:id           "test"
     :source-paths ["src/cljs" "src/cljc" "test/cljs"]
     :compiler     {:main          lipas.ui.runner
                    :output-to     "resources/public/js/compiled/test.js"
                    :output-dir    "resources/public/js/compiled/test/out"
                    :optimizations :none}}
    ]}

  )
