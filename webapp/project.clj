(defproject lipas "0.1.0-SNAPSHOT"
  :dependencies [
                 ;;; Common ;;;
                 [org.clojure/clojure "1.9.0"]
                 [camel-snake-kebab "0.4.0"]
                 [com.cemerick/url "0.1.1"]

                 ;;; Frontend ;;;
                 [org.clojure/clojurescript "1.9.908"]
                 [cljsjs/react "16.3.0-1"]
                 [cljsjs/react-dom "16.3.0-1"]
                 [reagent "0.8.0"]
                 [re-frame "0.10.5"]
                 [secretary "1.2.3"]
                 [ns-tracker "0.3.0"]
                 [cljsjs/material-ui "1.0.0-beta.40-0"]
                 [tongue "0.2.4"]
                 [day8.re-frame/http-fx "0.1.6"]

                 ;;; Backend ;;;
                 [org.clojure/test.check "0.9.0"]
                 [metosin/compojure-api "2.0.0-alpha20"]
                 [metosin/jsonista "0.2.0"]
                 [buddy "2.0.0"]
                 [com.layerware/hugsql "0.4.8"]
                 [org.postgresql/postgresql  "42.2.2"]
                 [cc.qbits/spandex "0.6.2"]
                 [integrant "0.6.3"]
                 [migratus "1.0.6"]
                 [environ "1.1.0"]
                 [com.fzakaria/slf4j-timbre "0.3.7"]
                 [ring "1.4.0"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.5"]
            [lein-ring "0.12.4"]
            [migratus-lein "0.5.7"]]

  :ring {:handler lipas.dev/dev-handler
         :port 8091
         :auto-reload? true
         :reload-paths ["src/clj" "src/cljc"]}

  :min-lein-version "2.5.3"

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :migratus {:store         :database
             :migration-dir "migrations"
             :db            {:dbtype "postgresql"
                             :dbname ~(get (System/getenv) "DB_NAME")
                             :host ~(get (System/getenv) "DB_HOST")
                             :user ~(get (System/getenv) "DB_USER")
                             :port ~(get (System/getenv) "DB_PORT")
                             :password ~(get (System/getenv) "DB_PASSWORD")}}

  :profiles
  {:dev
   {:dependencies
                   [;;; Frontend ;;;
                   [binaryage/devtools "0.9.4"]
                   [day8.re-frame/re-frame-10x "0.2.0"]
                   [figwheel-sidecar "0.5.13"]
                   [com.cemerick/piggieback "0.2.2"]

                   ;;; Backend ;;;
                   [ring/ring-mock "0.3.2"]]

    :plugins      [[lein-figwheel "0.5.13"]
                   [lein-doo "0.1.8"]]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs" "src/cljc"]
     :figwheel     {:on-jsload "lipas.ui.core/mount-root"}
     :compiler     {:main                 lipas.ui.core
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
