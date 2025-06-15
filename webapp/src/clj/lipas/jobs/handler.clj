(ns lipas.jobs.handler
  "Admin endpoints for job queue monitoring and management."
  (:require
   [lipas.backend.core :as core]
   [lipas.jobs.schema :as schema]
   [lipas.roles :as roles]
   [reitit.coercion.malli]))

(defn routes
  "Admin job queue monitoring routes."
  [{:keys [db] :as _ctx}]
  [""
   {:coercion reitit.coercion.malli/coercion
    :tags ["admin-jobs"]
    :no-doc true}

   ["/actions/create-jobs-metrics-report"
    {:post
     {:require-privilege :users/manage
      :parameters {:body schema/jobs-metrics-request-schema}
      :responses {200 {:body schema/jobs-metrics-response-schema}}
      :handler
      (fn [req]
        (let [opts (-> req :parameters :body)]
          {:status 200
           :body (core/get-job-admin-metrics db opts)}))}}]

   ["/actions/get-jobs-health-status"
    {:post
     {:require-privilege :users/manage
      :parameters {:body schema/jobs-health-request-schema}
      :responses {200 {:body schema/jobs-health-response-schema}}
      :handler
      (fn [_req]
        {:status 200
         :body (core/get-job-queue-health db)})}}]])
