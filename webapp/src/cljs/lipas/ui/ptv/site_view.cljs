(ns lipas.ui.ptv.site-view
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :refer [prod?]]
            [lipas.utils :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defui new-service-form [{:keys [org-id tr service data]}]
  (let [{:keys [source-id sub-category-id]} service
        loading? (use-subscribe [::subs/generating-descriptions?])
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
       {:sx #js {:p 2} :elevation 6}

       ($ Stack
          {:direction "column"
           :sx #js {:gap 2}}
          ($ Typography
             {:variant "h6"}
             "Luo palvelu: "
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
              :on-change set-selected-tab
              :enabled-languages (set org-languages)})

          ;; Summary
          (r/as-element
            [lui/text-field
             {:disabled (or loading?
                            read-only?)
              :multiline true
              :variant "outlined"
              :on-change (fn [v])
              :label "Tiivistelmä"
              :value (get summary selected-tab)}])

          ;; Description
          (r/as-element
            [lui/text-field
             {:disabled (or loading?
                            read-only?)
              :variant "outlined"
              :rows 5
              :multiline true
              :on-change (fn [v])
              :label "Kuvaus"
              :value (get description selected-tab)}])

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
             "Luo palvelu")))))

(defui audit-summary-notification
  "Displays a summary notification if there are audit issues"
  [{:keys [lipas-id]}]
  (let [summary-feedback (use-subscribe [::subs/site-audit-field-feedback lipas-id :summary])
        summary-status (use-subscribe [::subs/site-audit-field-status lipas-id :summary])
        desc-feedback (use-subscribe [::subs/site-audit-field-feedback lipas-id :description])
        desc-status (use-subscribe [::subs/site-audit-field-status lipas-id :description])

        has-summary-issues? (and summary-feedback
                                 (not (str/blank? summary-feedback))
                                 (= summary-status "changes-requested"))
        has-desc-issues? (and desc-feedback
                              (not (str/blank? desc-feedback))
                              (= desc-status "changes-requested"))

        issues-count (+ (if has-summary-issues? 1 0)
                        (if has-desc-issues? 1 0))]

    (when (> issues-count 0)
      ($ Alert
         {:severity "warning"
          :variant "outlined"
          :sx #js {:mb 2}}
         ($ AlertTitle "Auditoinnissa havaittu ongelmia")
         (cond
           (and has-summary-issues? has-desc-issues?)
           "Sekä tiivistelmä että kuvaus vaativat muutoksia. Katso tarkemmat kommentit kenttien alta."

           has-summary-issues?
           "Tiivistelmä vaatii muutoksia. Katso tarkemmat kommentit kentän alta."

           has-desc-issues?
           "Kuvaus vaatii muutoksia. Katso tarkemmat kommentit kentän alta.")))))

(defui audit-feedback-component
  "Displays audit feedback for a specific field (summary or description)"
  [{:keys [lipas-id field-name]}]
  (let [feedback (use-subscribe [::subs/site-audit-field-feedback lipas-id field-name])
        status (use-subscribe [::subs/site-audit-field-status lipas-id field-name])]
    (when (and feedback (not (str/blank? feedback)))
      ($ Alert
         {:severity (case status
                      "changes-requested" "error"
                      "approved" "success"
                      "warning")
          :variant "outlined"
          :sx #js {:mt 1 :mb 1}}
         ($ AlertTitle
            (case status
              "changes-requested" "Auditoijan palaute - vaatii muutoksia"
              "approved" "Auditoijan palaute - hyväksytty"
              "Auditoijan palaute"))
         feedback))))

(defui site-view [{:keys [tr lipas-id can-edit? edit-data]}]
  (let [[selected-tab set-selected-tab] (uix/use-state :fi)
        locale (tr)

        editing* (boolean (use-subscribe [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        editing? (and can-edit? editing*)
        sports-site (use-subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])

        ;; NOTE: Edit-data and sports-site schema can be a bit different.
        ;; Shouldn't matter for the :ptv fields we mostly need here.
        site (if editing?
               edit-data
               sports-site)

        ;; _ (js/console.log edit-data sports-site)

        {:keys [org-id sync-enabled delete-existing last-sync publishing-status]} (:ptv site)

        orgs (use-subscribe [::subs/users-orgs])
        org-languages (use-subscribe [::subs/org-languages org-id])

        ;; _ (js/console.log org-id)

        ;; default-settings {}
        ;; enabled    (boolean (:ptv site))

        loading? (use-subscribe [::subs/generating-descriptions?])

        type-code (-> site :type :type-code)

        type-code-changed? (not= type-code (:previous-type-code (:ptv site)))
        previous-sent? (ptv-data/is-sent-to-ptv? site)
        ready? (ptv-data/ptv-ready? site)
        candidate-now? (ptv-data/ptv-candidate? site)

        read-only? (or (not editing?)
                       (not candidate-now?))

        types (use-subscribe [:lipas.ui.sports-sites.subs/all-types])
        loading-ptv? (use-subscribe [::subs/loading-from-ptv?])

        services (use-subscribe [::subs/services-by-id org-id])
        services* (use-subscribe [::subs/services org-id])
        missing-services-input [{:service-ids #{}
                                 :sub-category-id (-> site :type :type-code types :sub-category)
                                 :sub-category (-> site :search-meta :type :sub-category :name :fi)}]
        missing-services (when (and org-id candidate-now? (not loading-ptv?))
                           (ptv-data/resolve-missing-services org-id services missing-services-input))

        source-id->service (utils/index-by :sourceId (vals services))
        new-service (ptv-data/sub-category-id->service org-id source-id->service (-> site :type :type-code types :sub-category))

        new-service-sub-cat (get types/sub-categories (-> site :type :type-code types :sub-category))

        to-archive? (and previous-sent?
                         (not candidate-now?))

        org-options (uix/use-memo (fn []
                                    (->> orgs
                                         (map (fn [{:keys [name _id ptv-data]}]
                                                {:label name
                                                 :value (:org-id ptv-data)}))))
                                  [orgs])]

    #_(js/console.log missing-services new-service new-service-sub-cat)
    #_(js/console.log "sync enabled" sync-enabled)

    (uix/use-effect (fn []
                      (when org-id
                        (rf/dispatch [::events/fetch-ptv-org {:id org-id}])
                        (rf/dispatch [::events/fetch-ptv-services {:id org-id}])))
                    [org-id])

    ($ Stack
       {:direction "column"
        :sx #js {:gap 2
                 :position "relative"}}

       (when loading-ptv?
         ($ Stack
            {:severity "info"
             :sx #js {:position "absolute"
                      :background "rgba(255, 255, 255, 0.7)"
                      :zIndex 2000
                      :top "0"
                      :left "0"
                      :width "100%"
                      :height "100%"}}
            ($ Stack
               {:direction "column"
                :alignItems "center"
                :sx #js {:position "absolute"
                         :top "50%"
                         :left "50%"
                         :transform "translateX(-50%) translateY(-50%)"
                         :gap 2}}
               ($ CircularProgress)
               "Ladataan PTV tietoja...")))

       ($ Stack
          {:direction "row"}
          ($ FormControlLabel
             {:label "Synkronoi PTV:hen"
              :control ($ Switch
                          {:disabled read-only?
                           :checked sync-enabled
                           :on-change (fn [_e v]
                                        (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :sync-enabled] v]))})})
          (when (and (not sync-enabled)
                     previous-sent?)
            ($ FormControlLabel
               {:label "Arkistoi paikka PTV:ssä"
                :control ($ Switch
                            {:disabled read-only?
                             :checked (or delete-existing false)
                             :on-change (fn [_e v]
                                          (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :delete-existing] v]))})})))

       (when sync-enabled
         ($ :<> {}
            ($ FormControl
               #_($ FormLabel
                    "PTV-tila")
               (cond
                 (:error (:ptv site))
                 (let [e (:error (:ptv site))]
                   ($ Alert {:severity "error"}
                      "Virhe PTV-integraatiossa, uusimpia tietoja ei ole viety PTV:hen. " (:message e)))

                 (and previous-sent? candidate-now? ready?)
                 (if sync-enabled
                   ($ Alert {:severity "success"} "PTV-integraatio on käytössä")
                   (if delete-existing
                     ($ Alert {:severity "success"} "Liikuntapaikka poistetaan PTV:stä tallennuksen yhteydessä")
                     ($ Alert {:severity "success"} "PTV-integraatio on käytössä, mutta liikuntapaikan synkronointi PTV:hen on kytketty pois päältä.")))

                 (and previous-sent? (not candidate-now?))
                 ($ Alert {:severity "warning"} "Liikuntapaikka on viety aiemmin PTV:hen, mutta tietoja on muutettu siten, että tietoja ei enää viedä. PTV-palvelupaikka tullaan arkistoimaan tallennuksen yhteydessä.")

                 (and candidate-now? ready? sync-enabled)
                 ($ Alert {:severity "info"} "Liikuntapaikkaa ei ole aiemmin viety PTV:hen. Uusi palvelupaikka tullaan luomaan tallennuksen yhteydessä.")

                 (and candidate-now? (not ready?))
                 ($ Alert {:severity "info"} "PTV-tiedot ovat vielä puutteelliset. Täytä puuttuvat tiedot, niin liikuntapaikka viedään PTV:hen tallennuksen yhteydessä.")

                 :else
                 "-")
               (when-let [x (first (:service-channel-ids (:ptv site)))]
                 ($ :<>
                    ($ Link
                       {:target "new"
                        :href (str (if (prod?)
                                     "https://palvelutietovaranto.suomi.fi/channels/serviceLocation/"
                                     "https://palvelutietovaranto.trn.suomi.fi/channels/serviceLocation/")
                                   x)}
                       "Avaa PTV:ssä")
                    (when (prod?)
                      ($ Link
                         {:target "new"
                          :href (str "https://www.suomi.fi/palvelut/palvelupiste/x/" x)}
                         "Avaa suomi.fi:ssä")))))

            ;; Audit summary notification - show if there are audit issues
            ($ audit-summary-notification {:lipas-id lipas-id})

            (when (and candidate-now? (not (:org-id (:ptv site))))
              ($ :<>
                 ($ Alert {:severity "warning"}
                    "Valitse organisaatio")))

            (when (not candidate-now?)
              ($ Alert {:severity "warning"} "Liikuntapaikka ei sovellu PTV:hen vietäväksi."))

            ($ autocomplete2
               {:options org-options
                :disabled (or loading?
                              read-only?)
                :label "Organisaatio"
                :value (:org-id (:ptv site))
                :on-change (fn [_e v]
                             (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :org-id] (:value v)]))})

            (when candidate-now?
              ($ FormControl
                 ($ controls/services-selector
                    {:disabled (or loading?
                                   read-only?)
                     :value (:service-ids (:ptv site))
                     :options services*
                     :on-change (fn [ids]
                                  (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :service-ids] ids]))
                     :value-fn :service-id
                     :label (tr :ptv.actions/select-service)})

                 (cond
                   (seq missing-services)
                   ($ Alert {:severity "warning"} "Liikuntapaikan tyyppi on muuttunut ja uutta tyyppiä vastaava Palvelu puuttuu PTV:stä.")

                   (and previous-sent? type-code-changed?)
                   ($ Alert {:severity "info"} "Liikuntapaikan tyyppi on vaihdettu. Liikuntapaikka liitetään PTV:ssä mahdollisesti toiseen Palveluun vaihdon seurauksena."))))

            (when (seq missing-services)
              ($ new-service-form
                 {:data site
                  :tr tr
                  :org-id org-id
                  :service (first missing-services)}))

            ($ Typography {:variant "h6"} "Palvelupaikan kuvaukset")

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
                              :mt "-12px"}})))

            ($ controls/lang-selector
               {:value selected-tab
                :on-change set-selected-tab
                :enabled-languages (set (or (:languages (:ptv site))
                                            org-languages))})

           ;; Summary
            (r/as-element
              [lui/text-field
               {:disabled (or loading?
                              read-only?)
                :multiline true
                :variant "outlined"
                :on-change (fn [v]
                             (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :summary selected-tab] v]))
                :label "Tiivistelmä"
                :value (or (get-in edit-data [:ptv :summary selected-tab])
                           (get-in sports-site [:ptv :summary selected-tab]))}])

            ;; Summary audit feedback
            ($ audit-feedback-component
               {:lipas-id lipas-id
                :field-name :summary})

           ;; Description
            (r/as-element
              [lui/text-field
               {:disabled (or loading?
                              read-only?)
                :variant "outlined"
                :rows 5
                :multiline true
                :on-change (fn [v]
                             (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :description selected-tab] v]))
                :label "Kuvaus"
                :value (or (get-in edit-data [:ptv :description selected-tab])
                           (get-in sports-site [:ptv :description selected-tab]))}])

            ;; Description audit feedback
            ($ audit-feedback-component
               {:lipas-id lipas-id
                :field-name :description})

            (when (> (count org-languages) 1)
              ($ Button
                 {:disabled (or loading?
                                read-only?)
                  :on-click (fn [_e]
                              (rf/dispatch [::events/translate-to-other-langs lipas-id {:from (name selected-tab)
                                                                                        :to (disj #{"fi" "en" "se"} (name selected-tab))}]))}
                 "Käännä muille kielille")))))))
