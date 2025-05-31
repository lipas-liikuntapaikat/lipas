(ns lipas.backend.ptv.handler
  (:require [clojure.spec.alpha :as s]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.roles :as roles]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.ptv :as ptv-schema]
            [reitit.coercion.malli]
            [reitit.coercion.spec]))

;; Schemas moved to lipas.schema.sports-sites.ptv

(defn routes [{:keys [db search ptv] :as _ctx}]
  [""
   {:coercion reitit.coercion.malli/coercion
    :tags ["ptv"]
    :no-doc false}

   ["/actions/get-ptv-integration-candidates"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
                          [:lipas-id :int]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-descriptions
                search
                (-> req :parameters :body :lipas-id))})}}]

   ["/actions/generate-ptv-descriptions-from-data"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :coercion reitit.coercion.spec/coercion
      :parameters {:body :lipas/new-or-existing-sports-site}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-descriptions-from-data
                (-> req :parameters :body))})}}]

   ["/actions/translate-to-other-langs"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:from :string]
                          [:to [:set :string]]
                          [:summary :string]
                          [:description :string]]}
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
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-org ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-collections"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
                          [:source-id :string]
                          [:sub-category-id :int]
                          [:languages [:vector [:enum "fi" "se" "en"]]]
                          [:summary (ptv-schema/localized-string-schema {:max 150})]
                          [:description (ptv-schema/localized-string-schema nil)]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/upsert-ptv-service! ptv (-> req :parameters :body))})}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-services ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channels"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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

   ["/actions/save-ptv-service-location"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body ptv-schema/create-ptv-service-location}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/upsert-ptv-service-location! db ptv search (:identity req) (-> req :parameters :body))})}}]

   ["/actions/save-ptv-meta"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :coercion reitit.coercion.spec/coercion
      :parameters {:body (s/map-of int? :lipas.sports-site/ptv)}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/save-ptv-integration-definitions db search (:identity req) (-> req :parameters :body))})}}]

   ["/actions/save-ptv-audit"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:audit [:map {:closed true}
                                   [:summary {:optional true} #'ptv-schema/audit-field]
                                   [:description {:optional true} #'ptv-schema/audit-field]]]]}
      :handler
      (fn [req]
        (let [body (-> req :parameters :body)
              audit (:audit body)]
          ;; Additional validation to catch extra fields in audit sub-maps
          (if (or (and (contains? audit :summary)
                       (let [summary (:summary audit)]
                         (some #(not (contains? #{:status :feedback} %)) (keys summary))))
                  (and (contains? audit :description)
                       (let [description (:description audit)]
                         (some #(not (contains? #{:status :feedback} %)) (keys description)))))
            {:status 400 :body {:error "Invalid audit field: extra keys not allowed"}}
            (if-let [result (ptv-core/save-ptv-audit db search (:identity req) body)]
              {:status 200 :body result}
              {:status 404 :body {:error "Sports site not found"}}))))}}]])
