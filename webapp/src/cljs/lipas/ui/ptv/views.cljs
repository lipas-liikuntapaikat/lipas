(ns lipas.ui.ptv.views
  (:require
   ["@mui/material/Paper$default" :as Paper]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.ptv.subs :as subs]
   [lipas.ui.ptv.events :as events]
   [lipas.ui.utils :refer [<== ==>]]
   [reagent.core :as r]))

;; Memo
;; - preset service structure with descriptions
;; - linking to existing service channels
;;   - maybe define what to overwrite?
;;   - ...or pre-fill fields from PTV via linking?
;;   - ...anyway, somehow re-using stuff that's already there
;; - auto-sync on save

(defn org-selector
  []
  (let [selected-org (<== [::subs/selected-org])]
    [lui/select
     {:items     [{:name "Utajärven kunta"
                   :id   "7b83257d-06ad-4e3b-985d-16a5c9d3fced"}]
      :label     "Valitse organisaatio"
      :label-fn  :name
      :value-fn  identity
      :value     selected-org
      :on-change #(==> [::events/select-org %])}]))


(defn service-selector
  [{:keys [value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [items (<== [::subs/services])]
    [lui/autocomplete
     {:items     items
      :multi?    true
      :label     label
      :label-fn  :label
      :value-fn  value-fn
      :value     value
      :on-change on-change}]))

(defn service-channel-selector
  [{:keys [value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [items (<== [::subs/service-channels])]
    [lui/autocomplete
     {:items     items
      :multi?    true
      :label     label
      :label-fn  :name
      :value-fn  value-fn
      :value     value
      :on-change on-change}]))

(defn info-text
  [s]
  #_[mui/paper {:style {:padding "1em" :background-color mui/gray3}}]
  [mui/typography {:variant "body1" #_#_:style {:font-size "0.9rem"}} s])

(defn settings
  []
  (let [default-settings (<== [::subs/default-settings])]
    [mui/grid {:container true :spacing 4 :style {:margin-left "-32px"}}

     [mui/grid {:item true :xs 12}
      [mui/stack {:spacing 2}

       [mui/typography {:variant "h5"}
        "Vienti Palvelutietovarantoon"]

       [mui/form-control
        [mui/form-label "Milloin muutokset viedään Palvelutietovarantoon?"]
        [mui/radio-group
         {:on-change #(==> [::events/select-integration-interval %2])
          :value     (:integration-interval default-settings)}
         [mui/form-control-label
          {:value   "immediate"
           :label   "Automaattisesti kun muutoksia tallennetaan Lipaksessa (suositeltu)"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "daily"
           :label   "Automaattisesti kerran päivässä"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Vain manuaalisesti"
           :control (r/as-element [mui/radio])}]]]]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "h5"} "Oletusasetukset"]]

     [mui/grid {:item true :xs 12}
      [info-text
       "Oletusasetukset voi ylikirjoittaa määrittämällä Liikuntapaikat-välilehdellä kohdekohtaiset asetukset."]]

     ;; Service
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        "Palvelut"]

       ;; Integration type
       [mui/form-control
        [mui/form-label "Valitse kytkemistapa"]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-integration-default %2])
          :value     (:service-integration default-settings)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   "LIPAS määrittää palvelut (suositeltu)"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Kytke manuaalisesti"
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-integration default-settings))
         [info-text "LIPAS perustaa tarvittavat palvelut PTV:oon DVV:n suositusten mukaisesti ja kytkee palvelukanavat (liikuntapaikat) niihin automaattisesti."])

       (when (= "manual" (:service-integration default-settings))
         [info-text "Liikuntapaikat kytketään olemassa oleviin Palvelutietovarannon palveluihin manuaalisesti. Kytkentä tarvitsee tehdä jokaiselle liikuntapaikalle kerran."])]]

     ;; Service channel
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        "Palvelukanava"]

       ;; Integration type
       [mui/form-control
        [mui/form-label "Valitse kytkemistapa"]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-channel-integration-default %2])
          :value     (:service-channel-integration default-settings)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   "LIPAS määrittää palvelukanavat (suositeltu)"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Kytke manuaalisesti"
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-channel-integration default-settings))
         [info-text "LIPAS perustaa jokaiselle liikuntapaikalle palvelukanavan ja synkronoi tiedot."])

       (when (= "manual" (:service-channel-integration default-settings))
         [info-text
           "Liikuntapaikka kytketään olemassa olevaan palvelukanavaan manuaalisesti. Kytkentä tarvitsee tehdä jokaiselle liikuntapaikalle kerran"])]]

     ;; Descriptions
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        "Kuvaukset"]

       ;; Integration type
       [mui/form-control
        [mui/form-label "Valitse kytkemistapa"]
        [mui/radio-group
         {:on-change #(==> [::events/select-descriptions-integration-default %2])
          :value     (:descriptions-integration default-settings)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   "Käytä liikuntapaikan lisätietokenttää"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Syötä kuvaukset manuaalisesti"
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:descriptions-integration default-settings))
         [info-text "Palvelukanavan kuvaus ylläpidetään liikuntapaikan lisätietokentässä. Tiivistelmä on kuvauksen ensimmäinen enter-painikkeella erotettu kappale."])

       (when (= "manual" (:descriptions-integration default-settings))
         [info-text "Kuvaus ja tiivistelmä ylläpidetään liikuntapaikasta erillään tässä työkalussa."])]]]))

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
        "Palvelut"]

       ;; Integration type
       [mui/form-control
        [mui/form-label "Valitse kytkemistapa"]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-integration site %2])
          :value     (:service-integration site)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   "LIPAS määrittää palvelut (suositeltu)"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Kytke manuaalisesti"
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-integration site))
         "TODO")

       (when (= "manual" (:service-integration site))
         [service-selector
          {:value     (:service-id site)
           :on-change #(==> [::events/select-service site %])
           :value-fn  :service-id
           :label     "Valitse palvelut"}])]]

     ;; Service channel
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        "Palvelukanava"]

       ;; Integration type
       [mui/form-control
        [mui/form-label "Valitse kytkemistapa"]
        [mui/radio-group
         {:on-change #(==> [::events/select-service-channel-integration site %2])
          :value     (:service-channel-integration site)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   "LIPAS määrittää palvelukanavat (suositeltu)"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Kytke manuaalisesti"
           :control (r/as-element [mui/radio])}]]]

       (when (= "lipas-managed" (:service-channel-integration site))
         "TODO")

       (when (= "manual" (:service-channel-integration site))
         [service-channel-selector
          {:value     (:service-channel-id site)
           :value-fn  :id
           :on-change #(==> [::events/select-service-channel site %])
           :label     "Valitse palvelukanava"}])]]

     ;; Descriptions
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        "Kuvaukset"]

       ;; Integration type
       [mui/form-control
        [mui/form-label "Valitse kytkemistapa"]
        [mui/radio-group
         {:on-change #(==> [::events/select-descriptions-integration site %2])
          :value     (:descriptions-integration site)}
         [mui/form-control-label
          {:value   "lipas-managed"
           :label   "Käytä liikuntapaikan lisätietokenttää"
           :control (r/as-element [mui/radio])}]

         [mui/form-control-label
          {:value   "manual"
           :label   "Syötä kuvaukset manuaalisesti"
           :control (r/as-element [mui/radio])}]]]

       ;; Summary
       [lui/text-field
        {:multiline  true
         :read-only? (not= "manual" (:descriptions-integration site))
         :variant    "outlined"
         :on-change  #(==> [::events/set-summary site locale %])
         :label      "Tiivistelmä"
         :value      (:summary site)}]

       ;; Description
       [lui/text-field
        {:variant    "outlined"
         :read-only? (not= "manual" (:descriptions-integration site))
         :rows       5
         :multiline  true
         :on-change  #(==> [::events/set-description site locale %])
         :label      "Kuvaus"
         :value      (:description site)}]]]]))

(defn table []
  (r/with-let [expanded-rows (r/atom {})]
    (let [tr    (<== [:lipas.ui.subs/translator])
          sites (<== [::subs/sports-sites])

          headers [{:key :expand :label ""}
                   {:key :selected :label "Vie"}
                   #_{:key :auto-sync :label "Vie automaattisesti"}
                   {:key :last-sync :label "Viety viimeksi"}
                   {:key :name :label (tr :general/name)}
                   {:key :type :label (tr :general/type)}
                   ;;{:key :admin :label (tr :lipas.sports-site/admin)}
                   {:key :owner :label (tr :lipas.sports-site/owner)}
                   #_{:key :service :label "Palvelu"}
                   #_{:key :service-channel :label "Palvelukanava"}
                   #_{:key :service-channel-summary :label "Tiivistelmä"}
                   #_{:key :service-channel-description :label "Kuvaus"}]]

      (when (seq sites)
        [mui/table-container {:component Paper}
         [mui/table

          ;; Headers
          [mui/table-head
           [mui/table-row
            (for [{:keys [key label]} headers]
              [mui/table-cell {:key (name key)}
               label])]]

          ;; Body
          [mui/table-body
           (doall
            (for [{:keys [lipas-id] :as site} sites]

              [:<> {:key lipas-id}

               ;; Summary row
               [mui/table-row

                ;; Expand toggle
                [mui/table-cell
                 [mui/icon-button
                  {:size     "small"
                   :on-click (fn [] (swap! expanded-rows update lipas-id not))}
                  [mui/icon
                   (if (get @expanded-rows lipas-id false)
                     "keyboard_arrow_up_icon"
                     "keyboard_arrow_down_icon")]]]

                ;; Select
                [mui/table-cell
                 [lui/switch
                  {:value     (:sync-enabled site)
                   :on-change #(==> [::events/toggle-sync-enabled site %])}]]

                ;; Last-sync
                [mui/table-cell
                 (:last-sync site)]

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
                   #_[service-selector]]

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

(defn dialog
  [{:keys [tr]}]
  (let [open?        (<== [::subs/dialog-open?])
        selected-tab (<== [::subs/selected-tab])
        loading?     (<== [::subs/loading-from-ptv?])
        org-data     (<== [::subs/selected-org-data])]

    [lui/dialog
     {:open?         open?
      :on-save       #(==> [::events/save])
      :save-enabled? true
      :save-label    "Tallenna"
      :title         (tr :ptv/tooltip)
      :max-width     "xl"
      :cancel-label  (tr :actions/cancel)
      :on-close      #(==> [::events/close-dialog])}


     [mui/stack {:spacing 2}

      [org-selector]

      (when loading?
        [mui/stack {:direction "row" :spacing 2 :alignItems "center"}
         [mui/circular-progress]
         [mui/typography "Haetaan tietoja PTV:sta..."]])

      (when (and org-data (not loading?))
        [:<>
         [mui/tabs
          {:value          selected-tab
           :on-change      #(==> [::events/select-tab %2])
           :textColor      "primary"
           :indicatorColor "secondary"}

          [mui/tab {:value "sports-sites" :label "Liikuntapaikat"}]
          [mui/tab {:value "settings" :label "Asetukset"}]]

         (when (= selected-tab "settings")
           [settings])

         (when (= selected-tab "sports-sites")
           [table])])]]))
