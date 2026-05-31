(ns lipas.backend.ptv.handler
  (:require [lipas.backend.middleware :as mw]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.roles :as roles]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.ptv :as ptv-schema]
            [taoensso.timbre :as log]))

;; Schemas moved to lipas.schema.sports-sites.ptv

;; Custom privilege check that allows either ptv/manage or ptv/audit
(defn ptv-read-access? [req]
  (let [user (:identity req)]
    (or
      ;; Global audit privilege
     (roles/check-privilege user {} :ptv/audit)
      ;; Context-specific manage privilege (check with any city-code the user has)
     (some (fn [permission]
             (when-let [city-code (:city-code permission)]
               (some #(roles/check-privilege user {:city-code %} :ptv/manage)
                     city-code)))
           (get-in user [:permissions :roles])))))

(defn routes [{:keys [db search ptv emailer] :as _ctx}]
  [""
   {#_#_:middleware [mw/token-auth mw/auth]
    :tags ["ptv"]
    :no-doc false}

   ["/actions/get-ptv-integration-candidates"
    {:post
     {:require-privilege ptv-read-access?
      :parameters {:body [:map
                          [:city-codes [:vector :int]]
                          [:type-codes {:optional true} [:vector :int]]
                          [:owners [:vector :string]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/get-ptv-integration-candidates search (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-descriptions"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:lipas-id :int]
                          [:reference {:optional true}
                           [:maybe [:map
                                    [:summary :string]
                                    [:description :string]]]]]}
      :handler
      (fn [req]
        (let [{:keys [lipas-id reference]} (-> req :parameters :body)]
          {:status 200
           :body (ptv-core/generate-ptv-descriptions
                  search lipas-id {:reference reference})}))}}]

   ["/actions/generate-ptv-descriptions-from-data"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body #'sports-sites-schema/new-or-existing-sports-site}
      :handler
      (fn [req]
        (let [body (-> req :parameters :body)
              reference (:reference body)
              doc (dissoc body :reference)]
          {:status 200
           :body (ptv-core/generate-ptv-descriptions-from-data
                  doc {:reference reference})}))}}]

   ["/actions/generate-ptv-descriptions-batch"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:lipas-ids [:vector :int]]
                          [:reference {:optional true}
                           [:maybe [:map
                                    [:summary :string]
                                    [:description :string]]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-descriptions-batch
                search
                (-> req :parameters :body))})}}]

   ["/actions/translate-to-other-langs"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:from :string]
                          [:to [:set :string]]
                          [:summary :string]
                          [:description :string]
                          [:user-instruction {:optional true} [:maybe :string]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/translate-to-other-langs
                (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-service-descriptions"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:city-codes [:vector :int]]
                          [:sub-category-id :int]
                          [:overview {:optional true
                                      :description "Use this to replace the AI input with non-saved site information"}
                           [:maybe
                            [:map
                             [:city-name (ptv-schema/localized-string-schema nil)]
                             [:service-name (ptv-schema/localized-string-schema nil)]
                             [:sports-facilties [:vector
                                                 [:map
                                                  [:type :string]]]]]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-service-descriptions search (-> req :parameters :body))})}}]

   ["/actions/fetch-ptv-org"
    {:post
     {:require-privilege ptv-read-access?
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-org ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-collections"
    {:post
     {:require-privilege ptv-read-access?
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-service-collections ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/save-ptv-service"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]
                          [:city-codes [:vector :int]]
                          [:source-id {:optional true} [:maybe :string]]
                          [:sub-category-id {:optional true} [:maybe :int]]
                          [:languages [:vector [:enum "fi" "se" "en"]]]
                          [:summary (ptv-schema/localized-string-schema {:max 150})]
                          [:description (ptv-schema/localized-string-schema nil)]
                          [:user-instruction {:optional true} (ptv-schema/localized-string-schema nil)]
                          [:service-name {:optional true} [:maybe :string]]
                          [:service-id {:optional true} [:maybe :string]]]}
      :handler
      (fn [req]
        (try
          {:status 200
           :body (ptv-core/upsert-ptv-service! ptv (-> req :parameters :body))}
          (catch clojure.lang.ExceptionInfo e
            (if-let [ptv-err (ptv-core/parse-ptv-error e)]
              (do
                (log/warnf "save-ptv-service failed (org: %s, source-id: %s, service-id: %s): %s"
                           (-> req :parameters :body :org-id)
                           (-> req :parameters :body :source-id)
                           (-> req :parameters :body :service-id)
                           (pr-str ptv-err))
                {:status 409
                 :body ptv-err})
              (throw e)))))}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:require-privilege ptv-read-access?
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-services ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channels"
    {:post
     {:require-privilege ptv-read-access?
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-service-channels ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channel"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]
                          [:service-channel-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-service-channel ptv
                                                   (-> req :parameters :body :org-id)
                                                   (-> req :parameters :body :service-channel-id))})}}]

   ["/actions/check-ptv-service-channel-link"
    {:post
     {:require-privilege ptv-read-access?
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:service-channel-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/check-service-channel-link
                db
                (select-keys (-> req :parameters :body) [:lipas-id :service-channel-id]))})}}]

   ["/actions/save-ptv-service-location"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body ptv-schema/create-ptv-service-location}
      :handler
      (fn [req]
        (try
          {:status 200
           :body (ptv-core/upsert-ptv-service-location! db ptv search (:identity req) (-> req :parameters :body))}
          (catch clojure.lang.ExceptionInfo e
            (cond
              ;; A different sports-site already owns this service-location.
              (= :double-link (:type (ex-data e)))
              (do
                (log/warnf "save-ptv-service-location rejected (double-link, lipas-id: %s): %s"
                           (-> req :parameters :body :lipas-id)
                           (pr-str (ex-data e)))
                {:status 409
                 :body (ex-data e)})

              (ptv-core/parse-ptv-error e)
              (let [ptv-err (ptv-core/parse-ptv-error e)]
                (log/warnf "save-ptv-service-location failed (lipas-id: %s, org: %s): %s"
                           (-> req :parameters :body :lipas-id)
                           (-> req :parameters :body :org-id)
                           (pr-str ptv-err))
                {:status 409
                 :body ptv-err})

              :else
              (throw e)))))}}]

   ["/actions/save-ptv-meta"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map-of :int #'ptv-schema/ptv-meta]}
      :handler
      (fn [req]
        (try
          {:status 200
           :body (ptv-core/save-ptv-integration-definitions db search (:identity req) (-> req :parameters :body))}
          (catch clojure.lang.ExceptionInfo e
            (if (= :double-link (:type (ex-data e)))
              (do
                (log/warnf "save-ptv-meta rejected (double-link): %s" (pr-str (ex-data e)))
                {:status 409
                 :body (ex-data e)})
              (throw e)))))}}]

   ["/actions/save-ptv-audit"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:audit #'ptv-schema/audit-data]]}
      :handler
      (fn [req]
        (let [body (-> req :parameters :body)]
          (if-let [result (ptv-core/save-ptv-audit db search (:identity req) body)]
            {:status 200 :body result}
            {:status 404 :body {:error "Sports site not found"}})))}}]

   ["/actions/send-audit-notification"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map
                          [:org-id :uuid]
                          [:stats [:map
                                   [:total-sites :int]
                                   [:summary [:map [:approved :int] [:changes-requested :int]]]
                                   [:description [:map [:approved :int] [:changes-requested :int]]]]]]}
      :handler
      (fn [req]
        (let [params (-> req :parameters :body)
              org-id (:org-id params)
              stats (:stats params)
              result (ptv-core/send-audit-notification! db emailer org-id stats)]
          {:status 200 :body result}))}}]])

