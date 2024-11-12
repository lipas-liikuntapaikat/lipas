(ns lipas.backend.ptv.handler
  (:require [lipas.backend.ptv.core :as ptv-core]
            [reitit.coercion.malli]
            [reitit.coercion.spec]))

(defn localized-string-schema [string-props]
  [:map
   {:closed true}
   [:fi [:string string-props]]
   [:se [:string string-props]]
   [:en [:string string-props]]])

(def integration-enum
  [:enum "lipas-managed"])

(def ptv-meta
  [:map
   ;; TODO: one or many?
   [:service-channel-id :string]
   [:service-channel-integration integration-enum]
   [:sync-enabled :boolean]
   [:service-integration integration-enum]
   [:summary (localized-string-schema {:max 150})]
   [:description (localized-string-schema {})]])

(def create-ptv-service-location
  [:map
   {:closed true}
   [:org :string]
   [:lipas-id :string]
   [:ptv ptv-meta]])

(defn routes [{:keys [db search ptv] :as _ctx}]
  [""
   {:coercion reitit.coercion.malli/coercion
    :tags ["ptv"]
    :no-doc false}

   ["/actions/get-ptv-integration-candidates"
    {:post
     {:require-privilege :ptv/manage
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/get-ptv-integration-candidates search body-params)})}}]

   ["/actions/generate-ptv-descriptions"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:map
                          [:lipas-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/generate-ptv-descriptions
                   search
                   (-> req :parameters :body :lipas-id))})}}]

   ["/actions/generate-ptv-descriptions-from-data"
    {:post
     {:require-privilege :ptv/manage
      :coercion reitit.coercion.spec/coercion
      :parameters {:body :lipas/new-or-existing-sports-site}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/generate-ptv-descriptions-from-data
                   (-> req :parameters :body))})}}]

   ["/actions/translate-to-other-langs"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:map
                          [:from :string]
                          [:to [:set :string]]
                          [:summary :string]
                          [:description :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/translate-to-other-langs
                   (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-service-descriptions"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/generate-ptv-service-descriptions search body-params)})}}]

   ["/actions/fetch-ptv-org"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-org ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-collections"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-service-collections ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/save-ptv-service"
    {:post
     ;; FIXME: Schema
     {:require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/upsert-ptv-service! ptv body-params)})}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-services ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channels"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-service-channels ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/save-ptv-service-location"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body create-ptv-service-location}
      :handler
      (fn [{:keys [body-params identity]}]
        {:status 200
         :body   (ptv-core/upsert-ptv-service-location! db ptv identity body-params)})}}]

   ["/actions/save-ptv-meta"
    {:post
     {:require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params identity]}]
        {:status 200
         :body   (ptv-core/save-ptv-integration-definitions db search identity body-params)})}}]])
