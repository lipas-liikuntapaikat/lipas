(ns lipas.ui.utils
  (:require [re-frame.core :as re-frame]
            [clojure.reader :refer [read-string]]
            [clojure.walk :as walk]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(defn ->setter-fn [event]
  (fn [& args]
    (==> [event (butlast args) (last args)])))

(defn set-field [db path value]
  (if value
    (assoc-in db path value)
    (if-let [base-path (butlast path)]
      (update-in db (into [] base-path) dissoc (last path))
      (dissoc db path))))

(def maxc (partial apply max))

(defn next-id [db path]
  (gensym))

(defn save-entity [db path entity]
  (let [id (or (:id entity) (next-id db path))]
    (assoc-in db (conj path id) (assoc entity :id id))))

(defn ->indexed-map [coll]
  (when coll
    (into {} (map-indexed (fn [idx item]
                            [idx
                             (if (map? item)
                               (assoc item :id idx)
                               item)])
                          coll))))

(defn index-by [idx-fn coll]
  (into {} (map (juxt idx-fn identity)) coll))

(defn localize-field [tr k prefix m]
  (if (k m)
    (update m k #(tr (keyword prefix %)))
    m))

(defn ->localized-select-entry [tr prefix k]
  (->> {:value k :label k}
       (localize-field tr :label prefix)))

(defn ->select-entries [tr prefix enum-map]
  (map (partial ->localized-select-entry tr prefix) (keys enum-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO consider using proper time-manipulation lib ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment (resolve-year "2014-12-02"))
(comment (resolve-year 2014))
(defn resolve-year [timestamp]
  (read-string (reduce str (take 4 (str timestamp)))))

(def this-year (.getFullYear (js/Date.)))

(defn timestamp []
  (.toISOString (js/Date.)))

(defn ->timestamp [year]
  (str year "-01-01T00:00:00.000Z"))

(defn reverse-cmp [a b]
  (compare b a))

(comment (prev-or-first [1 3 5] 4))
(comment (prev-or-first ["2005" "2012" "2001"] "2006"))
(defn prev-or-first [coll x]
  (let [ordered   (->> (conj coll x) (sort reverse-cmp))
        head-at-x (drop-while #(not= x %) ordered)]
    (or (fnext head-at-x) (last (drop-last ordered)))))

(defn resolve-prev-rev [history rev-ts]
  (let [closest (prev-or-first (keys history) rev-ts)]
    (get history closest)))

(defn latest-by-year [history]
  (let [by-year (group-by resolve-year (keys history))]
    (reduce-kv (fn [res k v]
                 (assoc res k (first (sort reverse-cmp v))))
               {}
               by-year)))

(defn make-year-list
  "Highlights matching `years`."
  [years]
  (let [data (for [y    (range 2000 (inc this-year))
                   :let [data-exists? (some #{y} years)]]
               {:label (if data-exists?
                         (str y " " "✓")
                         (str y))
                :value y})]
    (sort-by :label reverse-cmp data)))

(defn energy-consumption-history [{:keys [history]}]
  (let [by-year (latest-by-year history)
        entries (select-keys history (vals by-year))]
    (map #(assoc (:energy-consumption %)
                 :year (resolve-year (:timestamp %))) (vals entries))))

(defn find-revision [{:keys [history]} year]
  (let [latest-by-year (latest-by-year history)
        timestamp      (get latest-by-year year)]
    (get history timestamp)))

(defn make-revision
  ([site]
   (make-revision site (timestamp)))
  ([site timestamp]
   (let [history-with-edits (merge (:history site) (:edits site))
         prev-rev (resolve-prev-rev history-with-edits timestamp)]
     (-> prev-rev
         (assoc :timestamp timestamp)
         (dissoc :energy-consumption)
         (dissoc :visitors)))))

(defn latest-edit [edits]
  (let [latest (first (sort reverse-cmp (keys edits)))]
    (get edits latest)))

(defn different? [rev1 rev2]
  (let [rev1 (dissoc rev1 :timestamp :energy-consumption :visitors)
        rev2 (dissoc rev2 :timestamp :energy-consumption :visitors)]
    (not= rev1 rev2)))

(defn remove-ids [m]
  (not-empty (map #(dissoc % :id) m)))

(defn save-edits [db rev]
  (let [lipas-id    (:lipas-id rev)
        site        (get-in db [:sports-sites lipas-id])
        original    (get-in site [:history (:latest site)])
        original?   (not (different? rev original))
        latest-edit (latest-edit (-> site :edits))
        dirty?      (different? rev (or latest-edit original))
        timestamp   (:timestamp rev)]
    (cond
      original? (assoc-in db [:sports-sites lipas-id :edits] nil)
      dirty?    (assoc-in db [:sports-sites lipas-id :edits timestamp] rev)
      :else     db)))

(defn commit-edits [db rev]
  (let [lipas-id (:lipas-id rev)]
    (-> db
        (assoc-in [:sports-sites lipas-id :edits] nil)
        (assoc-in [:sports-sites lipas-id :latest] (:timestamp rev))
        (assoc-in [:sports-sites lipas-id :history (:timestamp rev)] rev))))

(defn discard-edits [db lipas-id]
  (assoc-in db [:sports-sites lipas-id :edits] nil))

(defn commit-energy-consumption [db rev]
  (let [lipas-id (:lipas-id rev)
        ts       (:timestamp rev)]
    ;; TODO Need to update latest maybe?
    (assoc-in db [:sports-sites lipas-id :history ts] rev)))

(defn maybe-update-in
  "Like `update-in` but updates only if path is present. Otherwise
  returns the map unmodified."
  [m path update-fn & args]
  (if (not-empty (get-in m path))
    (apply update-in (into [m path update-fn] args))
    m))

(defn clean
  "Removes nil values and empty {} entries recursively from maps."
  [m]
  (walk/postwalk
   (fn [x]
     (cond
       (map? x) (not-empty (into {} (filter (comp some? val)) x))
       :else x))
   m))
