(ns lipas.jobs.handler
  "Admin endpoints for job queue monitoring and management."
  (:require
   [lipas.backend.core :as backend-core]
   [lipas.jobs.core :as core]
   [lipas.jobs.schema :as schema]
   [reitit.coercion.malli]))

(defn routes
  "Admin job queue monitoring routes."
  [{:keys [db] :as _ctx}]
  [""
   {:coercion reitit.coercion.malli/coercion
    :tags ["admin-jobs"]
    :no-doc false}

   ["/actions/create-jobs-metrics-report"
    {:post
     {:require-privilege :jobs/manage
      :parameters {:body schema/jobs-metrics-request-schema}
      :responses {200 {:body schema/jobs-metrics-response-schema}}
      :handler
      (fn [req]
        (let [opts (-> req :parameters :body)]
          {:status 200
           :body (backend-core/get-job-admin-metrics db opts)}))}}]

   ["/actions/get-jobs-health-status"
    {:post
     {:require-privilege :jobs/manage
      :parameters {:body schema/jobs-health-request-schema}
      :responses {200 {:body schema/jobs-health-response-schema}}
      :handler
      (fn [_req]
        {:status 200
         :body (backend-core/get-job-queue-health db)})}}]

   ["/actions/search-jobs"
    {:post
     {:require-privilege :jobs/manage
      :parameters {:body schema/search-jobs-request-schema}
      :responses {200 {:body schema/search-jobs-response-schema}}
      :handler
      (fn [req]
        (let [opts (-> req :parameters :body)]
          {:status 200
           :body (core/search-jobs db opts)}))}}]

   ["/actions/get-dead-letter-jobs"
    {:get
     {:require-privilege :jobs/manage
      :parameters {:query [:map [:acknowledged {:optional true} :boolean]]}
      :handler
      (fn [req]
        (let [opts (-> req :parameters :query)]
          {:status 200
           :body (core/get-dead-letter-jobs db opts)}))}}]

   ["/actions/reprocess-dead-letter-jobs"
    {:post
     {:require-privilege :jobs/manage
      :parameters {:body [:map
                          [:dead-letter-ids [:sequential :int]]
                          [:max-attempts {:optional true} :int]]}
      :handler
      (fn [req]
        (let [{:keys [dead-letter-ids max-attempts]} (-> req :parameters :body)
              user-email (-> req :identity :email)]
          {:status 200
           :body (core/reprocess-dead-letter-jobs!
                  db
                  dead-letter-ids
                  user-email
                  (when max-attempts {:max-attempts max-attempts}))}))}}]

   ["/actions/acknowledge-dead-letter-jobs"
    {:post
     {:require-privilege :jobs/manage
      :parameters {:body [:map
                          [:dead-letter-ids [:sequential :int]]]}
      :handler
      (fn [req]
        (let [{:keys [dead-letter-ids]} (-> req :parameters :body)
              user-email (-> req :identity :email)]
          {:status 200
           :body (core/acknowledge-dead-letter-jobs!
                  db
                  dead-letter-ids
                  user-email)}))}}]])

(comment
  (require '[lipas.backend.jwt :as jwt])
  (def admin (repl/get-robot-user))
  (def token (jwt/create-token admin {:terse? true :valid-seconds (* 15 60)}))
  (println
   (str (format
         "curl -X POST -H \"Authorization: Token %s\" http://localhost:8091/api/actions/create-jobs-metrics-report"
         token)))

  ;; Seed the dev DB with jobs activity and dead letters so the admin UI
  ;; has something to show (throughput chart, queue, DLQ groups incl. a
  ;; superseded case). Safe to run repeatedly.
  (require '[next.jdbc :as jdbc])
  (def db (user/db))

  ;; Completed activity over the last 24 h (some retried) -> chart + recent list
  (doseq [h (range 24)
          _ (range (inc (rand-int 3)))]
    (jdbc/execute! db
                   ["INSERT INTO jobs (type, payload, status, priority, attempts, max_attempts, run_at, created_at, started_at, completed_at)
       VALUES (?, ?::jsonb, 'completed', 80, ?, 3,
               now() - (? || ' hours')::interval - interval '10 minutes',
               now() - (? || ' hours')::interval - interval '10 minutes',
               now() - (? || ' hours')::interval - interval '5 minutes',
               now() - (? || ' hours')::interval)"
                    (rand-nth ["analysis" "elevation"])
                    (str "{\"lipas-id\": " (+ 600000 (rand-int 100)) "}")
                    (if (< (rand) 0.2) 2 1)
                    h h h h]))

  ;; Queue now: one pending (future retry) + one processing
  (jdbc/execute! db
                 ["INSERT INTO jobs (type, payload, status, priority, attempts, max_attempts, run_at, created_at)
     VALUES ('analysis', '{\"lipas-id\": 600123}'::jsonb, 'pending', 80, 1, 3,
             now() + interval '55 minutes', now() - interval '10 minutes')"])
  (jdbc/execute! db
                 ["INSERT INTO jobs (type, payload, status, priority, attempts, max_attempts, run_at, created_at, started_at)
     VALUES ('elevation', '{\"lipas-id\": 600124}'::jsonb, 'processing', 70, 1, 3,
             now() - interval '2 minutes', now() - interval '3 minutes', now() - interval '2 minutes')"])

  ;; Fresh dead letters -> an ACTIVE timeout group
  (doseq [[lipas-id hours-ago] [[600555 2] [600556 26] [600557 50]]]
    (jdbc/execute! db
                   [(str "INSERT INTO dead_letter_jobs (original_job, error_message, died_at) VALUES ("
                         "jsonb_build_object('id', 0, 'type', 'analysis',"
                         " 'payload', jsonb_build_object('lipas-id', ?::int),"
                         " 'status', 'processing', 'priority', 80, 'attempts', 3, 'max_attempts', 3,"
                         " 'created_at', to_char(now() - (? || ' hours')::interval - interval '90 minutes', 'YYYY-MM-DD\"T\"HH24:MI:SS+00:00'),"
                         " 'started_at', to_char(now() - (? || ' hours')::interval - interval '61 minutes', 'YYYY-MM-DD\"T\"HH24:MI:SS+00:00')),"
                         "'Job execution timed out after 60 minutes',"
                         "now() - (? || ' hours')::interval)")
                    lipas-id hours-ago hours-ago hours-ago]))

  ;; ... one of which is superseded by a later completed job for the same site
  (jdbc/execute! db
                 ["INSERT INTO jobs (type, payload, status, priority, attempts, max_attempts, run_at, created_at, started_at, completed_at)
     VALUES ('analysis', '{\"lipas-id\": 600555}'::jsonb, 'completed', 80, 1, 3,
             now() - interval '1 hour', now() - interval '1 hour',
             now() - interval '55 minutes', now() - interval '50 minutes')"])

  ;; A second error class (MML connectivity)
  (jdbc/execute! db
                 ["INSERT INTO dead_letter_jobs (original_job, error_message, died_at)
     VALUES (jsonb_build_object('id', 0, 'type', 'elevation',
                                'payload', jsonb_build_object('lipas-id', 600666),
                                'status', 'processing', 'priority', 70,
                                'attempts', 3, 'max_attempts', 3),
             'java.net.SocketException: Connection reset', now() - interval '3 days')"]))
