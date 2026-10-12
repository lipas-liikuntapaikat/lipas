(ns lipas.backend.ptv.adoption
  "PTV integration adoption statistics for the admin view.

  Everything counts sports sites (PTV service locations), never PTV services,
  and is read from the sports_site revision log, so the numbers are live
  (unlike the nightly-rebuilt analytics index behind Kibana).

  - \"In PTV\" means the current revision's publishing status is Published —
    what PTV returned at LIPAS's last sync, not a live PTV read.
  - Adoption over time counts a site from the month it was FIRST published in
    PTV. Sites that got PTV data but never reached PTV don't count as adopted.
    Sites that later left PTV (archived, or the PTV data gone) are counted
    separately by the month they left, so total - left-total is the number in
    PTV at the end of each month and, for the last one, equals the tile.
  - What needs an admin's attention comes from `ptv-data/attention`, the same
    rules the attention list uses."
  (:require [lipas.backend.db.db :as db]
            [lipas.data.cities :as cities]
            [lipas.data.ptv :as ptv-data])
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

(defn- with-state
  "Adds the derived PTV state to a row from `db/get-ptv-adoption`."
  [{:keys [site city-code] :as row}]
  (let [ptv (:ptv site)
        attention (ptv-data/attention site)]
    (assoc row
           :municipality (city-name city-code)
           :sync-enabled? (boolean (:sync-enabled ptv))
           :publishing-status (:publishing-status ptv)
           :attention attention
           :error? (boolean (some #(= :error (:severity %)) attention))
           :flagged? (boolean (seq attention)))))

(defn- in-ptv? [row] (= "Published" (:publishing-status row)))

(defn- status-counts [rows]
  (let [errors (filter :error? rows)]
    {:in-ptv (count (filter in-ptv? rows))
     :in-ptv-sync-off (count (filter #(and (in-ptv? %) (not (:sync-enabled? %))) rows))
     :sync-enabled (count (filter :sync-enabled? rows))
     :deleted (count (filter #(= "Deleted" (:publishing-status %)) rows))
     ;; sync on but not (correctly) in PTV, see ptv-data/attention
     :attention (count errors)
     :attention-never-reached (count (filter #(nil? (:publishing-status %)) errors))
     :attention-failing (count (remove #(nil? (:publishing-status %)) errors))
     ;; in PTV, but in a state someone should look at (no errors)
     :warnings (count (filter #(and (:flagged? %) (not (:error? %))) rows))}))

(defn- attention-row [{:keys [site] :as row}]
  (let [ptv (:ptv site)]
    {:lipas-id (:lipas-id row)
     :name (:name site)
     :city-code (:city-code row)
     :municipality (:municipality row)
     :type-code (get-in site [:type :type-code])
     :status (:status site)
     :owner (:owner site)
     :sync-enabled? (:sync-enabled? row)
     :publishing-status (:publishing-status row)
     :last-sync (:last-sync ptv)
     :org-id (:org-id ptv)
     :service-channel-id (first (:service-channel-ids ptv))
     :error-message (get-in ptv [:error :message])
     :reasons (:attention row)}))

(defn summarize
  "Aggregates per-site rows (see `db/get-ptv-adoption`) into totals, a
  gap-free monthly series of first publications up to `current-month`
  (\"YYYY-MM\"), per-municipality figures and the sites needing attention."
  [rows current-month]
  (let [rows (map with-state rows)
        published (filter :first-published-month rows)
        by-month (group-by :first-published-month published)
        ;; ever published but not in PTV now, by when they left
        left-by-month (group-by :left-month (filter #(and (:first-published-month %)
                                                          (:left-month %)
                                                          (not (in-ptv? %)))
                                                    rows))
        months (if (seq published)
                 (month-range (reduce #(if (neg? (compare %1 %2)) %1 %2)
                                      (keys by-month))
                              current-month)
                 [])]
    {:totals (assoc (status-counts rows)
                    :municipalities (count (distinct (map :city-code (filter in-ptv? rows))))
                    :new-this-month (count (by-month current-month)))
     :monthly (->> months
                   (reductions
                     (fn [{:keys [total left-total]} month]
                       (let [new-rows (by-month month)
                             left-rows (left-by-month month)]
                         {:month month
                          :new (count new-rows)
                          :total (+ total (count new-rows))
                          :left (count left-rows)
                          :left-total (+ left-total (count left-rows))
                          :by-municipality (->> new-rows
                                                (group-by :municipality)
                                                (map (fn [[m rs]] {:municipality m :new (count rs)}))
                                                (sort-by (juxt (comp - :new) :municipality))
                                                vec)}))
                     {:total 0 :left-total 0})
                   rest
                   vec)
     :municipalities (->> (group-by :city-code rows)
                          (map (fn [[city-code rs]]
                                 (let [months (sort (keep :first-published-month rs))]
                                   (assoc (status-counts rs)
                                          :city-code city-code
                                          :municipality (city-name city-code)
                                          :first-month (first months)
                                          :latest-month (last months)))))
                          (sort-by (juxt (comp - :in-ptv) (comp - :attention) :municipality))
                          vec)
     :attention (->> rows
                     (filter :flagged?)
                     (sort-by (juxt (comp not :error?) :municipality #(str (get-in % [:site :name]))))
                     (mapv attention-row))}))

(defn get-adoption-stats [db-spec]
  (assoc (summarize (db/get-ptv-adoption db-spec)
                    (str (YearMonth/now helsinki)))
         :generated-at (str (java.time.Instant/now))))
