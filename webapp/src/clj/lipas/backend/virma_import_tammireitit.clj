(ns lipas.backend.virma-import-tammireitit
  "Adapter for the 2026 VIRMA 'Tammireitit' CSV dump
   (virma_tammireitit_final_cleaned_mapped.csv).

   The dump has drifted from the column layout the original importer
   (lipas.backend.virma-import) was built for:

   | virma-import expects | Tammireitit dump has                        |
   |----------------------|---------------------------------------------|
   | name_fi              | kohde                                       |
   | info_fi              | lisatieto                                   |
   | www_fi               | linkki                                      |
   | ownerclass           | omistaja                                    |
   | munici_nro (code)    | kunta (municipality name)                   |
   | geom 'POINT (x y)'   | geometry 'Point (x y)' (case mismatch; the  |
   |                      | x_eureffin/y_eureffin fallback covers it)   |
   | address, zip         | absent (importer placeholders kick in)      |

   This namespace only adapts rows and post-fixes geometries; all
   conversion and validation is delegated to lipas.backend.virma-import.

   Fixes applied on top of the plain renames:
   - Type 205 'Rantautumispaikka' is deprecated -> remapped to the
     active type 203 'Veneilyn palvelupaikka' (also a Point type).
   - Sports-site rows without a name get the placeholder 'NIMI PUUTTUU'
     so they survive validation and are easy to find for manual fixing.
   - Types whose schema requires LineString (4403) or Polygon (1110)
     geometry get a small stub geometry of the correct type around the
     original point. Sites are imported in 'planning' status, so the
     stubs are corrected by hand before publishing."
  (:require [clojure.string :as str]
            [lipas.backend.virma-import :as virma]
            [lipas.data.cities :as cities]
            [lipas.data.types :as types]))

(def city-name->code
  "Finnish municipality name -> city code, e.g. \"Kaarina\" -> 202."
  (into {}
        (map (fn [{:keys [name city-code]}] [(:fi name) city-code]))
        cities/all))

(def kunta-overrides
  "kunta is empty for these source_ids in the dump; resolved manually
   from the location. Halistenkoski is the rapids on the Aurajoki in
   Turku (the Aurajoki paddling route passes through Turku)."
  {"179" "Turku"})

(def missing-name-placeholder "NIMI PUUTTUU")

(defn- blank->nil [s]
  (when-not (str/blank? s) s))

(defn adapt-row
  "Adapt a parsed Tammireitit CSV row (keyword keys, as returned by
   virma/read-virma-csv) to the column layout virma-import expects."
  [{:keys [source_id kohde lisatieto linkki omistaja kunta
           lipas_type_code] :as row}]
  (let [kunta* (or (blank->nil kunta) (get kunta-overrides source_id))
        city-code (some-> kunta* str/trim city-name->code)
        sports-site? (some? (blank->nil lipas_type_code))
        rantautumispaikka? (= "205" (some-> lipas_type_code str/trim))
        name* (or (blank->nil kohde)
                  (when sports-site? missing-name-placeholder))]
    (cond-> (assoc row
                   :name_fi name*
                   :info_fi (blank->nil lisatieto)
                   :www_fi (blank->nil linkki)
                   :ownerclass (blank->nil omistaja)
                   :munici_nro (some-> city-code str))
      rantautumispaikka?
      (assoc :lipas_type_code "203"
             :lipas_type_name "Veneilyn palvelupaikka"))))

;; ~0.0004 deg longitude is ~22 m at 60°N; enough to be visible on the
;; map but obviously a stub.
(def ^:private stub-offset 0.0004)

(defn- point->stub-line-string [[lon lat]]
  {:type "LineString"
   :coordinates [[lon lat]
                 [(+ lon stub-offset) lat]]})

(defn- point->stub-polygon [[lon lat]]
  {:type "Polygon"
   :coordinates [[[lon lat]
                  [(+ lon stub-offset) lat]
                  [(+ lon stub-offset) (+ lat (/ stub-offset 2))]
                  [lon (+ lat (/ stub-offset 2))]
                  [lon lat]]]})

(defn fix-geometry
  "Replace the Point geometry of a converted sports-site document with a
   stub LineString/Polygon when the site's type requires one. Returns
   the site unchanged for Point types."
  [site]
  (let [type-code (get-in site [:type :type-code])
        geom-type (get-in types/all [type-code :geometry-type])
        coords (get-in site [:location :geometries :features 0 :geometry :coordinates])
        stub (case geom-type
               "LineString" (point->stub-line-string coords)
               "Polygon" (point->stub-polygon coords)
               nil)]
    (if stub
      (assoc-in site [:location :geometries :features]
                [{:type "Feature" :geometry stub}])
      site)))

(defn import-tammireitit-csv
  "Like virma/import-virma-csv but for the Tammireitit dump: adapts rows
   first and stub-fixes non-Point geometries after conversion."
  [file-path]
  (let [rows (map adapt-row (virma/read-virma-csv file-path))
        classified (group-by virma/determine-import-type rows)

        sports-sites (->> (get classified :sports-site [])
                          (map virma/csv-row->sports-site)
                          (filter some?)
                          (mapv fix-geometry))

        lois (->> (concat (get classified :loi [])
                          (get classified :unknown []))
                  (map virma/csv-row->loi)
                  (filterv some?))]

    {:sports-sites sports-sites
     :lois lois
     :stats {:total-rows (count rows)
             :sports-sites-created (count sports-sites)
             :lois-created (count lois)
             :sports-sites-skipped (- (count (get classified :sports-site []))
                                      (count sports-sites))
             :lois-skipped (- (count (concat (get classified :loi [])
                                             (get classified :unknown [])))
                              (count lois))
             :unknown-type (count (get classified :unknown []))}}))

(comment
  (def result
    (import-tammireitit-csv "local-scrap/virma_tammireitit_final_cleaned_mapped.csv"))

  (:stats result)

  ;; Validate everything before saving
  (def sports-validation (virma/validate-all-sports-sites (:sports-sites result)))
  [(:valid-count sports-validation) (:invalid-count sports-validation)]
  (take 3 (:invalid-sites sports-validation))

  (def loi-validation (virma/validate-all-lois (:lois result)))
  [(:valid-count loi-validation) (:invalid-count loi-validation)]
  (take 3 (:invalid-lois loi-validation))

  ;; Spot-check the remapped and stub-fixed sites
  (->> (:sports-sites result)
       (filter #(= 203 (get-in % [:type :type-code])))
       (map :name))
  (->> (:sports-sites result)
       (filter #(#{4403 1110} (get-in % [:type :type-code])))
       (map (juxt :name #(get-in % [:location :geometries :features 0 :geometry :type]))))

  ;; Save (same flow as the original importer)
  (require '[lipas.backend.db.db :as db])
  (require '[lipas.backend.core :as core])

  (def db (user/db))
  (def robot (core/get-user db "robot@lipas.fi"))

  (do
    (println "Saving" (count (:sports-sites result)) "sports sites")
    (doseq [m (:sports-sites result)]
      (println "Saving" (:name m))
      (db/upsert-sports-site! db robot m false))
    (println "All Sports sites saved!"))

  (do
    (println "Saving" (count (:lois result)) "lois")
    (doseq [m (:lois result)]
      (println "Saving" (:fi (:name m)))
      (db/upsert-loi! db robot m))
    (println "All Lois saved!")))
