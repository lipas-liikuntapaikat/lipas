(ns lipas.ui.utils
  (:require [cemerick.url :as url]
            [clojure.data :as data]
            [clojure.reader :refer [read-string]]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [goog.crypt.base64 :as b64]
            [re-frame.core :as re-frame]
            [testdouble.cljs.csv :as csv]))

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

(defn- data-exists? [year history]
  (some (complement nil?)
        (vals (select-keys (get history year)
                           [:electricity-mwh
                            :heat-mwh
                            :water-m3
                            :cold-mwh]))))

(defn make-energy-consumption-year-list
  "Highlights years where there exists any energy consumption data."
  [history]
  (let [history (index-by :year history)
        data (for [y    (range 2000 (inc this-year))
                   :let [data-exists? (data-exists? y history)]]
               {:label (if data-exists?
                         (str y " " "✓")
                         (str y))
                :value y})]
    (sort-by :label reverse-cmp data)))

(defn energy-consumption-history [{:keys [history]}]
  (let [by-year (latest-by-year history)
        entries (select-keys history (vals by-year))]
    (map #(assoc (:energy-consumption %)
                 :year (resolve-year (:event-date %))) (vals entries))))

(defn visitors-history [{:keys [history]}]
  (let [by-year (latest-by-year history)
        entries (select-keys history (vals by-year))]
    (map #(assoc (:visitors %)
                 :year (resolve-year (:event-date %))) (vals entries))))

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
         (assoc :event-date timestamp)
         (dissoc :energy-consumption)
         (dissoc :visitors)))))

(defn latest-edit [edits]
  (let [latest (first (sort reverse-cmp (keys edits)))]
    (get edits latest)))

(defn clean
  "Removes nil values and empty entries recursively from maps."
  [m]
  (walk/postwalk
   (fn [x]
     (cond
       (map? x) (not-empty (into {} (filter (comp some? val)) x))
       (and (coll? x) (empty? x)) nil
       :else x))
   m))

(defn- make-comparable [rev]
  (-> rev
      (dissoc :event-date :energy-consumption :visitors)
      clean))

(defn different? [rev1 rev2]
  (let [rev1 (make-comparable rev1)
        rev2 (make-comparable rev2)]
    ;; (prn (data/diff rev1 rev2))
    (not= rev1 rev2)))

(defn save-edits [db rev]
  (let [lipas-id    (:lipas-id rev)
        site        (get-in db [:sports-sites lipas-id])
        original    (get-in site [:history (:latest site)])
        original?   (not (different? rev original))
        latest-edit (latest-edit (-> site :edits))
        dirty?      (different? rev (or latest-edit original))
        timestamp   (:event-date rev)]
    (as-> db $
      (assoc-in $ [:sports-sites lipas-id :editing] nil)
      (cond
        original? (assoc-in $ [:sports-sites lipas-id :edits] nil)
        dirty?    (assoc-in $ [:sports-sites lipas-id :edits timestamp] rev)
        :else     $))))

(defn latest? [rev history]
  (let [event-date  (:event-date rev)
        event-dates (conj (keys history) event-date)]
    (= event-date (first (sort reverse-cmp event-dates)))))

(defn commit-edits [db rev]
  (let [lipas-id (:lipas-id rev)
        history (get-in db [:sports-sites lipas-id :history])]
    (as-> db $
        (assoc-in $ [:sports-sites lipas-id :edits] nil)
        (assoc-in $ [:sports-sites lipas-id :history (:event-date rev)] rev)
        (if (latest? rev history)
          (assoc-in $ [:sports-sites lipas-id :latest] (:event-date rev))
          $))))

(defn discard-edits [db lipas-id]
  (assoc-in db [:sports-sites lipas-id :edits] nil))

(defn commit-energy-consumption [db rev]
  (let [lipas-id (:lipas-id rev)
        ts       (:event-date rev)]
    ;; TODO Need to update latest maybe?
    (assoc-in db [:sports-sites lipas-id :history ts] rev)))

(comment (->basic-auth {:username "kissa" :password "koira"}))
(defn ->basic-auth
  "Creates base64 encoded Authorization header value"
  [{:keys [username password]}]
  (str "Basic " (b64/encodeString (str username ":" password))))

(defn add-to-db [db {:keys [lipas-id event-date] :as rev}]
  (let [new-db (assoc-in db [:sports-sites lipas-id :history event-date] rev)]
    (if (latest? rev (get-in db [:sports-sites lipas-id :history]))
      (assoc-in new-db [:sports-sites lipas-id :latest] event-date)
      new-db)))

(defn join-pretty [coll]
  (string/join ", " coll))

(defn make-editable [sports-site]
  (-> sports-site

      ;; Swimming pools
      (update-in [:pools]  ->indexed-map)
      (update-in [:saunas] ->indexed-map)
      (update-in [:slides] ->indexed-map)

      ;; Ice Stadiums
      (update-in [:rinks] ->indexed-map)))

(defn remove-ids [m]
  (not-empty (map #(dissoc % :id) m)))

(defn make-saveable [sports-site]
  (-> sports-site

      ;; Swimming pools
      (update-in [:pools]  (comp not-empty remove-ids vals))
      (update-in [:saunas] (comp not-empty remove-ids vals))
      (update-in [:slides] (comp not-empty remove-ids vals))

      ;; Ice stadiums
      (update-in [:rinks] (comp not-empty remove-ids vals))

      clean))

(defn mobile? [width]
  (case width
    ("xs" "sm") true
    false))

(defn base-url []
  (let [m (-> js/window .-location .-href url/url)
        {:keys [protocol host port]} m]
    (str protocol "://"
         host
         (when-not (#{80 443} port) (str ":" port)))))

(defn ->row [d headers]
  (let [header-keys (map first headers)]
    (mapv (fn [k] (get d k)) header-keys)))

(defn ->csv [data headers]
  (csv/write-csv (mapv #(->row % headers) data) :quote? true))

(defn prod? []
  (-> (base-url)
      (string/includes? "lipas.fi")))
