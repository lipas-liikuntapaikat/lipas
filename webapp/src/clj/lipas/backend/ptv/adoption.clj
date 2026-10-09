(ns lipas.backend.ptv.adoption
  "PTV integration adoption statistics for the admin view.

  A site counts as adopted from the month of its first revision that carries a
  :ptv subtree (source of truth: the sports_site revision log, so the numbers
  are live, unlike the nightly-rebuilt analytics index behind Kibana)."
  (:require [lipas.backend.db.db :as db]
            [lipas.data.cities :as cities])
  (:import [java.time YearMonth ZoneId]))

(def ^:private helsinki (ZoneId/of "Europe/Helsinki"))

(defn- city-name [city-code]
  (or (get-in cities/by-city-code [city-code :name :fi])
      (get-in cities/abolished-by-city-code [city-code :name :fi])
      (str city-code)))

(defn- month-range
  "Inclusive \"YYYY-MM\" strings from `from` to `to`."
  [from to]
  (let [end (YearMonth/parse to)]
    (->> (YearMonth/parse from)
         (iterate #(.plusMonths ^YearMonth % 1))
         (take-while #(not (.isAfter ^YearMonth % end)))
         (mapv str))))

(defn- status-counts [rows]
  {:sites (count rows)
   :integrated (count (filter :ptv-org-id rows))
   :sync-enabled (count (filter :sync-enabled? rows))
   ;; sync on but never reached PTV (no publishing status at all) — a
   ;; failed or blocked integration someone thinks is live
   :sync-pending (count (filter #(and (:sync-enabled? %) (nil? (:publishing-status %))) rows))
   :published (count (filter #(= "Published" (:publishing-status %)) rows))
   :deleted (count (filter #(= "Deleted" (:publishing-status %)) rows))})

(defn summarize
  "Aggregates per-site adoption rows (see `db/get-ptv-adoption`) into totals,
  a gap-free monthly series up to `current-month` (\"YYYY-MM\") and
  per-municipality figures."
  [rows current-month]
  (let [rows (map #(assoc % :municipality (city-name (:city-code %))) rows)
        by-month (group-by :first-month rows)
        months (if (seq rows)
                 (month-range (reduce #(if (neg? (compare %1 %2)) %1 %2)
                                      (keys by-month))
                              current-month)
                 [])]
    {:totals (assoc (status-counts rows)
                    :municipalities (count (distinct (map :city-code rows))))
     :monthly (->> months
                   (reductions
                     (fn [{:keys [total]} month]
                       (let [new-rows (by-month month)]
                         {:month month
                          :new (count new-rows)
                          :total (+ total (count new-rows))
                          :by-municipality (->> new-rows
                                                (group-by :municipality)
                                                (map (fn [[m rs]] {:municipality m :new (count rs)}))
                                                (sort-by (juxt (comp - :new) :municipality))
                                                vec)}))
                     {:total 0})
                   rest
                   vec)
     :municipalities (->> (group-by :city-code rows)
                          (map (fn [[city-code rs]]
                                 (let [months (sort (map :first-month rs))]
                                   (assoc (status-counts rs)
                                          :city-code city-code
                                          :municipality (city-name city-code)
                                          :first-month (first months)
                                          :latest-month (last months)))))
                          (sort-by (juxt (comp - :sites) :municipality))
                          vec)}))

(defn get-adoption-stats [db-spec]
  (assoc (summarize (db/get-ptv-adoption db-spec)
                    (str (YearMonth/now helsinki)))
         :generated-at (str (java.time.Instant/now))))
