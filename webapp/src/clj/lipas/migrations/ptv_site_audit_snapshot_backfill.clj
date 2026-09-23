(ns lipas.migrations.ptv-site-audit-snapshot-backfill
  "Anchors the legacy change requests the ptv-site-audit-move migration
  (20260918100100) brought over to the text they were given on.

  Verdicts saved before the whose-move workflow (b071eddf, 2026-07) carry
  no :audited-content snapshot, and lipas.data.ptv/audit-field-state reads
  a snapshotless verdict as unchanged. For an approval that is the intended
  grandfathering, but a change request without a snapshot can never turn
  :fixed: it stays open however the municipality edits the text. The move
  also resurrected such audits that the old in-document storage had
  already lost from the current revision, so they reappeared as open
  change requests (lipas-dev: Haapajoen jääkiekkokenttä and three others).

  The move recorded the revision the auditor saw (site_revision_id), so
  the missing snapshot is that revision's text. For every current audit
  with a change request lacking :audited-content, a copy of the audit is
  appended with that field's snapshot filled in — same event_date,
  auditor and revision, so it becomes the current audit without moving
  the audit date. ptv_site_audit is append-only: the original row stays
  as it was. Approvals are left grandfathered.

  Idempotent: once the current audit carries the snapshots it no longer
  matches."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private audited-fields
  "Same keys as lipas.data.ptv/site-audited-field-keys."
  [:summary :description])

(defn- snapshotless-change-request?
  "A change request audit-field-state can never see as fixed."
  [field-audit]
  (and (= "changes-requested" (:status field-audit))
       (nil? (:audited-content field-audit))))

(defn- candidates
  [db]
  (jdbc/execute!
    db
    ["SELECT a.lipas_id, a.event_date, a.site_revision_id, a.status, a.auditor_id,
             a.document,
             r.document->'ptv'->'summary' AS rev_summary,
             r.document->'ptv'->'description' AS rev_description
      FROM ptv_site_audit_current a
      LEFT JOIN sports_site r ON r.id = a.site_revision_id
      WHERE EXISTS (
        SELECT 1 FROM jsonb_each(a.document) f
        WHERE f.key IN ('summary', 'description')
          AND jsonb_typeof(f.value) = 'object'
          AND f.value->>'status' = 'changes-requested'
          AND COALESCE(jsonb_typeof(f.value->'audited-content'), 'null') = 'null')
      ORDER BY a.lipas_id"]
    {:builder-fn rs/as-unqualified-maps}))

(defn backfill-document
  "The audit `document` with every snapshotless change request anchored to
   the audited revision's content (`rev-content`: field -> localized map,
   nil when the revision had no text; stored as {} so any text written
   since reads as a fix)."
  [document rev-content]
  (reduce (fn [doc field]
            (if (snapshotless-change-request? (get doc field))
              (assoc-in doc [field :audited-content] (or (get rev-content field) {}))
              doc))
          document
          audited-fields))

(defn compute-plan
  "Pure-read plan; REPL-callable for a dry run. :backfills are the audits
   to append (the new document included), :unresolvable the ones without
   an audited revision to take the snapshot from (left as they are)."
  [db]
  (let [rows (candidates db)
        {resolvable true unresolvable false} (group-by (comp some? :site_revision_id) rows)]
    {:backfills (mapv (fn [{:keys [rev_summary rev_description document] :as row}]
                        (-> row
                            (dissoc :rev_summary :rev_description)
                            (assoc :document (backfill-document
                                               document
                                               {:summary rev_summary
                                                :description rev_description}))
                            (assoc :fields (filterv #(snapshotless-change-request? (get document %))
                                                    audited-fields))))
                      resolvable)
     :unresolvable (mapv #(select-keys % [:lipas_id :event_date]) unresolvable)}))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: ptv-site-audit-snapshot-backfill")
  (let [{:keys [backfills unresolvable]} (compute-plan db)]
    (log/info "Anchoring" (count backfills) "legacy site change requests to their audited revision")
    (doseq [{:keys [lipas_id event_date site_revision_id status auditor_id document fields]} backfills]
      (log/info "lipas-id" lipas_id "fields" fields "revision" (str site_revision_id))
      (jdbc/execute-one!
        db
        ["INSERT INTO ptv_site_audit (lipas_id, event_date, site_revision_id, status, auditor_id, document)
          VALUES (?, ?, ?, ?, ?, ?)"
         lipas_id event_date site_revision_id status auditor_id document]))
    (doseq [{:keys [lipas_id]} unresolvable]
      (log/warn "lipas-id" lipas_id "has a snapshotless change request but no audited revision; left as is"))
    (log/info "Migration complete: ptv-site-audit-snapshot-backfill")))

(defn migrate-down [_config]
  ;; The original audits are untouched below the appended copies.
  (log/warn "Rollback not supported for ptv-site-audit-snapshot-backfill migration"))
