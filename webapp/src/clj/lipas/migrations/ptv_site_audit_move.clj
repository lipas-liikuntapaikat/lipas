(ns lipas.migrations.ptv-site-audit-move
  "Moves DVV's site katselmointi verdicts out of the sports_site documents
  into the ptv_site_audit table (created by 20260918100000) and repairs the
  damage the old storage did to the site history.

  Until this migration every audit save appended a sports_site revision:
  the previous document plus [:ptv :audit], stamped with a fresh event_date
  equal to the audit's :timestamp. That moved the site's event_date although
  no content changed, and the PTV views compare event_date against
  [:ptv :last-sync] to decide whether a site is in sync — so every audited
  site read as \"out of date\" (LIPAS-2026-09 DVV report).

  Two steps, both idempotent:

  1. Copy every distinct audit (lipas_id + audit :timestamp) found in any
     sports_site revision into ptv_site_audit, unless already there, with
     site_revision_id = the revision the auditor saw: the latest revision
     before the audit's timestamp that is not itself an audit-only
     revision. Old revisions keep their [:ptv :audit] key untouched — the
     backend hides it on read (lipas.backend.db/unmarshall).
  2. For each audit-only revision (event_date = its audit :timestamp), set
     event_date — the column AND the document's own :event-date string,
     which is what gets indexed and read — back to the value of the latest
     earlier revision that is not itself an audit-only revision.
     sports_site_current orders by event_date DESC, created_at DESC, so the
     audit-only revision stays the current one; its event_date just stops
     lying.

  NOTE: the search index goes stale for the repaired sites (event-date, and
  the audit that ES documents no longer carry) — run a search reindex after
  deploying this migration."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private audit-only-rev
  "SQL predicate: sports_site row `a` is an audit-only revision."
  "a.document->'ptv'->'audit' IS NOT NULL
   AND a.event_date = (a.document->'ptv'->'audit'->>'timestamp')::timestamptz")

(defn- audits-to-copy
  [db]
  (jdbc/execute!
    db
    ["SELECT DISTINCT ON (a.lipas_id, a.document->'ptv'->'audit'->>'timestamp')
             a.lipas_id,
             (a.document->'ptv'->'audit'->>'timestamp')::timestamptz AS event_date,
             seen.id AS site_revision_id,
             acc.id AS auditor_id,
             a.document->'ptv'->'audit' AS document
      FROM sports_site a
      LEFT JOIN account acc
        ON acc.id::text = a.document->'ptv'->'audit'->>'auditor-id'
      LEFT JOIN LATERAL (
        SELECT p.id
        FROM sports_site p
        WHERE p.lipas_id = a.lipas_id
          AND p.event_date < (a.document->'ptv'->'audit'->>'timestamp')::timestamptz
          AND NOT (p.document->'ptv'->'audit' IS NOT NULL
                   AND p.event_date = (p.document->'ptv'->'audit'->>'timestamp')::timestamptz)
        ORDER BY p.event_date DESC, p.created_at DESC
        LIMIT 1) seen ON true
      WHERE a.document->'ptv'->'audit'->>'timestamp' IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM ptv_site_audit b
          WHERE b.lipas_id = a.lipas_id
            AND b.event_date = (a.document->'ptv'->'audit'->>'timestamp')::timestamptz)
      ORDER BY a.lipas_id, a.document->'ptv'->'audit'->>'timestamp', a.created_at DESC"]
    {:builder-fn rs/as-unqualified-maps}))

(defn- event-date-repairs
  [db]
  (jdbc/execute!
    db
    [(str "SELECT a.id, a.lipas_id, a.event_date,
                  p.event_date AS content_event_date,
                  p.document->>'event-date' AS content_event_date_str
           FROM sports_site a
           LEFT JOIN LATERAL (
             SELECT p.event_date, p.document
             FROM sports_site p
             WHERE p.lipas_id = a.lipas_id
               AND p.event_date < a.event_date
               AND NOT (p.document->'ptv'->'audit' IS NOT NULL
                        AND p.event_date = (p.document->'ptv'->'audit'->>'timestamp')::timestamptz)
             ORDER BY p.event_date DESC, p.created_at DESC
             LIMIT 1) p ON true
           WHERE " audit-only-rev
          " ORDER BY a.lipas_id, a.event_date")]
    {:builder-fn rs/as-unqualified-maps}))

(defn compute-plan
  "Pure-read plan; REPL-callable for a dry run. :audits are the rows to
   insert into ptv_site_audit, :repairs the audit-only revisions with the
   event_date they get back (nil content_event_date = no earlier content
   revision exists, left alone)."
  [db]
  {:audits (audits-to-copy db)
   :repairs (event-date-repairs db)})

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: ptv-site-audit-move")
  (let [{:keys [audits repairs]} (compute-plan db)]
    (log/info "Copying" (count audits) "site audits into ptv_site_audit")
    (doseq [{:keys [lipas_id event_date site_revision_id auditor_id document]} audits]
      (jdbc/execute-one!
        db
        ["INSERT INTO ptv_site_audit (lipas_id, event_date, site_revision_id, auditor_id, document)
          VALUES (?, ?, ?, ?, ?)"
         lipas_id event_date site_revision_id auditor_id document]))
    (log/info "Repairing event_date of" (count repairs) "audit-only site revisions")
    (doseq [{:keys [id lipas_id event_date content_event_date content_event_date_str]} repairs]
      (if content_event_date
        (do
          (log/info "lipas-id" lipas_id "revision" (str id) ":" (str event_date) "->" (str content_event_date))
          ;; jsonb_set is strict: a NULL value would null the whole document.
          ;; Fall back to formatting the column when the earlier revision's
          ;; document has no event-date string of its own.
          (jdbc/execute-one!
            db
            ["UPDATE sports_site
              SET event_date = ?,
                  document = jsonb_set(
                    document, '{event-date}',
                    to_jsonb(COALESCE(?::text,
                                      to_char(?::timestamptz AT TIME ZONE 'UTC',
                                              'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"'))))
              WHERE id = ?"
             content_event_date content_event_date_str content_event_date id]))
        (log/warn "lipas-id" lipas_id "revision" (str id)
                  "has no earlier content revision; event_date left as is")))
    (log/info "Migration complete: ptv-site-audit-move")
    (when (or (seq audits) (seq repairs))
      (log/warn "Search index is now stale for the audited sites - run a search reindex."))))

(defn migrate-down [_config]
  ;; The copied audits still exist in the site revision history; the
  ;; event_date repair is not reverted (it corrected an artifact).
  (log/warn "Rollback not supported for ptv-site-audit-move migration"))
