(ns lipas.ui.utils
  (:require [cemerick.url :as url]
            [cljsjs.date-fns]
            [clojure.data :as data]
            [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [goog.crypt.base64 :as b64]
            [lipas.utils :as utils]
            [re-frame.core :as re-frame]))

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

(def index-by utils/index-by)

(defn localize-field [tr k prefix m]
  (if (k m)
    (update m k #(tr (keyword prefix %)))
    m))

(defn ->localized-select-entry [tr prefix k]
  (->> {:value k :label k}
       (localize-field tr :label prefix)))

(defn ->select-entries [tr prefix enum-map]
  (map (partial ->localized-select-entry tr prefix) (keys enum-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO refactor to use js/dateFns where appropriate ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment (resolve-year "2014-12-02"))
(comment (resolve-year 2014))
(defn resolve-year [timestamp]
  (read-string (reduce str (take 4 (str timestamp)))))

(def this-year (.getFullYear (js/Date.)))

(defn this-year? [x]
  (= (str x) (str this-year)))

(defn timestamp []
  (.toISOString (js/Date.)))

(defn ->begin-of-year [year]
  (str year "-01-01T00:00:00.000Z"))

(defn ->end-of-year [year]
  (str year "-12-31T23:59:59.999Z"))

(defn pretty-since-kw [s]
  (let [tz-offset     (.getTimezoneOffset (js/Date.))
        ts-utc        (.parse js/dateFns s)
        ts            (.addMinutes js/dateFns ts-utc (- tz-offset))
        now           (.addMinutes js/dateFns (js/Date.) (- tz-offset))
        minutes-delta (.differenceInMinutes js/dateFns now ts)
        hours-delta   (.differenceInHours js/dateFns now ts)
        years-delta   (.differenceInCalendarYears js/dateFns now ts)]
    (cond
      (< minutes-delta 10)         :just-a-moment-ago
      (< hours-delta 1)            :less-than-hour-ago
      (.isToday js/dateFns ts)     :today
      (.isYesterday js/dateFns ts) :yesterday
      (.isThisWeek js/dateFns ts)  :this-week
      (.isThisMonth js/dateFns ts) :this-month
      (.isThisYear js/dateFns ts)  :this-year
      (= years-delta 1)            :last-year
      (= years-delta 2)            :two-years-ago
      (= years-delta 3)            :three-years-ago
      :else                        :long-time-ago)))

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
  (let [history (index-by :year history)]
    (for [y    (range 2000 (inc this-year))
          :let [data-exists? (data-exists? y history)]]
      {:label (if data-exists?
                (str y " " "✓")
                (str y))
       :value y})))

(defn some-energy-data-exists? [{:keys [energy-consumption
                                        energy-consumption-monthly]}]
  (or (:electricity-mwh energy-consumption)
      (:heat-mwh energy-consumption)
      (:water-m3 energy-consumption)
      (:cold-mwh energy-consumption)
      (not-empty (vals energy-consumption-monthly))))

(defn energy-consumption-history [{:keys [history]}]
  (let [by-year (latest-by-year history)
        entries (select-keys history (vals by-year))]
    (->> entries
         vals
         (filter some-energy-data-exists?)
         (map #(assoc (:energy-consumption %)
                      :year (resolve-year (:event-date %)))))))

(defn some-visitor-data-exists? [{:keys [visitors visitors-monthly]}]
  (or (:total-count visitors)
      (:spectators visitors)
      (not-empty (vals visitors-monthly))))

(defn visitors-history [{:keys [history]}]
  (let [by-year (latest-by-year history)
        entries (select-keys history (vals by-year))]
    (->> entries
         vals
         (filter some-visitor-data-exists?)
         (map #(assoc (:visitors %)
                      :year (resolve-year (:event-date %)))))))

(defn find-revision [{:keys [history]} year]
  (let [latest-by-year (latest-by-year history)
        timestamp      (get latest-by-year year)]
    (get history timestamp)))

(comment
  (.getFullYear (.parse js/dateFns "2014-01-01T00:00:00.000Z")) ; This works
  (.getFullYear (.parse js/dateFns "2013-12-31T23:59:59.999Z")) ; Interesting...
  (.getFullYear (.parse js/dateFns "2013-12-31T23:59:59.999"))) ; This works
(defn same-year? [ts1 ts2]
  (.isSameYear js/dateFns
               (string/replace ts1 "Z" "")
               (string/replace ts2 "Z" "")))

(defn make-revision
  ([site]
   (make-revision site (timestamp)))
  ([site event-date]
   (let [history-with-edits (merge (:history site) (:edits site))
         prev-rev           (resolve-prev-rev history-with-edits event-date)
         same-year?         (same-year? (:event-date prev-rev) event-date)]
     (-> prev-rev
         (assoc :event-date event-date)
         (as-> $ (if same-year?
                   $
                   (-> $
                       (dissoc :energy-consumption)
                       (dissoc :energy-consumption-monthly)
                       (dissoc :visitors)
                       (dissoc :visitors-monthly))))))))

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
      (dissoc :event-date)
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
  (assoc-in db [:sports-sites lipas-id :editing] nil))

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

(defn make-editable [sports-site]
  (-> sports-site

      ;; Swimming pools
      (update-in [:pools]  ->indexed-map)
      (update-in [:saunas] ->indexed-map)
      (update-in [:slides] ->indexed-map)

      ;; Ice Stadiums
      (update-in [:rinks] ->indexed-map)))

(defn valid? [sports-site] ;; TODO maybe multimethod?
  (let [spec (case (-> sports-site :type :type-code)
               (3110 3120 3130) :lipas.sports-site/swimming-pool
               (2510 2520)      :lipas.sports-site/ice-stadium
               :lipas/sports-site)]
    ; (s/explain spec sports-site)
    (s/valid? spec sports-site)))

(defn mobile? [width]
  (case width
    ("xs" "sm") true
    false))

(defn base-url []
  (let [m (-> js/window .-location .-href url/url)
        {:keys [protocol host port]} m]
    (str protocol "://"
         host
         (when-not (#{80 443 -1 nil ""} port) (str ":" port)))))

(defn domain []
  (-> js/window .-location .-href url/url :host))

(defn current-path []
  (let [path (-> js/window .-location .-href url/url :anchor)]
    (str "/#" path)))

(defn parse-token [s]
  (-> s
      url/url
      :anchor
      (string/split "?token=")
      second))

(defn prod? []
  (-> (base-url)
      (string/includes? "lipas.fi")))

(defn year-labels-map [start end]
  (->> (range start (inc end))
       (map (juxt identity str))
       (into {})))

(defn truncate-size-category [s]
  (when (string? s)
    (-> s
        (string/split #"(<|>)")
        first
        (string/trim))))

(defn ->excel-row [headers m]
  (reduce
   (fn [res [k _]]
     (let [v (get m k)]
       (conj res {:value v
                  :type  (if (number? v)
                           "number"
                           "string")})))
   [] headers))

(defn ->excel-data [headers coll]
  (let [header-row (->excel-row headers (into {} headers))]
    (into [header-row]
          (mapv (partial ->excel-row headers) coll))))

(comment
  (->excel-data [[:kissa "Kissa"] [:kana "Kana"]]
                [{:kissa "koira" :kana 12}])
  (->excel-row [[:kissa "Kissa"] [:kana "Kana"]]
                (into {} [[:kissa "Kissa"] [:kana "Kana"]])))
