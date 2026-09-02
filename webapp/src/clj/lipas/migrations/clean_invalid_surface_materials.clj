(ns lipas.migrations.clean-invalid-surface-materials
  "Replace deprecated values in the site-level :surface-material prop with
  their LIPAS equivalents: resin (\"Massa\") -> synthetic
  (\"Muovi/synteettinen\"), natural-surface (\"Luonnonmukainen\", a 2023
  routes-era near-duplicate) -> soil (\"Maa/luonnonmukainen\"), and
  carpet (\"Matto\") -> removed — a carpet is a movable overlay, not a
  surface material in the LIPAS sense, and what lies under it is not
  recorded anywhere. The values stay in the schemas so historical
  revisions keep validating; only current data is normalized.

  These values leaked in through short-lived wide pickers (carpet/resin
  via the floorball fields derivation, natural-surface Dec 2023 - May
  2024). Because this rewrites values instead of filling empty ones, each
  fix is appended as a new revision (authored by robot@lipas.fi) so the
  old value stays in the site's history.

  NOTE: the search index goes stale for the cleaned sites — run a search
  reindex after deploying this migration."
  (:require [cheshire.core :as json]
            [lipas.backend.db.db :as db]
            [lipas.data.prop-types :as prop-types]
            [lipas.utils :as utils]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def replacement
  {"resin" "synthetic"
   "natural-surface" "soil"
   "carpet" nil})

(defn clean-surface-material
  "Mapped, deduped surface-material coll; nil when nothing survives."
  [sm]
  (->> sm
       (keep #(if (contains? replacement %) (get replacement %) %))
       distinct
       vec
       not-empty))

(defn- parse-doc
  "Migrations run without lipas.backend.db's PGobject extensions, so the
  document column may arrive raw; the REPL has them loaded and returns maps."
  [doc]
  (if (map? doc)
    doc
    (json/parse-string (str doc) true)))

(defn compute-plan
  "Pure-read cleanup plan; REPL-callable for a dry run."
  [db-spec]
  (let [rows (jdbc/execute!
               db-spec
               ["SELECT lipas_id, document
                 FROM sports_site_current
                 WHERE jsonb_exists_any(document->'properties'->'surface-material',
                                        ARRAY['carpet','resin','natural-surface'])"]
               {:builder-fn rs/as-unqualified-maps})
        schema (get prop-types/schemas :surface-material)]
    (for [{:keys [lipas_id document]} rows
          :let [site (parse-doc document)
                sm (get-in site [:properties :surface-material])]]
      (if-not (sequential? sm)
        {:lipas-id lipas_id :skip (str "unexpected value " (pr-str sm))}
        (let [cleaned (clean-surface-material sm)]
          (if (and (some? cleaned) (not (m/validate schema cleaned)))
            {:lipas-id lipas_id :skip (str "cleaned value fails schema " (pr-str cleaned))}
            {:lipas-id lipas_id
             :name (:name site)
             :old sm
             :new cleaned
             :site (as-> site $
                     (assoc $ :event-date (utils/timestamp))
                     (if cleaned
                       (assoc-in $ [:properties :surface-material] cleaned)
                       (update $ :properties dissoc :surface-material)))}))))))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: clean-invalid-surface-materials")
  (let [plan (compute-plan db)
        {skips true fixes false} (group-by (comp some? :skip) plan)]
    (doseq [{:keys [lipas-id skip]} skips]
      (log/warn "Skipping lipas-id" lipas-id ":" skip))
    (log/info "Found" (count fixes) "sports sites with deprecated surface materials")
    (when (seq fixes)
      ;; Looked up only when there is work to do — fresh databases (CI,
      ;; empty installs) have neither sports sites nor the robot user.
      (let [user (db/get-user-by-email db {:email "robot@lipas.fi"})]
        (when-not (:id user)
          (throw (ex-info "robot@lipas.fi user not found" {})))
        (doseq [{:keys [lipas-id name old new site]} fixes]
          (log/info "Cleaning lipas-id" lipas-id (pr-str name)
                    (pr-str old) "->" (pr-str new))
          (db/upsert-sports-site! db user site))
        (log/warn "Search index is now stale for the cleaned sites"
                  "- run a search reindex.")))
    (log/info "Migration complete: clean-invalid-surface-materials. Cleaned"
              (count fixes) "sites")))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for clean-invalid-surface-materials migration"))
