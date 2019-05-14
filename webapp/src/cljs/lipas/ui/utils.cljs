(ns lipas.ui.utils
  (:require
   [cemerick.url :as url]
   ;;[clojure.data :as data]
   [clojure.reader :refer [read-string]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [goog.crypt.base64 :as b64]
   [goog.labs.userAgent.browser :as gbrowser]
   [lipas.utils :as utils]
   [re-frame.core :as re-frame]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(defn set-field [db path value]
  (if value
    (assoc-in db path value)
    (if-let [base-path (butlast path)]
      (update-in db (into [] base-path) dissoc (last path))
      (dissoc db path))))

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

(comment
  (resolve-year 2014)
  (resolve-year "2014-12-02"))
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

(def reverse-cmp utils/reverse-cmp)

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

(defn same-year? [ts1 ts2]
  (= (subs (str ts1) 0 4)
     (subs (str ts2) 0 4)))

(defn make-revision
  ([site]
   (make-revision site (timestamp)))
  ([site event-date]
   (let [history-with-edits (merge (:history site) (:edits site))
         prev-rev           (resolve-prev-rev history-with-edits event-date)
         new-rev            (assoc prev-rev :event-date event-date)]
     (if (same-year? (:event-date prev-rev) event-date)
       new-rev
       (-> new-rev
           (dissoc :energy-consumption
                   :energy-consumption-monthly
                   :visitors
                   :visitors-monthly))))))

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

(defn latest? [rev history]
  (let [event-date  (:event-date rev)
        event-dates (conj (keys history) event-date)]
    (= event-date (first (sort reverse-cmp event-dates)))))

(comment (->basic-auth {:username "kissa" :password "koira"}))
(defn ->basic-auth
  "Creates base64 encoded Authorization header value"
  [{:keys [username password]}]
  (str "Basic " (b64/encodeString (str username ":" password))))

(defn decode-jwt-payload [s]
  (-> s
      (string/split #"\.")
      second
      (b64/decodeString true)
      js/JSON.parse
      (js->clj :keywordize-keys true)))

(defn jwt-expired?
  "`s` is a jwt-token as string"
  [s]
  (let [now (-> (js/Date.) .getTime (/ 1000))
        exp (-> s decode-jwt-payload :exp)]
    (> now exp)))

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
  (let [url (-> js/window .-location .-href url/url)]
    (if-let [anchor (:anchor url)]
      (str "/#" anchor)
      (:path url))))

(defn parse-token [s]
  (-> s
      url/url
      :query
      (get "token")))

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

;; https://caniuse.com/#search=position%3Asticky
(defn supports-sticky? []
  (cond
    (and
     (gbrowser/isEdge)
     (gbrowser/isVersionOrHigher 16)) true
    (gbrowser/isChrome)               true
    (gbrowser/isFirefox)              true
    (gbrowser/isSilk)                 true
    (gbrowser/isCoast)                true
    (gbrowser/isOpera)                true
    (gbrowser/isAndroidBrowser)       true
    :else                             false))

(defn supports-webkit-sticky? []
  (or (gbrowser/isSafari)
      (gbrowser/isIosWebview)))

(defn ie? []
  (gbrowser/isIE))

(defn add-to-db [db {:keys [lipas-id event-date] :as rev}]
  (let [new-db (assoc-in db [:sports-sites lipas-id :history event-date] rev)]
    (if (latest? rev (get-in db [:sports-sites lipas-id :history]))
      (assoc-in new-db [:sports-sites lipas-id :latest] event-date)
      new-db)))

(defn ->feature [{:keys [lipas-id name type] :as site}]
  (let [type-code (-> type :type-code)]
    (-> site
        :location
        :geometries
        (update-in [:features]
                   #(map-indexed (fn [idx f]
                                   (-> f
                                       (assoc-in [:id] (str lipas-id "-" idx))
                                       (assoc-in [:properties :name] name)
                                       (assoc-in [:properties :lipas-id] lipas-id)
                                       (assoc-in [:properties :type-code] type-code)))
                                 %)))))

(defn ->short-date [s]
  (-> s (string/split #"T") first))

(defn ->human-date [s]
  (-> s
      (string/split #"T")
      first
      (string/split "-")
      reverse
      (->> (string/join "."))))

(defn navigate!
  ([path]
   (navigate! path :comeback? false))
  ([path & {:keys [comeback?]}]
   (when comeback?
     (==> [:lipas.ui.login.events/set-comeback-path (current-path)]))
   (==> [:lipas.ui.events/navigate path])))

(def check-mark "✓")

(defn link? [x]
  (and (string? x)
       (or
        (string/starts-with? x "http")
        (string/starts-with? x "www"))))

(defn truncate [s]
  (if (> (count s) 30)
    (str (subs s 0 27) "...")
    s))

(defn display-value [v & {:keys [empty links?]
                          :or   {empty  ""
                                 links? true}}]
  (cond
    (link? v)  (if links? [:a {:href v} (truncate v)] v)
    (coll? v)  (if (empty? v) empty (string/join ", " v))
    (true? v)  check-mark
    (false? v) empty
    (nil? v)   empty
    :else      v))

(defn ->mailto [{:keys [email subject body]}]
  (str "mailto:" email "?" (url/map->query {:subject subject :body body})))

(defn now+ [ms]
  (.toISOString (js/Date. (-> (js/Date.) .getTime (+ ms)))))

(defn round-safe
  ([x]
   (round-safe x 2))
  ([x precision]
   (if (number? x)
     (.toFixed x precision))))
