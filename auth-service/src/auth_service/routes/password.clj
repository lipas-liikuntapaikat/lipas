(ns auth-service.routes.password
  (:require [auth-service.middleware.cors :refer [cors-mw]]
            [auth-service.route-functions.password.password-reset :refer [password-reset-response]]
            [auth-service.route-functions.password.request-password-reset :refer [request-password-reset-response]]
            [compojure.api.sweet :refer :all]))

(def password-routes
  (context "/api/v1/password" []
           :tags       ["Password"]
           :return     {:message String}
           :middleware [cors-mw]

    (POST "/reset-request" []
           :body-params [userEmail         :- String
                         fromEmail         :- String
                         subject           :- String
                         {emailBodyHtml    :- String ""}
                         {emailBodyPlain   :- String ""}
                         responseBaseLink  :- String]
           :summary     "Request a password reset for the registered user with the matching email"
           :description "The `response-base-link` will get a reset key appended to it and then the
                         link itself will be appended to the email body. The reset key will be valid
                         for 24 hours after creation. *NOTE* do not use a fromEmail address ending
                         with @gmail.com because of the DMARC policy. It is recommended to use a custom
                         domain you own instead"
           (request-password-reset-response userEmail fromEmail subject emailBodyPlain emailBodyHtml responseBaseLink))

    (POST "/reset-confirm" []
           :body-params [resetKey    :- String
                         newPassword :- String]
           :summary     "Replace an existing user password with the newPassowrd given a valid resetKey"
           (password-reset-response resetKey newPassword))))
