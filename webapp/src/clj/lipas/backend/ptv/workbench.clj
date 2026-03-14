(ns lipas.backend.ptv.workbench
  (:require [lipas.backend.ptv.ai :as ai]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integration]
            [lipas.backend.search :as search]
            [lipas.data.types :as types]
            [cheshire.core :as json]))

(defn preview-service-location
  [search-client lipas-id]
  (let [idx  (get-in search-client [:indices :sports-site :search])
        doc  (-> (search/fetch-document (:client search-client) idx lipas-id)
                 :body
                 :_source)
        prompt-doc (ai/->prompt-doc doc)]
    {:prompt-doc           prompt-doc
     :system-prompt        ai/ptv-system-instruction-v3
     :user-prompt-template ai/generate-utp-descriptions-prompt
     :user-prompt          (format ai/generate-utp-descriptions-prompt (json/encode prompt-doc))
     :defaults             ai/default-params}))

(defn preview-service
  [search-client {:keys [city-code sub-category-id overview-mode aggregate-fields]}]
  (let [type-codes (->> (types/by-sub-category sub-category-id)
                        (map :type-code))
        sites      (ptv-integration/get-eligible-sites
                     search-client
                     {:type-codes type-codes
                      :city-codes [city-code]
                      :owners     ["city" "city-main-owner"]})
        overview   (if (= overview-mode :aggregate)
                     (ptv-core/make-aggregate-overview sites (or aggregate-fields {}))
                     (ptv-core/make-overview sites))
        prompt-doc overview]
    {:prompt-doc           prompt-doc
     :system-prompt        ai/ptv-system-instruction-v3
     :user-prompt-template ai/generate-utp-service-descriptions-prompt
     :user-prompt          (format ai/generate-utp-service-descriptions-prompt (json/encode prompt-doc))
     :defaults             ai/default-params}))

(defn preview-data
  [search params]
  (case (:flow params)
    :service-location (preview-service-location search (:lipas-id params))
    :service          (preview-service search params)))

(defn run-experiment
  [{:keys [system-prompt user-prompt params]}]
  (let [provider (ai/model->provider (:model params))
        base-cfg (case provider
                   :gemini ai/gemini-config
                   ai/openai-config)
        config   (case provider
                   :gemini (merge base-cfg
                                  (select-keys params [:model :top-p :max-tokens
                                                       :temperature :thinking-level]))
                   (merge base-cfg
                          (select-keys params [:model :top-p :presence-penalty
                                               :max-tokens :temperature])
                          {:message-format {:type "json_schema"
                                            :json_schema {:name   "Response"
                                                          :strict true
                                                          :schema ai/Response}}}))
        start    (System/currentTimeMillis)
        result   (case provider
                   :gemini (ai/gemini-complete-raw config system-prompt user-prompt)
                   (ai/complete-raw config system-prompt user-prompt))
        elapsed  (- (System/currentTimeMillis) start)]
    (assoc result :elapsed-ms elapsed)))

(defn routes [{:keys [search] :as _ctx}]
  [""
   {:tags ["ptv-workbench"]
    :no-doc true}

   ["/actions/preview-ptv-workbench-data"
    {:post
     {:require-privilege :users/manage
      :parameters {:body [:map
                          [:flow [:enum :service-location :service]]
                          [:lipas-id {:optional true} :int]
                          [:city-code {:optional true} :int]
                          [:sub-category-id {:optional true} :int]
                          [:overview-mode {:optional true} [:enum :list :aggregate]]
                          [:aggregate-fields {:optional true}
                           [:map
                            [:free-use? {:optional true} :boolean]
                            [:surface-materials? {:optional true} :boolean]
                            [:lighting? {:optional true} :boolean]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (preview-data search (-> req :parameters :body))})}}]

   ["/actions/run-ptv-workbench-experiment"
    {:post
     {:require-privilege :users/manage
      :parameters {:body [:map
                          [:system-prompt :string]
                          [:user-prompt :string]
                          [:params [:map
                                    [:model :string]
                                    [:top-p {:optional true} :double]
                                    [:presence-penalty {:optional true} :double]
                                    [:max-tokens {:optional true} :int]
                                    [:temperature {:optional true} [:maybe :double]]
                                    [:thinking-level {:optional true} :string]]]]}

      :handler
      (fn [req]
        {:status 200
         :body (run-experiment (-> req :parameters :body))})}}]])
