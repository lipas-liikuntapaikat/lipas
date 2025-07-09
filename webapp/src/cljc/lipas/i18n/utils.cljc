(ns lipas.i18n.utils
  (:require
   #?(:clj [clojure.java.io :as io])
   [clojure.edn :as edn]
   [clojure.string :as str]))

(def top-level-keys
  [:accessibility
   :actions
   :admin
   :analysis
   :building-materials
   :ceiling-structures
   :condensate-energy-targets
   :confirm
   :data-users
   :dimensions
   :disclaimer
   :dryer-duty-types
   :dryer-types
   :duration
   :error
   :filtering-methods
   :general
   :harrastuspassi
   :heat-pump-types
   :heat-recovery-types
   :heat-sources
   :help
   :home-page
   :ice
   :ice-comparison
   :ice-energy
   :ice-form
   :ice-resurfacer-fuels
   :ice-rinks
   :lipas.admin
   :lipas.building
   :lipas.bulk-operations
   :lipas.energy-consumption
   :lipas.energy-stats
   :lipas.floorball
   :lipas.ice-stadium.conditions
   :lipas.ice-stadium.envelope
   :lipas.ice-stadium.refrigeration
   :lipas.ice-stadium.rinks
   :lipas.ice-stadium.ventilation
   :lipas.location
   :lipas.org
   :lipas.org.ptv
   :lipas.properties
   :lipas.sports-site
   :lipas.swimming-pool.conditions
   :lipas.swimming-pool.energy-saving
   :lipas.swimming-pool.facilities
   :lipas.swimming-pool.pool
   :lipas.swimming-pool.pools
   :lipas.swimming-pool.saunas
   :lipas.swimming-pool.slides
   :lipas.swimming-pool.water-treatment
   :lipas.user
   :lipas.user.permissions
   :lipas.user.permissions.roles
   :lipas.visitors
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
   :month
   :newsletter
   :notifications
   :open-data
   :owner
   :partners
   :physical-units
   :pool-structures
   :pool-types
   :ptv
   :ptv.actions
   :ptv.audit
   :ptv.audit.status
   :ptv.integration
   :ptv.integration.default-settings
   :ptv.integration.description
   :ptv.integration.interval
   :ptv.integration.service
   :ptv.integration.service-channel
   :ptv.name-conflict
   :ptv.service
   :ptv.tools.ai
   :ptv.tools.ai.sports-sites-filter
   :ptv.tools.generate-services
   :ptv.wizard
   :refrigerant-solutions
   :refrigerants
   :register
   :reminders
   :reports
   :reset-password
   :restricted
   :retkikartta
   :sauna-types
   :search
   :size-categories
   :slide-structures
   :sport
   :sports-site.elevation-profile
   :stats
   :stats-metrics
   :status
   :statuses
   :supporting-structures
   :swim
   :swim-energy
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
