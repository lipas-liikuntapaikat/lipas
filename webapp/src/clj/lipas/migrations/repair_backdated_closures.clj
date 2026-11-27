(ns lipas.migrations.repair-backdated-closures
  "Repair sites whose backdated closure didn't take, and give every closed
  site and LOI an explicit :decommission-year.

  Until 2026-11, saving a permanent closure dated into the past (\"closed in
  2018\", saved in 2025) ran invalidate-since!: it rewrote the status of
  every later-dated revision to incorrect-data, in place. Those revisions
  still outranked the closure, so the site showed as \"Väärä tieto\". In
  production (2026-11-14) that left 568 sites broken whose last action was
  the closure, plus 15 more where the elevation job's robot copy carried the
  rewritten status forward.

  1. Rebase: for each site whose newest revision saved by a person is a
     permanent closure but whose current revision is not closed, append the
     closed copies a closure gets today (db/rebase-onto-closure!, authored by
     robot@lipas.fi). The rewritten originals stay as the audit trail. Sites
     where a person saved again after the closure are left for review: their
     save is newer than the closure.
  2. Backfill: write :decommission-year into the current revision of every
     closed site and LOI that lacks it, in place (it only adds a field). The
     year is that of the revision that started the current closure: UTC for
     the year picker's Dec 31 marker, Finnish time otherwise
     (db/event-date->decommission-year).

  `compute-plan` is a pure-read dry run of both.

  NOTE: run a full search reindex (sites and LOIs) after deploying: the
  mapping gains :decommission-year and the repaired sites change status."
  (:require [lipas.backend.db.db :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private robot-email "robot@lipas.fi")

(defn- query [db sql-params]
  (jdbc/execute! db sql-params {:builder-fn rs/as-unqualified-maps}))

(defn rebase-candidates
  "Sites whose closure didn't take: the newest revision saved by a person is
  a permanent closure, yet a later-dated revision that is not closed is
  current."
  [db]
  (query db
         ["WITH last_person AS (
             SELECT DISTINCT ON (s.lipas_id)
                    s.lipas_id, s.event_date, s.acting_org_id,
                    s.document->>'status' AS status,
                    (s.document->>'decommission-year')::int AS decommission_year
             FROM sports_site s
             LEFT JOIN account a ON a.id = s.author_id
             WHERE s.status <> 'draft'
               AND a.email IS DISTINCT FROM ?
             ORDER BY s.lipas_id, s.created_at DESC, s.event_date DESC)
           SELECT lp.lipas_id, lp.event_date, lp.acting_org_id, lp.decommission_year,
                  c.document->>'name' AS name,
                  c.document->>'status' AS current_status
           FROM last_person lp
           JOIN sports_site_current c USING (lipas_id)
           WHERE lp.status = 'out-of-service-permanently'
             AND c.document->>'status' <> 'out-of-service-permanently'
             AND c.event_date > lp.event_date
           ORDER BY lp.lipas_id"
          robot-email]))

(defn review-candidates
  "Sites with revisions the invalidation rewrote whose current revision is
  still incorrect-data but which the rebase leaves alone, because a person
  saved again after the closure. A human has to look at these."
  [db]
  (query db
         ["WITH rewritten AS (
             SELECT DISTINCT s.lipas_id
             FROM sports_site s
             WHERE s.status = 'published'
               AND s.document->>'status' = 'incorrect-data'
               AND EXISTS (SELECT 1 FROM sports_site o
                           WHERE o.lipas_id = s.lipas_id
                             AND o.status = 'published'
                             AND o.document->>'status' = 'out-of-service-permanently'
                             AND o.created_at > s.created_at
                             AND o.event_date < s.event_date))
           SELECT r.lipas_id, c.document->>'name' AS name
           FROM rewritten r
           JOIN sports_site_current c USING (lipas_id)
           WHERE c.document->>'status' = 'incorrect-data'
           ORDER BY r.lipas_id"]))

(def ^:private closed "out-of-service-permanently")

(defn closure-started-at
  "The event_date of the revision that started the current closure, given
  all revisions of one site or LOI as maps of :event_date, :created_at and
  :status. That's the earliest closed winning revision (newest created_at
  at its event_date) after the last winning revision that was open.

  A revision the invalidation rewrote to incorrect-data (recorded before a
  closure dated earlier than it) doesn't count as open: it was an edit of a
  site already closed. Otherwise a site whose closure was hidden, and which a
  person then closed again (with the year picker's default), would get the
  second closure's year instead of the one first chosen."
  [revs]
  (let [after? (fn [a b] (pos? (compare a b)))
        rewritten? (fn [r]
                     (and (= "incorrect-data" (:status r))
                          (some #(and (= closed (:status %))
                                      (after? (:created_at %) (:created_at r))
                                      (after? (:event_date r) (:event_date %)))
                                revs)))
        winners (->> (group-by :event_date revs)
                     (map (fn [[_ at-date]] (last (sort-by :created_at compare at-date))))
                     (sort-by :event_date compare))
        last-open (->> winners
                       (remove #(or (= closed (:status %)) (rewritten? %)))
                       last)]
    (->> winners
         (filter #(and (= closed (:status %))
                       (or (nil? last-open) (after? (:event_date %) (:event_date last-open)))))
         first
         :event_date)))

(def ^:private backfill-sql
  "Current closed rows without :decommission-year, and all revisions of
  those keys. Placeholders: %1$s key column, %2$s table, %3$s current view,
  %4$s row filter on alias r, %5$s row-id expression."
  {:targets
   "SELECT %5$s, c.%1$s AS key, c.event_date AS current_event_date,
           c.created_at AS current_created_at
    FROM %3$s c
    WHERE c.document->>'status' = 'out-of-service-permanently'
      AND c.document->'decommission-year' IS NULL"
   :revisions
   "SELECT r.%1$s AS key, r.event_date, r.created_at, r.document->>'status' AS status
    FROM %2$s r
    WHERE %4$s
      AND r.%1$s IN (SELECT c.%1$s FROM %3$s c
                     WHERE c.document->>'status' = 'out-of-service-permanently'
                       AND c.document->'decommission-year' IS NULL)"})

(defn backfill-candidates
  "Current closed revisions without :decommission-year, with the year to
  write. `kind` is :sports-site or :loi."
  [db kind]
  (let [args (case kind
               :sports-site ["lipas_id" "sports_site" "sports_site_current"
                             "r.status <> 'draft'" "c.id AS row_id"]
               :loi ["id" "loi" "loi_current" "true" "NULL::uuid AS row_id"])
        sql #(apply format (backfill-sql %) args)
        revs-by-key (group-by :key (query db [(sql :revisions)]))]
    (->> (query db [(sql :targets)])
         (mapv (fn [{:keys [key] :as row}]
                 (let [closed-at (closure-started-at (revs-by-key key))]
                   (assoc row
                          :closed_at closed-at
                          :decommission-year (db/event-date->decommission-year closed-at))))))))

(defn- closed-without-year
  "Current closed sites and LOIs still without :decommission-year."
  [db]
  (-> (query db ["SELECT
                    (SELECT count(*) FROM sports_site_current
                     WHERE document->>'status' = 'out-of-service-permanently'
                       AND document->'decommission-year' IS NULL) AS sites,
                    (SELECT count(*) FROM loi_current
                     WHERE document->>'status' = 'out-of-service-permanently'
                       AND document->'decommission-year' IS NULL) AS lois"])
      first))

(defn compute-plan
  "Pure-read dry run: what migrate-up would do. :backfill-sites leaves out
  the rebased sites, whose copies get the year from the rebase."
  [db]
  (let [rebase (rebase-candidates db)
        rebased? (set (map :lipas_id rebase))]
    {:rebase rebase
     :review (vec (remove (comp rebased? :lipas_id) (review-candidates db)))
     :backfill-sites (backfill-candidates db :sports-site)
     :backfill-lois (backfill-candidates db :loi)}))

(defn- rebase! [java-db robot {:keys [lipas_id event_date acting_org_id decommission_year name current_status]}]
  (let [year (or decommission_year (db/event-date->decommission-year event_date))
        current (db/rebase-onto-closure! java-db robot {:lipas-id lipas_id
                                                        :event-date event_date
                                                        :acting-org-id acting_org_id
                                                        :decommission-year year})]
    (log/info "Rebased closure of" lipas_id (pr-str name) "closed" year
              "(was" current_status "->" (:status current) ")")))

(defn- backfill! [db kind {:keys [row_id key current_event_date current_created_at decommission-year]}]
  (let [n (case kind
            :sports-site
            (jdbc/execute-one!
              db
              ["UPDATE sports_site
                SET document = jsonb_set(document, '{decommission-year}', to_jsonb(?::int))
                WHERE id = ? AND document->'decommission-year' IS NULL"
               decommission-year row_id])
            :loi
            (jdbc/execute-one!
              db
              ["UPDATE loi
                SET document = jsonb_set(document, '{decommission-year}', to_jsonb(?::int))
                WHERE id = ? AND event_date = ? AND created_at = ?
                  AND document->'decommission-year' IS NULL"
               decommission-year key current_event_date current_created_at]))]
    (:next.jdbc/update-count n)))

(defn- connections
  "next.jdbc and clojure.java.jdbc handles on one connection. Migratus passes
  its transaction's connection as :conn (:db is the plain spec, outside the
  transaction); tests and the REPL pass only :db.

  :level 1 tells clojure.java.jdbc it is already inside a transaction.
  Without it, every insert of the rebase (hugsql on java.jdbc) runs in its
  own begin/COMMIT on that connection, committing migratus's transaction
  piecemeal."
  [{:keys [conn db]}]
  (if conn
    [conn {:connection conn :level 1 :rollback (atom false)}]
    [db db]))

(defn migrate-up
  "All writes go through the same connection, so under migratus the repair
  is one transaction."
  [config]
  (log/info "Starting migration: repair-backdated-closures")
  (let [[db java-db] (connections config)
        rebase (rebase-candidates db)]
    (log/info "Backdated closures to rebase:" (count rebase))
    (when (seq rebase)
      ;; Looked up only when there is work to do — fresh databases (CI, empty
      ;; installs) have neither sports sites nor the robot user. Raw SQL with a
      ;; pinned column list (see clean-invalid-surface-materials).
      (let [robot (first (query db ["SELECT id, email, username FROM account WHERE email = ?"
                                    robot-email]))]
        (when-not (:id robot)
          (throw (ex-info "robot@lipas.fi user not found" {})))
        (doseq [candidate rebase]
          (rebase! java-db robot candidate))))
    ;; After the rebase, so the repaired sites drop out.
    (when-let [review (seq (review-candidates db))]
      (log/warn "Left for review (a person saved after the closure, still incorrect-data):"
                (pr-str (mapv :lipas_id review))))
    ;; After the rebase: its copies already carry the year.
    (let [sites (reduce + 0 (map #(backfill! db :sports-site %)
                                 (backfill-candidates db :sports-site)))
          lois (reduce + 0 (map #(backfill! db :loi %)
                                (backfill-candidates db :loi)))]
      (log/info "Backfilled :decommission-year on" sites "closed sites and" lois "closed LOIs"))
    ;; A closed site without the year can't be saved any more (400 from
    ;; core/check-decommission-year): fail the migration rather than leave one.
    (let [missing (closed-without-year db)]
      (when (some pos? (vals missing))
        (throw (ex-info "Closed sites or LOIs still without :decommission-year" missing))))
    (log/warn "Search indices are now stale - run a full reindex of sites and LOIs.")
    (log/info "Migration complete: repair-backdated-closures")))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for repair-backdated-closures migration"))
