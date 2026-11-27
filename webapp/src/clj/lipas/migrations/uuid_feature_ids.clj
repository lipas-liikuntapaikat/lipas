(ns lipas.migrations.uuid-feature-ids
  "Give every feature of a sports site a UUID id.

  Before the map editor rewrite (PR #103, merged 2024-03-04), saving a site
  could store feature ids the editor used internally: `<lipas-id>-<n>` and
  `tempN`. Production had 144 of them on 33 sites (2026-11-20, every
  status), the newest from 2024-02-28; every id created since is a UUID.
  API v2 returns feature ids, and activity routes refer to the features
  they consist of by id (`:fids`), so the ids should all be UUIDs.

  For each site whose current revision has a feature id that isn't one, a
  copy of that revision is appended (robot@lipas.fi) with each such id
  replaced by a new random UUID everywhere the site uses it: the feature
  itself, the `:fids` of its activity routes, and the features of route
  geometries stored with a route. A fid that matches no feature is left as
  it is. The copy keeps the current revision's event_date, so it becomes
  current without looking like an edit to anything that compares event
  dates (PTV sync state, the v1 API's lastModified). The originals stay as
  the audit trail.

  Every copy is checked before it is written, and a site that fails a check
  fails the migration: an old id used anywhere else in the document is a
  reference this can't rewrite.

  `compute-plan` is a pure-read dry run.

  NOTE: reindex the repaired sites right after deploying (the migration logs
  their lipas-ids). Until then the search index serves their old ids, which
  the UUID feature-id schema rejects: v2 answers 500 for them, and saving
  one from the editor fails with 400."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.sports-site :as sports-site]
            [lipas.schema.common :as common]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private robot-email "robot@lipas.fi")

(defn uuid-id?
  "Whether `id` is written the way feature ids are now: a lowercase UUID v4,
  as `lipas.schema.common/uuid` requires."
  [id]
  (and (string? id) (some? (re-matches common/-uuid-pattern id))))

;;; Pure part ;;;

(defn- features
  "The features of FeatureCollection `fcoll`, or nil when it has none."
  [fcoll]
  (let [fs (:features fcoll)]
    (when (sequential? fs) fs)))

(defn- routes
  "The activity routes of `doc`."
  [doc]
  (when (map? (:activities doc))
    (for [[_ activity] (:activities doc)
          :when (and (map? activity) (sequential? (:routes activity)))
          route (:routes activity)
          :when (map? route)]
      route)))

(defn- ids-of [features]
  (->> features (filter map?) (keep :id)))

(defn feature-ids
  "Every feature id of `doc`: those of its own features and of the route
  geometries stored with its activity routes."
  [doc]
  (concat (ids-of (features (get-in doc [:location :geometries])))
          (ids-of (mapcat (comp features :geometries) (routes doc)))))

(defn legacy-ids
  "The feature ids of `doc` that aren't UUIDs, each once."
  [doc]
  (->> (feature-ids doc) (remove uuid-id?) distinct vec))

(defn rename-plan
  "A new random UUID for each legacy id of `doc`, as {old-id new-id}."
  [doc]
  (into {} (map (fn [id] [id (str (random-uuid))])) (legacy-ids doc)))

(defn- rename-features [fcoll rename]
  (if (features fcoll)
    (update fcoll :features
            (partial mapv (fn [f]
                            (if (and (map? f) (contains? rename (:id f)))
                              (assoc f :id (rename (:id f)))
                              f))))
    fcoll))

(defn- rename-route [route rename]
  (cond-> route
    (sequential? (:fids route))
    (update :fids (partial mapv #(get rename % %)))

    (features (:geometries route))
    (update :geometries rename-features rename)))

(defn- rename-routes [activities rename]
  (reduce-kv (fn [acc k activity]
               (cond-> acc
                 (and (map? activity) (sequential? (:routes activity)))
                 (assoc k (update activity :routes
                                  (partial mapv #(cond-> %
                                                   (map? %) (rename-route rename)))))))
             activities
             activities))

(defn rename-feature-ids
  "`doc` with the feature ids in `rename` ({old-id new-id}) replaced
  everywhere the site uses them: its features, the :fids of its activity
  routes and the features of route geometries stored with a route."
  [doc rename]
  (cond-> doc
    (features (get-in doc [:location :geometries]))
    (update-in [:location :geometries] rename-features rename)

    (map? (:activities doc))
    (update :activities rename-routes rename)))

(defn- mentions
  "The members of `ids` that `doc` contains anywhere, as a value or as a map
  key (which the JSON reader turns into a keyword)."
  [doc ids]
  (->> (tree-seq coll? seq doc)
       (keep (fn [x]
               (cond
                 (contains? ids x) x
                 (keyword? x) (let [n (name x)] (when (contains? ids n) n)))))
       distinct
       vec))

(defn check-copy
  "The problems of `copy`, made from `doc` with `rename`, or [] when it is
  `doc` with exactly those ids replaced and nothing else changed."
  [doc rename copy]
  (let [left (legacy-ids copy)
        elsewhere (mentions copy (set (keys rename)))]
    (cond-> []
      (seq left)
      (conj (str "feature ids that still aren't UUIDs: " (pr-str left)))

      (seq elsewhere)
      (conj (str "old ids used outside features, :fids and route geometries: "
                 (pr-str elsewhere)))

      (not= doc (rename-feature-ids copy (set/map-invert rename)))
      (conj "renaming the ids back doesn't give the original document"))))

(defn- usage
  "Where `doc` uses `ids`."
  [doc ids]
  {:features (count (filter ids (ids-of (features (get-in doc [:location :geometries])))))
   :fids (count (filter ids (mapcat #(when (sequential? (:fids %)) (:fids %)) (routes doc))))
   :route-geometries (count (filter ids (ids-of (mapcat (comp features :geometries) (routes doc)))))})

;;; Database ;;;

(defn- query [db sql-params]
  (jdbc/execute! db sql-params {:builder-fn rs/as-unqualified-maps}))

(def ^:private candidates-sql
  "Sites whose current revision has a feature id that isn't a UUID, in its
  own features or in a route geometry. A JSON null counts as no id. The
  pattern is passed in, so SQL and `uuid-id?` agree."
  "SELECT c.lipas_id
   FROM sports_site_current c
   WHERE EXISTS (SELECT 1
                 FROM jsonb_path_query(c.document, '$.location.geometries.features[*].id') i
                 WHERE jsonb_typeof(i) <> 'null'
                   AND NOT (jsonb_typeof(i) = 'string' AND i #>> '{}' ~ ?))
      OR EXISTS (SELECT 1
                 FROM jsonb_path_query(c.document, '$.activities.*.routes[*].geometries.features[*].id') i
                 WHERE jsonb_typeof(i) <> 'null'
                   AND NOT (jsonb_typeof(i) = 'string' AND i #>> '{}' ~ ?))
   ORDER BY c.lipas_id")

(defn candidates
  "lipas-ids of the sites whose current revision has a feature id that isn't
  a UUID."
  [db]
  (let [pattern (.pattern ^java.util.regex.Pattern common/-uuid-pattern)]
    (mapv :lipas_id (query db [candidates-sql pattern pattern]))))

(defn- plan-site
  "The current revision of `lipas-id` and the ids to replace in it."
  [java-db lipas-id]
  (let [row (sports-site/get java-db {:lipas_id lipas-id})
        doc (sports-site/unmarshall row)]
    {:lipas-id lipas-id
     :row row
     :doc doc
     :rename (rename-plan doc)}))

(defn compute-plan
  "Pure-read dry run: the sites migrate-up would copy, their legacy ids and
  where each site uses them."
  [db]
  (->> (candidates db)
       (mapv (fn [lipas-id]
               (let [{:keys [doc]} (plan-site db lipas-id)
                     ids (legacy-ids doc)]
                 (merge {:lipas-id lipas-id
                         :name (:name doc)
                         :status (:status doc)
                         :ids ids}
                        (usage doc (set ids))))))))

(defn- copy!
  "Appends the copy of the site's current revision with its ids replaced,
  after checking it."
  [java-db robot {:keys [lipas-id row doc rename]}]
  (let [copy (rename-feature-ids doc rename)
        problems (check-copy doc rename copy)]
    (when (seq problems)
      (throw (ex-info (str "Can't give the features of " lipas-id " UUID ids: "
                           (str/join "; " problems))
                      {:lipas-id lipas-id :problems problems})))
    (->> (sports-site/marshall (assoc copy :event-date (db/row-event-date row doc))
                               robot
                               "published")
         (sports-site/insert-sports-site-rev! java-db))
    (log/info "Feature ids of" lipas-id (pr-str (:name doc)) (str "(" (:status doc) ")")
              "replaced:" (pr-str rename))))

(defn- connections
  "next.jdbc and clojure.java.jdbc handles on one connection, as in
  lipas.migrations.repair-backdated-closures: migratus passes its
  transaction's connection as :conn (:db is the plain spec, outside the
  transaction); tests and the REPL pass only :db. :level 1 tells
  clojure.java.jdbc it is already inside a transaction, so its inserts
  don't commit on their own."
  [{:keys [conn db]}]
  (if conn
    [conn {:connection conn :level 1 :rollback (atom false)}]
    [db db]))

(defn migrate-up
  "All writes go through the same connection, so under migratus the repair
  is one transaction: a site that fails its checks rolls all of it back."
  [config]
  (log/info "Starting migration: uuid-feature-ids")
  (let [[db java-db] (connections config)
        sites (mapv #(plan-site java-db %) (candidates db))]
    (log/info "Sites with feature ids that aren't UUIDs:" (count sites))
    (when (seq sites)
      ;; Looked up only when there is work to do: fresh databases (CI, empty
      ;; installs) have neither sports sites nor the robot user.
      (let [robot (first (query db ["SELECT id, email, username FROM account WHERE email = ?"
                                    robot-email]))]
        (when-not (:id robot)
          (throw (ex-info "robot@lipas.fi user not found" {})))
        (doseq [site sites
                :when (seq (:rename site))]
          (copy! java-db robot site))))
    ;; Catches a site the query selects but the copy didn't fix, and a site
    ;; saved meanwhile with a newer created_at than the copy's.
    (when-let [left (seq (candidates db))]
      (throw (ex-info "Sites still have feature ids that aren't UUIDs"
                      {:lipas-ids (vec left)})))
    (when (seq sites)
      (log/warn "The search index still has the old ids, which v2 rejects - reindex these sites now:"
                (pr-str (mapv :lipas-id sites))))
    (log/info "Migration complete: uuid-feature-ids")))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for uuid-feature-ids migration;"
            "the revisions before it are still in sports_site"))
