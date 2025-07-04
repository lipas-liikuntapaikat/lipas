(ns lipas.jobs.handler
  "Admin endpoints for job queue monitoring and management."
  (:require
   [lipas.backend.core :as backend-core]
   [lipas.jobs.core :as core]
   [lipas.backend.middleware :as mw]
   [lipas.jobs.schema :as schema]
   [reitit.coercion.malli]))

(defn routes
  "Admin job queue monitoring routes."
  [{:keys [db] :as _ctx}]
  [""
   {:coercion reitit.coercion.malli/coercion
    #_#_:middleware [mw/token-auth mw/auth]
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
         token))))
