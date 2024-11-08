(ns lipas.backend.ptv.handler
  (:require [lipas.backend.ptv.core :as ptv-core]
            [reitit.coercion.malli]))

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

(defn routes [{:keys [db search] :as _ctx}]
  [""
   {:coercion reitit.coercion.malli/coercion
    :tags ["ptv"]}

   ["/actions/get-ptv-integration-candidates"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/get-ptv-integration-candidates search body-params)})}}]

   ["/actions/generate-ptv-descriptions"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/generate-ptv-descriptions search body-params)})}}]

   ["/actions/generate-ptv-service-descriptions"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/generate-ptv-service-descriptions search body-params)})}}]

   ["/actions/save-ptv-service"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/upsert-ptv-service! body-params)})}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/fetch-ptv-services body-params)})}}]

   ["/actions/save-ptv-service-location"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body create-ptv-service-location}
      :handler
      (fn [{:keys [body-params identity]}]
        {:status 200
         :body   (ptv-core/upsert-ptv-service-location! db search identity body-params)})}}]

   ["/actions/save-ptv-meta"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body [:any]}
      :handler
      (fn [{:keys [body-params identity]}]
        {:status 200
         :body   (ptv-core/save-ptv-integration-definitions db search identity body-params)})}}]])
