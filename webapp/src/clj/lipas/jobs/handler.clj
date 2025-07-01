(ns lipas.jobs.handler
  "Admin endpoints for job queue monitoring and management."
  (:require
   [lipas.backend.core :as core]
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
           :body (core/get-job-admin-metrics db opts)}))}}]

   ["/actions/get-jobs-health-status"
    {:post
     {:require-privilege :jobs/manage
      :parameters {:body schema/jobs-health-request-schema}
      :responses {200 {:body schema/jobs-health-response-schema}}
      :handler
      (fn [_req]
        {:status 200
         :body (core/get-job-queue-health db)})}}]])

(comment
  (require '[lipas.backend.jwt :as jwt])
  (def admin (repl/get-robot-user))
  (def token (jwt/create-token admin {:terse? true :valid-seconds (* 15 60)}))
  (println
   (str (format
         "curl -X POST -H \"Authorization: Token %s\" http://localhost:8091/api/actions/create-jobs-metrics-report"
         token))))
