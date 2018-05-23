(ns lipas.backend.routes
  (:require [compojure.api.sweet :refer [context GET]]
            [ring.util.http-response :refer [ok]]))

(def auth-routes
  (context "/api/v1/auth" []
     (GET "/"             {:as request}
           :tags          ["Auth"]
           :header-params [authorization :- String]
           :middleware    [basic-auth-mw cors-mw authenticated-mw]
           :summary "Returns auth info given a username and password
           in the '`Authorization`' header."
           :description "Authorization header expects '`Basic
                           username:password`' where
                           `username:password` is base64 encoded. To
                           adhere to basic auth standards we have to
                           use a field called `username` however we
                           will accept a valid username or email as a
                           value for this key."
           (ok {:kissa "koira"}))))
