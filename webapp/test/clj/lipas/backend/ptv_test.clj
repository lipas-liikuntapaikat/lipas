(ns lipas.backend.ptv-test
  (:require [clojure.test :refer [deftest is use-fixtures] :as t]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.schema.core]
            [lipas.test-utils :refer [<-json app db] :as tu]
            [lipas.utils :as utils]
            [ring.mock.request :as mock]))

(use-fixtures :once (fn [f]
                      (tu/init-db!)
                      (f)))

(use-fixtures :each (fn [f]
                      (tu/prune-db!)
                      (tu/prune-es!)
                      (f)))

;; This test requires PTV training environment to be operational.
;; It's up only on weekdays between 8-17 Finnish time...

#_(deftest ^:ptv ^:integration init-site-ptv
  (let [user     (tu/gen-user {:db? true :admin? true})
        token    (jwt/create-token user)

        rev1     (-> (tu/gen-sports-site)
                     (assoc :status "active")
                     ;; need to set up realistic type and location for the ptv integration to work
                     ;; yleisurheilukenttä
                     (assoc-in [:type :type-code] 1210)
                     (assoc-in [:location :postal-code] "91900")
                     (assoc-in [:location :city :city-code] 425))
        _        (core/upsert-sports-site!* db user rev1)
        lipas-id (:lipas-id rev1)
        resp     (app (-> (mock/request :get (str "/api/sports-sites/" lipas-id))
                          (mock/content-type "application/json")
                          (tu/token-header token)))
        body     (<-json (:body resp))
        site     body

        ;; TODO: Use another id for tests run?
        ;; Liminka, org 9
        org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
        org-langs ["fi" "se" "en"]
        ;; re-frame app-db defaults
        types types/all
        default-settings {:service-integration         "lipas-managed"
                          :service-channel-integration "lipas-managed"
                          :descriptions-integration    "lipas-managed-ptv-fields"
                          :integration-interval        "manual"}

        sports-sites (->> [body]
                          (utils/index-by :lipas-id))]

    (is (some? lipas-id))
    (is (= 200 (:status resp)))
    ;; (is (some? (:ptv body)))

    (let [;; Get list of services already on PTV
          resp (app (-> (mock/request :post (str "/api/actions/fetch-ptv-services"))
                        (mock/json-body {:org-id org-id})
                        (tu/token-header token)))
          services (->> (<-json (:body resp))
                        :itemList
                        (utils/index-by :id))

          _ (is (= 200 (:status resp)))
          _ (is (> (count services) 1))

          ;; Get list of service channels already on PTV
          resp (app (-> (mock/request :post (str "/api/actions/fetch-ptv-service-channels"))
                        (mock/json-body {:org-id org-id})
                        (tu/token-header token)))
          service-channels (->> (<-json (:body resp))
                                :itemList
                                (utils/index-by :id))

          _ (is (= 200 (:status resp)))
          _ (is (> (count service-channels) 1))

          ;; Initializing a PTV data for a site that wasn't previously synced to ptv works like this:
          ;; - summary and description are written or generated
          ;; - after that this ptv-input functions should return that the site is valid (this is used in the view component)
          ;; - save-ptv-service-location is called BUT this doesn't take the
          ;;   ptv-input but the lipas-id and ptv-meta as parameters

          ;; TODO: is this ptv-input data useful? Could everything (the view components) just use raw site data directly?
          ptv-sites (for [site (vals sports-sites)]
                      (ptv-data/sports-site->ptv-input {:types types
                                                        :org-id org-id
                                                        :org-defaults default-settings
                                                        :org-langs org-langs}
                                                       service-channels
                                                       services
                                                       site))]

      (is (= 1 (count ptv-sites)))

      (is (= []
             (ptv-data/resolve-missing-services org-id
                                                services
                                                ptv-sites)))

      ;; Add ptv summary and description to the site, enabling the
      ;; ptv integration for the site -> will create Service Location.
      (let [updated-site (assoc site :ptv (merge default-settings
                                                 {:sync-enabled true
                                                  :org-id org-id
                                                  ;; TODO: Need to setup the link to services
                                                  :service-ids []
                                                  :summary {:fi "foobar"
                                                            :se "foobar"
                                                            :en "foobar"}
                                                  :description {:fi "foobar"
                                                                :se "foobar"
                                                                :en "foobar"}}))
            resp (app (-> (mock/request :post (str "/api/sports-sites"))
                          (mock/json-body updated-site)
                          (tu/token-header token)))
            body (<-json (:body resp))]
        ;; Responds with 201 for both creates and updates
        (is (= 201 (:status resp)))
        (println body)
        (is (some? (:last-sync (:ptv body))))
        (is (= "Published" (:publishing-status (:ptv body))))))

    ;; TODO: Archive the site in Lipas and PTV
    ))

(comment
  (t/run-tests *ns*)
  (t/run-test-var #'init-site-ptv)
  )
