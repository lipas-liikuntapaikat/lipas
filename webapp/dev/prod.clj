(ns prod
  (:require [clojure.data.csv :as csv]
            [integrant.core :as ig]
            [lipas.backend.config :as config]
            [lipas.utils :as utils]
            [malli.core :as m]))


;; exploring
(comment
  (def system (ig/init (select-keys config/system-config [:lipas/search :lipas/db])))
  (require '[lipas.backend.core :as be])
  (def pops (be/get-populations (:lipas/search system) 2023))
  ;; => 2023 is the latest as of 9/2025
  )

;; Iisalmi manual updates
(comment
  (require '[clojure.data.csv :as csv])

  (def iisalmi-address-data (-> "lipas_raportti_iisalmi.csv"
                                slurp
                                csv/read-csv))

  (require '[lipas.backend.core :as bcore])
  (require '[integrant.core :as ig])
  (require '[lipas.backend.config :as config])
  (require '[lipas.utils :as utils])
  (require '[malli.core :as m])
  (require '[lipas.schema.sports-sites :as ss-schema])

  (def system (ig/init (select-keys config/system-config [:lipas/search :lipas/db])))

  (def robot (bcore/get-user (:lipas/db system) "robot@lipas.fi"))

  (doseq [[lipas-id nimi www phone-number email] (drop 1 iisalmi-address-data)]
    (let [lipas-id (parse-long lipas-id)]
      (println "Processing: " lipas-id)
      (when-let [site (bcore/get-sports-site2 (:lipas/search system) lipas-id)]
        (let [www (not-empty www)
              phone-number (not-empty phone-number)
              email (not-empty email)
              upd (cond-> site
                    www (assoc :www www)
                    email (assoc :email email)
                    phone-number (assoc :phone-number phone-number))
              orig (select-keys site [:www :email :phone-number])
              new (select-keys upd [:www :email :phone-number])]

          (if (= orig new)
            (println "NO CHANGES DETECTED FOR " lipas-id)
            (do
              (assert (m/validate ss-schema/sports-site upd))
              (println "Saving updates: " lipas-id)
              (bcore/upsert-sports-site!* (:lipas/db system) robot upd)
              (bcore/index! (:lipas/search system) upd)
              ))

          ))))

  )
