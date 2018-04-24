(defproject auth-service "0.1.0-SNAPSHOT"
  :description "compojure-api with token-based authentication using Buddy."

  :dependencies [[org.clojure/clojure        "1.8.0"]
                 [metosin/compojure-api      "1.1.11"]
                 [metosin/ring-http-response "0.9.0"]
                 [cheshire                   "5.8.0"]
                 [http-kit                   "2.2.0"]
                 [buddy                      "2.0.0"]
                 [org.clojure/java.jdbc      "0.7.5"]
                 [org.postgresql/postgresql  "42.2.2"]
                 [com.layerware/hugsql       "0.4.8"]
                 [environ                    "1.1.0"]
                 [clj-time                   "0.14.2"]
                 [mount                      "0.1.11"]
                 [com.taoensso/timbre        "4.10.0"]
                 [migratus                   "1.0.0"]
                 [com.fzakaria/slf4j-timbre  "0.3.7"]
                 [conman                     "0.7.4"]
                 [com.draines/postal         "2.0.2"]]

  :plugins      [[lein-environ    "1.1.0"]
                 [migratus-lein   "0.5.2"]
                 [funcool/codeina "0.5.0" :exclusions [org.clojure/clojure]]]

  :min-lein-version  "2.5.0"

  :resource-paths ["resources"]

  :migratus {:store         :database
             :migration-dir "migrations"
             :db            ~(get (System/getenv) "DATABASE_URL")}

  :uberjar-name "server.jar"

  :codeina {:sources ["src"]
            :reader :clojure}

  :profiles {:uberjar {:resource-paths ["swagger-ui"]
                       :aot :all}

             :dev        [{:dependencies [[ring/ring-mock "0.3.2"]]}]
             :test       {}
             :production {:ring {:open-browser? false
                                 :stacktraces?  false
                                 :auto-reload?  false}}}

  :test-selectors {:default (constantly true)
                   :wip     :wip})
