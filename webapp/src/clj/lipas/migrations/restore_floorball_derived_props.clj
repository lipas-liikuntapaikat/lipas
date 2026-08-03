(ns lipas.migrations.restore-floorball-derived-props
  "Restore salibandyhalli (2240) props that the old frontend auto-derivation
  wiped. Until fix/floorball-derivable-props, editing a hall recomputed 8
  props (dimensions, surface material, fields count, stand capacity) from
  the structured :fields data on every edit — stamping nil/\"\"/0/[] over
  existing values whenever :fields was empty, and dropping resin/carpet
  surface materials through a too-narrow enum filter even when it wasn't.

  For each current 2240 revision, fills props that are currently empty:
  preferring a fresh derivation from :fields (fixed fns), falling back to
  the latest non-empty value found in the site's revision history. Never
  overwrites a non-empty current value. Updates the current revision in
  place (same approach as fix-postal-codes).

  NOTE: the search index goes stale for the repaired sites — run a search
  reindex after deploying this migration."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [lipas.data.floorball :as floorball]
            [lipas.data.prop-types :as prop-types]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def derivable-prop-ks
  (keys floorball/prop-k->derive-fn))

(defn- parse-doc
  "Migrations run without lipas.backend.db's PGobject extensions, so the
  document column may arrive raw; the REPL has them loaded and returns maps."
  [doc]
  (if (map? doc)
    doc
    (json/parse-string (str doc) true)))

(defn- clean-str [v]
  (if (string? v) (not-empty (str/trim v)) v))

(defn- empty-val? [v]
  (or (nil? v)
      (= v "")
      (and (number? v) (zero? v))
      (and (sequential? v) (empty? v))))

(defn- latest-nonempty
  "Latest non-empty historical value of prop `k`; `history` is newest-first."
  [history k]
  (->> history
       (map #(get-in % [:properties k]))
       (map clean-str)
       (remove empty-val?)
       first))

(defn- repair-for-site
  "Props to fill for one site: currently-empty derivable props, valued from
  a fresh :fields derivation or, failing that, from revision history. Every
  candidate value is validated against the prop's schema before inclusion."
  [current history]
  (let [props (:properties current)
        derived (or (floorball/derive-props (:fields current)) {})]
    (into {}
          (for [k derivable-prop-ks
                :when (empty-val? (clean-str (get props k)))
                :let [v (or (get derived k) (latest-nonempty history k))
                      schema (get prop-types/schemas k)]
                :when (and (some? v) schema (m/validate schema v))]
            [k v]))))

(defn compute-plan
  "Pure-read repair plan; REPL-callable for a dry run."
  [db]
  (let [rows (jdbc/execute!
               db
               ["SELECT id, lipas_id, event_date, document
                 FROM sports_site
                 WHERE type_code = 2240 AND status = 'published'
                 ORDER BY lipas_id, event_date"]
               {:builder-fn rs/as-unqualified-maps})]
    (for [[lipas-id revs] (group-by :lipas_id rows)
          :let [current-row (last revs)
                current (parse-doc (:document current-row))
                history (->> (butlast revs) reverse (map (comp parse-doc :document)))]
          :when (contains? #{"active" "out-of-service-temporarily"} (:status current))
          :let [repair (repair-for-site current history)]
          :when (seq repair)]
      {:lipas-id lipas-id
       :name (:name current)
       :row-id (:id current-row)
       :current-props (:properties current)
       :repair repair})))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: restore-floorball-derived-props")
  (let [plan (compute-plan db)]
    (log/info "Found" (count plan) "salibandyhallis with restorable prop values")
    (doseq [{:keys [lipas-id name row-id current-props repair]} plan]
      (log/info "Restoring lipas-id" lipas-id (pr-str name) "->" (pr-str repair))
      (jdbc/execute-one!
        db
        ["UPDATE sports_site
          SET document = jsonb_set(document, '{properties}', ?::jsonb, true)
          WHERE id = ?"
         (json/generate-string (merge current-props repair))
         row-id]))
    (log/info "Migration complete: restore-floorball-derived-props. Repaired"
              (count plan) "sites")
    (when (seq plan)
      (log/warn "Search index is now stale for the repaired sites"
                "- run a search reindex."))))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for restore-floorball-derived-props migration"))
