(ns lipas.backend.bulk-operations.handler
  "HTTP routes for bulk operations"
  (:require [lipas.backend.bulk-operations.core :as bulk-ops]
            [lipas.backend.middleware :as mw]
            [reitit.coercion.malli]))

(defn routes [{:keys [db search ptv] :as ctx}]
  [["/actions/mass-update-sports-sites"
    {:post
     {:no-doc false
      :middleware [mw/token-auth mw/auth]
      :coercion reitit.coercion.malli/coercion
      :parameters {:body [:map
                          [:lipas-ids [:vector :int]]
                          [:updates [:map
                                     [:email {:optional true} [:maybe [:string {:min 1 :max 200}]]]
                                     [:phone-number {:optional true} [:maybe [:string {:min 1 :max 50}]]]
                                     [:www {:optional true} [:maybe [:string {:min 1 :max 500}]]]
                                     [:reservation-link {:optional true} [:maybe [:string {:min 1 :max 500}]]]]]]}
      :responses {200 {:body [:map
                              [:updated-sites [:vector :int]]
                              [:total-updated :int]]}}
      :handler
      (fn [{:keys [body-params identity] :as req}]
        (let [{:keys [lipas-ids updates]} body-params]
          {:status 200
           :body (bulk-ops/mass-update-sports-sites-contacts
                  db
                  search
                  ptv
                  identity
                  lipas-ids
                  updates)}))}}]])
