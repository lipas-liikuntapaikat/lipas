(ns lipas.ui.ptv.views
  (:require ["@mui/material/Paper$default" :as Paper]
            [goog.string.format]
            [lipas.data.ptv :as ptv-data]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :refer [<== ==>]]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]
            ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Typography$default" :as Typography]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]))

;; Memo
;; - preset service structure with descriptions
;; - linking to existing service channels
;;   - maybe define what to overwrite?
;;   - ...or pre-fill fields from PTV via linking?
;;   - ...anyway, somehow re-using stuff that's already there
;; - auto-sync on save

(def orgs
  [{:name "Utajärven kunta (test)"
    :id ptv-data/uta-org-id-test}
   {:name "Limingan kunta (test)"
    :id ptv-data/liminka-org-id-test}
   #_{:name "Utajärven kunta (prod)"
      :id ptv-data/uta-org-id-prod}])

(defn lang-selector
  [{:keys [value on-change opts]}]
  (let [opts (set opts)]
    [mui/tabs
     {:value     value
      :on-change on-change}
     [mui/tab {:value "fi" :label "FI"}]
     (when (contains? opts "se")
       [mui/tab {:value "se" :label "SE"}])
     (when (contains? opts "en")
       [mui/tab {:value "en" :label "EN" :disabled (not (contains? opts "en"))}])]))

(defn org-selector
  [{:keys [label]}]
  (let [selected-org (<== [::subs/selected-org])]
    [lui/select
     {:items     orgs
      :label     label
      :label-fn  :name
      :value-fn  identity
      :value     selected-org
      :on-change #(==> [::events/select-org %])}]))

(defui services-selector
  [{:keys [value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [items (use-subscribe [::subs/services])
        options (uix/use-memo (fn []
                                (map (fn [x]
                                       {:value (value-fn x)
                                        :label (:label x)})
                                     items))
                              [items value-fn])]
    ($ autocomplete2
       {:options   options
        :multiple  true
        :label     label
        :value     (to-array value)
        :on-change (fn [_e v]
                     (on-change (:value v)))})))

(defui service-channel-selector
  [{:keys [value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [items (use-subscribe [::subs/service-channels-list])
        options (uix/use-memo (fn []
                                (map (fn [x]
                                       {:value (value-fn x)
                                        :label (:name x)})
                                     items))
                              [items value-fn])]
    ($ autocomplete2
      {:options   options
       :multiple  false
       :label     label
       :value     (first value)
       :on-change (fn [_e v]
                    (println v)
                    (on-change [(:value v)]))})))

(defn info-text
  [s]
  #_[mui/paper {:style {:padding "1em" :background-color mui/gray3}}]
  [mui/typography {:variant "body1" #_#_:style {:font-size "0.9rem"}} s])

(defn settings
  []
  (let [tr               (<== [:lipas.ui.subs/translator])
        default-settings (<== [::subs/default-settings])]
    [mui/grid {:container true :spacing 4 :style {:margin-left "-32px"}}

     [mui/grid {:item true :xs 12}
      [mui/stack {:spacing 2}

       [mui/typography {:variant "h5"}
        (tr :ptv.integration.interval/headline)]

       [mui/form-control
        [mui/form-label (tr :ptv.integration.interval/label)]
        [mui/radio-group
         {:on-change #(==> [::events/select-integration-interval %2])
          :value     (:integration-interval default-settings)}
         [mui/form-control-label
          {:value   "immediate"
           :label   (tr :ptv.integration.interval/immediate)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "daily"
           :label   (tr :ptv.integration.interval/daily)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   (tr :ptv.integration.interval/manual)
           :control (r/as-element [mui/radio])}]]]]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "h5"} (tr :ptv.integration.default-settings/headline)]]

     [mui/grid {:item true :xs 12}
      [info-text (tr :ptv.integration.default-settings/helper)]]

     ;; Service
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        (tr :ptv/services)]

       ;; Integration type
       [mui/form-control
        [mui/form-label (tr :ptv.actions/select-integration)]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-integration-default %2])
          :value     (:service-integration default-settings)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   (tr :ptv.integration.service/lipas-managed)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   (tr :ptv.integration/manual)
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-integration default-settings))
         [info-text (tr :ptv.integration.service/lipas-managed-helper)])

       (when (= "manual" (:service-integration default-settings))
         [info-text (tr :ptv.integration.service/manual-helper)])]]

     ;; Service channel
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        (tr :ptv/service-channels)]

       ;; Integration type
       [mui/form-control
        [mui/form-label (tr :ptv.actions/select-integration)]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-channel-integration-default %2])
          :value     (:service-channel-integration default-settings)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   (tr :ptv.integration.service-channel/lipas-managed)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   (tr :ptv.integration/manual)
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-channel-integration default-settings))
         [info-text (tr :ptv.integration.service-channel/lipas-managed-helper)])

       (when (= "manual" (:service-channel-integration default-settings))
         [info-text (tr :ptv.integration.service-channel/manual-helper)])]]

     ;; Descriptions
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        (tr :ptv/descriptions)]

       ;; Integration type
       [mui/form-control
        [mui/form-label (tr :ptv.actions/select-integration)]
        [mui/radio-group
         {:on-change #(==> [::events/select-descriptions-integration-default %2])
          :value     (:descriptions-integration default-settings)}
         [mui/form-control-label
          {:value   "lipas-managed-ptv-fields"
           :label   (tr :ptv.integration.description/lipas-managed-ptv-fields)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "lipas-managed-comment-field"
           :label   (tr :ptv.integration.description/lipas-managed-comment-field)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "ptv-managed"
           :label   (tr :ptv.integration.description/ptv-managed)
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed-ptv-fields" (:descriptions-integration default-settings))
         [info-text (tr :ptv.integration.description/lipas-managed-ptv-fields-helper)])

       (when (= "lipas-managed-comment-field" (:descriptions-integration default-settings))
         [info-text (tr :ptv.integration.description/lipas-managed-comment-field-helper)])

       (when (= "ptv-managed" (:descriptions-integration default-settings))
         [info-text (tr :ptv.integration.description/ptv-managed-helper)])]]]))

(defn form
  [{:keys [tr site]}]
  (let [locale (tr)]
    [mui/grid
     {:container true
      :spacing   2
      :style     {:padding-top "1em" :padding-bottom "1em"}}

     ;; Service
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        (tr :ptv/services)]

       ;; Integration type
       [mui/form-control
        [mui/form-label (tr :ptv.actions/select-integration)]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-integration site %2])
          :value     (:service-integration site)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   (tr :ptv.integration.service/lipas-managed)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   (tr :ptv.integration/manual)
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-integration site))
         (tr :ptv.integration.service/lipas-managed-helper))

       (when (= "manual" (:service-integration site))
         ($ services-selector
            {:value     (:service-ids site)
             :on-change #(==> [::events/select-services site %])
             :value-fn  :service-id
             :label     (tr :ptv.actions/select-service)}))]]

     ;; Service channels
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        (tr :ptv/service-channels)]

       ;; Integration type
       [mui/form-control
        [mui/form-label (tr :ptv.actions/select-integration)]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-channel-integration site %2])
          :value     (:service-channel-integration site)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   (tr :ptv.integration.service-channel/lipas-managed)
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   (tr :ptv.integration/manual)
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-channel-integration site))
         (tr :ptv.integration.service-channel/lipas-managed-helper))

       (when (= "manual" (:service-channel-integration site))
         ($ service-channel-selector
            {:value     (:service-channel-ids site)
             :value-fn  :id
             :on-change #(==> [::events/select-service-channels site %])
             :label     (tr :ptv.actions/select-service-channel)}))]]

     ;; Descriptions
     (r/with-let [selected-tab (r/atom :fi)]
       (let [loading? (<== [::subs/generating-descriptions?])]
         [mui/grid {:item true :xs 12 :lg 4}
          [mui/stack {:spacing 2}

           [mui/typography {:variant "h6"}
            (tr :ptv/descriptions)]

           ;; Integration type
           [mui/form-control
            [mui/form-label (tr :ptv.actions/select-integration)]
            [mui/radio-group
             {:on-change #(==> [::events/select-descriptions-integration site %2])
              :value     (:descriptions-integration site)}

             [mui/form-control-label
              {:value   "lipas-managed-ptv-fields"
               :label   (tr :ptv.integration.description/lipas-managed-ptv-fields)
               :control (r/as-element [mui/radio])}]

             [mui/form-control-label
              {:value   "lipas-managed-comment-field"
               :label   (tr :ptv.integration.description/lipas-managed-comment-field)
               :control (r/as-element [mui/radio])}]

             [mui/form-control-label
              {:value   "ptv-managed"
               :label   (tr :ptv.integration.description/ptv-managed)
               :control (r/as-element [mui/radio])}]]]

           (when (= "lipas-managed-ptv-fields" (:descriptions-integration site))
             (tr :ptv.integration.description/lipas-managed-ptv-fields-helper))

           (when (= "lipas-managed-comment-field" (:descriptions-integration site))
             (tr :ptv.integration.description/lipas-managed-comment-field-helper))

           (when (= "ptv-managed" (:descriptions-integration site))
             (tr :ptv.integration.description/ptv-managed-helper))

           (when (= "lipas-managed-ptv-fields" (:descriptions-integration site))
             [mui/button {:disabled loading?
                          :on-click #(==> [::events/generate-descriptions (:lipas-id site)])}
              (tr :ptv.actions/generate-with-ai)])

           (when loading?
             [mui/circular-progress])

           [mui/tabs
            {:value     @selected-tab
             :on-change #(reset! selected-tab (keyword %2))}
            [mui/tab {:value "fi" :label "FI"}]
            [mui/tab {:value "se" :label "SE"}]
            [mui/tab {:value "en" :label "EN"}]]

           ;; Summary
           [lui/text-field
            {:disabled   loading?
             :multiline  true
             :read-only? (not= "manual" (:descriptions-integration site))
             :variant    "outlined"
             :on-change  #(==> [::events/set-summary site @selected-tab %])
             :label      "Tiivistelmä"
             :value      (get-in site [:summary @selected-tab])}]

           ;; Description
           [lui/text-field
            {:disabled   loading?
             :variant    "outlined"
             :read-only? (not= "manual" (:descriptions-integration site))
             :rows       5
             :multiline  true
             :on-change  #(==> [::events/set-description site @selected-tab %])
             :label      "Kuvaus"
             :value      (get-in site [:description @selected-tab])}]]]))]))

(defn table []
  (r/with-let [expanded-rows (r/atom {})]
    (let [tr                (<== [:lipas.ui.subs/translator])
          sites             (<== [::subs/sports-sites])
          sync-all-enabled? (<== [::subs/sync-all-enabled?])

          headers [{:key :expand :label "" :padding "checkbox"}
                   {:key     :selected :label (tr :ptv.actions/export)
                    :padding "checkbox"
                    :action-component
                    [mui/switch
                     {:value     sync-all-enabled?
                      :on-change #(==> [::events/toggle-sync-all %2])}]}
                   #_{:key :auto-sync :label "Vie automaattisesti"}
                   {:key :last-sync :label "Viety viimeksi"}
                   {:key :name :label (tr :general/name)}
                   {:key :type :label (tr :general/type)}
                   ;;{:key :admin :label (tr :lipas.sports-site/admin)}
                   {:key :owner :label (tr :lipas.sports-site/owner)}
                   #_{:key :service :label "Palvelu"}
                   #_{:key :service-channel :label (tr :ptv/service-channel)}
                   #_{:key :service-channel-summary :label "Tiivistelmä"}
                   #_{:key :service-channel-description :label "Kuvaus"}]]

      (when (seq sites)
        [mui/table-container {:component Paper}
         [mui/table

          ;; Headers
          [mui/table-head
           [mui/table-row
            (doall
              (for [{:keys [key label action-component padding]} headers]
                [mui/table-cell {:key (name key) :padding padding}
                 action-component
                 label]))]]

          ;; Body
          [mui/table-body
           (doall
             (for [{:keys [lipas-id] :as site} (sort-by :type sites)]

               [:<> {:key lipas-id}

               ;; Summary row
                [mui/table-row #_{:on-click (fn [] (swap! expanded-rows update lipas-id not))}

                ;; Expand toggle
                 [mui/table-cell
                  [mui/icon-button
                   {:style    {:zIndex 1}
                    :size     "small"
                    :on-click (fn [] (swap! expanded-rows update lipas-id not))}
                   [mui/icon
                    (if (get @expanded-rows lipas-id false)
                      "keyboard_arrow_up_icon"
                      "keyboard_arrow_down_icon")]]]

                ;; Enable sync
                 [mui/table-cell
                  [lui/switch
                   {:value     (:sync-enabled site)
                    :on-change #(==> [::events/toggle-sync-enabled site %])}]]

                ;; Last-sync
                 [mui/table-cell
                  (:last-sync-human site)]

                ;; Name
                 [mui/table-cell
                  (:name site)]

                ;; Type
                 [mui/table-cell
                  (:type site)]

                ;; Admin
                ;;[mui/table-cell]

                ;; Owner
                 [mui/table-cell
                  (:owner site)]

                ;; Service
                 #_[mui/table-cell
                    [services-selector]]

                ;; Service channell
                 #_[mui/table-cell
                    #_[service-channel-selector]]

                ;; Description
                 #_[mui/table-cell]]

               ;; Details row
                [mui/table-row
                 [mui/table-cell
                  {:style   {:paddingTop 0 :paddingBottom 0}
                   :colSpan (count headers)}
                  [mui/collapse {:in            (get @expanded-rows lipas-id false)
                                 :timeout       "auto"
                                 :unmountOnExit true}
                   [form {:tr tr :site site}]]]]]))]]]))))

(defn descriptions-generator
  []
  (let [tr                  (<== [:lipas.ui.subs/translator])
        sports-sites        (<== [::subs/sports-sites-filtered])
        sports-sites-count  (<== [::subs/sports-sites-filtered-count])
        sports-sites-filter (<== [::subs/sports-sites-filter])

        {:keys [in-progress?
                processed-lipas-ids
                processed-count
                total-count
                processed-percent
                halt?] :as m} (<== [::subs/batch-descriptions-generation-progress])]

    [lui/expansion-panel {:default-expanded false :label (tr :ptv.tools.ai/headline)}
     [mui/grid {:container true :spacing 4}
      [mui/grid {:item true :xs 12 :lg 4}

       ;; Settings
       [mui/stack {:spacing 4}
        [mui/typography (tr :ptv.tools.ai/start-helper)]

        [mui/form-control
         [mui/form-label (tr :ptv.tools.ai.sports-sites-filter/label)]
         [mui/radio-group
          {:on-change #(==> [::events/select-sports-sites-filter %2])
           :value     sports-sites-filter}
          [mui/form-control-label
           {:value   "all"
            :label   (tr :ptv.tools.ai.sports-sites-filter/all)
            :control (r/as-element [mui/radio])}]

          [mui/form-control-label
           {:value   "no-existing-description"
            :label   (tr :ptv.tools.ai.sports-sites-filter/no-existing-description)
            :control (r/as-element [mui/radio])}]

          [mui/form-control-label
           {:value   "sync-enabled"
            :label   (tr :ptv.tools.ai.sports-sites-filter/sync-enabled)
            :control (r/as-element [mui/radio])}]

          [mui/form-control-label
           {:value   "sync-enabled-no-existing-description"
            :label   (tr :ptv.tools.ai.sports-sites-filter/sync-enabled-no-existing-description)
            :control (r/as-element [mui/radio])}]

          #_[mui/form-control-label
             {:value   "manual"
              :label   (tr :ptv.tools.ai.sports-sites-filter/manual)
              :control (r/as-element [mui/radio])}]]]

        ;; Start button
        [mui/button
         {:variant   "outlined"
          :disabled  in-progress?
          :color     "secondary"
          :startIcon (r/as-element [mui/icon "play_arrow"])
          :on-click  #(==> [::events/generate-all-descriptions sports-sites])}
         (tr :ptv.tools.ai/start)]

        ;; Cancel button
        (when in-progress?
          [mui/button
           {:variant   "outlined"
            :disabled  halt?
            :color     "secondary"
            :startIcon (r/as-element [mui/icon "cancel"])
            :on-click  #(==> [::events/halt-descriptions-generation])}
           (tr :actions/cancel)])

        (when (and halt? in-progress?)
          [mui/typography (tr :ptv.tools.ai/canceling)])

        (when in-progress?
          [mui/stack {:direction "row" :spacing 2 :align-items "center"}
           [mui/circular-progress {:variant "indeterminate" :value processed-percent}]
           [mui/typography (str processed-count "/" total-count)]])]]

      ;; Results
      (r/with-let [selected-tab (r/atom :fi)]
        [mui/grid {:item true :xs 12 :lg 8}
         [mui/stack {:spacing 4}

          [mui/typography {:variant "h6"}
           (tr :ptv/sports-sites)]

          [mui/typography {:variant "subtitle1" :style {:margin-top "0px"}}
           (str sports-sites-count " kpl")]

          (doall
            (for [{:keys [lipas-id] :as site} sports-sites]
              ^{:key lipas-id}
              [lui/expansion-panel
               {:label    (:name site)
                :disabled (not (contains? processed-lipas-ids lipas-id))}
               [mui/stack {:spacing 2}
                [mui/tabs
                 {:value     @selected-tab
                  :on-change #(reset! selected-tab (keyword %2))}
                 [mui/tab {:value "fi" :label "FI"}]
                 [mui/tab {:value "se" :label "SE"}]
                 [mui/tab {:value "en" :label "EN"}]]

               ;; Summary
                [lui/text-field
                 {:multiline  true
                  :read-only? (not= "manual" (:descriptions-integration site))
                  :variant    "outlined"
                  :on-change  #(==> [::events/set-summary site @selected-tab %])
                  :label      (tr :ptv/summary)
                  :value      (get-in site [:summary @selected-tab])}]

               ;; Description
                [lui/text-field
                 {:variant    "outlined"
                  :read-only? (not= "manual" (:descriptions-integration site))
                  :rows       5
                  :multiline  true
                  :on-change  #(==> [::events/set-description site @selected-tab %])
                  :label      (tr :ptv/description)
                  :value      (get-in site [:description @selected-tab])}]]]))]])]]))

(defn create-services
  []
  (r/with-let [selected-tab (r/atom :fi)]
    (let [tr                        (<== [:lipas.ui.subs/translator])
          service-candidates        (<== [::subs/service-candidates])
          {:keys [in-progress?
                  halt?
                  processed-percent
                  total-count
                  processed-count]} (<== [::subs/service-descriptions-generation-progress])]

      [lui/expansion-panel
       {:label      (str "1. " (tr :ptv.tools.generate-services/headline))
        :label-icon (if (empty? service-candidates)
                      [mui/icon {:color "success"} "done"]
                      [mui/icon {:color "disabled"} "done"])}

       [mui/grid {:container true :spacing 4}

        [mui/grid {:item true :xs 12 :lg 4}
         [mui/stack {:spacing 4}
          [mui/typography {:variant "h6"} (tr :ptv.wizard/generate-descriptions)]
          [mui/typography (tr :ptv.wizard/generate-descriptions-helper2)]
          [mui/typography (tr :ptv.tools.ai/start-helper)]

          ;; Start descriptions generation button
          [mui/button
           {:variant   "outlined"
            :disabled  in-progress?
            :color     "secondary"
            :startIcon (r/as-element [mui/icon "auto_fix_high"])
            :on-click  #(==> [::events/generate-all-service-descriptions service-candidates])}
           (tr :ptv.wizard/generate-descriptions)]

          ;; Cancel descriptions generation button
          (when in-progress?
            [mui/button
             {:variant   "outlined"
              :disabled  halt?
              :color     "secondary"
              :startIcon (r/as-element [mui/icon "cancel"])
              :on-click  #(==> [::events/halt-service-descriptions-generation])}
             (tr :actions/cancel)])

          (when (and halt? in-progress?)
            [mui/typography (tr :ptv.tools.ai/canceling)])

          (when in-progress?
            [mui/stack {:direction "row" :spacing 2 :align-items "center"}
             [mui/circular-progress {:variant "indeterminate" :value processed-percent}]
             [mui/typography (str processed-count "/" total-count)]])

          [mui/typography (tr :ptv.wizard/generate-descriptions-helper1)]

          ;; Sync to PTV button
          [mui/button
           {:variant   "outlined"
            :disabled  (some false? (map :valid service-candidates))
            :color     "primary"
            :startIcon (r/as-element [mui/icon "ios_share"])
            :on-click  #(==> [::events/create-all-ptv-services service-candidates])}
           (tr :ptv.wizard/export-services-to-ptv)]]]

        ;; Results panel
        [mui/grid {:item true :xs 12 :lg 8}
         [mui/stack {:spacing 4}

          [mui/typography {:variant "h6"}
           (tr :ptv.wizard/services-to-add)]

          (when (empty? service-candidates)
            [mui/typography (tr :ptv.wizard/all-services-exist)])

          [:div
           (doall
             (for [{:keys [source-id valid sub-category sub-category-id languages] :as m} service-candidates]

               ^{:key sub-category-id}
               [lui/expansion-panel
                {:label      sub-category
                 :label-icon (if valid
                               [mui/icon {:color "success"} "done"]
                               [mui/icon {:color "disabled"} "done"])}
                [mui/stack {:spacing 2}

                 #_[mui/form-control
                    [mui/form-label (tr :ptv.integration.interval/label)]
                    [mui/radio-group
                     {:on-change #(==> [::events/select-integration-interval %2])
                      :value     "lipas-managed"}
                     [mui/form-control-label
                      {:value   "lipas-managed"
                       :label   (tr :ptv.integration.service/lipas-managed)
                       :control (r/as-element [mui/radio])}]

                     [mui/form-control-label
                      {:value   "manual"
                       :label   (tr :ptv.integration/manual)
                       :control (r/as-element [mui/radio])}]]]

                 [lui/autocomplete
                  {:label     (tr :ptv.actions/select-languages)
                   :multi?    true
                   :items     [{:label "FI" :value "fi"}
                               {:label "SE" :value "se"}
                               {:label "EN" :value "en"}]
                   :value     languages
                   :value-fn  :value
                   :label-fn  :label
                   :on-change #(==> [::events/set-service-candidate-languages source-id %])}]

                 ($ services-selector
                    {:value     (get m :service-ids)
                     :on-change #(==> [::events/link-candidate-to-existing-service source-id %])
                     :value-fn  :service-id
                     :label     (tr :ptv/service)})

                 (let [languages (set languages)]
                   [mui/tabs
                    {:value     @selected-tab
                     :on-change #(reset! selected-tab (keyword %2))}
                    (when (contains? languages "fi")
                      [mui/tab {:value "fi" :label "FI"}])
                    (when (contains? languages "se")
                      [mui/tab {:value "se" :label "SE"}])
                    (when (contains? languages "en")
                      [mui/tab {:value "en" :label "EN"}])])

                ;; Summary
                 [lui/text-field
                  {:multiline true
                   :variant   "outlined"
                   :on-change #(==> [::events/set-service-candidate-summary source-id @selected-tab %])
                   :label     (tr :ptv/summary)
                   :value     (get-in m [:summary @selected-tab])}]

                ;; Description
                 [lui/text-field
                  {:variant   "outlined"
                   :rows      5
                   :multiline true
                   :on-change #(==> [::events/set-service-candidate-description source-id @selected-tab %])
                   :label     (tr :ptv/description)
                   :value     (get-in m [:description @selected-tab])}]]]))]]]]])))

(defui service-location-details
  [{:keys [tr site lipas-id sync-enabled name-conflict service-ids selected-tab set-selected-tab service-channel-ids]}]
  ($ AccordionDetails
     {}
     (r/as-element
       [mui/stack {:spacing 2}

        [lui/switch
         {:label     (tr :ptv.actions/export-disclaimer)
          :value     sync-enabled
          :on-change #(==> [::events/toggle-sync-enabled site %])}]

        ;; Services selector
        ($ services-selector
           {:value     service-ids
            :value-fn  :service-id
            :on-change #(==> [::events/select-services site %])
            :label     (tr :ptv/services)})

        ;; Service channel selector

        [:span (when name-conflict {:style
                                    {:border  "1px solid rgb(237, 108, 2)"
                                     :padding "1em"}})

         (when name-conflict
           [mui/stack
            [lui/icon-text
             {:icon       "warning"
              :icon-color "warning"
              :text       (tr :ptv.wizard/service-channel-name-conflict (:name site))}]

            [mui/typography
             {:style   {:padding-left "1em" :margin-bottom "0"}
              :variant "body2"}
             (tr :ptv.name-conflict/do-one-of-these)]

            [:ul
             [:li (tr :ptv.name-conflict/opt1)]
             [:li (tr :ptv.name-conflict/opt2)]
             [:li (tr :ptv.name-conflict/opt3)]
             #_[:li (tr :ptv.name-conflict/opt4)]]])

         (when name-conflict
           [mui/button
            {:on-click #(==> [::events/select-service-channels {:lipas-id lipas-id}
                              [(:service-channel-id name-conflict)]])}
            (tr :ptv.wizard/attach-to-conflicting-service-channel)])

         ($ service-channel-selector
            {:value     service-channel-ids
             :value-fn  :service-channel-id
             :on-change #(==> [::events/select-service-channels site %])
             :label     (tr :ptv/service-channel)})]

        [mui/tabs
         {:value     selected-tab
          :on-change #(set-selected-tab (keyword %2))}
         [mui/tab {:value "fi" :label "FI"}]
         [mui/tab {:value "se" :label "SE"}]
         [mui/tab {:value "en" :label "EN"}]]

        ;; Summary
        [lui/text-field
         {:multiline  true
          :read-only? (not= "manual" (:descriptions-integration site))
          :variant    "outlined"
          :on-change  #(==> [::events/set-summary site selected-tab %])
          :label      (tr :ptv/summary)
          :value      (get-in site [:summary selected-tab])}]

        ;; Description
        [lui/text-field
         {:variant    "outlined"
          :read-only? (not= "manual" (:descriptions-integration site))
          :rows       5
          :multiline  true
          :on-change  #(==> [::events/set-description site selected-tab %])
          :label      (tr :ptv/description)
          :value      (get-in site [:description selected-tab])}]])))

(defui service-location
  [{:keys [site sync-enabled name-conflict valid]
    :as props}]
  ($ Accordion
     {:defaultExpanded false
      :disableGutters true
      :square true
      ;; Much faster this way, only render the accordion content for open sites
      :slotProps #js {:transition #js {:unmountOnExit true}}
      :sx #js {:mb 2
               :backgroundColor (when (false? sync-enabled)
                                  mui/gray3)}}
     ($ AccordionSummary
        {:expandIcon ($ Icon "expand_more")}
        ($ Typography
           {:sx #js {:mr 1.5}}
           (cond
             name-conflict ($ Icon {:color "warning"} "warning")
             valid         ($ Icon {:color "success"} "done")
             :else         ($ Icon {:color "disabled"} "done")))
        ($ Typography
           {:sx #js {:color "inherit"
                     :variant "button"}}
           (:name site)))

     ($ service-location-details props)))

(defn integrate-service-locations
  []
  (let [tr                  (<== [:lipas.ui.subs/translator])
        sports-sites        (<== [::subs/sports-sites])
        setup-done?         (<== [::subs/sports-site-setup-done])
        sports-sites-count  (<== [::subs/sports-sites-count])
        sports-sites-filter (<== [::subs/sports-sites-filter])

        [selected-tab set-selected-tab] (uix/use-state :fi)

        {:keys [in-progress?
                processed-lipas-ids
                processed-count
                total-count
                processed-percent
                halt?] :as m}
        (<== [::subs/batch-descriptions-generation-progress])]

    [lui/expansion-panel
     {:label      (str "2. " (tr :ptv.wizard/integrate-service-locations))
      :label-icon (if setup-done?
                    [mui/icon {:color "success"} "done"]
                    [mui/icon {:color "disabled"}  "done"])}

     [mui/grid {:container true :spacing 4}
      [mui/grid {:item true :xs 12 :lg 4}

       ;; Settings
       [mui/stack {:spacing 4}

        #_[mui/button
           {:on-click #(==> [::events/assign-services-to-sports-sites])
            :variant  "outlined"
            :color    "secondary"}
           (tr :ptv.wizard/assign-services-to-sports-sites)]

        [mui/typography {:variant "h6"} (tr :ptv.wizard/generate-descriptions)]
        [mui/typography (tr :ptv.wizard/generate-descriptions-helper2)]
        [mui/typography (tr :ptv.tools.ai/start-helper)]

        #_[mui/form-control
           [mui/form-label (tr :ptv.tools.ai.sports-sites-filter/label)]

           #_[mui/radio-group
              {:on-change #(==> [::events/select-sports-sites-filter %2])
               :value     sports-sites-filter}
              [mui/form-control-label
               {:value   "all"
                :label   (tr :ptv.tools.ai.sports-sites-filter/all)
                :control (r/as-element [mui/radio])}]

              [mui/form-control-label
               {:value   "no-existing-description"
                :label   (tr :ptv.tools.ai.sports-sites-filter/no-existing-description)
                :control (r/as-element [mui/radio])}]

              [mui/form-control-label
               {:value   "sync-enabled"
                :label   (tr :ptv.tools.ai.sports-sites-filter/sync-enabled)
                :control (r/as-element [mui/radio])}]

              [mui/form-control-label
               {:value   "sync-enabled-no-existing-description"
                :label   (tr :ptv.tools.ai.sports-sites-filter/sync-enabled-no-existing-description)
                :control (r/as-element [mui/radio])}]

              #_[mui/form-control-label
                 {:value   "manual"
                  :label   (tr :ptv.tools.ai.sports-sites-filter/manual)
                  :control (r/as-element [mui/radio])}]]]

        ;; Start button
        [mui/button
         {:variant   "outlined"
          :disabled  in-progress?
          :color     "secondary"
          :startIcon (r/as-element [mui/icon "play_arrow"])
          :on-click  #(==> [::events/generate-all-descriptions sports-sites])}
         (tr :ptv.wizard/generate-descriptions)]

        ;; Cancel button
        (when in-progress?
          [mui/button
           {:variant   "outlined"
            :disabled  halt?
            :color     "secondary"
            :startIcon (r/as-element [mui/icon "cancel"])
            :on-click  #(==> [::events/halt-descriptions-generation])}
           (tr :actions/cancel)])

        (when (and halt? in-progress?)
          [mui/typography (tr :ptv.tools.ai/canceling)])

        (when in-progress?
          [mui/stack {:direction "row" :spacing 2 :align-items "center"}
           [mui/circular-progress {:variant "indeterminate" :value processed-percent}]
           [mui/typography (str processed-count "/" total-count)]])

        [mui/typography (tr :ptv.wizard/generate-descriptions-helper3)]

        [mui/typography (tr :ptv.wizard/unselect-helper)]

        ;; Export to PTV button
        [mui/button
         {:variant   "outlined"
          :disabled  (not (every? true? (map :valid sports-sites)))
          :color     "primary"
          :startIcon (r/as-element [mui/icon "ios_share"])
          :on-click  #(==> [::events/create-all-ptv-service-locations sports-sites])}
         (tr :ptv.wizard/export-service-locations-to-ptv)]

        (let [{:keys [in-progress?
                      processed-lipas-ids
                      processed-count
                      total-count
                      processed-percent
                      halt?] :as m}
              (<== [::subs/service-location-creation-progress])]

          (when in-progress?
            [mui/stack {:direction "row" :spacing 2 :align-items "center"}
             [mui/circular-progress {:variant "indeterminate" :value processed-percent}]
             [mui/typography (str processed-count "/" total-count)]])

          (when halt?
            "Something went wrong, ask engineer."))]]

      ;; Results

      [mui/grid {:item true :xs 12 :lg 8}
       [mui/stack {:spacing 4}

        [mui/typography {:variant "h6"}
         (tr :ptv/sports-sites)]

        [mui/typography {:variant "subtitle1" :style {:margin-top "0px"}}
         (str sports-sites-count " kpl")]]

       [mui/stack
        (for [{:keys [lipas-id valid name-conflict sync-enabled service-ids service-channel-ids service-name] :as site} sports-sites]
          ($ service-location
             {:key lipas-id
              :tr tr
              :site site
              :lipas-id lipas-id
              :name-conflict name-conflict
              :sync-enabled sync-enabled
              :valid valid
              :service-ids service-ids
              :selected-tab selected-tab
              :set-selected-tab set-selected-tab
              :service-channel-ids service-channel-ids}))]]]]))

(defn tools
  []
  [mui/paper
   [descriptions-generator]])

(defn service-panel
  [{:keys [service]}]
  (r/with-let [lang (r/atom "fi")]
    (let [tr (<== [:lipas.ui.subs/translator])]
      [lui/expansion-panel {:label (:label service)}

       [mui/stack {:spacing 2}
        [lang-selector
         {:value     @lang
          :opts      (:languages service)
          :on-change #(reset! lang %2)}]

        [mui/stack {:direction "row" :spacing 2}
         [mui/stack {:spacing 2 :flex 1}

          ;; Last modified
          [mui/typography (str (tr :general/last-modified) " " (:last-modified-human service))]

          ;; Service classes
          [mui/typography (tr :ptv.service/classes)]
          [mui/stack {:direction "row" :spacing 1}
           (doall
             (for [class (:service-classes service)]
               (let [label (get class @lang)]
                 ^{:key label}
                 [mui/chip {:label label :variant "outlined"}])))]

          ;; keywords
          [mui/typography (tr :ptv/keywords)]
          [mui/stack {:direction "row" :spacing 1}
           (doall
             (for [onto (:ontology-terms service)]
               (let [label (get onto @lang)]
                 ^{:key label}
                 [mui/chip {:label label :variant "outlined"}])))]

          ;; Summary
          [mui/typography (tr :ptv/summary)]
          [mui/typography {:variant "body2"} (get-in service [:summary @lang])]
          #_[lui/text-field
             {:disabled  true
              :on-change #()
              :multiline true
              :label     (tr :ptv/summary)
              :value     (get-in service [:summary @lang])}]

          ;; Descriptions
          [mui/typography (tr :ptv/description)]
          [mui/typography {:variant "body2"} (get-in service [:description @lang])]
          #_[lui/text-field
             {:disabled  true
              :on-change #()
              :multiline true
              :label     (tr :ptv/description)
              :value     (get-in service [:description @lang])}]]

         [mui/stack {:spacing 2 :flex 1}

          ;; Service channels
          [mui/typography (tr :ptv/service-channels)]
          [:ul
           (doall
             (for [sc (sort-by :name (:service-channels service))]
               (let [label (:name sc)]
                 ^{:key label}
                 [:li label])))]]]]])))

(defn services
  []
  (let [tr              (<== [:lipas.ui.subs/translator])
        services-filter (<== [::subs/services-filter])
        services        (<== [::subs/services-filtered])]
    [mui/paper

     ;; Filter checkbox
     [lui/checkbox
      {:label     (tr :ptv.service/show-only-lipas-managed)
       :value     (= "lipas-managed" services-filter)
       :on-change #(==> [::events/toggle-services-filter])}]

     ;; Services list
     (doall
       (for [service services]
         ^{:key (:service-id service)}
         [service-panel {:service service}]))]))

(defn wizard
  []
  [mui/paper
   [create-services]
   [:f> integrate-service-locations]])

(defn dialog
  [{:keys [tr]}]
  (let [open?        (<== [::subs/dialog-open?])
        selected-tab (<== [::subs/selected-tab])
        loading?     (<== [::subs/loading-from-ptv?])
        org-data     (<== [::subs/selected-org-data])
        sites        (<== [::subs/sports-sites])]

    [lui/dialog
     {:open?         open?
      :on-save       #(==> [::events/save sites])
      :save-enabled? true
      :save-label    (tr :actions/save)
      :title         (tr :ptv/tooltip)
      :max-width     "xl"
      :cancel-label  (tr :actions/cancel)
      :on-close      #(==> [::events/close-dialog])}

     [mui/stack {:spacing 2}

      [org-selector {:label (tr :ptv.actions/select-org)}]

      (when loading?
        [mui/stack {:direction "row" :spacing 2 :alignItems "center"}
         [mui/circular-progress]
         [mui/typography (tr :ptv/loading-from-ptv)]])

      (when (and org-data (not loading?))
        [:<>
         [mui/tabs
          {:value          selected-tab
           :on-change      #(==> [::events/select-tab %2])
           :textColor      "primary"
           :indicatorColor "secondary"}

          [mui/tab {:value "wizard" :label (tr :ptv/wizard)}]
          [mui/tab {:value "services" :label (tr :ptv/services)}]
          [mui/tab {:value "sports-sites" :label (tr :ptv/sports-sites)}]
          [mui/tab {:value "ai" :label (tr :ptv/tools)}]
          [mui/tab {:value "settings" :label (tr :ptv/settings)}]]

         (when (= selected-tab "wizard")
           [wizard])

         (when (= selected-tab "services")
           [services])

         (when (= selected-tab "sports-sites")
           [table])

         (when (= selected-tab "settings")
           [settings])

         (when (= selected-tab "ai")
           [tools])])]]))

;; Juhan kommentit wizardiin
;; - mahdollisuus valita kielet
;; - mahdollisuus linkittää olemassa oleva palvelu
;; - pakotusmekanismi "olen lukenut kuvaukset"
;; - ohjaavammat ohjetekstit
