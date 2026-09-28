(ns lipas.migrations.clean-legacy-admin-owner-values
  "Replace the legacy \"no-information\" value in :admin and :owner with
  \"unknown\".

  \"no-information\" is the V1 API's wire spelling of \"Ei tietoa\"; the
  stored value has always been \"unknown\". Between de4d8c92 (2025-12-14)
  and e7668755 (2026-01-01) the key in lipas.data.admins/owners was
  renamed to \"no-information\" so that V1 output would match production.
  Those maps back BOTH the Malli enum and the editor's select options
  (lipas.ui.sports-sites.db), so for that window the UI offered \"Ei
  tietoa\" with value \"no-information\" and the save endpoint accepted
  it. e7668755 renamed the key back but left the rows written in between.

  In production this is 11 sites, lipas-ids 618830-618840, all type 201,
  each a single revision saved 2025-12-30/31. They 500 the V2 list and
  single-site endpoints on response coercion, and are silently served
  without :admin/:owner by V1.

  Unlike clean-invalid-surface-materials, this rewrites revisions IN
  PLACE rather than appending a fix revision:

  - It is a spelling correction, not a semantic remap. The editor picked
    \"Ei tietoa\" and \"unknown\" is that same value, so there is nothing
    for history to preserve.
  - An appended revision would not fix the history endpoint. It buckets
    by year (sports_site_by_year), so the 2025 revision would still be
    returned and would still fail coercion.
  - These sites have one revision each; appending would also fabricate an
    edit that never happened.

  NOTE: the search index goes stale for the cleaned sites - run a search
  reindex after deploying this migration."
  (:require [cheshire.core :as json]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def legacy-value "no-information")
(def replacement "unknown")

(defn- parse-doc
  "Migrations run without lipas.backend.db's PGobject extensions, so the
  document column may arrive raw; the REPL has them loaded and returns maps."
  [doc]
  (if (map? doc)
    doc
    (json/parse-string (str doc) true)))

(defn- assert-replacement!
  "Fail loudly rather than write a second invalid value if the enums are
  ever renamed again."
  []
  (doseq [[field vs] {:admin admins/all :owner owners/all}]
    (when-not (contains? vs replacement)
      (throw (ex-info "replacement value is not in the enum"
                      {:field field :replacement replacement :allowed (keys vs)})))
    (when (contains? vs legacy-value)
      (throw (ex-info "legacy value is back in the enum - is this migration still correct?"
                      {:field field :legacy-value legacy-value})))))

(defn compute-plan
  "Pure-read cleanup plan; REPL-callable for a dry run. Covers every
  revision, not just the current one, because the history endpoint
  validates older revisions too."
  [db-spec]
  (let [rows (jdbc/execute!
               db-spec
               ["SELECT id, lipas_id, event_date, status, document
                 FROM sports_site
                 WHERE document->>'admin' = ? OR document->>'owner' = ?"
                legacy-value legacy-value]
               {:builder-fn rs/as-unqualified-maps})]
    (for [{:keys [id lipas_id event_date status document]} rows
          :let [site (parse-doc document)]]
      {:id id
       :lipas-id lipas_id
       :event-date event_date
       :status status
       :name (:name site)
       :fields (cond-> []
                 (= legacy-value (:admin site)) (conj :admin)
                 (= legacy-value (:owner site)) (conj :owner))})))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: clean-legacy-admin-owner-values")
  (assert-replacement!)
  (let [plan (compute-plan db)]
    (log/info "Found" (count plan) "sports site revisions with"
              (pr-str legacy-value) "in :admin or :owner"
              "across" (count (distinct (map :lipas-id plan))) "sites")
    (doseq [{:keys [lipas-id event-date name fields]} plan]
      (log/info "Cleaning lipas-id" lipas-id (pr-str name)
                "revision" (str event-date)
                (pr-str fields) (pr-str legacy-value) "->" (pr-str replacement)))
    (when (seq plan)
      (let [admin-n (::jdbc/update-count
                      (jdbc/execute-one!
                        db
                        ["UPDATE sports_site
                          SET document = jsonb_set(document, '{admin}', to_jsonb(?::text))
                          WHERE document->>'admin' = ?"
                         replacement legacy-value]))
            owner-n (::jdbc/update-count
                      (jdbc/execute-one!
                        db
                        ["UPDATE sports_site
                          SET document = jsonb_set(document, '{owner}', to_jsonb(?::text))
                          WHERE document->>'owner' = ?"
                         replacement legacy-value]))]
        (log/info "Updated" admin-n ":admin and" owner-n ":owner values")
        (let [remaining (count (compute-plan db))]
          (when (pos? remaining)
            (throw (ex-info "revisions still hold the legacy value after the update"
                            {:remaining remaining}))))
        (log/warn "Search index is now stale for the cleaned sites"
                  "- run a search reindex.")))
    (log/info "Migration complete: clean-legacy-admin-owner-values. Cleaned"
              (count plan) "revisions")))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for clean-legacy-admin-owner-values migration"))
