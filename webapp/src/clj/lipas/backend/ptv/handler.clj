(ns lipas.backend.ptv.handler
  (:require [lipas.backend.ptv.core :as ptv-core]))

(defn routes [{:keys [db search] :as _ctx}]
  [["/actions/get-ptv-integration-candidates"
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
      :parameters {:body map?}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/generate-ptv-descriptions search body-params)})}}]

   ["/actions/generate-ptv-service-descriptions"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body map?}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/generate-ptv-service-descriptions search body-params)})}}]

   ["/actions/save-ptv-service"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body map?}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/upsert-ptv-service! body-params)})}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body map?}
      :handler
      (fn [{:keys [body-params]}]
        {:status 200
         :body   (ptv-core/fetch-ptv-services body-params)})}}]

   ["/actions/save-ptv-service-location"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body map?}
      :handler
      (fn [{:keys [body-params identity]}]
        {:status 200
         :body   (ptv-core/upsert-ptv-service-location! db search identity body-params)})}}]

   ["/actions/save-ptv-meta"
    {:post
     {:no-doc     false
      :require-privilege :ptv/manage
      :parameters {:body map?}
      :handler
      (fn [{:keys [body-params identity]}]
        {:status 200
         :body   (ptv-core/save-ptv-integration-definitions db search identity body-params)})}}]])
