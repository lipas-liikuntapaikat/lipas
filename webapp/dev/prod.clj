(ns prod
  (:require [clojure.data.csv :as csv]
            [integrant.core :as ig]
            [lipas.backend.config :as config]
            [lipas.utils :as utils]
            [malli.core :as m]
            [lipas.wfs.mappings :as wfs-mappings]
            [lipas.wfs.core :as wfs]))

;; exploring
(comment
  (def system (ig/init (select-keys config/system-config [:lipas/search :lipas/db])))
  (require '[lipas.backend.core :as be])
  (def pops (be/get-populations (:lipas/search system) 2023))
  ;; => 2023 is the latest as of 9/2025
  )

;; Fixing invalid activities
(comment
  (require '[lipas.schema.sports-sites :as ss-schema])
  (require '[lipas.schema.sports-sites.location :as loc-schema])
  (require '[lipas.schema.common :as common-schema])
  (require '[lipas.data.types :as types])

  (def robot (be/get-user (:lipas/db system) "robot@lipas.fi"))

  (def type-codes [4402 4440 4412 4411 4401 4403 4404 4405 4406])
  (def type-codes (keys types/all))
  (def data (reduce (fn [m k]
                      (assoc m k (be/get-sports-sites-by-type-code (:lipas/db system) k)))
                    {}
                    type-codes))

  ;; ALL the invalid ones
  (def invalid2 (reduce (fn [coll k]
                      (let [sites (be/get-sports-sites-by-type-code (:lipas/db system) k)]
                        (into coll (filter (complement (fn [x] (m/validate ss-schema/sports-site x))))
                              sites)))
                    []
                    type-codes))

  (count invalid2)
  (map #(me/humanize (m/explain ss-schema/sports-site %)) invalid2)
  (map :event-date invalid2)
  (map :lipas-id invalid2)
  (map #(-> % :type :type-code) invalid2)
  (map #(-> % :location  :geometries :features) invalid2)

  (require '[lipas.utils :as utils])

  (doseq [m invalid2]
    (println "saving" (:lipas-id m))
    (be/save-sports-site! (:lipas/db system) (:lipas/search system) nil robot
                          (assoc m :status "incorrect-data")))


  (def invalid
    (->> data vals (mapcat identity)
         (filter (complement (fn [x] (m/validate ss-schema/sports-site x))))))

  (first invalid)
  (m/explain loc-schema/point-location (-> invalid first :location))
  (me/humanize (m/explain common-schema/coordinates (-> invalid first :location :geometries :features first :geometry :coordinates)))
  (->> invalid first :type :type-code)
  (->> invalid first :location :geometries)
  (count invalid)
  invalid
  (require '[malli.error :as me])
  (me/humanize (m/explain ss-schema/sports-site (first invalid)))
  (->> invalid first :lipas-id)

  (def site (be/get-sports-site (:lipas/db system) 614506))
  (def site (be/get-sports-site (:lipas/db system) 617582))
  (me/humanize (m/explain ss-schema/sports-site site))

  (be/index! (:lipas/search system) site)

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
              (bcore/index! (:lipas/search system) upd))))))))



;; WFS Legacy Layer Management - New Type Codes Deployment
;; Support for type codes: 1190, 1650, 2225, 2620, 3250, 4406, 4407, 4441, 6150
(comment
  (require '[lipas.wfs.core :as wfs])
  (require '[integrant.core :as ig])
  (require '[lipas.backend.config :as config])

  ;; Initialize system with database
  (def system (ig/init (select-keys config/system-config [:lipas/db])))
  (def db (:lipas/db system))

  ;; Step 1: Refresh WFS master table with all type codes (including new ones)
  ;; This will populate the wfs.master table with data from all active sports sites
  (wfs/refresh-wfs-master-table! db)

  ;; Step 2: Create materialized views for the new type codes
  ;; This creates the PostgreSQL materialized views that Geoserver will publish
  (wfs/create-legacy-mat-views! db)

  ;; Debug
  (require '[lipas.wfs.mappings :as wfs-mappings])
  (wfs/type-layer-mat-views (first (wfs-mappings/type-code->view-names 1650)))

  ;; Step 3: Publish new layers to Geoserver (one by one)
  ;; Point geometry types (single view each)
  (with-redefs [wfs/geoserver-config {:root-url "https://lipas.fi/geoserver/rest" #_"http://localhost:8888/geoserver/rest"
                                      :workspace-name "lipas"
                                      :datastore-name "lipas-wfs-v2"
                                      :default-http-opts
                                      {:basic-auth [(get (System/getenv) "GEOSERVER_ADMIN_USER")
                                                    (get (System/getenv) "GEOSERVER_ADMIN_PASSWORD")]
                                       :accept :json
                                       :as :json}}]
    (do
      (wfs/publish-layer "lipas_1190_pulkkamaki" "lipas_1190_pulkkamaki" "Point")
      (wfs/publish-layer "lipas_2225_sisaleikki_sisaaktiviteettipuisto" "lipas_2225_sisaleikki_sisaaktiviteettipuisto" "Point")
      (wfs/publish-layer "lipas_2620_biljardisali" "lipas_2620_biljardisali" "Point")
      (wfs/publish-layer "lipas_3250_vesiurheilukeskus" "lipas_3250_vesiurheilukeskus" "Point")
      (wfs/publish-layer "lipas_6150_ovaalirata" "lipas_6150_ovaalirata" "Point")
      *1
      ;; Polygon geometry types (single view)
      (wfs/publish-layer "lipas_1650_golfkentta" "lipas_1650_golfkentta" "Polygon")

      ;; LineString geometry types (dual views - 2D and 3D)
      (wfs/publish-layer "lipas_4406_monikayttoreitti" "lipas_4406_monikayttoreitti" "LineString")
      (wfs/publish-layer "lipas_4406_monikayttoreitti_3d" "lipas_4406_monikayttoreitti_3d" "LineString")
      (wfs/publish-layer "lipas_4407_rullahiihtorata" "lipas_4407_rullahiihtorata" "LineString")
      (wfs/publish-layer "lipas_4407_rullahiihtorata_3d" "lipas_4407_rullahiihtorata_3d" "LineString")
      (wfs/publish-layer "lipas_4441_koiravaljakkoreitti" "lipas_4441_koiravaljakkoreitti" "LineString")
      (wfs/publish-layer "lipas_4441_koiravaljakkoreitti_3d" "lipas_4441_koiravaljakkoreitti_3d" "LineString")))

  ;; Step 5: Update layer groups if needed
  ;; This rebuilds all legacy layer groups to include the new layers

  (with-redefs [wfs/geoserver-config {:root-url "https://lipas.fi/geoserver/rest" #_"http://localhost:8888/geoserver/rest"
                                      :workspace-name "lipas"
                                      :datastore-name "lipas-wfs-v2"
                                      :default-http-opts
                                      {:basic-auth [(get (System/getenv) "GEOSERVER_ADMIN_USER")
                                                    (get (System/getenv) "GEOSERVER_ADMIN_PASSWORD")]
                                       :accept :json
                                       :as :json}}]

    (wfs/rebuild-all-legacy-layer-groups))

  ;; Rollback capability (if needed)
  ;; Drop specific materialized views if issues arise
  ;; (wfs/drop-legacy-mat-view! db "lipas_1190_pulkkamaki")
  ;; (wfs/unpublish-layer "lipas_1190_pulkkamaki")
  )
