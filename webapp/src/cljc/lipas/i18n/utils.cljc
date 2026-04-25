(ns ^:dev/always lipas.i18n.utils
  (:require
   #?(:clj [clojure.java.io :as io])
   [clojure.edn :as edn]
   [clojure.string :as str]))

(def top-level-keys
  [:accessibility
   :actions
   :analysis
   :confirm
   :data-users
   :dimensions
   :disclaimer
   :duration
   :error
   :general
   :harrastuspassi
   :help
   :home-page
   :ice
   :lipas.admin
   :lipas.bulk-operations
   :lipas.floorball
   :lipas.ice-stadium.rinks
   :lipas.location
   :lipas.org
   :lipas.org.ptv
   :lipas.properties
   :lipas.sports-site
   :lipas.swimming-pool.facilities
   :lipas.swimming-pool.pool
   :lipas.swimming-pool.pools
   :lipas.swimming-pool.slides
   :lipas.user
   :lipas.user.permissions
   :lipas.user.permissions.roles
   :login
   :loi
   :map
   :map.address-search
   :map.basemap
   :map.demographics
   :map.import
   :map.overlay
   :map.resolve-address
   :map.tools
   :map.tools.simplify
   :menu
   :newsletter
   :notifications
   :open-data
   :partners
   :physical-units
   :pool-structures
   :pool-types
   :ptv
   :ptv.actions
   :ptv.audit
   :ptv.audit.status
   :ptv.drift
   :ptv.name-conflict
   :ptv.service
   :ptv.tools.ai
   :ptv.tools.generate-services
   :ptv.wizard
   :register
   :reminders
   :reports
   :reset-password
   :retkikartta
   :search
   :sport
   :sports-site.elevation-profile
   :stats
   :stats-metrics
   :status
   :swim
   :time
   :type
   :units
   :user
   :utp])

(defn safe-filename
  "Converts a keyword name to a safe filename by replacing dots with underscores"
  [filename]
  (str/replace filename "." "_"))

#?(:clj
   (defmacro deftranslations
     "Defines a translations map at compile time by loading all EDN files"
     [lang]
     (let [translations-map
           (reduce
            (fn [m kw]
              (let [safe-name (safe-filename (name kw))
                    path (str "lipas/i18n/" lang "/" safe-name ".edn")
                    resource (io/resource path)]
                (if resource
                  (let [content (-> resource slurp edn/read-string)]
                    (assoc m kw content))
                  (do
                    (println "WARNING: Missing translation file:" path)
                    m))))
            {}
            top-level-keys)]
       `(def ~'translations '~translations-map))))
