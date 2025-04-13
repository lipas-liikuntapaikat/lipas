(ns lipas.backend.ptv.handler
  (:require [clojure.spec.alpha :as s]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.roles :as roles]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [reitit.coercion.malli]
            [reitit.coercion.spec]))

(defn localized-string-schema [string-props]
  [:map
   {:closed true}
   [:fi {:optional true} [:string string-props]]
   [:se {:optional true} [:string string-props]]
   [:en {:optional true} [:string string-props]]])

(def integration-enum
  [:enum "lipas-managed" "manual"])

(def audit-status-enum
  [:enum "approved" "changes-requested"])

(def audit-field
  [:map
   {:closed true}
   [:status audit-status-enum]
   [:feedback [:string {:min 0 :max 1000}]]])

(def ptv-audit
  [:map
   {:closed true}
   [:timestamp [:string {:min 24 :max 30}]] ;; ISO-8601 format timestamps
   [:auditor-id :string]
   [:summary {:optional true} audit-field]
   [:description {:optional true} audit-field]])

(def ptv-meta
  [:map
   {:closed true}
   [:org-id :string]
   [:sync-enabled :boolean]
   [:delete-existing {:optional true} :boolean]

   ;; These options aren't used now:
   ;; TODO: Remove
   [:service-channel-integration
    {:optional true}
    integration-enum]
   [:service-integration
    {:optional true}
    integration-enum]

   [:service-channel-ids [:vector :string]]
   [:service-ids [:vector :string]]
   ;; [:languages [:vector :string]]

   [:summary (localized-string-schema {:max 150})]
   [:description (localized-string-schema {})]

   [:audit {:optional true} ptv-audit]])

(def create-ptv-service-location
  [:map
   {:closed true}
   [:org-id :string]
   [:lipas-id :int]
   [:ptv ptv-meta]])

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
         :body   (ptv-core/get-ptv-integration-candidates search (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-descriptions"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:lipas-id :int]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/generate-ptv-descriptions
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
         :body   (ptv-core/generate-ptv-descriptions-from-data
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
         :body   (ptv-core/translate-to-other-langs
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
                             [:city-name (localized-string-schema nil)]
                             [:service-name (localized-string-schema nil)]
                             [:sports-facilties [:vector
                                                 [:map
                                                  [:type :string]]]]]]]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/generate-ptv-service-descriptions search (-> req :parameters :body))})}}]

   ["/actions/fetch-ptv-org"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-org ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-collections"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-service-collections ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/save-ptv-service"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]
                          [:city-codes [:vector :int]]
                          [:source-id :string]
                          [:sub-category-id :int]
                          [:languages [:vector [:enum "fi" "se" "en"]]]
                          [:summary (localized-string-schema {:max 150})]
                          [:description (localized-string-schema nil)]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/upsert-ptv-service! ptv (-> req :parameters :body))})}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-services ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channels"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-service-channels ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channel"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:org-id :string]
                          [:service-channel-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/fetch-ptv-service-channel ptv
                                                     (-> req :parameters :body :org-id)
                                                     (-> req :parameters :body :service-channel-id))})}}]

   ["/actions/save-ptv-service-location"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body create-ptv-service-location}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/upsert-ptv-service-location! db ptv search (:identity req) (-> req :parameters :body))})}}]

   ["/actions/save-ptv-meta"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :coercion reitit.coercion.spec/coercion
      :parameters {:body (s/map-of int? :lipas.sports-site/ptv)}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/save-ptv-integration-definitions db search (:identity req) (-> req :parameters :body))})}}]

   ["/actions/save-ptv-audit"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:audit #'ptv-audit]]}
      :handler
      (fn [req]
        {:status 200
         :body   (ptv-core/save-ptv-audit db search (:identity req) (-> req :parameters :body))})}}]])
