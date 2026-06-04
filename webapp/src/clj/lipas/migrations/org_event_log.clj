(ns lipas.migrations.org-event-log
  "Convert the mutable single-row `org` table into an append-only revision
  table (mirroring `sports_site`) plus an `org_current` view.

  - The old `org` table is renamed to `org_legacy` (kept as a safety net for the
    down migration); its identity column becomes the stable `org_id` of the new
    table.
  - One seed revision is created per legacy org. The org `document` folds the old
    `name` / `data` / `ptv_data` columns into a single jsonb snapshot and adds the
    new org-management keys (`:type`, `:role-templates`, `:ownership`, `:members`).
  - Membership is backfilled from `account.permissions.roles`: every legacy
    `org-admin` / `org-user` role becomes a member entry in the owning org's
    document. The legacy account roles are left in place; a later migration drops
    them once derive-at-login is verified in production."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private ddl-up
  ["ALTER TABLE org RENAME TO org_legacy"
   "ALTER TABLE org_legacy RENAME CONSTRAINT org_pkey TO org_legacy_pkey"
   "ALTER TABLE org_legacy RENAME CONSTRAINT org_mail_key TO org_legacy_name_key"
   "CREATE TABLE org (
      id         uuid NOT NULL DEFAULT uuid_generate_v4(),
      org_id     uuid NOT NULL,
      event_date timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
      created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
      author_id  uuid,
      status     text NOT NULL DEFAULT 'active',
      document   jsonb NOT NULL,
      CONSTRAINT org_pkey PRIMARY KEY (id),
      CONSTRAINT org_author_fk FOREIGN KEY (author_id)
        REFERENCES account (id) ON UPDATE NO ACTION ON DELETE NO ACTION)"
   ;; GIN for the login reverse-membership query (document->'members' @> ?)
   "CREATE INDEX org_document_gin ON org USING gin (document jsonb_path_ops)"
   "CREATE INDEX org_org_id_idx ON org (org_id)"
   ;; latest active revision per org_id (mirror of sports_site_current)
   "CREATE OR REPLACE VIEW org_current AS
      SELECT a.id, a.org_id, a.event_date, a.created_at, a.author_id, a.status, a.document
      FROM org a,
      (SELECT id,
              row_number() OVER (PARTITION BY org_id
                                 ORDER BY event_date DESC, created_at DESC) AS rn
       FROM org
       WHERE status <> 'archived') b
      WHERE a.id = b.id AND b.rn = 1"])

(def ^:private ddl-down
  ["DROP VIEW IF EXISTS org_current"
   "DROP TABLE IF EXISTS org"
   "ALTER TABLE org_legacy RENAME CONSTRAINT org_legacy_pkey TO org_pkey"
   "ALTER TABLE org_legacy RENAME CONSTRAINT org_legacy_name_key TO org_mail_key"
   "ALTER TABLE org_legacy RENAME TO org"])

(defn- legacy->document
  "Fold a legacy org row (`{:id :name :data :ptv-data}`) into a revision document.
  `:type` defaults to \"city\" (refined later); `:ownership` is seeded from the
  ptv-data city-codes/owners (ownership ≠ a PTV concern, so it gets its own key)."
  [{:keys [name data ptv-data]}]
  {:name            name
   :type            "city"
   :primary-contact (:primary-contact data)
   :ptv-data        ptv-data
   :role-templates  {}
   :ownership       {:city-codes (vec (:city-codes ptv-data))
                     :owners     (vec (:owners ptv-data))}
   :members         []})

(defn- members-by-org
  "Scan accounts for legacy org roles and return a map
  org-id-string -> [member-entry ...]. org-admin -> :admin, org-user -> :member."
  [db]
  (let [accts (jdbc/execute! db
                             ["SELECT id, permissions->'roles' AS roles FROM account
                  WHERE permissions->'roles' @> '[{\"role\":\"org-admin\"}]'
                     OR permissions->'roles' @> '[{\"role\":\"org-user\"}]'"]
                             {:builder-fn rs/as-unqualified-kebab-maps})]
    (reduce
      (fn [acc {:keys [id roles]}]
        (reduce
          (fn [acc {r :role org-ids :org_id}]
            (if-let [org-role (case r "org-admin" "admin" "org-user" "member" nil)]
              (reduce (fn [acc oid]
                        (update acc (str oid) (fnil conj [])
                                {:user-id (str id) :org-role org-role :templates []}))
                      acc org-ids)
              acc))
          acc roles))
      {} accts)))

(defn migrate-up [{:keys [db]}]
  (log/info "org-event-log: converting org -> revision table")
  (doseq [stmt ddl-up] (jdbc/execute! db [stmt]))
  (let [legacy  (jdbc/execute! db ["SELECT id, name, data, ptv_data FROM org_legacy"]
                               {:builder-fn rs/as-unqualified-kebab-maps})
        members (members-by-org db)]
    (log/info "org-event-log: seeding" (count legacy) "orgs")
    (doseq [{:keys [id] :as row} legacy]
      (let [doc (assoc (legacy->document row) :members (get members (str id) []))]
        (jdbc/execute! db
                       ["INSERT INTO org (org_id, author_id, status, document) VALUES (?,?,?,?)"
                        id nil "active" doc])))
    (log/info "org-event-log: seeded" (count legacy) "orgs,"
              (reduce + (map count (vals members))) "member entries")))

(defn migrate-down [{:keys [db]}]
  (log/info "org-event-log: reverting to single-row org table")
  (doseq [stmt ddl-down] (jdbc/execute! db [stmt])))

(comment
  (require '[user])
  (def test-db (:lipas/db integrant.repl.state/system))
  (migrate-up {:db test-db})
  (migrate-down {:db test-db}))
