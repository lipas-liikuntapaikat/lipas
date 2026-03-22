(ns lipas.ui.admin.ai-workbench.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

;;; ——— Provider & model registry (frontend) ————————————————————————————
;;
;; Mirrors the backend providers map for UI concerns:
;; default params, model→provider lookup.

(def provider-defaults
  "Per-provider default params. Keys match what the backend expects."
  {:openai {:model            "gpt-4.1-mini"
            :top-p            0.5
            :presence-penalty -2
            :max-tokens       4096
            :temperature      nil}
   :gemini {:model          "gemini-3-flash-preview"
            :top-p          0.90
            :temperature    1.0
            :max-tokens     8192
            :thinking-level "minimal"}})

(def model->provider
  {"gpt-4.1-nano"  :openai "gpt-4.1-mini"  :openai "gpt-4.1"       :openai
   "gpt-4o-mini"   :openai "gpt-4o"        :openai
   "gpt-5-nano"    :openai "gpt-5-mini"    :openai "gpt-5"         :openai "gpt-5.4"       :openai
   "o3-mini"       :openai "o4-mini"       :openai
   "gemini-3-flash-preview" :gemini})

;;; ——— State ———————————————————————————————————————————————————————————

(def default-state
  {:flow              :service-location
   :lipas-id          nil
   :city-code         nil
   :sub-category-id   nil
   :overview-mode     :list
   :aggregate-fields  {:free-use?           false
                       :surface-materials?  false
                       :lighting?           false}
   :preview-data      nil
   :preview-loading?  false
   :defaults          nil
   :templates         nil
   :prompt-template   :v5
   :system-prompt     ""
   :user-prompt       ""
   :params            (:openai provider-defaults)
   :experiment-loading? false
   :results           []
   :selected-lang     :fi})

(def state-path [:admin :ai-workbench])

;;; ——— Events ——————————————————————————————————————————————————————————

(rf/reg-event-db ::init
  (fn [db _]
    (if (get-in db state-path)
      db
      (assoc-in db state-path default-state))))

(rf/reg-event-db ::set-flow
  (fn [db [_ flow]]
    (-> db
        (assoc-in (conj state-path :flow) flow)
        (assoc-in (conj state-path :preview-data) nil)
        (assoc-in (conj state-path :defaults) nil)
        (assoc-in (conj state-path :system-prompt) "")
        (assoc-in (conj state-path :user-prompt) ""))))

(rf/reg-event-db ::set-lipas-id
  (fn [db [_ v]]
    (assoc-in db (conj state-path :lipas-id) v)))

;; Sports site autocomplete search
(rf/reg-event-fx ::search-sites
  (fn [{:keys [db]} [_ search-string]]
    (let [s (some-> search-string str clojure.string/trim)]
      (if (or (empty? s) (< (count s) 2))
        {:db (assoc-in db (conj state-path :site-search-results) [])}
        {:http-xhrio
         {:method          :post
          :uri             (str (:backend-url db) "/actions/autocomplete-sports-site")
          :params          {:search-string s}
          :format          (ajax/json-request-format)
          :response-format (ajax/json-response-format {:keywords? true})
          :on-success      [::search-sites-success]
          :on-failure      [::search-sites-failure]}}))))

(rf/reg-event-db ::search-sites-success
  (fn [db [_ results]]
    (assoc-in db (conj state-path :site-search-results) (vec results))))

(rf/reg-event-db ::search-sites-failure
  (fn [db [_ _resp]]
    (assoc-in db (conj state-path :site-search-results) [])))

(rf/reg-event-db ::set-city-code
  (fn [db [_ v]]
    (assoc-in db (conj state-path :city-code) v)))

(rf/reg-event-db ::set-sub-category-id
  (fn [db [_ v]]
    (assoc-in db (conj state-path :sub-category-id) v)))

(rf/reg-event-db ::set-overview-mode
  (fn [db [_ mode]]
    (assoc-in db (conj state-path :overview-mode) mode)))

(rf/reg-event-db ::toggle-aggregate-field
  (fn [db [_ field]]
    (update-in db (conj state-path :aggregate-fields field) not)))

(rf/reg-event-db ::set-system-prompt
  (fn [db [_ v]]
    (assoc-in db (conj state-path :system-prompt) v)))

(rf/reg-event-db ::set-user-prompt
  (fn [db [_ v]]
    (assoc-in db (conj state-path :user-prompt) v)))

(rf/reg-event-db ::set-param
  (fn [db [_ k v]]
    (assoc-in db (conj state-path :params k) v)))

(rf/reg-event-db ::set-model
  "Set model and reset params to provider defaults when provider changes."
  (fn [db [_ new-model]]
    (let [current-model (get-in db (conj state-path :params :model))
          old-provider  (model->provider current-model)
          new-provider  (model->provider new-model)]
      (if (= old-provider new-provider)
        ;; Same provider — just swap model
        (assoc-in db (conj state-path :params :model) new-model)
        ;; Provider changed — reset to new provider defaults, override model
        (assoc-in db (conj state-path :params)
                  (assoc (get provider-defaults new-provider) :model new-model))))))

(rf/reg-event-db ::set-lang
  (fn [db [_ lang]]
    (assoc-in db (conj state-path :selected-lang) lang)))

(rf/reg-event-db ::clear-results
  (fn [db _]
    (assoc-in db (conj state-path :results) [])))

(rf/reg-event-db ::set-result-grade
  (fn [db [_ result-id grade]]
    (update-in db (conj state-path :results)
               (fn [results]
                 (mapv (fn [r]
                         (if (= (:id r) result-id)
                           (assoc r :grade grade)
                           r))
                       results)))))

(rf/reg-event-db ::set-result-notes
  (fn [db [_ result-id notes]]
    (update-in db (conj state-path :results)
               (fn [results]
                 (mapv (fn [r]
                         (if (= (:id r) result-id)
                           (assoc r :grade-notes notes)
                           r))
                       results)))))

(rf/reg-event-db ::reset-prompts
  (fn [db _]
    (let [defaults (get-in db (conj state-path :defaults))]
      (-> db
          (assoc-in (conj state-path :system-prompt) (:system-prompt defaults))
          (assoc-in (conj state-path :user-prompt) (:user-prompt defaults))
          (assoc-in (conj state-path :params) (merge (:params (get-in db state-path))
                                                     (:params defaults)))))))

(rf/reg-event-db ::reset-llm-params
  (fn [db _]
    (let [current-model (get-in db (conj state-path :params :model))
          provider      (model->provider current-model)
          defaults      (get provider-defaults provider)]
      (assoc-in db (conj state-path :params) defaults))))

;; HTTP effects

(rf/reg-event-fx ::fetch-preview
  (fn [{:keys [db]} _]
    (let [state   (get-in db state-path)
          flow    (:flow state)
          token   (-> db :user :login :token)
          body    (cond-> {:flow flow}
                    (= flow :service-location)
                    (assoc :lipas-id (:lipas-id state))

                    (= flow :service)
                    (assoc :city-code (:city-code state)
                           :sub-category-id (:sub-category-id state))

                    (and (= flow :service)
                         (= (:overview-mode state) :aggregate))
                    (assoc :overview-mode :aggregate
                           :aggregate-fields (:aggregate-fields state)))]
      {:db (-> db
               (assoc-in (conj state-path :preview-loading?) true)
               (assoc-in (conj state-path :preview-error) nil))
       :http-xhrio
       {:method          :post
        :uri             (str (:backend-url db) "/actions/preview-ptv-workbench-data")
        :headers         {:Authorization (str "Token " token)}
        :params          body
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::fetch-preview-success]
        :on-failure      [::fetch-preview-failure]}})))

(rf/reg-event-db ::fetch-preview-success
  (fn [db [_ resp]]
    (let [template-key (get-in db (conj state-path :prompt-template) :v5)
          templates    (:templates resp)
          active       (get templates template-key (get templates :v5))]
      (-> db
          (assoc-in (conj state-path :preview-loading?) false)
          (assoc-in (conj state-path :preview-data) (:prompt-doc resp))
          (assoc-in (conj state-path :templates) templates)
          (assoc-in (conj state-path :system-prompt) (:system-prompt active))
          (assoc-in (conj state-path :user-prompt) (:user-prompt active))
          (assoc-in (conj state-path :defaults)
                    {:system-prompt (:system-prompt active)
                     :user-prompt   (:user-prompt active)
                     :params        (:defaults resp)})))))

(rf/reg-event-db ::set-prompt-template
  (fn [db [_ template-key]]
    (let [templates (get-in db (conj state-path :templates))
          tmpl      (get templates template-key)]
      (when tmpl
        (-> db
            (assoc-in (conj state-path :prompt-template) template-key)
            (assoc-in (conj state-path :system-prompt) (:system-prompt tmpl))
            (assoc-in (conj state-path :user-prompt) (:user-prompt tmpl))
            (assoc-in (conj state-path :defaults)
                      (-> (get-in db (conj state-path :defaults))
                          (assoc :system-prompt (:system-prompt tmpl)
                                 :user-prompt   (:user-prompt tmpl)))))))))

(rf/reg-event-db ::fetch-preview-failure
  (fn [db [_ resp]]
    (-> db
        (assoc-in (conj state-path :preview-loading?) false)
        (assoc-in (conj state-path :preview-error) (str resp)))))

(rf/reg-event-fx ::run-experiment
  (fn [{:keys [db]} _]
    (let [state  (get-in db state-path)
          token  (-> db :user :login :token)
          params (-> (:params state)
                     (update :temperature #(when % %)))]
      {:db (-> db
               (assoc-in (conj state-path :experiment-loading?) true)
               (assoc-in (conj state-path :experiment-error) nil))
       :http-xhrio
       {:method          :post
        :uri             (str (:backend-url db) "/actions/run-ptv-workbench-experiment")
        :headers         {:Authorization (str "Token " token)}
        :params          {:system-prompt (:system-prompt state)
                          :user-prompt   (:user-prompt state)
                          :params        params}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::run-experiment-success]
        :on-failure      [::run-experiment-failure]}})))

(rf/reg-event-db ::run-experiment-success
  (fn [db [_ resp]]
    (let [state  (get-in db state-path)
          result {:id            (random-uuid)
                  :timestamp     (.toISOString (js/Date.))
                  :params        (:params state)
                  :system-prompt (:system-prompt state)
                  :user-prompt   (:user-prompt state)
                  :response      resp
                  :usage         (:usage resp)
                  :elapsed-ms    (:elapsed-ms resp)}]
      (-> db
          (assoc-in (conj state-path :experiment-loading?) false)
          (update-in (conj state-path :results) #(into [result] %))))))

;;; ——— Pricing & cost ————————————————————————————————————————————————

;; Pricing per 1M tokens [input output] in USD. Source: openai.com/api/pricing, ai.google.dev
(def model-pricing
  {"gpt-4.1-nano"  [0.20  0.80]
   "gpt-4.1-mini"  [0.80  3.20]
   "gpt-4.1"       [3.00  12.00]
   "gpt-4o-mini"   [0.15  0.60]
   "gpt-4o"        [2.50  10.00]
   "gpt-5-nano"    [0.05  0.40]
   "gpt-5-mini"    [0.25  2.00]
   "gpt-5"         [1.25  10.00]
   "gpt-5.4"       [2.50  15.00]
   "o3-mini"       [1.10  4.40]
   "o4-mini"       [1.10  4.40]
   "gemini-3-flash-preview" [0.50 3.00]})

(defn estimate-cost
  "Estimate cost in USD from usage map and model name."
  [model usage]
  (when-let [[input-price output-price] (model-pricing model)]
    (let [input-tokens  (:prompt_tokens usage 0)
          output-tokens (:completion_tokens usage 0)]
      (+ (* input-tokens (/ input-price 1000000))
         (* output-tokens (/ output-price 1000000))))))

(defn parse-content
  "Parse the content from an OpenAI/Gemini choice. Content may be a string (JSON) or already a map."
  [choice]
  (let [content (-> choice :message :content)]
    (if (string? content)
      (try
        (-> content js/JSON.parse (js->clj :keywordize-keys true))
        (catch :default _ {:description {:fi content :se "" :en ""}
                           :summary     {:fi "" :se "" :en ""}}))
      content)))

;;; ——— Export ————————————————————————————————————————————————————————

(rf/reg-event-fx ::export-results
  (fn [{:keys [db]} _]
    (let [results (get-in db (conj state-path :results))
          export  (mapv (fn [r]
                          {:timestamp     (:timestamp r)
                           :params        (:params r)
                           :system-prompt (:system-prompt r)
                           :user-prompt   (:user-prompt r)
                           :usage         (:usage r)
                           :elapsed-ms    (:elapsed-ms r)
                           :choices       (-> r :response :choices)
                           :grade         (:grade r)
                           :grade-notes   (:grade-notes r)})
                        results)
          json    (.stringify js/JSON (clj->js export) nil 2)]
      {:lipas.ui.effects/save-as!
       {:blob     (js/Blob. #js [json] #js {:type "application/json"})
        :filename (str "ptv-workbench-results-"
                       (.toISOString (js/Date.))
                       ".json")}})))

(def excel-headers
  [[:timestamp       "Timestamp"]
   [:model           "Model"]
   [:top-p           "top_p"]
   [:temperature     "Temperature"]
   [:presence-penalty "Presence Penalty"]
   [:thinking-level  "Thinking Level"]
   [:elapsed-s       "Time (s)"]
   [:prompt-tokens   "Input Tokens"]
   [:output-tokens   "Output Tokens"]
   [:total-tokens    "Total Tokens"]
   [:cost-usd        "Cost (USD)"]
   [:system-prompt   "System Prompt"]
   [:user-prompt     "User Prompt"]
   [:summary-fi      "Summary (FI)"]
   [:summary-se      "Summary (SE)"]
   [:summary-en      "Summary (EN)"]
   [:description-fi  "Description (FI)"]
   [:description-se  "Description (SE)"]
   [:description-en  "Description (EN)"]
   [:grade           "Grade (1-5)"]
   [:grade-notes     "Grade Notes"]])

(defn- result->excel-row [r]
  (let [params  (:params r)
        usage   (:usage r)
        parsed  (parse-content (-> r :response :choices first))
        model   (:model params)]
    {:timestamp        (:timestamp r)
     :model            model
     :top-p            (:top-p params)
     :temperature      (:temperature params)
     :presence-penalty (:presence-penalty params)
     :thinking-level   (:thinking-level params)
     :elapsed-s        (some-> (:elapsed-ms r) (/ 1000.0))
     :prompt-tokens    (:prompt_tokens usage)
     :output-tokens    (:completion_tokens usage)
     :total-tokens     (:total_tokens usage)
     :cost-usd         (estimate-cost model usage)
     :system-prompt    (:system-prompt r)
     :user-prompt      (:user-prompt r)
     :summary-fi       (get-in parsed [:summary :fi] "")
     :summary-se       (get-in parsed [:summary :se] "")
     :summary-en       (get-in parsed [:summary :en] "")
     :description-fi   (get-in parsed [:description :fi] "")
     :description-se   (get-in parsed [:description :se] "")
     :description-en   (get-in parsed [:description :en] "")
     :grade            (:grade r)
     :grade-notes      (:grade-notes r)}))

(rf/reg-event-fx ::export-results-excel
  (fn [{:keys [db]} _]
    (let [results (get-in db (conj state-path :results))
          rows    (mapv result->excel-row results)]
      {:lipas.ui.effects/download-excel!
       {:filename "ptv-workbench-results"
        :sheet    {:data (utils/->excel-data excel-headers rows)}}})))

(rf/reg-event-db ::run-experiment-failure
  (fn [db [_ resp]]
    (-> db
        (assoc-in (conj state-path :experiment-loading?) false)
        (assoc-in (conj state-path :experiment-error) (str resp)))))
