(ns lipas.migrations.add-route-ids
  "Migration to add missing :id fields to activity routes.

  Routes in UTP activities (outdoor-recreation-routes, cycling, paddling)
  should always have unique IDs, but due to a schema bug, some routes were
  saved without IDs. This migration adds UUIDs to all routes missing them."
  (:require [cheshire.core :as json]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(defn- add-ids-to-routes
  "Add UUIDs to routes that are missing :id field"
  [routes]
  (mapv (fn [route]
          (if (:id route)
            route
            (assoc route :id (str (utils/gen-uuid)))))
        routes))

(defn- fix-activities
  "Walk through activities and fix routes missing IDs"
  [activities]
  (when activities
    (reduce-kv
     (fn [acc activity-k activity-v]
       (if-let [routes (:routes activity-v)]
         (let [fixed-routes (add-ids-to-routes routes)]
           (assoc-in acc [activity-k :routes] fixed-routes))
         (assoc acc activity-k activity-v)))
     {}
     activities)))

(defn- has-routes-without-ids?
  "Check if any route in activities is missing an :id"
  [activities]
  (some (fn [[_ activity-v]]
          (when-let [routes (:routes activity-v)]
            (some #(nil? (:id %)) routes)))
        activities))

(defn migrate-up
  "Add UUIDs to all routes missing :id field"
  [{:keys [db] :as _config}]
  (log/info "Starting migration: add-route-ids")

  ;; Query for sites with activities that have routes
  (let [query "SELECT lipas_id, document
               FROM sports_site_current
               WHERE document->'activities' IS NOT NULL"
        results (jdbc/execute! db [query] {:builder-fn rs/as-unqualified-maps})

        sites-to-fix (->> results
                          (filter #(has-routes-without-ids? (:activities (:document %)))))]

    (log/info "Found" (count sites-to-fix) "sports sites with routes missing IDs")

    (doseq [{:keys [lipas_id document]} sites-to-fix]
      (let [fixed-activities (fix-activities (:activities document))
            fixed-document (assoc document :activities fixed-activities)]

        (log/info "Fixing routes for lipas-id:" lipas_id)

        ;; Update the document in place for the current revision
        ;; This is a data fix, not a normal edit, so we update directly
        (jdbc/execute-one!
         db
         ["UPDATE sports_site
           SET document = ?::jsonb
           WHERE lipas_id = ?
             AND (lipas_id, event_date) IN (
               SELECT lipas_id, MAX(event_date)
               FROM sports_site
               WHERE status = 'published'
               GROUP BY lipas_id
             )"
          (json/generate-string fixed-document)
          lipas_id])))

    (log/info "Migration complete: add-route-ids. Fixed" (count sites-to-fix) "sites")))

(defn migrate-down
  "No-op - cannot safely remove generated IDs without knowing original state"
  [{:keys [db] :as _config}]
  (log/warn "Rollback not supported for add-route-ids migration - IDs cannot be removed"))

(comment
  ;; Test locally
  (require '[user])
  (def test-db (user/db))

  ;; Check how many sites need fixing
  (let [query "SELECT lipas_id, document->'activities' as activities
               FROM sports_site_current
               WHERE document->'activities' IS NOT NULL"
        results (jdbc/execute! test-db [query])]
    (->> results
         (filter #(has-routes-without-ids? (:activities %)))
         count))

  ;; Run migration
  (migrate-up {:db test-db})

  ;; Verify
  (let [query "SELECT lipas_id, document->'activities' as activities
               FROM sports_site_current
               WHERE document->'activities' IS NOT NULL"
        results (jdbc/execute! test-db [query])]
    (->> results
         (filter #(has-routes-without-ids? (:activities %)))
         count)))
