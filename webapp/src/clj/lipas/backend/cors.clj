(ns lipas.backend.cors)

(def allow {:origin "*"
            :methods "GET, PUT, PATCH, POST, DELETE, OPTIONS"
            :headers "Authorization, Content-Type"})

(defn cors-mw
  "Cross-origin Resource Sharing (CORS) middleware. Allow requests from all
   origins, all http methods and Authorization and Content-Type headers."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] {:origin allow})
          (assoc-in [:headers "Access-Control-Allow-Methods"] {:methods allow})
          (assoc-in [:headers "Access-Control-Allow-Headers"] {:headers allow})))))
