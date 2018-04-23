(ns auth-service.routes.preflight
  (:require [auth-service.middleware.cors :refer [cors-mw]]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :as respond]))

(def preflight-route
  (context "/api" []

    (OPTIONS "*"           {:as request}
              :tags        ["Preflight"]
              :return      {}
              :middleware  [cors-mw]
              :summary     "This will catch all OPTIONS preflight requests from the
                            browser. It will always return a success for the purpose
                            of the browser retrieving the response headers to validate CORS
                            requests. Does not work in the swagger UI."
              (respond/ok  {}))))
