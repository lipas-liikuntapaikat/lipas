(ns lipas.migrations.ptv-service-audit-move
  "Moves DVV's PTV Service katselmointi verdicts out of the ptv_service
  documents into the ptv_service_audit table (created by 20260918100200),
  the counterpart of ptv-site-audit-move for sports sites.

  Until this migration every service audit save appended a ptv_service
  revision (the previous document plus [:audit], event_date = the audit's
  :timestamp) and every content sync carried the audit forward. Unlike the
  sports-site side there is no event_date damage to repair: a Service's
  sync status is derived from live PTV content, not from ptv_service dates.

  Copies every distinct audit (org_id + source_id + audit :timestamp) found
  in any ptv_service revision into ptv_service_audit, unless already there,
  with service_revision_id = the revision the auditor saw: the latest
  revision of the lineage before the audit's timestamp that is not itself
  an audit-only revision, or — for an audit whose save also created the
  lineage's first revision — that revision. Old revisions keep their
  [:audit] key untouched; lipas.backend.db.ptv-service hides it on read.
  Idempotent."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(defn- audits-to-copy
  [db]
  (jdbc/execute!
    db
    ["SELECT DISTINCT ON (a.org_id, a.source_id, a.document->'audit'->>'timestamp')
             a.org_id,
             a.source_id,
             a.service_id,
             (a.document->'audit'->>'timestamp')::timestamptz AS event_date,
             COALESCE(seen.id, a.id) AS service_revision_id,
             acc.id AS auditor_id,
             a.document->'audit' AS document
      FROM ptv_service a
      LEFT JOIN account acc
        ON acc.id::text = a.document->'audit'->>'auditor-id'
      LEFT JOIN LATERAL (
        SELECT p.id
        FROM ptv_service p
        WHERE p.org_id = a.org_id
          AND p.source_id = a.source_id
          AND p.event_date < (a.document->'audit'->>'timestamp')::timestamptz
          AND NOT (p.document->'audit' IS NOT NULL
                   AND p.event_date = (p.document->'audit'->>'timestamp')::timestamptz)
        ORDER BY p.event_date DESC, p.created_at DESC
        LIMIT 1) seen ON true
      WHERE a.document->'audit'->>'timestamp' IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM ptv_service_audit b
          WHERE b.org_id = a.org_id
            AND b.source_id = a.source_id
            AND b.event_date = (a.document->'audit'->>'timestamp')::timestamptz)
      ORDER BY a.org_id, a.source_id, a.document->'audit'->>'timestamp', a.created_at ASC"]
    {:builder-fn rs/as-unqualified-maps}))

(defn compute-plan
  "Pure-read plan; REPL-callable for a dry run: the rows to insert into
   ptv_service_audit."
  [db]
  {:audits (audits-to-copy db)})

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: ptv-service-audit-move")
  (let [{:keys [audits]} (compute-plan db)]
    (log/info "Copying" (count audits) "service audits into ptv_service_audit")
    (doseq [{:keys [org_id source_id service_id event_date service_revision_id auditor_id document]} audits]
      (jdbc/execute-one!
        db
        ["INSERT INTO ptv_service_audit
            (org_id, source_id, service_id, event_date, service_revision_id, auditor_id, document)
          VALUES (?, ?, ?, ?, ?, ?, ?)"
         org_id source_id service_id event_date service_revision_id auditor_id document]))
    (log/info "Migration complete: ptv-service-audit-move")))

(defn migrate-down [_config]
  ;; The copied audits still exist in the ptv_service revision history.
  (log/warn "Rollback not supported for ptv-service-audit-move migration"))
