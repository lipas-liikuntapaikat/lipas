(ns lipas.migrations.fix-ptv-archived-stale-revisions
  "Restore the PTV archive result for sites whose browser-dated revision
  outranked it.

  Closing a PTV-integrated site (out-of-service-permanently) writes two
  revisions: the user's, dated by the browser clock and still carrying the
  pre-archive :ptv (Published), and the PTV sync's, dated by the server
  clock (Deleted). With the browser clock ahead, the user's revision was the
  newer one and so the current one, and saving the sync's revision ran the
  backdating rule in db/upsert-sports-site!, which re-marked it
  incorrect-data. Fixed going forward in core/save-sports-site! (future
  event-dates are clamped); this repairs the sites already affected.

  In production (checked 2026-10-11) that's 7 sites, Raahe 78380, 88935,
  89627, 603489 and Utajärvi 501480, 520373, 520817, Jan-Oct 2026. PTV has
  no live version of any of their service locations; only LIPAS's record is
  wrong.

  Matched by the signature, not by id: the current revision is
  incorrect-data + Published, and the revision just before it (at most a
  minute earlier) is out-of-service-permanently + Deleted. For each, a
  revision is appended 1 ms after the stale one (authored by
  robot@lipas.fi) with the status the user chose and the archive's :ptv.
  History is kept as is.

  NOTE: run a search reindex after deploying, or the affected sites keep
  whatever the index last saw."
  (:require [cheshire.core :as json]
            [lipas.backend.db.db :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private max-gap-ms
  "How far the stale revision may be from the archive revision before it."
  60000)

(defn- parse-doc
  "Migrations run without lipas.backend.db's PGobject extensions, so the
  document column may arrive raw; the REPL has them loaded and returns maps."
  [doc]
  (if (map? doc)
    doc
    (json/parse-string (str doc) true)))

(defn- ->instant [t]
  (cond
    (instance? java.time.Instant t) t
    (instance? java.sql.Timestamp t) (.toInstant ^java.sql.Timestamp t)
    (instance? java.time.OffsetDateTime t) (.toInstant ^java.time.OffsetDateTime t)
    :else (java.time.Instant/parse (str t))))

(defn compute-plan
  "Pure-read repair plan; REPL-callable for a dry run."
  [db-spec]
  (let [rows (jdbc/execute!
               db-spec
               ["WITH suspects AS (
                   SELECT lipas_id FROM sports_site_current
                   WHERE document->>'status' = 'incorrect-data'
                     AND document #>> '{ptv,publishing-status}' = 'Published'),
                 ranked AS (
                   SELECT s.lipas_id, s.event_date, s.document,
                          row_number() OVER (PARTITION BY s.lipas_id ORDER BY s.event_date DESC) AS rn
                   FROM sports_site s JOIN suspects USING (lipas_id)
                   WHERE s.status = 'published')
                 SELECT lipas_id, event_date, document, rn FROM ranked WHERE rn <= 2"]
               {:builder-fn rs/as-unqualified-maps})]
    (for [[lipas-id revs] (group-by :lipas_id rows)
          :let [{stale :document stale-at :event_date} (first (filter #(= 1 (:rn %)) revs))
                {archive :document archive-at :event_date} (first (filter #(= 2 (:rn %)) revs))
                stale (some-> stale parse-doc)
                archive (some-> archive parse-doc)
                gap-ms (when (and stale-at archive-at)
                         (- (.toEpochMilli (->instant stale-at))
                            (.toEpochMilli (->instant archive-at))))]
          :when (and stale archive
                     (= "out-of-service-permanently" (:status archive))
                     (= "Deleted" (get-in archive [:ptv :publishing-status]))
                     (<= 0 gap-ms max-gap-ms))]
      {:lipas-id lipas-id
       :name (:name stale)
       :gap-ms gap-ms
       :site (assoc stale
                    :status "out-of-service-permanently"
                    :ptv (:ptv archive)
                    :event-date (str (.plusMillis (->instant stale-at) 1)))})))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: fix-ptv-archived-stale-revisions")
  (let [plan (compute-plan db)]
    (log/info "Found" (count plan) "sites whose PTV archive revision was outranked")
    (when (seq plan)
      ;; Looked up only when there is work to do — fresh databases (CI,
      ;; empty installs) have neither sports sites nor the robot user.
      ;; Raw SQL with a pinned column list (see clean-invalid-surface-materials).
      (let [user (first (jdbc/execute!
                          db
                          ["SELECT id, email, username FROM account WHERE email = ?"
                           "robot@lipas.fi"]
                          {:builder-fn rs/as-unqualified-maps}))]
        (when-not (:id user)
          (throw (ex-info "robot@lipas.fi user not found" {})))
        (doseq [{:keys [lipas-id name gap-ms site]} plan]
          (log/info "Restoring PTV archive for lipas-id" lipas-id (pr-str name)
                    "(stale revision" gap-ms "ms after the archive)")
          (db/upsert-sports-site! db user site))
        (log/warn "Search index is now stale for the repaired sites"
                  "- run a search reindex.")))
    (log/info "Migration complete: fix-ptv-archived-stale-revisions. Repaired"
              (count plan) "sites")))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for fix-ptv-archived-stale-revisions migration"))
