(ns lipas.migrate-data
  (:require [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [environ.core :refer [env]]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.system :as backend]
            [lipas.data.admins :as admins]
            [lipas.data.ice-stadiums :as ice-stadiums]
            [lipas.data.owners :as owners]
            [lipas.schema.core]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

;; Data migrations from ice-stadium/swimming-pool portals (VTT) and
;; old Lipas to new LIPAS.

;; Lipas-data is fetched from JSON REST-Api and portal data is
;; acquired from .csv dumps placed into a downloadable location.

;; This file can be removed when we're certain that we don't need to read
;; data again from legacy systems (portals, 'old' Lipas).

(def fixed-mappings
 ;; Halli-ID  Lipas-ID
  {"47400II1" 76276    ; Iitin jäähalli
   "43500KA1" 518758   ; Karstula-Areena
   "29900ME1" 520708   ; Merikarvian jäähalli
   "27430PA1" 508155   ; Eura, Panelian jäähalli
   "21450TA1" 89812    ; Tiileriareena
   "62100LA1" 89847    ; Lapuan Jäähalli / Lapuan Autohuolto Areena
   "21100NA2" 508187   ; Naantali, Aurinko Areena
   "03100NU2" 527008   ; Vihdin Osuuspankki-halli
   "01800KL2" 528685   ; Klaukkalan harjoitusjäähalli
   "60100SE1" 520756   ; Seinäjoen jäähalli
   "02230ES2" 526368   ; Espoo, Matinkylän harjoitushalli
   "60100SE2" 89850    ; Seinäjoen vanha jäähalli / Jouppilanvuoren jäähalli
   "66100MA1" 76962    ; Maalahden Jäähalli Oy
   "02720ES1" 526370   ; Espoo. Laaksolahden harjoitusjäähalli
   "65350VA2" 508200   ; Vaasan harjoitusjäähalli 3 ???

   "UU007000" 505753   ; Päiväkummun kylpylä
   "MI010000" 505999   ; HAUKIPUTAAN UIMAHALLI / Vesi-Jatuli
   "UU052000" 505802   ; Helsinki SYK:n uimahalli / Haagan uimahalli
   "MI006000" 522048   ; Mikkeli, Otavan seurakuntatalon uimahalli / uima-allas
   "YY400000" 529313   ; Heikinhalli
   "YY360000" 529312   ; Liperin uimahalli Liprakka
   "YY410000" 529314   ; Kuopio, Kylpylähotelli Rauhalahti
   "YY330000" 529315   ; TAHKO SPA OY / NILSIÄ (deleted from Lipas 12/2016)
   })

(def missing
  ["96400RO2" ; Rovaniemi Lappi-areena harjoitushalli2 (doesn't exist in Lipas)
   "UU041000" ; ESPOO-TAPIOLAN UIMAHALLI (deleted from Lipas, will be
              ; most probably demolished but maybe opened again in 2020)
   "TP012000" ; KIIKAN UIMAHALLI (closed, will be demolished soon)
   "PK007000" ; Pääskynpesä (duplicate for YY430000 'Ilomantsin uimahalli')
   "UU058000" ; Lapinjärven uimahalli (closed 2012)
   ])

(def lipas-url (str "http://lipas.cc.jyu.fi/api/sports-places?"
                    "fields=properties"
                    "&fields=schoolUse"
                    "&fields=email"
                    "&fields=type.name"
                    "&fields=location.sportsPlaces"
                    "&fields=renovationYears"
                    "&fields=admin"
                    "&fields=www"
                    "&fields=location.geometries"
                    "&fields=name"
                    "&fields=type.typeCode"
                    "&fields=location.locationId"
                    "&fields=freeUse"
                    "&fields=location.city.name"
                    "&fields=lastModified"
                    "&fields=location.postalCode"
                    "&fields=location.postalOffice"
                    "&fields=location.city.cityCode"
                    "&fields=phoneNumber"
                    "&fields=location.neighborhood"
                    "&fields=owner"
                    "&fields=location.address"
                    "&pageSize=100"))

(defn get-lipas-data* [url]
  (as-> url $
    (slurp $)
    (json/decode $ true)
    (utils/index-by :sportsPlaceId $)))

(defn get-lipas-ice-stadiums []
  (let [url (str lipas-url "&typeCodes=2520" "&typeCodes=2510")]
    (merge
     (get-lipas-data* (str url "&page=1")) ; n=233, page-size=100
     (get-lipas-data* (str url "&page=2"))
     (get-lipas-data* (str url "&page=3")))))

(defn get-lipas-swimming-pools []
  (let [url (str lipas-url "&typeCodes=3110" "&typeCodes=3130")]
    (merge
     (get-lipas-data* (str url "&page=1")) ; n=305, page-size=100
     (get-lipas-data* (str url "&page=2"))
     (get-lipas-data* (str url "&page=3"))
     (get-lipas-data* (str url "&page=4")))))

(defn get-portal-data [path]
  (as-> path $
    (slurp $)
    (csv/read-csv $)
    (utils/csv-data->maps $)
    (group-by #(get % "Halli_ID") $)))

(def admins (utils/index-by (comp :fi second) first admins/all))
(def owners (utils/index-by (comp :fi second) first owners/all))

(def size-categories
  {"SH"  "large"
   "PKH" "small"
   "KH"  "competition"})

(def heat-recovery-types
  (utils/index-by (comp :fi second) first ice-stadiums/heat-recovery-types))

(def dryer-types
  (utils/index-by (comp :fi second) first ice-stadiums/dryer-types))

(def refrigerant-solutions
  (utils/index-by (comp :fi second) first ice-stadiums/refrigerant-solutions))

(defn- when-gt0* [coercer s]
  (when-let [num (coercer s)]
      (when (< 0 num) num)))

(def when-gt0 (partial when-gt0* utils/->number))
(def when-gt0-int (partial when-gt0* utils/->int))

(defn- when-lt0* [coercer s]
  (when-let [num (coercer s)]
      (when (> 0 num) num)))

(def when-lt0 (partial when-lt0* utils/->number))
(def when-lt0-int (partial when-lt0* utils/->int))

(defn- ->boolean [s]
  (when-let [s (not-empty s)]
    (condp = s
      "Ei" false
      "On" true
      nil)))

(def trim (fnil string/trim ""))
(def sreplace (fnil string/replace ""))

(defn- kwh->mwh [s]
  (when-let [kwh (when-gt0-int s)]
    (int (/ kwh 1000))))

(defn ->sports-site [portal-entry lipas-entry]
  {:lipas-id (-> lipas-entry :sportsPlaceId)
   :hall-id  (get portal-entry "Halli_ID")

   :event-date (if-let [year (not-empty (get portal-entry "Vuosi"))]
                 (str year "-01-01T00:00:00.000Z")
                 (-> lipas-entry :lastModified (string/replace " " "T") (str "Z")))

   :status "active"

   :name           (-> lipas-entry :name)
   :marketing-name nil

   :admin (get admins (:admin lipas-entry))
   :owner (get owners (:owner lipas-entry))

   :www   (-> lipas-entry :www trim not-empty)
   :email (-> lipas-entry :email trim
              (sreplace " " "")
              (sreplace "(at)" "@")
              (sreplace "[at]" "@")
              (as-> $ (if (spec/valid? :lipas/email $) $ ""))
              not-empty)

   :phone-number (-> lipas-entry :phoneNumber trim not-empty)

   :construction-year (or (-> lipas-entry :constructionYear)
                          (when-gt0-int (get portal-entry "Rak.,vuosi")))

   :renovation-years (-> lipas-entry :renovationYears)

   :location
   {:address    (-> lipas-entry :location :address trim not-empty)
    :geometries (-> lipas-entry :location :geometries
                    (update :features
                            (fn [fs] (mapv #(dissoc % :properties) fs))))

    :postal-code   (-> lipas-entry :location :postalCode trim not-empty)
    :postal-office (-> lipas-entry :location :postalOffice trim not-empty)
    :city
    {:city-code    (-> lipas-entry :location :city :cityCode)
     :neighborhood (-> lipas-entry :location :neighborhood trim not-empty)}}

   :type
   {:type-code     (-> lipas-entry :type :typeCode)
    :size-category (when (= 2520 (-> lipas-entry :type :typeCode))
                     (get size-categories (get portal-entry "Halli-,tyyppi")))}})

(defn ->ice-stadium [portal-entry lipas-entry]
  (merge
   (->sports-site portal-entry lipas-entry)
   {:building
    {:total-volume-m3           (when-gt0 (get portal-entry "Tilavuus"))
     :total-surface-area-m2     (or (when-gt0 (get portal-entry "Pinta-,ala"))
                                    (-> lipas-entry :properties :areaM2))
     :total-ice-surface-area-m2 (when-gt0 (get portal-entry "Jään,pinta,ala"))
     :seating-capacity          (-> lipas-entry :properties :standCapacityPerson
                                    str
                                    utils/->int)}

    :envelope
    {:base-floor-structure    nil ; Never asked in either system
     :insulated-exterior?     (->boolean (get portal-entry "Seinän,lämpö-,eristeys"))
     :insulated-ceiling?      (->boolean (get portal-entry "Katon,lämpö-,eristeys"))
     :low-emissivity-coating? (->boolean (get portal-entry "Matala,emis.,pinnoite"))}

    :rinks
    (into [] (remove empty?)
          (map utils/remove-nils
               [{:length-m (-> lipas-entry :properties :field1LengthM)
                 :width-m  (-> lipas-entry :properties :field1WidthM)}
                {:length-m (-> lipas-entry :properties :field2LengthM)
                 :width-m  (-> lipas-entry :properties :field2WidthM)}
                {:length-m (-> lipas-entry :properties :field3LengthM)
                 :width-m  (-> lipas-entry :properties :field3WidthM)}]))

    :refrigeration
    {:original?                      nil ; missing from dump?
     :individual-metering?           nil ; missing from dump?
     :power-kw                       (when-gt0 (get portal-entry "Kylmä-,kone, teho"))
     :condensate-energy-recycling?   nil ; missing from dump?
     :condensate-energy-main-targets nil ; missing from dump?
     :refrigerant                    (-> (get portal-entry "Kylmä-,aine") not-empty)
     :refrigerant-amount-kg          nil ; Never asked in either system
     :refrigerant-solution           (->> (get portal-entry "Kylmä-,liuos")
                                          (get refrigerant-solutions))
     :refrigerant-solution-amount-l  nil ; Never asked in either system
     }

    :conditions
    {:daily-open-hours           (when-gt0-int (get portal-entry "Auki-,olo,tunnit/,paivä"))
     :open-months                (when-gt0-int (get portal-entry "Auki-,olo,kk:t/,vuosi"))
     :air-humidity-min           (when-gt0-int (get portal-entry "Ilman-,kosteus,(min)"))
     :air-humidity-max           (when-gt0-int (get portal-entry "Ilman-,kosteus,(max)"))
     :ice-surface-temperature-c  (when-lt0-int (get portal-entry "Jään,lämpö,tila"))
     :skating-area-temperature-c (utils/->int (get portal-entry "'Luistelu-,alue,lämpöt."))
     :stand-temperature-c        (utils/->int (get portal-entry "Katsomo,lämpötila"))

     ;; :daily-maintenances-week-days 8
     ;; :daily-maintenances-weekends  12
     :weekly-maintenances             (when-gt0-int (get portal-entry "Jään,hoito-,kerrat/,viikko"))
     :average-water-consumption-l     nil ; missing from dump?
     :maintenance-water-temperature-c (when-gt0-int (get portal-entry "Jään,hoito-,veden,lämpöt."))
     :ice-average-thickness-mm        (when-gt0-int (get portal-entry "Jään,paksuus"))
     }

    :ventilation
    {:heat-recovery-type       (get heat-recovery-types (get portal-entry "LTO-,tyyppi"))
     :heat-recovery-efficiency (when-gt0-int (get portal-entry "LTO-,hyöty-,suhde"))
     :dryer-type               (get dryer-types (get portal-entry "Ilmankuivaus"))
     :dryer-duty-type          nil ; missing from dump?
     :heat-pump-type           nil ; missing from dump?
     }

    :energy-consumption
    {:electricity-mwh (kwh->mwh (get portal-entry "Sähkö"))
     :heat-mwh        (kwh->mwh (get portal-entry "Lämpö"))
     :water-m3        (utils/->number (get portal-entry "Vesi"))}}))

(def filtering-methods
  {"Avo-/Painehiekka"     ["open-sand" "pressure-sand"]
   "Avohiekka"            ["open-sand"]
   "Avohiekka/monikerros" ["open-sand" "multi-layer-filtering"]
   "Hiekka/aktiivihiili"  ["activated-carbon"]
   "Hiekka/saostus"       ["precipitation"]
   "Hiili/hiekka"         ["coal"]
   "Imuhiekka"            ["other"]
   "Moniker./painehiekka" ["multi-layer-filtering" "pressure-sand"]
   "Monikerrossuodatus"   ["multi-layer-filtering"]
   "Muu"                  ["other"]
   "Painehiekka"          ["pressure-sand"]
   "Paineimu"             ["other"]})

(defn ->swimming-pool [portal-entry lipas-entry]
  (merge
   (->sports-site portal-entry lipas-entry)
   {:building
    {:main-designers nil ; Controversial whether person data can be stored

     :total-volume-m3         (when-gt0 (get portal-entry "Tilavuus"))
     :total-surface-area-m2   (or (when-gt0 (get portal-entry "Pinta-,ala"))
                                  (-> lipas-entry :properties :areaM2))
     :total-pool-room-area-m2 (when-gt0 (get portal-entry "Allas-,huoneen,ala"))
     :total-water-area-m2     (or (when-gt0 (get portal-entry "Allas-,ala"))
                                  (-> lipas-entry :properties :poolWaterAreaM2))

     :heat-sections?              nil ; Not present in data dump
     :main-construction-materials nil ; Not present in data dump
     :piled?                      nil ; Not present in data dump
     :supporting-structures       nil ; Not present in data dump
     :ceiling-structures          nil ; Not present in data dump

     :staff-count             (when-gt0 (get portal-entry "Henkilö-,kuntaa"))
     :seating-capacity        (-> lipas-entry :properties :standCapacityPerson
                                  utils/->int)
     :heat-sources            nil ; Not present in data dump
     :ventilation-units-count (when-gt0 (get portal-entry "IV-,koneita"))}

    :water-treatment
    {:ozonation?        (->boolean (get portal-entry "Otso-,nointi"))
     :uv-treatment?     (->boolean (get portal-entry "Aktiivi-,hiili"))
     :activated-carbon? (->boolean (get portal-entry "UV-,käsit-,tely"))
     :filtering-methods (get filtering-methods (get portal-entry "Suodatus"))
     :comment           nil} ; relevant?

    :pools
    (into [] (remove empty?)
          (map utils/remove-nils

               ;; NOTE: pool related prop names are a bit messed up in old Lipas

               [{:type          nil ; will be later inferred by human
                 :temperature-c (-> lipas-entry :properties :pool1TemperatureC
                                    str when-gt0-int)
                 :volume-m3     nil ; will be later inferred by human
                 :area-m2       nil ; will be later inferred by human
                 ;; Yes, typo in old db, 'MM' should be 'M'
                 :length-m      (-> lipas-entry :properties :pool1LengthMM
                                    str when-gt0)
                 :width-m       (-> lipas-entry :properties :pool1WidthM
                                    str when-gt0)
                 :depth-min-m   (-> lipas-entry :properties :pool1MinDepthM
                                    str when-gt0)
                 :depth-max-m   (-> lipas-entry :properties :pool1MaxDepthM
                                    str when-gt0)}
                {:type          nil ; will be later inferred by human
                 :temperature-c (-> lipas-entry :properties :pool2TemperatureC
                                    str when-gt0-int)
                 :volume-m3     nil ; will be later inferred by human
                 :area-m2       nil ; will be later inferred by human
                 :length-m      (-> lipas-entry :properties :pool2LengthM
                                    str when-gt0)
                 :width-m       (-> lipas-entry :properties :pool2WidthM
                                    str when-gt0)
                 :depth-min-m   (-> lipas-entry :properties :pool2MinDepthM
                                    str when-gt0)
                 :depth-max-m   (-> lipas-entry :properties :pool2MaxDepthM
                                    str when-gt0)}
                {:type          nil
                 :temperature-c (-> lipas-entry :properties :pool3TemperatureC
                                    str when-gt0-int)
                 :volume-m3     nil
                 :area-m2       nil
                 :length-m      (-> lipas-entry :properties :pool3LengthM
                                    str when-gt0)
                 :width-m       (-> lipas-entry :properties :pool3WidthM
                                    str when-gt0)
                 :depth-min-m   (-> lipas-entry :properties :pool3MinDepthM
                                    str when-gt0)
                 :depth-max-m   (-> lipas-entry :properties :pool3MaxDepthM
                                    str when-gt0)}
                {:type          nil
                 :temperature-c (-> lipas-entry :properties :pool4TemperatureC
                                    str when-gt0-int)
                 :volume-m3     nil
                 :area-m2       nil
                 :length-m      (-> lipas-entry :properties :pool4LengthM
                                    str when-gt0)
                 :width-m       (-> lipas-entry :properties :pool4WidthM
                                    str when-gt0)
                 :depth-min-m   (-> lipas-entry :properties :pool4MinDepthM
                                    str when-gt0)
                 :depth-max-m   (-> lipas-entry :properties :pool4MaxDepthM
                                    str when-gt0)}
                {:type          nil
                 :temperature-c (-> lipas-entry :properties :pool5TemperatureC
                                    str when-gt0-int)
                 :volume-m3     nil
                 :area-m2       nil
                 :length-m      (-> lipas-entry :properties :pool5LengthM
                                    str when-gt0)
                 :width-m       (-> lipas-entry :properties :pool5WidthM
                                    str when-gt0)
                 :depth-min-m   (-> lipas-entry :properties :pool5MinDepthM
                                    str when-gt0)
                 :depth-max-m   (-> lipas-entry :properties :pool5MaxDepthM
                                    str when-gt0)}]))

    ;; This will only get sites with 1 slide right. Others will be
    ;; fixed manually by a human.

    :slides
    (into [] (remove empty?)
          (map utils/remove-nils
               [{:length-m  (when (= 1 (when-gt0-int (-> lipas-entry :waterSlidesCount str)))
                              (-> lipas-entry :properties :waterslidesTotalLengthM
                                  str utils/->int))
                 :structure nil}])) ; not asked before in either system

    :saunas
    (into [] (repeat (or (utils/->int (get portal-entry "Saunoja")) 0)
                     {:type "sauna"}))

    :facilities
    {:hydro-massage-spots-count      (when-gt0 (get portal-entry "Hieronta-,pisteitä"))
     :hydro-neck-massage-spots-count nil ; not asked before in either system
     :platforms-1m-count             (-> lipas-entry :properties :1mPlatformsCount
                                         str utils/->int)
     :platforms-3m-count             (-> lipas-entry :properties :3mPlatformsCount
                                         str utils/->int)
     :platforms-5m-count             (-> lipas-entry :properties :5mPlatformsCount
                                         str utils/->int)
     :platforms-7.5m-count           (-> lipas-entry :properties :7m5PlatformsCount
                                         str utils/->int)
     :platforms-10m-count            (-> lipas-entry :properties :10mPlatformsCount
                                         str utils/->int)
     :kiosk?                         (-> lipas-entry :properties :kiosk)
     :showers-men-count              nil ; not present in data dump
     :showers-women-count            nil ; not present in data dump
     :lockers-men-count              nil ; not present in data dump
     :lockers-women-count            nil} ; not present in data dump

    :conditions
    {:open-days-in-year (when-gt0 (get portal-entry "Aukiolo,päivät" ))
     :daily-open-hours  (when-gt0 (get portal-entry "Aukiolo,tunnit"))}

    :visitors
    {:total-count (when-gt0-int (get portal-entry "Kävijät"))}

    :energy-consumption
    {:electricity-mwh (kwh->mwh (get portal-entry "Sähkö"))
     :heat-mwh        (kwh->mwh (get portal-entry "Lämpö"))
     :water-m3        (utils/->number (get portal-entry "Vesi"))}}))

(defn- ->sports-sites [migrate-fn lipas-data portal-data]
  (for [[hall-id data] portal-data
        portal-entry   data
        :let           [lipas-id (or (get fixed-mappings hall-id)
                                     (utils/->number (get portal-entry "Lipas-ID")))
                        lipas-entry (get lipas-data lipas-id)]]
    (if (or (empty? lipas-entry)
            (nil? lipas-id)
            (some #{hall-id} missing))
      (log/warn "No Lipas data found for hall-id:" hall-id "lipas-id:" lipas-id)
      (-> (migrate-fn portal-entry lipas-entry)
          utils/clean))))

(defn upsert! [db user sports-sites]
  (log/info "Starting to put data into db...")
  (db/upsert-sports-sites! db user sports-sites)
  (log/info "Done inserting data!"))

(defn migrate-ice-stadiums! [db user csv-path]
  (let [lipas-data   (get-lipas-ice-stadiums)
        portal-data  (get-portal-data csv-path)

        ice-stadiums (->> (->sports-sites ->ice-stadium lipas-data portal-data)
                          (remove nil?))]

    (if (utils/all-valid? :lipas.sports-site/ice-stadium ice-stadiums)
      (upsert! db user ice-stadiums)
      (log/error "Invalid data, check messages in STDOUT."))))

(defn migrate-swimming-pools! [db user csv-path]
  (let [lipas-data     (get-lipas-swimming-pools)
        portal-data    (get-portal-data csv-path)

        swimming-pools (->> (->sports-sites ->swimming-pool lipas-data portal-data)
                            (remove nil?))]

    (if (utils/all-valid? :lipas.sports-site/swimming-pool swimming-pools)
      (upsert! db user swimming-pools)
      (log/error "Invalid data, check messages in REPL."))))


(defn- resolve-transform [data]
  (case (-> data :type :typeCode)
    (3110 3130) {:transform-fn (comp utils/clean (partial ->swimming-pool {}))
                 :spec         :lipas.sports-site/swimming-pool}
    (2510 2520) {:transform-fn (comp utils/clean (partial ->ice-stadium {}))
                 :spec         :lipas.sports-site/ice-stadium}
    {:transform-fn (comp utils/clean (partial ->sports-site {}))
     :spec         :lipas/sports-site}))

(defn migrate-from-old-lipas [db user lipas-ids]
  (log/info "Starting to migrate sports-sites" lipas-ids)
  (doseq [lipas-id lipas-ids]
    (let [url   (str "http://lipas.cc.jyu.fi/api/sports-places/" lipas-id)
          data  (-> url slurp (json/decode true))
          instr (resolve-transform data)
          data  ((:transform-fn instr) data)]
      (if (spec/valid? (:spec instr) data)
        (do
          (db/upsert-sports-site! db user data)
          (log/info "Successfully migrated" lipas-id))
        (do
          (spec/explain (:spec instr) data)
          (log/error "Failed to migrate lipas-id" lipas-id))))))

(defn -main [& args]
  (let [source                  (first args)
        ice-stadiums-csv-path   (:ice-stadiums-csv-url env)
        swimming-pools-csv-path (:swimming-pools-csv-url env)
        config                  (select-keys backend/default-config [:db])
        {:keys [db]}            (backend/start-system! config)
        user                    (core/get-user db "import@lipas.fi")]
    (case source
      "--csv"   (do
                  (migrate-ice-stadiums! db user ice-stadiums-csv-path)
                  (migrate-swimming-pools! db user swimming-pools-csv-path))
      "--lipas" (migrate-from-old-lipas db user (rest args))
      (log/error "Please provide --csv or --lipas 123 234 ..."))))

(comment
  (-main "--csv") ;Careful with this one
  (-main "--lipas" "529736"))
