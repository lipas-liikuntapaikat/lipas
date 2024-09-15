(ns lipas.dev
  "Entrypoint for `lein ring` dev server."
  (:require
   [lipas.backend.system :as backend]
   [lipas.backend.config :as config]
   [ring.middleware.reload :refer [wrap-reload]]
   [lipas.backend.core :as core]
   [lipas.search-indexer :as si]
   [lipas.data.types :as types]))

(def system (backend/start-system! (dissoc config/system-config :lipas/server :lipas/nrepl)))
(def app (:app system))
(def dev-handler (-> #'app wrap-reload))

(comment

  ;; See `lipas.repl` for more automated solution

  (require '[lipas.backend.config :as config])
  (require '[lipas.backend.system :as system])
  (require '[lipas.backend.core :as core])

  (def dev-config (dissoc config/system-config :lipas/nrepl))
  (def s0 (system/start-system!))

  (def current-system (atom nil))

  (do
    (when @current-system
      (system/stop-system! @current-system))
    (reset! current-system (system/start-system! dev-config)))


  (require '[migratus.core :as migratus])

  (def migratus-config
    {:store         :database
     :migration-dir "migrations/"
     :db            {:dbtype   "postgresql"
                     :dbname   (get (System/getenv) "DB_NAME")
                     :host     (get (System/getenv) "DB_HOST")
                     :user     (get (System/getenv) "DB_USER")
                     :port     (get (System/getenv) "DB_PORT")
                     :password (get (System/getenv) "DB_PASSWORD")}})

  (migratus/create migratus-config "organization")
  (migratus/migrate migratus-config)

  (require '[lipas.data.types :as types])

  (require '[lipas.search-indexer :as si])
  (si/main
   nil
   (:lipas/db @current-system)
   (:lipas/search @current-system)
   "search")


  )


(comment
  (require '[lipas.integration.ptv.core :as ptv])
  (ptv/get-eligible-sites (:search @current-system) {:city-codes [889] :owners ["city" "city-main-owner"]})

  (def r *1)
  r
  (-> r :body :hits :hits (->> (map (comp :owner :_source))))

  (require '[lipas.backend.core :as core])


  (core/get-sports-site (:db @current-system) 89913)
  {:properties {:area-m2 1539, :surface-material []},
      :email "palaute@utajarvi.fi",
      :envelope
      {:insulated-ceiling? true,
       :insulated-exterior? false,
       :low-emissivity-coating? false},
      :phone-number "+358858755700",
      :building
      {:total-volume-m3 17700,
       :seating-capacity 250,
       :total-ice-area-m2 1539,
       :total-surface-area-m2 2457,
       :total-ice-surface-area-m2 1539},
      :ventilation
      {:dryer-type "munters",
       :heat-pump-type "none",
       :dryer-duty-type "automatic",
       :heat-recovery-type "thermal-wheel",
       :heat-recovery-efficiency 75},
      :admin "city-technical-services",
      :www "https://www.utajarvi.fi",
      :name "Utajärven jäähalli",
      :construction-year 1997,
      :type {:type-code 2520, :size-category "small"},
      :lipas-id 89913,
      :renovation-years [2014],
      :conditions
      {:open-months 6,
       :stand-temperature-c 7,
       :ice-average-thickness-mm 40,
       :air-humidity-min 60,
       :air-humidity-max 90,
       :maintenance-water-temperature-c 45,
       :ice-surface-temperature-c -4,
       :weekly-maintenances 12,
       :skating-area-temperature-c 7,
       :daily-open-hours 11,
       :average-water-consumption-l 700},
      :status "active",
      :event-date "2019-04-05T13:54:19.910Z",
      :refrigeration
      {:original? true,
       :refrigerant "R404A",
       :refrigerant-solution "freezium"},
      :location
      {:city {:city-code 889},
       :address "Laitilantie 5",
       :geometries
       {:type "FeatureCollection",
        :features
        [{:type "Feature",
          :geometry
          {:type "Point",
           :coordinates [26.4131256689191 64.7631112249574]}}]},
       :postal-code "91600",
       :postal-office "Utajärvi"},
      :owner "city",
      :hall-id "91600UT1"}
  )
