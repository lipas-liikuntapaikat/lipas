(ns lipas.ui.ptv.site-view
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.ui.components :as lui]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.utils :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defui new-service-form [{:keys [org-id tr service data]}]
  (let [{:keys [source-id sub-category-id]} service
        loading? false
        read-only? false

        [selected-tab set-selected-tab] (uix/use-state :fi)

        org-languages (use-subscribe [::subs/org-languages org-id])
        service-candidate-descriptions (use-subscribe [::subs/service-candidate-descriptions org-id])
        {:keys [summary description]} (get service-candidate-descriptions source-id)

        city (use-subscribe [:lipas.ui.sports-sites.subs/city (-> data :location :city :city-code)])
        type-data (use-subscribe [:lipas.ui.sports-sites.subs/type-by-type-code (-> data :type :type-code)])
        overview {:city-name (:name city)
                  :service-name (:name (get types/sub-categories sub-category-id))
                  :sports-facilties [{:type (-> type-data :name :fi)}]}]
    ($ Paper
       {:sx #js {:p 2}}

       ($ Stack
          {:direction "column"
           :sx #js {:gap 2}}
          ($ Typography
             {:variant "h5"}
             "Luo "
             (:fi (:name (get types/sub-categories sub-category-id))))

          ($ Stack
             {:sx #js {:position "relative"}}
             ($ Button
                {:disabled (or loading?
                               read-only?)
                 :variant "outlined"
                 :on-click (fn [_e]
                             (rf/dispatch [::events/generate-service-descriptions org-id source-id overview [] []]))}
                (tr :ptv.actions/generate-with-ai))
             (when loading?
               ($ CircularProgress
                  {:size 24
                   :sx #js {:position "absolute"
                            :top "50%"
                            :left "50%"
                            :mt "-12px"}})))

          ($ controls/lang-selector
             {:value selected-tab
              :on-change set-selected-tab})

          ;; Summary
          (r/as-element
            [lui/text-field
             {:disabled   (or loading?
                              read-only?)
              :multiline  true
              :variant    "outlined"
              :on-change  (fn [v]
                            )
              :label      "Tiivistelmä"
              :value      (get summary selected-tab)}])

          ;; Description
          (r/as-element
            [lui/text-field
             {:disabled   (or loading?
                              read-only?)
              :variant    "outlined"
              :rows       5
              :multiline  true
              :on-change  (fn [v]
                            )
              :label      "Kuvaus"
              :value      (get description selected-tab)}])

          ($ Button
             {:variant "contained"
              :disabled (or loading?
                            read-only?)
              :on-click (fn [_e]
                          (let [data {:source-id source-id
                                      :sub-category-id sub-category-id
                                      :summary summary
                                      :description description
                                      :languages (vec org-languages)}]
                            (rf/dispatch [::events/create-ptv-service org-id source-id data [] []])))}
             "Luo Palvelu"))
       )))

(defui site-view [{:keys [tr lipas-id can-edit? edit-data]}]
  (let [[selected-tab set-selected-tab] (uix/use-state :fi)

        editing*   (boolean (use-subscribe [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        editing?   (and can-edit? editing*)
        read-only? (not editing?)
        sports-site (use-subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])

        ;; FIXME: Edit-data and sports-site schema can be a bit different?
        x (if editing?
            edit-data
            sports-site)

        _ (js/console.log edit-data sports-site)

        {:keys [descriptions-integration org-id sync-enabled last-sync publishing-status]} (:ptv x)

        _ (js/console.log org-id)

        ;; default-settings {}
        ;; enabled    (boolean (:ptv site))
        descriptions-enabled (not= "manual" descriptions-integration)

        loading?  (use-subscribe [::subs/generating-descriptions?])

        type-code (-> x :type :type-code)

        type-code-changed? (not= type-code (:previous-type-code (:ptv x)))
        previous-sent? (ptv-data/is-sent-to-ptv? x)
        candidate-now? (ptv-data/ptv-candidate? x)

        types (use-subscribe [:lipas.ui.sports-sites.subs/all-types])
        services (use-subscribe [::subs/services-by-id org-id])
        missing-services-input [{:service-ids #{}
                                 :sub-category-id (-> x :type :type-code types :sub-category)
                                 :sub-category    (-> x :search-meta :type :sub-category :name :fi)}]
        missing-services (ptv-data/resolve-missing-services org-id services missing-services-input)

        source-id->service (utils/index-by :sourceId (vals services))
        new-service (ptv-data/sub-category-id->service org-id source-id->service (-> x :type :type-code types :sub-category))

        to-archive? (and previous-sent?
                         (not candidate-now?))]

    (js/console.log missing-services new-service)

    (uix/use-effect (fn []
                      (rf/dispatch [::events/fetch-org {:id org-id}])
                      (rf/dispatch [::events/fetch-services {:id org-id}]))
                    [org-id])

    ($ Stack
       {:direction "column"
        :sx #js {:gap 2}}

       (cond
         (and previous-sent? candidate-now?)
         (if sync-enabled
           ($ Alert {:severity "success"} "PTV integraatio käytössä")
           ($ Alert {:severity "success"} "PTV integraatio käytössä, mutta paikan synkronointi PTV on kytketty pois päältä."))

         (and previous-sent? (not candidate-now?))
         ($ Alert {:severity "warning"} "Paikka on viety PTV, mutta on muutettu niin että näyttää nyt siltä että sen ei pidä mennä PTV -> PTV palvelu paikka arkistoidaan tallennuksessa.")

         candidate-now?
         ($ Alert {:sevierty "info"} "Paikkaa ei viety PTV, mutta palvelu paikka luodaan tallennuksessa")

         :else
         ($ Alert {:severity "warning"} "Paikka näyttää siltä ettei sitä pidä viedä PTV"))

       (when candidate-now?
         (cond
           (seq missing-services)
           ($ Alert {:severity "warning"} "Lipas tyyppi muuttuu, service puuttuu PTV")

           type-code-changed?
           ($ Alert {:severity "info"} "Lipas tyyppi muuttuu, uusi service " (:id new-service))))

       ; ($ FormControl
       ;    ($ FormLabel
       ;       "Lipas tila")
       ;    ($ Typography
       ;       status))

       (when (seq missing-services)
         ($ new-service-form
            {:data x
             :tr tr
             :org-id org-id
             :service (first missing-services)}))

       ($ FormControl
          ($ FormLabel
             "PTV Tila")
          ($ Typography
             publishing-status)
          ($ Typography
             last-sync))

       #_
       ($ controls/description-integration
          {:value (:descriptions-integration (:ptv site))
           :on-change identity
           :tr tr})

       ($ FormControlLabel
          {:label "Sync-enabled"
           :control ($ Switch
                       {:disabled read-only?
                        :value sync-enabled
                        :checked sync-enabled
                        :on-change (fn [_e v]
                                     (js/console.log _e v)
                                     (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :sync-enabled] v]))})})

       (when (= "lipas-managed-ptv-fields" descriptions-integration)
         ($ Stack
            {:sx #js {:position "relative"}}
            ($ Button
               {:disabled (or loading?
                              read-only?)
                :variant "outlined"
                ;; NOTE: Could use the lipas-id version when not editing? But then we don't have
                ;; place to store the results.
                :on-click (fn [_e]
                            (rf/dispatch [::events/generate-descriptions-from-data lipas-id]))}
               (tr :ptv.actions/generate-with-ai))
            (when loading?
              ($ CircularProgress
                 {:size 24
                  :sx #js {:position "absolute"
                           :top "50%"
                           :left "50%"
                           :mt "-12px"}}))))

       ($ controls/lang-selector
          {:value selected-tab
           :on-change set-selected-tab})

       ;; Summary
       (r/as-element
         [lui/text-field
          {:disabled   (or loading?
                           (not descriptions-enabled)
                           read-only?)
           :multiline  true
           :variant    "outlined"
           :on-change  (fn [v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :summary selected-tab] v]))
           :label      "Tiivistelmä"
           :value      (or (get-in edit-data [:ptv :summary selected-tab])
                           (get-in sports-site [:ptv :summary selected-tab]))}])

       ;; Description
       (r/as-element
         [lui/text-field
          {:disabled   (or loading?
                           (not descriptions-enabled)
                           read-only?)
           :variant    "outlined"
           :rows       5
           :multiline  true
           :on-change  (fn [v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :description selected-tab] v]))
           :label      "Kuvaus"
           :value      (or (get-in edit-data [:ptv :description selected-tab])
                           (get-in sports-site [:ptv :description selected-tab]))}])

       ($ Button
          {:disabled (or loading?
                         read-only?)
           :on-click (fn [_e]
                       (rf/dispatch [::events/translate-to-other-langs lipas-id {:from (name selected-tab)
                                                                                 :to (disj #{"fi" "en" "se"} (name selected-tab))}]))}
          "Käännä muille kielille"))))
