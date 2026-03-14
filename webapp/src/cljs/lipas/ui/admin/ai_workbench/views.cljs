(ns lipas.ui.admin.ai-workbench.views
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Slider$default" :as Slider]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Autocomplete$default" :as Autocomplete]
            ["@mui/material/ToggleButton$default" :as ToggleButton]
            ["@mui/material/ToggleButtonGroup$default" :as ToggleButtonGroup]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            ["react" :as react]
            [lipas.data.types :as types]
            [lipas.ui.admin.ai-workbench.events :as events]
            [lipas.ui.admin.ai-workbench.subs :as subs]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.events :as ui-events]
            [lipas.ui.ptv.controls :as ptv-controls]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

;;; ——— Constants ————————————————————————————————————————————————————————

(def model-options
  [;; OpenAI — Budget
   {:value "gpt-4.1-nano"  :label "GPT-4.1 Nano"   :desc "Cheapest, fastest"}
   {:value "gpt-5-nano"    :label "GPT-5 Nano"      :desc "Budget, next-gen"}
   ;; OpenAI — Balanced
   {:value "gpt-4.1-mini"  :label "GPT-4.1 Mini"    :desc "Good balance of quality and cost"}
   {:value "gpt-4o-mini"   :label "GPT-4o Mini"     :desc "Balanced, multimodal"}
   {:value "gpt-5-mini"    :label "GPT-5 Mini"      :desc "Balanced, next-gen"}
   ;; OpenAI — Quality
   {:value "gpt-4.1"       :label "GPT-4.1"         :desc "High quality"}
   {:value "gpt-4o"        :label "GPT-4o"          :desc "High quality, multimodal"}
   {:value "gpt-5"         :label "GPT-5"           :desc "High quality, next-gen"}
   {:value "gpt-5.4"       :label "GPT-5.4"         :desc "Latest generation"}
   ;; OpenAI — Reasoning
   {:value "o3-mini"       :label "o3 Mini"          :desc "Reasoning model, cost-effective"}
   {:value "o4-mini"       :label "o4 Mini"          :desc "Reasoning model, latest"}
   ;; Gemini
   {:value "gemini-3-flash-preview" :label "Gemini 3 Flash" :desc "Google, fast preview"}])

;; --- Helpers ---

(defn- copy! [text]
  (rf/dispatch [::ui-events/copy-to-clipboard! text]))

(defn- help-icon [tooltip-text]
  [:> Tooltip {:title tooltip-text :arrow true}
   [:> Icon {:sx    #js {:fontSize "1rem" :color "action.active" :cursor "help"}
             :color "action"}
    "help_outline"]])

(defn- slider-labels
  "Row of evenly-spaced labels below a slider. Labels is a vec of strings."
  [labels]
  [:> Stack {:direction "row" :justify-content "space-between" :sx #js {:mt -0.5}}
   (for [[i label] (map-indexed vector labels)]
     ^{:key i}
     [:> Typography {:variant    "caption"
                     :color      "text.secondary"
                     :text-align (cond
                                   (zero? i) "left"
                                   (= i (dec (count labels))) "right"
                                   :else "center")}
      label])])

(defn- section-header [title]
  [:> Typography {:variant   "overline"
                  :color     "text.secondary"
                  :sx        #js {:mt 2 :mb 1 :letterSpacing "0.1em"}}
   title])

;; --- Flow selector ---

(r/defc flow-selector []
  (let [flow @(rf/subscribe [::subs/flow])]
    [:<>
     [section-header "MODE"]
     [:> ToggleButtonGroup
      {:value     (some-> flow name)
       :exclusive true
       :fullWidth true
       :on-change (fn [_e v]
                    (when v
                      (rf/dispatch [::events/set-flow (keyword v)])))}
      [:> ToggleButton {:value "service-location"
                        :sx    #js {:textTransform "none" :py 1.5}}
       [:> Stack {:align-items "center" :spacing 0}
        [:> Typography {:variant "body2" :font-weight "bold"} "Service Location"]
        [:> Typography {:variant "caption" :color "text.secondary"} "one LIPAS site"]]]
      [:> ToggleButton {:value "service"
                        :sx    #js {:textTransform "none" :py 1.5}}
       [:> Stack {:align-items "center" :spacing 0}
        [:> Typography {:variant "body2" :font-weight "bold"} "Service"]
        [:> Typography {:variant "caption" :color "text.secondary"} "across cities"]]]]]))

;; --- Input sections ---

(defn- format-site-option [site]
  (str (:lipas-id site) " — " (:name site)
       (when-let [t (:type-name site)] (str " (" t ")"))
       (when-let [c (:city-name site)] (str ", " c))))

(r/defc service-location-input []
  (let [lipas-id       @(rf/subscribe [::subs/lipas-id])
        loading?       @(rf/subscribe [::subs/preview-loading?])
        error          @(rf/subscribe [::subs/preview-error])
        search-results @(rf/subscribe [::subs/site-search-results])
        options        (hooks/use-memo
                         (fn [] (to-array (or search-results [])))
                         [search-results])
        debounce-ref   (hooks/use-ref nil)
        selected-ref   (hooks/use-ref nil)]
    [:<>
     [section-header "DATA SOURCE"]
     [:> Stack {:direction "row" :spacing 2 :align-items "center"}
      (r/create-element
        Autocomplete
        #js {:options            options
             :value              (.-current selected-ref)
             :filterOptions      (fn [opts _state] opts) ;; server-side filtering
             :getOptionKey       (fn [opt] (str (:lipas-id opt)))
             :getOptionLabel     (fn [opt]
                                   (if (map? opt)
                                     (format-site-option opt)
                                     (str opt)))
             :isOptionEqualToValue (fn [opt val]
                                     (= (:lipas-id opt) (:lipas-id val)))
             :onInputChange      (fn [_e v reason]
                                   (when (= reason "input")
                                     (when-let [t (.-current debounce-ref)]
                                       (js/clearTimeout t))
                                     (set! (.-current debounce-ref)
                                           (js/setTimeout
                                             #(rf/dispatch [::events/search-sites v])
                                             250))))
             :onChange            (fn [_e v]
                                    (let [site (js->clj v :keywordize-keys true)]
                                      (set! (.-current selected-ref) v)
                                      (when-let [id (:lipas-id site)]
                                        (rf/dispatch [::events/set-lipas-id id])
                                        (rf/dispatch [::events/fetch-preview]))))
             :renderInput         (fn [^js props]
                                    (r/create-element
                                      TextField
                                      (doto props
                                        (set! -label "Search sports facility")
                                        (set! -size "small")
                                        (set! -placeholder "Name, type or LIPAS ID..."))))
             :sx                  #js {:minWidth 400}
             :noOptionsText       "Type to search..."
             :loading             loading?})
      (when loading?
        [:> CircularProgress {:size 20}])]
     (when error
       [:> Alert {:severity "error" :sx #js {:mt 1}} (str error)])]))

(r/defc service-input []
  (let [locale           @(rf/subscribe [:lipas.ui.subs/locale])
        city-code        @(rf/subscribe [::subs/city-code])
        sub-category-id  @(rf/subscribe [::subs/sub-category-id])
        loading?         @(rf/subscribe [::subs/preview-loading?])
        error            @(rf/subscribe [::subs/preview-error])
        overview-mode    @(rf/subscribe [::subs/overview-mode])
        aggregate-fields @(rf/subscribe [::subs/aggregate-fields])
        sub-cats         (hooks/use-memo
                           (fn []
                             (->> types/sub-categories
                                  vals
                                  (map (fn [sc]
                                         {:value (:type-code sc)
                                          :label (get-in sc [:name locale] (get-in sc [:name :fi]))}))
                                  (sort-by :label)))
                           [locale])]
    [:<>
     [section-header "DATA SOURCE"]
     [:> Stack {:spacing 2}
      [selects/city-selector-single
       {:value     city-code
        :on-change #(rf/dispatch [::events/set-city-code %])}]
      [ac/autocomplete2
       {:options  sub-cats
        :label    "Sub-category"
        :value    sub-category-id
        :onChange (fn [_e v]
                    (rf/dispatch [::events/set-sub-category-id (ac/safe-value v)]))}]

      ;; Overview mode selector
      [section-header "OVERVIEW MODE"]
      [:> ToggleButtonGroup
       {:value     (name overview-mode)
        :exclusive true
        :fullWidth true
        :size      "small"
        :on-change (fn [_e v]
                     (when v
                       (rf/dispatch [::events/set-overview-mode (keyword v)])))}
       [:> ToggleButton {:value "list" :sx #js {:textTransform "none"}}
        "List each facility"]
       [:> ToggleButton {:value "aggregate" :sx #js {:textTransform "none"}}
        "Aggregate overview"]]

      ;; Aggregate field checkboxes (visible when aggregate mode)
      (when (= overview-mode :aggregate)
        [:> Stack {:spacing 0 :sx #js {:ml 1}}
         [:> FormControlLabel
          {:control (r/as-element
                      [:> Checkbox {:checked true :disabled true :size "small"}])
           :label   "Count by type (always included)"}]
         [:> FormControlLabel
          {:control (r/as-element
                      [:> Checkbox {:checked  (:free-use? aggregate-fields)
                                    :size     "small"
                                    :on-change #(rf/dispatch [::events/toggle-aggregate-field :free-use?])}])
           :label   "Free / paid use"}]
         [:> FormControlLabel
          {:control (r/as-element
                      [:> Checkbox {:checked  (:surface-materials? aggregate-fields)
                                    :size     "small"
                                    :on-change #(rf/dispatch [::events/toggle-aggregate-field :surface-materials?])}])
           :label   "Surface materials"}]
         [:> FormControlLabel
          {:control (r/as-element
                      [:> Checkbox {:checked  (:lighting? aggregate-fields)
                                    :size     "small"
                                    :on-change #(rf/dispatch [::events/toggle-aggregate-field :lighting?])}])
           :label   "Lighting"}]])

      [:> Button
       {:variant  "contained"
        :size     "small"
        :disabled (or loading?
                      (nil? city-code)
                      (nil? sub-category-id))
        :on-click #(rf/dispatch [::events/fetch-preview])}
       (if loading?
         [:> CircularProgress {:size 20}]
         "Generate prompt")]
      (when error
        [:> Alert {:severity "error"} (str error)])]]))

(r/defc input-section []
  (let [flow @(rf/subscribe [::subs/flow])]
    (case flow
      :service-location [service-location-input]
      :service          [service-input]
      nil)))

;; --- Prompts section (collapsible) ---

(r/defc data-preview-subsection []
  (r/with-let [expanded? (r/atom false)]
    (let [preview @(rf/subscribe [::subs/preview-data])]
      (when preview
        [:<>
         [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
          [:> Button
           {:size     "small"
            :sx       #js {:textTransform "none" :justifyContent "flex-start" :pl 0}
            :on-click #(swap! expanded? not)
            :startIcon (r/as-element [:> Icon (if @expanded? "expand_less" "expand_more")])}
           "LIPAS source data"]
          [:> Tooltip {:title "Copy source data as JSON" :arrow true}
           [:> IconButton {:size "small"
                           :on-click #(copy!
                                        (.stringify js/JSON (clj->js preview) nil 2))}
            [:> Icon {:sx #js {:fontSize "1.1rem"}} "content_copy"]]]]
         [:> Collapse {:in @expanded?}
          [:> Box {:component "pre"
                   :sx        #js {:overflow      "auto"
                                   :maxHeight     400
                                   :fontSize      "0.75rem"
                                   :bgcolor       "#f5f5f5"
                                   :p             1
                                   :borderRadius  1
                                   :mt            1}}
           (.stringify js/JSON (clj->js preview) nil 2)]]]))))

(r/defc prompt-editor-section []
  (r/with-let [system-expanded? (r/atom false)
               user-expanded?   (r/atom true)]
    (let [system-prompt @(rf/subscribe [::subs/system-prompt])
          user-prompt   @(rf/subscribe [::subs/user-prompt])
          defaults      @(rf/subscribe [::subs/defaults])]
      (when defaults
        [:<>
         [:> Divider {:sx #js {:my 1}}]
         [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
          [section-header "PROMPTS"]
          [:> Button
           {:size     "small"
            :on-click #(rf/dispatch [::events/reset-prompts])}
           "Reset defaults"]]
         [:> Stack {:spacing 2 :sx #js {:mt 1}}
          ;; System prompt — collapsible, collapsed by default
          [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
           [:> Button
            {:size      "small"
             :sx        #js {:textTransform "none" :justifyContent "flex-start" :pl 0}
             :on-click  #(swap! system-expanded? not)
             :startIcon (r/as-element [:> Icon (if @system-expanded? "expand_less" "expand_more")])}
            "System Prompt"]
           [:> Tooltip {:title "Copy system prompt to clipboard" :arrow true}
            [:> IconButton {:size "small"
                            :on-click #(copy! (or system-prompt ""))}
             [:> Icon {:sx #js {:fontSize "1.1rem"}} "content_copy"]]]]
          [:> Collapse {:in @system-expanded?}
           [:> TextField
            {:label      "System Prompt"
             :multiline  true
             :rows       12
             :fullWidth  true
             :value      (or system-prompt "")
             :on-change  #(rf/dispatch [::events/set-system-prompt (.. % -target -value)])
             :InputProps #js {:inputComponent text-fields/patched-textarea}}]]
          ;; User prompt — collapsible, expanded by default
          [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
           [:> Button
            {:size      "small"
             :sx        #js {:textTransform "none" :justifyContent "flex-start" :pl 0}
             :on-click  #(swap! user-expanded? not)
             :startIcon (r/as-element [:> Icon (if @user-expanded? "expand_less" "expand_more")])}
            "User Prompt"]
           [:> Tooltip {:title "Copy user prompt to clipboard" :arrow true}
            [:> IconButton {:size "small"
                            :on-click #(copy! (or user-prompt ""))}
             [:> Icon {:sx #js {:fontSize "1.1rem"}} "content_copy"]]]]
          [:> Collapse {:in @user-expanded?}
           [:> TextField
            {:label      "User Prompt"
             :multiline  true
             :rows       12
             :fullWidth  true
             :value      (or user-prompt "")
             :on-change  #(rf/dispatch [::events/set-user-prompt (.. % -target -value)])
             :InputProps #js {:inputComponent text-fields/patched-textarea}}]]
          [data-preview-subsection]]]))))

;; --- LLM Settings ---

(defn- current-provider [params]
  (events/model->provider (:model params)))

;; --- OpenAI-specific controls ---

(defn- openai-controls [params]
  [:<>
   ;; top-p slider (0–1)
   [:> Stack {:spacing 0}
    [:> Stack {:direction "row" :align-items "center" :spacing 1}
     [:> Typography {:variant "body2"}
      (str "Focus (top_p): " (.toFixed (or (:top-p params) 0.5) 2))]
     [help-icon "Controls word variety. Low = predictable text, high = more varied word choices."]]
    [:> Slider
     {:value     (or (:top-p params) 0.5)
      :min       0
      :max       1
      :step      0.05
      :on-change (fn [_e v] (rf/dispatch [::events/set-param :top-p v]))}]
    [slider-labels ["Focused" "Balanced" "Varied"]]]

   ;; presence-penalty slider
   [:> Stack {:spacing 0}
    [:> Stack {:direction "row" :align-items "center" :spacing 1}
     [:> Typography {:variant "body2"}
      (str "Repetition (presence_penalty): " (.toFixed (or (:presence-penalty params) -2) 1))]
     [help-icon "Negative = stay on topic, positive = avoid repetition and explore new topics."]]
    [:> Slider
     {:value     (or (:presence-penalty params) -2)
      :min       -2
      :max       2
      :step      0.1
      :on-change (fn [_e v] (rf/dispatch [::events/set-param :presence-penalty v]))}]
    [slider-labels ["On-topic" "Neutral" "Diverse"]]]

   ;; temperature (optional, with toggle)
   [:> Stack {:spacing 0}
    [:> Stack {:direction "row" :align-items "center" :spacing 1}
     [:> FormControlLabel
      {:control (r/as-element
                  [:> Switch
                   {:checked   (some? (:temperature params))
                    :size      "small"
                    :on-change (fn [_e checked]
                                 (rf/dispatch [::events/set-param :temperature
                                               (if checked 1.0 nil)]))}])
       :label   (r/as-element
                  [:> Typography {:variant "body2"}
                   (str "Randomness (temperature)"
                        (when (some? (:temperature params))
                          (str ": " (.toFixed (:temperature params) 2))))])}]
     [help-icon "Alternative variety control. Usually disabled when using top_p focus."]]
    (when (some? (:temperature params))
      [:<>
       [:> Slider
        {:value     (:temperature params)
         :min       0
         :max       2
         :step      0.05
         :on-change (fn [_e v] (rf/dispatch [::events/set-param :temperature v]))}]
       [slider-labels ["Precise" "Balanced" "Creative"]]])]])

;; --- Gemini-specific controls ---

(defn- gemini-controls [params]
  [:<>
   ;; top-p slider (narrow range 0.85–0.95)
   [:> Stack {:spacing 0}
    [:> Stack {:direction "row" :align-items "center" :spacing 1}
     [:> Typography {:variant "body2"}
      (str "Focus (top_p): " (.toFixed (or (:top-p params) 0.90) 2))]
     [help-icon "Controls word variety. Recommended range 0.85\u20130.95 for Gemini."]]
    [:> Slider
     {:value     (or (:top-p params) 0.90)
      :min       0.85
      :max       0.95
      :step      0.01
      :on-change (fn [_e v] (rf/dispatch [::events/set-param :top-p v]))}]
    [slider-labels ["0.85" "0.90" "0.95"]]]

   ;; thinking level toggle
   [:> Stack {:spacing 0 :sx #js {:mt 1}}
    [:> Stack {:direction "row" :align-items "center" :spacing 1}
     [:> Typography {:variant "body2"} "Thinking level"]
     [help-icon "Controls how much reasoning the model does before answering. Higher = better quality but slower."]]
    [:> ToggleButtonGroup
     {:value     (or (:thinking-level params) "minimal")
      :exclusive true
      :size      "small"
      :fullWidth true
      :on-change (fn [_e v]
                   (when v
                     (rf/dispatch [::events/set-param :thinking-level v])))}
     [:> ToggleButton {:value "minimal" :sx #js {:textTransform "none"}} "Minimal"]
     [:> ToggleButton {:value "low" :sx #js {:textTransform "none"}} "Low"]
     [:> ToggleButton {:value "medium" :sx #js {:textTransform "none"}} "Medium"]
     [:> ToggleButton {:value "high" :sx #js {:textTransform "none"}} "High"]]]])

;; --- LLM settings panel (provider-aware) ---

(r/defc llm-settings-panel []
  (r/with-let [expanded? (r/atom false)]
    (let [params @(rf/subscribe [::subs/params])]
      (when params
        (let [provider (current-provider params)]
          [:<>
           [:> Divider {:sx #js {:my 1}}]
           [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
            [:> Button
             {:size      "small"
              :sx        #js {:textTransform "none" :pl 0}
              :on-click  #(swap! expanded? not)
              :startIcon (r/as-element [:> Icon (if @expanded? "expand_less" "expand_more")])}
             "LLM Settings"]
            [:> Button
             {:size     "small"
              :on-click #(rf/dispatch [::events/reset-llm-params])}
             "Reset defaults"]]
           [:> Collapse {:in @expanded?}
            [:> Stack {:spacing 3 :sx #js {:mt 1}}

             ;; Model dropdown (always visible)
             [:> TextField
              {:label     "AI Model"
               :select    true
               :size      "small"
               :fullWidth true
               :value     (or (:model params) "gpt-4.1-mini")
               :on-change #(rf/dispatch [::events/set-model (.. % -target -value)])}
              (for [{:keys [value label desc]} model-options]
                ^{:key value}
                [:> MenuItem {:value value}
                 [:> Stack {:spacing 0}
                  [:> Typography {:variant "body2"} label]
                  [:> Typography {:variant "caption" :color "text.secondary"} desc]]])]

             ;; Provider-specific controls
             (case provider
               :gemini [gemini-controls params]
               [openai-controls params])]]])))))

;; --- Run button ---

(r/defc run-button []
  (let [loading?  @(rf/subscribe [::subs/experiment-loading?])
        defaults  @(rf/subscribe [::subs/defaults])
        error     @(rf/subscribe [::subs/experiment-error])]
    (when defaults
      [:<>
       [:> Divider {:sx #js {:my 1}}]
       [:> Stack {:direction "row" :spacing 2 :align-items "center"}
        [:> Button
         {:variant   "contained"
          :color     "primary"
          :fullWidth true
          :disabled  loading?
          :on-click  #(rf/dispatch [::events/run-experiment])
          :startIcon (when loading?
                       (r/as-element [:> CircularProgress {:size 20}]))}
         "Run Experiment"]
        [:> Tooltip {:title "Clear all results" :arrow true}
         [:> IconButton
          {:on-click #(rf/dispatch [::events/clear-results])
           :color    "default"}
          [:> Icon "delete_outline"]]]]
       (when error
         [:> Alert {:severity "error" :sx #js {:mt 1}} (str error)])])))

;; --- Result cards ---

(def parse-content events/parse-content)

(defn- char-count-label [count limit]
  [:> Typography {:variant   "caption"
                  :color     (if (> count limit) "error" "text.secondary")
                  :fontWeight (when (> count limit) "bold")}
   (str count "/" limit " chars")])

(defn- format-timestamp [iso-string]
  (try
    (let [d (js/Date. iso-string)]
      (str (.toLocaleDateString d "fi-FI") " "
           (.toLocaleTimeString d "fi-FI" #js {:hour "2-digit" :minute "2-digit"})))
    (catch :default _ iso-string)))

(r/defc result-card [{:keys [result number default-expanded?]}]
  (r/with-let [expanded?    (r/atom (boolean default-expanded?))
               show-params? (r/atom false)]
    (let [lang     @(rf/subscribe [::subs/selected-lang])
          response (:response result)
          choices  (:choices response)
          usage    (:usage response)]
      [:> Card {:variant "outlined" :sx #js {:mb 2}}
       ;; Clickable header
       [:> Box {:sx       #js {:px 2 :py 1.5 :cursor "pointer"
                               :display "flex" :alignItems "center" :justifyContent "space-between"
                               "&:hover" #js {:bgcolor "action.hover"}}
                :on-click #(swap! expanded? not)}
        [:> Stack {:direction "row" :spacing 1 :align-items "center"}
         [:> Icon {:sx #js {:fontSize "1.2rem" :color "action.active"}}
          (if @expanded? "expand_less" "expand_more")]
         [:> Typography {:variant "subtitle2"}
          (str "Experiment #" number)]
         [:> Typography {:variant "body2" :color "text.secondary"}
          (format-timestamp (:timestamp result))]
         [:> Chip {:label (-> result :params :model) :size "small" :variant "outlined"}]
         (when-let [ms (:elapsed-ms result)]
           [:> Chip {:label (str (/ ms 1000.0) "s")
                     :size  "small" :variant "outlined"}])
         (when-let [cost (events/estimate-cost (-> result :params :model) usage)]
           [:> Chip {:label (str "$" (.toFixed cost 4))
                     :size  "small" :variant "outlined"
                     :sx    #js {:fontWeight "bold"}}])]
        [:> Stack {:direction "row" :spacing 0}
         [:> Tooltip {:title "Copy summary and description for selected language" :arrow true}
          [:> IconButton {:size     "small"
                          :on-click (fn [e]
                                      (.stopPropagation e)
                                      (let [choice  (first choices)
                                            parsed  (parse-content choice)
                                            summary (get-in parsed [:summary lang] "")
                                            desc    (get-in parsed [:description lang] "")]
                                        (copy!
                                          (str "Summary:\n" summary "\n\nDescription:\n" desc))))}
           [:> Icon {:sx #js {:fontSize "1.1rem"}} "content_copy"]]]
         [:> Tooltip {:title "Show parameters" :arrow true}
          [:> IconButton {:size     "small"
                          :on-click (fn [e]
                                      (.stopPropagation e)
                                      (swap! show-params? not))}
           [:> Icon {:sx #js {:fontSize "1.1rem"}} "tune"]]]]]

       ;; Collapsible body
       [:> Collapse {:in @expanded?}
        [:> CardContent {:sx #js {:pt 0}}
         ;; Params collapse
         [:> Collapse {:in @show-params?}
          [:> Box {:component "pre"
                   :sx        #js {:fontSize "0.7rem" :bgcolor "#f5f5f5" :p 1
                                   :borderRadius 1 :mb 2 :overflow "auto"}}
           (.stringify js/JSON (clj->js (:params result)) nil 2)]]

         ;; Choices
         (for [[idx choice] (map-indexed vector choices)]
           (let [parsed      (parse-content choice)
                 summary-txt (get-in parsed [:summary lang] (get-in parsed [:summary :fi] ""))
                 desc-txt    (get-in parsed [:description lang] (get-in parsed [:description :fi] ""))]
             ^{:key idx}
             [:> Stack {:spacing 1.5 :sx #js {:mb (when (< idx (dec (count choices))) 3)}}
              (when (> (count choices) 1)
                [:> Typography {:variant "caption" :color "text.secondary" :fontWeight "bold"}
                 (str "Variation " (inc idx))])

              ;; Summary
              [:> Stack {:spacing 0.5}
               [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
                [:> Typography {:variant "subtitle2" :color "text.secondary"} "Summary"]
                [char-count-label (count summary-txt) 150]]
               [:> Box {:sx #js {:bgcolor "#f8f8f8" :p 1.5 :borderRadius 1}}
                [:> Typography {:variant "body2"} summary-txt]]]

              ;; Description
              [:> Stack {:spacing 0.5}
               [:> Stack {:direction "row" :align-items "center" :justify-content "space-between"}
                [:> Typography {:variant "subtitle2" :color "text.secondary"} "Description"]
                [char-count-label (count desc-txt) 2000]]
               [:> Box {:sx #js {:bgcolor "#f8f8f8" :p 1.5 :borderRadius 1}}
                [:> Typography {:variant "body2" :sx #js {:whiteSpace "pre-wrap"}} desc-txt]]]]))]]])))

;; --- Results panel (right side) ---

(r/defc results-panel []
  (let [results @(rf/subscribe [::subs/results])
        lang    @(rf/subscribe [::subs/selected-lang])
        total   (count results)]
    [:> Box {:sx #js {:p 2 :height "100%" :overflow "auto"}}
     [:> Stack {:direction "row" :align-items "center" :justify-content "space-between" :sx #js {:mb 2}}
      [:> Typography {:variant "h6"} "Results"]
      [:> Stack {:direction "row" :spacing 1 :align-items "center"}
       (when (seq results)
         [:<>
          [:> Tooltip {:title "Download as Excel" :arrow true}
           [:> IconButton
            {:size     "small"
             :on-click #(rf/dispatch [::events/export-results-excel])}
            [:> Icon "table_view"]]]
          [:> Tooltip {:title "Download as JSON" :arrow true}
           [:> IconButton
            {:size     "small"
             :on-click #(rf/dispatch [::events/export-results])}
            [:> Icon "download"]]]])
       [ptv-controls/lang-selector
        {:value     (name lang)
         :on-change #(rf/dispatch [::events/set-lang %])}]]]
     (if (seq results)
       ;; Results are newest-first; number them so #1 is the latest
       (for [[idx result] (map-indexed vector results)]
         ^{:key (:id result)}
         [result-card {:result             result
                       :number             (- total idx)
                       :default-expanded?  (zero? idx)}])
       ;; Empty state
       [:> Stack {:align-items "center"
                  :justify-content "center"
                  :spacing 2
                  :sx #js {:height "60%" :color "text.disabled"}}
        [:> Icon {:sx #js {:fontSize "3rem"}} "science"]
        [:> Typography {:variant "body2" :color "text.disabled" :text-align "center"}
         "No results yet \u2014 load data and run an experiment"]])]))

;; --- Main tab (two-panel layout) ---

(r/defc ai-workbench-tab []
  (hooks/use-effect
    (fn []
      (rf/dispatch [::events/init])
      js/undefined)
    [])
  [:> Box {:sx #js {:display "flex" :height "calc(100vh - 180px)"}}
   ;; Left panel — controls
   [:> Box {:sx #js {:width         560
                     :minWidth      560
                     :overflowY     "auto"
                     :overflowX     "hidden"
                     :borderRight   "1px solid"
                     :borderColor   "divider"
                     :p             2}}
    [:> Typography {:variant "h5"} "PTV AI Workbench"]
    [:> Typography {:variant "body2" :color "text.secondary" :sx #js {:mb 2}}
     "Experiment with PTV description generation prompts and parameters."]
    [flow-selector]
    [input-section]
    [prompt-editor-section]
    [llm-settings-panel]
    [run-button]]
   ;; Right panel — results
   [:> Box {:sx #js {:flex 1 :overflow "auto"}}
    [results-panel]]])
