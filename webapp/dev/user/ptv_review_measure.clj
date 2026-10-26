;; READ-ONLY phase 0 measurement for the PTV sync review v2 plan.
;; Defines helpers in a scratch ns; reads the DB (SELECTs only) and PTV
;; (list/organization GETs only). Writes nothing anywhere, no spit, no :creds.
(ns user.ptv-review-measure
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.sports-site :as ss]
            [lipas.backend.gis :as gis]
            [lipas.backend.org :as org]
            [lipas.backend.ptv.integration :as ptvi]
            [lipas.data.ptv :as ptv-data]
            [lipas.roles :as roles]))

(defn- sys []
  ;; local dev: integrant.repl.state; prod: lipas.backend.system/current-system
  (or (try (some-> (requiring-resolve 'integrant.repl.state/system) deref :lipas/db some?
                   (and @(requiring-resolve 'integrant.repl.state/system)))
           (catch Throwable _ nil))
      ;; the var holds the atom
      @@(requiring-resolve 'lipas.backend.system/current-system)))
(defn- dbc [] (:lipas/db (sys)))
(defn- ptv [] (:lipas/ptv (sys)))

(defn- quiet [f] (binding [*out* (java.io.StringWriter.)] (f)))

;;; Projection (prototype of lipas.data.ptv-review/location-values) ;;;

(defn- nws [s] (ptv-data/normalize-ws s))
(defn- num* [x] (cond (string? x) (parse-double x) (number? x) (double x)))

(defn- page-slot [{:keys [value language]}]
  (cond
    (= value (get-in ptv-data/web-page-names [:www language])) :www
    (= value (get-in ptv-data/web-page-names [:reservations-link language])) :reservations-link
    :else :other))

(defn location-values [loc]
  (let [texts (fn [k] (into {} (keep (fn [{:keys [type language value]}]
                                       (when-let [v (nws value)] [[type language] v])))
                            (get loc k)))
        loc-addrs (filter #(= "Location" (:type %)) (:addresses loc))
        addr (:streetAddress (first loc-addrs))
        street (fn [lang] (some #(when (= lang (:language %)) (:value %)) (:street addr)))
        with-no (fn [s] (nws (cond-> s (and s (:streetNumber addr)) (str " " (:streetNumber addr)))))
        muni (let [m (:municipality addr)] (if (map? m) (:code m) m))
        svcs (:services loc)]
    {:names (texts :serviceChannelNames)
     :descs (texts :serviceChannelDescriptions)
     :phones (set (map (fn [{:keys [prefixNumber number isFinnishServiceNumber language]}]
                         [language (when-not isFinnishServiceNumber prefixNumber) number])
                       (:phoneNumbers loc)))
     :emails (set (map (fn [{:keys [value language]}] [language (some-> value str/trim str/lower-case)])
                       (:emails loc)))
     :pages (update-vals (group-by page-slot (:webPages loc)) #(set (map :url %)))
     :street-fi (with-no (street "fi"))
     :streets (into {} (keep (fn [{:keys [language value]}] (when value [language (with-no value)])) (:street addr)))
     :postal (:postalCode addr)
     :municipality muni
     :point (when (and (:latitude addr) (:longitude addr)) [(num* (:latitude addr)) (num* (:longitude addr))])
     :address-count (count (:addresses loc))
     :address-types (frequencies (map :type (:addresses loc)))
     :languages (set (:languages loc))
     :services (if (string? (first svcs)) (set svcs) (set (keep (comp :id :service) svcs)))}))

(defn- dist [[a b] [c d]] (when (and a b c d) (Math/hypot (- a c) (- b d))))

(def compare-keys
  "key -> (fn [values] comparable). Web pages by URL only: their names are
   Lipas's fixed labels (unnamed before #269). Streets ignoring case: PTV
   capitalises them on save."
  {:phone (comp set #(map rest %) :phones)        ; ignore language for the slot
   :email (comp set #(map second %) :emails)
   :web-urls #(set (mapcat val (:pages %)))
   :street (comp #(some-> % str/lower-case) :street-fi)
   :postal :postal
   :municipality :municipality
   :languages :languages
   :services :services})

(defn- text-keys [l p b]
  (set (concat (keys (:names l)) (keys (:names p)) (keys (:names b))
               (map #(into [:desc] %) (keys (:descs l))) (map #(into [:desc] %) (keys (:descs p))))))

;;; Data ;;;

(defn- synced-sites [ptv-org-id]
  (->> (jdbc/query (dbc) ["SELECT id, lipas_id, document, author_id, status FROM sports_site_current
                           WHERE document #>> '{ptv,org-id}' = ?
                             AND document #>> '{ptv,last-sync}' IS NOT NULL
                             AND document #>> '{ptv,service-channel-ids,0}' IS NOT NULL" ptv-org-id])
       (map (fn [row] [(:lipas_id row) (ss/unmarshall row)]))
       (into {})))

(defn- base-revisions [lipas-ids]
  (if (empty? lipas-ids)
    {}
    (->> (jdbc/query (dbc) (into [(str "SELECT DISTINCT ON (s.lipas_id) s.id, s.lipas_id, s.document, s.author_id, s.status
                                         FROM sports_site s JOIN sports_site_current c USING (lipas_id)
                                         WHERE s.lipas_id IN (" (str/join "," (repeat (count lipas-ids) "?")) ")
                                           AND s.status = 'published'
                                           AND s.document #>> '{ptv,last-sync}' = c.document #>> '{ptv,last-sync}'
                                         ORDER BY s.lipas_id, s.created_at, s.event_date")]
                                 lipas-ids))
         (map (fn [row] [(:lipas_id row) (ss/unmarshall row)]))
         (into {}))))

(defn- payload-values [org-id site langs]
  (quiet #(location-values
            (ptv-data/->ptv-service-location org-id gis/wgs84->tm35fin-no-wrap "2026-01-01T00:00:00Z"
                                             (core/enrich (assoc-in site [:ptv :languages] langs))))))

(defn- after? [modified last-sync margin-s]
  (try
    (let [m (java.time.Instant/parse (str modified (when-not (str/ends-with? (str modified) "Z") "Z")))
          s (java.time.Instant/parse last-sync)]
      (.isAfter m (.plusSeconds s margin-s)))
    (catch Exception _ nil)))

(defn measure-org [{:keys [ptv-data name] :as _org}]
  (let [ptv-org-id (:org-id ptv-data)
        org-langs (or (:supported-languages ptv-data) ptv-data/fallback-languages)
        channels (->> (:itemList (ptvi/get-org-service-channels (ptv) ptv-org-id))
                      (filter #(= "ServiceLocation" (:serviceChannelType %)))
                      (map (juxt :id identity)) (into {}))
        sites (synced-sites ptv-org-id)
        bs (base-revisions (vec (keys sites)))]
    (vec
      (for [[lipas-id site] sites
            :let [ch (get channels (-> site :ptv :service-channel-ids first))
                  base (get bs lipas-id)]
            :when ch]
        (let [l (payload-values ptv-org-id site (ptv-data/effective-languages site org-langs))
              p (location-values ch)
              b (when base (payload-values ptv-org-id base (or (-> base :ptv :languages) org-langs)))
              ev (after? (:modified ch) (-> site :ptv :last-sync) 600)
              diffs (into {}
                          (concat
                            (for [[k f] compare-keys
                                  :let [lv (f l) pv (f p) bv (when b (f b))]
                                  :when (not= lv pv)]
                              [k {:lipas-changed (when b (not= lv bv)) :ptv-changed (when b (not= pv bv))}])
                            (for [tk (text-keys l p b)
                                  :let [get* (fn [v] (if (= :desc (first tk)) (get-in v [:descs (vec (rest tk))]) (get-in v [:names tk])))
                                        lv (get* l) pv (get* p) bv (when b (get* b))]
                                  :when (not= lv pv)]
                              [tk {:lipas-changed (when b (not= lv bv)) :ptv-changed (when b (not= pv bv))}])
                            (let [d (dist (:point l) (:point p))]
                              (when (and d (> d 1))
                                [[:point {:distance d
                                          :lipas-changed (when b (some-> (dist (:point l) (:point b)) (> 1)))
                                          :ptv-changed (when b (some-> (dist (:point p) (:point b)) (> 1)))}]]))))]
          {:org name
           :lipas-id lipas-id
           :base? (some? base)
           :evidence? ev
           :last-sync (-> site :ptv :last-sync)
           :diffs diffs
           :multi {:phones (count (:phones p))
                   :emails (count (:emails p))
                   :other-pages (count (get-in p [:pages :other]))
                   :addresses (:address-count p)
                   :street-langs-differ (> (count (set (vals (:streets p)))) 1)}
           :ptv-only-langs (let [extra (remove (:languages l) (:languages p))]
                             (when (seq extra)
                               {:langs (vec extra)
                                :enabled (vec (filter #(contains? (set (keep ptv-data/lipas-lang->ptv-lang org-langs)) %) extra))}))})))))

(defonce results (atom nil))

(defn run-all! []
  (let [orgs (filterv #(get-in % [:ptv-data :org-id]) (org/all-orgs (dbc)))]
    (reset! results
            (vec (mapcat (fn [o]
                           (try (measure-org o)
                                (catch Exception e [{:org (:name o) :error (ex-message e)}])))
                         orgs)))
    (count @results)))

(defn summary []
  (let [rs (remove :error @results)
        rows (for [r rs [k d] (:diffs r)] (assoc d :key (if (vector? k) (first k) k) :evidence? (:evidence? r) :base? (:base? r) :lipas-id (:lipas-id r)))
        bucket (fn [{:keys [base? lipas-changed ptv-changed evidence?]}]
                 (cond (not base?) :no-base
                       (and ptv-changed lipas-changed) (if evidence? :both :both-no-evidence)
                       ptv-changed (if evidence? :ptv :ptv-no-evidence)
                       lipas-changed :lipas
                       :else :neither))]
    {:errors (filterv :error @results)
     :sites (count rs)
     :sites-with-diffs (count (filter (comp seq :diffs) rs))
     :base-found (count (filter :base? rs))
     :with-evidence (count (filter :evidence? rs))
     :evidence-but-no-ptv-diff (count (filter #(and (:evidence? %) (not-any? :ptv-changed (vals (:diffs %)))) rs))
     :by-key (->> rows (group-by :key) (map (fn [[k rs]] [k (frequencies (map bucket rs))])) (into (sorted-map-by #(compare (str %1) (str %2)))))
     :examples (->> rows (group-by (juxt :key bucket)) (map (fn [[k rs]] [k (vec (take 3 (map :lipas-id rs)))])) (into {}))
     :point-distances (->> rows (filter #(= :point (:key %))) (map :distance)
                           (map #(condp > % 10 "<10m" 50 "10-50m" 200 "50-200m" 1000 "200m-1km" ">1km"))
                           frequencies)
     :multi {:phones>1 (count (filter #(> (-> % :multi :phones) 1) rs))
             :emails>1 (count (filter #(> (-> % :multi :emails) 1) rs))
             :other-pages (count (filter #(pos? (-> % :multi :other-pages)) rs))
             :addresses>1 (count (filter #(> (-> % :multi :addresses) 1) rs))
             :street-langs-differ (count (filter #(-> % :multi :street-langs-differ) rs))}
     :ptv-only-langs (frequencies (keep #(some-> (:ptv-only-langs %) (update-vals sort)) rs))
     :by-org (->> rs (group-by :org) (map (fn [[o rs]] [o [(count rs) (count (filter (comp seq :diffs) rs))]])) (into (sorted-map)))}))

;;; D2 impact: saves of synced sites by users without :ptv/manage ;;;

(defn d2-impact [days]
  (let [revs (jdbc/query (dbc) [(str "SELECT lipas_id, author_id, event_date, city_code
                                      FROM sports_site
                                      WHERE lipas_id IN (SELECT lipas_id FROM sports_site_current
                                                         WHERE document #>> '{ptv,last-sync}' IS NOT NULL)
                                        AND status = 'published'
                                        AND event_date > now() - interval '" (long days) " days'
                                        AND document #>> '{ptv,sync-enabled}' = 'true'
                                        AND document #>> '{ptv,last-sync}' IS NOT NULL
                                        AND document -> 'acting-org-id' IS NULL
                                        AND (document #>> '{ptv,last-sync}')::timestamptz <> event_date")])
        ;; org roles are derived at login (lipas.backend.auth), not stored
        users (memoize #(some->> (db/get-user-by-id (dbc) {:id (str %)}) (org/enrich-org-roles (dbc))))
        rows (for [{:keys [lipas_id author_id city_code]} revs
                   :let [u (users author_id)]]
               {:lipas-id lipas_id
                :ptv-user? (boolean (and u (roles/check-privilege u {:city-code (some-> city_code str parse-long)} :ptv/manage)))
                :author author_id})
        {ptv true other false} (group-by :ptv-user? rows)]
    {:days days
     :saves-by-ptv-users (count ptv)
     :saves-by-others (count other)
     :sites-saved-by-others (count (distinct (map :lipas-id other)))
     :other-authors (count (distinct (map :author other)))}))

(def ^:private ptv-relevant-paths
  [[:name] [:name-localized] [:marketing-name] [:email] [:phone-number] [:www]
   [:reservations-link] [:location :address] [:location :postal-code]
   [:location :city :city-code] [:location :geometries] [:status] [:type :type-code]
   [:owner]])

(defn d2-drilldown
  "Saves of synced sites by users without :ptv/manage in the last `days`:
   who (counts per author, flags only, no names) and what they changed."
  [days]
  (let [revs (jdbc/query (dbc) [(str "SELECT s.lipas_id, s.author_id, s.event_date, s.city_code, s.document,
                                             (SELECT p.document FROM sports_site p
                                              WHERE p.lipas_id = s.lipas_id AND p.status = 'published'
                                                AND p.event_date < s.event_date
                                              ORDER BY p.event_date DESC LIMIT 1) AS prev
                                      FROM sports_site s
                                      WHERE s.lipas_id IN (SELECT lipas_id FROM sports_site_current
                                                           WHERE document #>> '{ptv,last-sync}' IS NOT NULL)
                                        AND s.status = 'published'
                                        AND s.event_date > now() - interval '" (long days) " days'
                                        AND s.document #>> '{ptv,sync-enabled}' = 'true'
                                        AND s.document #>> '{ptv,last-sync}' IS NOT NULL
                                        AND s.document -> 'acting-org-id' IS NULL
                                        AND (s.document #>> '{ptv,last-sync}')::timestamptz <> s.event_date")])
        ;; org roles are derived at login (lipas.backend.auth), not stored
        users (memoize #(some->> (db/get-user-by-id (dbc) {:id (str %)}) (org/enrich-org-roles (dbc))))
        rows (for [{:keys [lipas_id author_id city_code document prev]} revs
                   :let [u (users author_id)]
                   :when (not (and u (roles/check-privilege u {:city-code (some-> city_code str parse-long)} :ptv/manage)))]
               {:lipas-id lipas_id
                :author author_id
                :robot? (boolean (some->> (:username u) (re-find #"(?i)robot|system|import|integra")))
                :ptv-anywhere? (boolean (and u (roles/check-privilege u {:city-code ::roles/any} :ptv/manage)))
                :changed (set (for [path ptv-relevant-paths
                                    :when (not= (get-in document path) (get-in prev path))]
                                (first path)))})]
    {:saves (count rows)
     :authors (->> rows (group-by :author) vals
                   (map (fn [rs] [(count rs) (:robot? (first rs)) (:ptv-anywhere? (first rs))]))
                   (sort-by first >) vec)
     :saves-touching-ptv-fields (count (filter (comp seq :changed) rows))
     :field-counts (frequencies (mapcat :changed rows))}))
