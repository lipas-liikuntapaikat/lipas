(ns lipas.migrations.ptv-organizations
  (:require [environ.core :as env]
            [lipas.backend.db.utils] ; jsonb map->PGobject protocol extensions
            [lipas.data.ptv :as ptv]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [taoensso.timbre :as log]))

(defn migrate-up
  "Migrate hardcoded PTV organizations from lipas.data.ptv to the database"
  [{:keys [db] :as config}]
  (let [environment (env/env :environment)
        ;; Select test orgs for dev/test, prod orgs for prod environment
        organizations (case environment
                        ("dev" "test") ptv/test-organizations
                        "prod" ptv/prod-organizations
                        ;; Default to test orgs if environment not set
                        ptv/test-organizations)]

    (log/info "Migrating PTV organizations for environment:" environment)
    (log/info "Organizations to migrate:" (count organizations))

    (doseq [org-config organizations]
      (let [org-name (:name org-config)
            ptv-config (:props org-config)]
        (try
          (log/info "Creating organization:" org-name)

          ;; Insert directly into the single-row `org` schema that existed when
          ;; this migration was written. It predates the org event-log
          ;; conversion (20260604120000); calling the evolving `org/create-org`
          ;; (which now targets the revision schema with org_id/document) breaks
          ;; a from-scratch migration run, because that schema does not exist
          ;; yet at this point. These seeded rows are folded into the new schema
          ;; by the event-log migration's migrate-up.
          (let [org-data {:name org-name
                          :data {} ; Empty data for now - contact info can be added later
                          :ptv-data ptv-config}
                created-org (sql/insert! db :org org-data
                                         (assoc jdbc/unqualified-snake-kebab-opts
                                                :return-keys true))]

            (log/info "Successfully created organization:" org-name
                      "with ID:" (:id created-org)))

          (catch Exception e
            (if (re-find #"duplicate key value" (.getMessage e))
              (log/warn "Organization already exists:" org-name "- skipping")
              (throw e))))))))

(defn migrate-down
  "Remove migrated PTV organizations from the database"
  [{:keys [db] :as config}]
  (let [environment (env/env :environment)
        organizations (case environment
                        ("dev" "test") ptv/test-organizations
                        "prod" ptv/prod-organizations
                        ptv/test-organizations)]

    (log/info "Rolling back PTV organizations for environment:" environment)

    ;; We need to delete organizations by name since we don't track their IDs
    ;; This is safe since organization names are unique
    (doseq [org-config organizations]
      (let [org-name (:name org-config)]
        (try
          (log/info "Removing organization:" org-name)

          ;; Use raw SQL since we need to delete by name
          (let [result (jdbc/execute-one!
                        db
                        ["DELETE FROM org WHERE name = ?" org-name])]
            (if (pos? (:next.jdbc/update-count result))
              (log/info "Successfully removed organization:" org-name)
              (log/warn "Organization not found:" org-name)))

          (catch Exception e
            (log/error e "Error removing organization:" org-name)))))))

(comment
  ;; Test the migration locally
  (require '[user])
  (def test-db (:lipas/db integrant.repl.state/system))

  ;; Test migrate-up
  (migrate-up {:db test-db})

  ;; Check if orgs were created
  (require '[lipas.backend.org :as org])
  (map #(select-keys % [:name :ptv-data]) (org/all-orgs test-db))

  ;; Test migrate-down
  (migrate-down {:db test-db})

  ;; Check if orgs were removed
  (org/all-orgs test-db))
