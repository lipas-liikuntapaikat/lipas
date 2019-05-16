(ns lipas.ui.interceptors
  (:require
   [lipas.ui.local-storage :as local-storage]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(def logout-event [:lipas.ui.login.events/logout])

(def check-token
  (re-frame/->interceptor
   :id      ::check-token
   :before  (fn [context]
              (let [expired? (some-> (local-storage/ls-get :login-data)
                                     :token
                                     utils/jwt-expired?)]
                (if expired?
                  (-> context
                      (assoc :queue []) ;; Delete any further actions in chain
                      (assoc-in [:effects :dispatch] logout-event))
                  context)))))
