(ns lipas.ui.ptv.views
  (:require ["@mui/icons-material/Close$default" :as CloseIcon]
            ["@mui/icons-material/Sync$default" :as Sync]
            ["@mui/icons-material/SyncDisabled$default" :as SyncDisabled]
            ["@mui/icons-material/SyncProblem$default" :as SyncProblem]
            ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/AppBar$default" :as AppBar]
            ["@mui/material/Avatar$default" :as Avatar]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepButton$default" :as StepButton]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [goog.string.format]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :as utils :refer [<== ==>]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

;; Memo
;; - preset service structure with descriptions
;; - linking to existing service channels
;;   - maybe define what to overwrite?
;;   - ...or pre-fill fields from PTV via linking?
;;   - ...anyway, somehow re-using stuff that's already there
;; - auto-sync on save

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
  (let [orgs (<== [::subs/users-orgs])
        selected-org (<== [::subs/selected-org])]
    [lui/select
     {:items     orgs
      :label     label
      :label-fn  :name
      :value-fn  identity
      :value     selected-org
      :on-change #(==> [::events/select-org %])}]))

(defui service-channel-selector
  [{:keys [org-id value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [items (use-subscribe [::subs/service-channels-list org-id])
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
                    (on-change [(:value v)]))})))

(defn form
  [{:keys [org-id tr site]}]
  (let [services @(rf/subscribe [::subs/services org-id])
        org-languages (ptv-data/org-id->languages org-id)]
    [mui/grid
     {:container true
      :spacing   2
      :style     {:padding-top "1em" :padding-bottom "1em"}}

     [mui/grid {:item true :xs 12}
      [lui/switch
        {:label     (tr :ptv.actions/export-disclaimer)
         :value     (:sync-enabled site)
         :on-change #(==> [::events/toggle-sync-enabled site %])}]]

     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}

       [mui/typography {:variant "h6"}
        (tr :ptv/services)]

       ;; Service
       ($ controls/services-selector
          {:options   services
           :value     (:service-ids site)
           :on-change (fn [ids] (rf/dispatch [::events/select-services site ids]))
           :value-fn  :service-id
           :label     (tr :ptv.actions/select-service)})]]

     ;; Service channels
     [mui/grid {:item true :xs 12 :lg 4}
      [mui/stack {:spacing 2}
       [mui/typography {:variant "h6"}
        (tr :ptv/service-channels)]

       ($ service-channel-selector
          {:org-id org-id
           :value     (:service-channel-ids site)
           :value-fn  :service-channel-id
           :on-change #(==> [::events/select-service-channels site %])
           :label     (tr :ptv.actions/select-service-channel)})]]

     ;; Descriptions
     (r/with-let [selected-tab (r/atom :fi)]
       (let [loading? (<== [::subs/generating-descriptions?])]
         [mui/grid {:item true :xs 12 :lg 4}
          [mui/stack {:spacing 2}
           [mui/typography {:variant "h6"}
            (tr :ptv/descriptions)]

           [mui/button {:disabled loading?
                        :on-click #(==> [::events/generate-descriptions (:lipas-id site) [] []])}
            (tr :ptv.actions/generate-with-ai)]

           (when loading?
             [mui/circular-progress])

           ($ controls/lang-selector
              {:value @selected-tab
               :on-change #(reset! selected-tab %)
               :enabled-languages (set org-languages)})

           ;; Summary
           [lui/text-field
            {:disabled   loading?
             :multiline  true
             :variant    "outlined"
             :on-change  #(==> [::events/set-summary site @selected-tab %])
             :label      "Tiivistelmä"
             :value      (get-in site [:summary @selected-tab])}]

           ;; Description
           [lui/text-field
            {:disabled   loading?
             :variant    "outlined"
             :rows       5
             :multiline  true
             :on-change  #(==> [::events/set-description site @selected-tab %])
             :label      "Kuvaus"
             :value      (get-in site [:description @selected-tab])}]

           (if (:sync-enabled site)
             [mui/button {:disabled loading?
                          :on-click #(==> [::events/create-ptv-service-location (:lipas-id site) [] []])}
              "Vie PTV:hen"]
             [mui/button {:disabled loading?
                          :on-click #(==> [::events/save-ptv-meta [site]])}
              "Tallenna"])

           ]]))]))

(defn table []
  (r/with-let [expanded-rows (r/atom {})]
    (let [tr                (<== [:lipas.ui.subs/translator])
          org-id            (<== [::subs/selected-org-id])
          sites             (<== [::subs/sports-sites org-id])
          sync-all-enabled? (<== [::subs/sync-all-enabled? org-id])

          headers [{:key :expand :label "" :padding "checkbox"}
                   #_
                   {:key     :selected :label (tr :ptv.actions/export)
                    :padding "checkbox"
                    :action-component
                    [mui/switch
                     {:value     sync-all-enabled?
                      :on-change #(==> [::events/toggle-sync-all %2])}]}
                   #_{:key :auto-sync :label "Vie automaattisesti"}
                   {:key :event-data :label "Tila"}
                   #_{:key :last-sync :label "Viety viimeksi"}
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
             (for [{:keys [lipas-id sync-status] :as site} (sort-by :type sites)]

               [:<> {:key lipas-id}

               [mui/table-row
                {:sx [{}
                      ]}

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
                #_
                [mui/table-cell
                 [lui/switch
                  {:value     (:sync-enabled site)
                   :on-change #(==> [::events/toggle-sync-enabled site %])}]]

                [mui/table-cell
                 ($ Stack
                    {:direction "row"
                     :alignItems "center"}
                    ($ Tooltip {:placement "right-end"
                                :title (if (:sync-enabled site)
                                               (case sync-status
                                                 :ok "PTV:ssä on ajantasaiset tiedot tästä liikuntapaikasta"
                                                 :not-synced "Liikuntapaikkaa ei ole vielä koskaan viety Palvelutietovarantoon"
                                                 :out-of-date "Liikuntapaikkaan on tehty muutoksia, joita ei ole viety Palvelutietovarantoon")
                                               "Integraatio ei ole päällä tälle liikuntapaikalle")}
                       ($ Avatar
                          {:sx #js {:bgcolor (if (:sync-enabled site)
                                               (case sync-status
                                                 :ok "success.main"
                                                 :not-synced "error.main"
                                                 :out-of-date "warning.main")
                                               mui/gray3)
                                    :mr 2}}
                          (if (:sync-enabled site)
                            (if (= :ok sync-status)
                              ($ Sync {:color "white"})
                              ($ SyncProblem
                                 {:color "white"}))
                            ($ SyncDisabled {:background "white"}))))
                    #_
                    (:event-date-human site))]

                ;; Last-sync
                #_[mui/table-cell
                 (or (some-> site :last-sync utils/->human-date-time-at-user-tz)
                     "Ei koskaan")]

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
                   [form {:tr tr
                          :org-id org-id
                          :site site}]]]]]))]]]))))

(defui set-types []
  (let [tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)
        options* (uix/use-memo (fn []
                                 (->> types/sub-categories
                                      vals
                                      (map (fn [{:keys [type-code] :as x}]
                                             {:value type-code
                                              :sort-value (case type-code
                                                            (1 2) (* 100 type-code)
                                                            type-code)
                                              :label (str type-code " " (get-in x [:name locale]))}))
                                      (sort-by :sort-value)))
                               [locale])
        value (:sub-cats (use-subscribe [::subs/candidates-search]))
        on-change (fn [v]
                    (rf/dispatch [::events/set-candidates-search {:sub-cats v}]))]
    ($ Stack
       {:sx #js {:gap 2}}

       ($ Typography
          "Voit valita haluamasi Lipas-tyyppiluokat vietäväksi PTV:hen. Jos et tee valintaa, kaikki kunnan omistamat liikuntapaikat viedään. Viimeisessä vaiheessa voit jättää tietyt liikuntapaikat pois viennistä.")

       ;; NOTE: Huoltotilat antaa aina 0 tulosta koska filteröity pois
       ($ autocomplete2
          {:options   options*
           :multiple  true
           :label     "Valitse ryhmät"
           :value     (to-array value)
           :on-change (fn [_e v]
                        (on-change (vec (map (fn [x]
                                               (if (map? x)
                                                 (:value x)
                                                 x))
                                             v))))})

       ($ Button
          {:onClick (fn [_e]
                      (rf/dispatch [::events/set-step 1]))}
          "Seuraava"
          ($ Icon "arrow_forward")))))

(defui service-preview
  [{:keys [source-id sub-category-id valid]}]
  (let [preview  (use-subscribe [::subs/service-preview source-id sub-category-id])
        row     (uix/fn [{:keys [label value tooltip]}]
                        ($ Tooltip {:title tooltip}
                           ($ TableRow
                              ($ TableCell ($ Typography {:variant "caption"} label))
                              ($ TableCell ($ Typography value)))))
        join     (fn [coll] (when (seq coll) (str/join ", " coll)))
        get-desc (fn [type lang] (or (->> preview
                                              :serviceDescriptions
                                              (filter (fn [m] (and (= (:type m) type)
                                                                   (= (:language m) lang))))
                                              (map :value)
                                              join)
                                     "-"))
        get-name (fn [lang] (or (->> preview :serviceNames
                                         (filter #(= (:language %) lang))
                                         (map :value)
                                         join)
                                "-"))

        tt-name "Lipaksen luokittelu (pääryhmä → alaryhmä → liikuntapaikkatyyppi) määrittää palvelun nimen. PTV-palvelu luodaan alaryhmätason mukaan ja se saa nimekseen alaryhmän nimen."
        tt-summary "Tiivistelmä on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."
        tt-description "Palvelun kuvaus on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."
        lang-disclaimer "Tieto täytetään vain mikäli integraation käyttöönoton yhteydessä on ilmoitettu että palvelupaikat halutaan kuvata tällä kielellä. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."]

    ($ Stack {:spacing 2}
       ($ Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
          ($ Typography "Esikatselu näyttää palvelun perustiedot ennen PTV-julkaisua. Vie hiiren osoitin rivin päälle nähdäksesi tiedon alkuperän."))

       ($ Table {:variant "dense"}
          ($ TableHead
             ($ TableRow
                ($ TableCell "PTV-tietue")
                ($ TableCell "Arvo")))

          ($ row {:label "Tila"
                  :value (:publishingStatus preview)
                  :tooltip "Integraation PTV:hen viemät kohteet julkaistaan automaattisesti. Vedokseksi vieminen ei ole tuettu."})

          ($ row {:label "Nimi suomeksi" :value (get-name "fi") :tooltip tt-name})

          ($ row {:label "Nimi ruotsiksi"
                  :value (get-name "sv")
                  :tooltip (str tt-name " " lang-disclaimer)})

          ($ row {:label "Nimi englanniksi"
                  :value (get-name "en")
                  :tooltip (str tt-name " " lang-disclaimer)})

          ($ row {:label "Tyyppi"
                  :value (:type preview)
                  :tooltip "Palvelun tyyppi on aina \"Service\"."})

          ($ row {:label "Palveluluokat"
                  :value (join (:serviceClasses preview))
                  :tooltip "PTV:n ohjeistuksen mukaiset palveluluokat on määritelty jokaiselle Lipaksen liikuntapaikkatyypin alaryhmälle ja ne tulevat palvelun tietoihin automaattisesti."})

          ($ row {:label "Kohderyhmät"
                  :value (join (:targetGroups preview))
                  :tooltip "Palvelun kohderyhmä on aina \"Kansalaiset\""})

          ($ row {:label "Ontologiatermit"
                  :value (join (:ontologyTerms preview))
                  :tooltip "Ontologiatermit, eli PTV:n ohjeistuksen mukaiset avainsanat, on määritetty jokaiselle Lipaksen liikuntapaikkaluokittelun pää- ja alaryhmälle, ja ne lisätään palvelun tietoihin automaattisesti."})

          ($ row {:label "Rahoitus"
                  :value (:fundingType preview)
                  :tooltip "Rahoitustyyppi on aina \"Julkisesti rahoitettu\"."})

          ($ row {:label "Palveluntuottajat"
                  :value (join (:organizations (first (:serviceProducers preview))))
                  :tooltip "Palveluntuottaja on se organisaatio (kunta), joka on ottanut integraation käyttöön."})

          ($ row {:label "Tuotantotapa"
                  :value (:provisionType (first (:serviceProducers preview)))
                  :tooltip "Palvelun tuotantotapa on aina \"Itse tuotettu\"."})

          ($ row {:label "Vastuuorganisaatio"
                  :value (:mainResponsibleOrganization preview)
                  :tooltip "Organisaatio (kunta) joka käyttää integraatiota."})

          ($ row {:label "Alueen tyyppi"
                  :value (-> preview :areas first :type)
                  :tooltip "Alueen tyyppi on aina \"Kunta\"."})

          ($ row {:label "Alueen koodit"
                  :value (-> preview :areas first :areaCodes)
                  :tooltip "Alueen koodi on integraation käyttöön ottaneen organisaation (kunnan) kuntanumero."})

          ($ row {:label "Tiivistelmä suomeksi"
                  :value (get-desc "Summary" "fi")
                  :tooltip tt-summary})

          ($ row {:label "Tiivistelmä ruotsiksi"
                  :value (get-desc "Summary" "sv")
                  :tooltip (str tt-summary " " lang-disclaimer)})

          ($ row {:label "Tiivistelmä englanniksi"
                  :value (get-desc "Summary" "en")
                  :tooltip (str tt-summary " " lang-disclaimer)})

          ($ row {:label "Kuvaus suomeksi"
                  :value (get-desc "Description" "fi")
                  :tooltip tt-description})

          ($ row {:label "Kuvaus ruotsiksi"
                  :value (get-desc "Description" "sv")
                  :tooltip (str tt-description " " lang-disclaimer)})

          ($ row {:label "Kuvaus ruotsiksi"
                  :value (get-desc "Description" "en")
                  :tooltip (str tt-description " " lang-disclaimer)})))))

(defui service-location-preview
  [{:keys [org-id lipas-id]}]
  (let [preview  (use-subscribe [::subs/service-location-preview org-id lipas-id])
        row     (uix/fn [{:keys [label value tooltip]}]
                        ($ Tooltip {:title (or tooltip "Selite puuttuu")}
                           ($ TableRow
                              ($ TableCell ($ Typography {:variant "caption"} label))
                              ($ TableCell ($ Typography value)))))
        join     (fn [coll] (when (seq coll) (str/join ", " coll)))
        get-desc (fn [type lang] (or (->> preview
                                              :serviceChannelDescriptions
                                              (filter (fn [m] (and (= (:type m) type)
                                                                   (= (:language m) lang))))
                                              (map :value)
                                              join)
                                     "-"))
        get-name (fn [type lang] (or (->> preview :serviceChannelNames
                                     (filter (fn [m] (and
                                                      (= (:language m) lang)
                                                      (= (:type m) type))))
                                         (map :value)
                                         join)
                                     "-"))

        tt-summary "Tiivistelmä on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."
        tt-description "Palvelupaikan kuvaus on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."

        lang-disclaimer "Tieto täytetään vain mikäli integraation käyttöönoton yhteydessä on ilmoitettu että palvelupaikat halutaan kuvata tällä kielellä. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."]

    ($ Stack {:spacing 2}
       ($ Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
          ($ Typography "Esikatselu näyttää palvelupaikan perustiedot ennen PTV-julkaisua. Vie hiiren osoitin rivin päälle nähdäksesi tiedon alkuperän."))

       ($ Table {:variant "dense"}
          ($ TableHead
             ($ TableRow
                ($ TableCell "PTV-tietue")
                ($ TableCell "Arvo")))

          ($ row {:label "Tila"
                  :value (:publishingStatus preview)
                  :tooltip "Integraation PTV:hen viemät kohteet julkaistaan automaattisesti. Vedokseksi vieminen ei ole tuettu."})

          ($ row {:label "Kielet"
                  :value (join (:languages preview))
                  :tooltip "Kielet, joilla kunta on ilmoittanut haluavansa kuvata palvelut ja palvelupaikat kun integraatio on otettu käyttöön ensimmäisen kerran. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."})

          ($ row {:label "Nimi suomeksi"
                  :value (get-name "Name" "fi")
                  :tooltip "Liikuntapaikan nimi Lipaksessa."})

          ($ row {:label "Nimi ruotsiksi"
                  :value (get-name "Name" "sv")
                  :tooltip (str "Liikuntapaikan nimi ruotsiksi Lipaksessa."
                                " "
                                lang-disclaimer)})

          ($ row {:label "Nimi englanniksi"
                  :value (get-name "Name" "en")
                  :tooltip (str "Liikuntapaikan nimi englanniksi Lipaksessa."
                                " "
                                lang-disclaimer)})

          ($ row {:label "Vaihtoehtoinen nimi"
                  :value (get-name "AlternativeName" "fi")
                  :tooltip "Liikuntapaikan markkinointinimi Lipaksessa."})

          ($ row {:label "Ensisijainen nimitieto"
                  :value (->> preview :displayNameType first :type)
                  :tooltip "Tämä arvo on aina \"Name\"."})

          ($ row {:label "Maa"
                  :value (->> preview :addresses first :country)
                  :tooltip "Tämä arvo on aina \"FI\""})

          ($ row {:label "Katuosoite"
                  :value (-> preview :addresses first :streetAddress :street first :value)
                  :tooltip "Liikuntapaikan katuosoite Lipaksessa."})

          ($ row {:label "Postinumero"
                  :value (-> preview :addresses first :streetAddress :postalCode)
                  :tooltip "Liikuntapaikan postinumero Lipaksessa."})

          ($ row {:label "Koordinaatit"
                  :value (str "(E) "
                              (-> preview :addresses first :streetAddress :longitude)
                              " (N) "
                              (-> preview :addresses first :streetAddress :latitude))
                  :tooltip "Liikuntapaikan geometrian koordinaatit Lipaksessa muutettuna PTV:n käyttämään ETRS-TM35FIN koordinaatistoon. Aluemaisissa liikuntapaikoissa valitaan keskipiste, reiteissä aloituspiste."})

          ($ row {:label "Sähköpostiosoitteet"
                  :value (->> preview :emails (map :value) join)
                  :tooltip "Liikuntapaikan sähköpostiosoite Lipaksessa."})

          ($ row {:label "Web-sivut"
                  :value (->> preview :webPages (map :url) join)
                  :tooltip "Liikuntapaikan www-osoite Lipaksessa."})

          ($ row {:label "Puhelinnumerot"
                  :value (->> preview
                              :phoneNumbers
                              (map (fn [{:keys [number prefixNumber isFinnishServiceNumber]}]
                                     (str prefixNumber " " number (when isFinnishServiceNumber " (suomalainen palvelunumero)"))))
                              join)
                  :tooltip "Liikuntapaikan puhelinnumero Lipaksessa."})

          ($ row {:label "Tiivistelmä suomeksi"
                  :value (get-desc "Summary" "fi")
                  :tooltip tt-summary})

          ($ row {:label "Tiivistelmä ruotsiksi"
                  :value (get-desc "Summary" "sv")
                  :tooltip (str tt-summary " " lang-disclaimer)})

          ($ row {:label "Tiivistelmä englanniksi"
                  :value (get-desc "Summary" "en")
                  :tooltip (str tt-summary " " lang-disclaimer)})

          ($ row {:label "Kuvaus suomeksi"
                  :value (get-desc "Description" "fi")
                  :tooltip tt-description})

          ($ row {:label "Kuvaus ruotsiksi"
                  :value (get-desc "Description" "sv")
                  :tooltip (str tt-description " " lang-disclaimer)})

          ($ row {:label "Kuvaus ruotsiksi"
                  :value (get-desc "Description" "en")
                  :tooltip (str tt-description " " lang-disclaimer)})

          ($ row {:label "Organisaation ID"
                  :value (:organizationId preview)
                  :tooltip "Organisaatio (kunta) joka käyttää integraatiota."})

          ($ row {:label "Palveluiden ID:t"
                  :value (join (:services preview))
                  :tooltip "Palvelut joihin palvelupaikka liitetään PTV:ssä."})))))

(defn create-services
  []
  (r/with-let [selected-tab (r/atom :fi)]
    (let [tr                        (<== [:lipas.ui.subs/translator])
          org-id                    (<== [::subs/selected-org-id])
          service-candidates        (<== [::subs/service-candidates org-id])
          {:keys [in-progress?
                  halt?
                  processed-percent
                  total-count
                  processed-count]} (<== [::subs/service-descriptions-generation-progress])

          services @(rf/subscribe [::subs/services org-id])

          manual-services @(rf/subscribe [::subs/manual-services-keys org-id])
          missing-subcategories @(rf/subscribe [::subs/missing-subcategories org-id])
          service-details-tab (<== [::subs/service-details-tab])]

      #_
      {:label      (str "1. " (tr :ptv.tools.generate-services/headline))
        :label-icon (if (empty? service-candidates)
                      [mui/icon {:color "success"} "done"]
                      [mui/icon {:color "disabled"} "done"])}

      [:> Stack
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
          (let [{:keys [in-progress?
                        processed-count
                        total-count
                        processed-percent
                        halt?]}
                (<== [::subs/services-creation-progress])]
            [:<>
             [mui/button
              {:variant   "outlined"
               :disabled  (some false? (map :valid service-candidates))
               :color     "primary"
               :startIcon (r/as-element [mui/icon "ios_share"])
               :on-click  #(==> [::events/create-all-ptv-services service-candidates])}
              (tr :ptv.wizard/export-services-to-ptv)]

             ;; TODO: Cancel?

             (when in-progress?
               [mui/stack {:direction "row" :spacing 2 :align-items "center"}
                [mui/circular-progress {:variant "indeterminate" :value processed-percent}]
                [mui/typography (str processed-count "/" total-count)]])

             (when halt?
               "Something went wrong, ask engineer.")])]]

        ;; Results panel
        [mui/grid {:item true :xs 12 :lg 8}
         [mui/stack {:spacing 4}

          [mui/typography {:variant "h6"}
           (tr :ptv.wizard/services-to-add)]

          ($ :<>
             ($ Typography
                "Oletuksena Lipas luo PTV-palvelut liikuntapaikkojen tyyppien
                mukaan, mutta tarvittaessa voit myös luoda muita palveluita ja
                liittää nämä palvelupaikoille manuaalisesti.")
             ($ controls/services-selector
                {:label "Luo palvelut manuaalisesti"
                 :options missing-subcategories
                 :value manual-services
                 :value-fn :source-id
                 :on-change (fn [services]
                              (rf/dispatch [::events/set-manual-services org-id services missing-subcategories]))}))

          (when (empty? service-candidates)
            [:<>
             [mui/typography (tr :ptv.wizard/all-services-exist)]
             ($ Button
                {:onClick (fn [_e]
                            (rf/dispatch [::events/set-step 2]))}
                "Seuraava"
                ($ Icon "arrow_forward"))])

          [:div
           (doall
             (for [{:keys [source-id valid sub-category sub-category-id languages] :as m} service-candidates]

               ^{:key sub-category-id}
               [lui/expansion-panel
                {:label      sub-category
                 :label-icon (if valid
                               [mui/icon {:color "success"} "done"]
                               [mui/icon {:color "disabled"} "done"])}

                ($ Stack {:spacing 2}
                   ($ Tabs {:value service-details-tab
                            :indicatorColor "secondary"
                            :on-change #(==> [::events/select-service-details-tab %2])}
                      ($ Tab {:value "descriptions" :label "Syötä kuvaukset"})
                      ($ Tab {:value "preview" :label "Esikatselu"}))

                   ;; Enter descriptions form
                   (when (= "descriptions" service-details-tab)
                     (r/as-element
                      [mui/stack {:spacing 2}
                       ;; TODO: Allow linking service to existing PTV Service
                       ;; NOTE: This currently also lists other Services created from Lipas, not only Services created in PTV,
                       ;;       this doesn't really make sense as overriding the Lipas linking would disconnect this from the other Lipas type.
                       #_
                       ($ controls/services-selector
                          {:options   services
                           :value     (get m :service-ids)
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
                         :value     (get-in m [:description @selected-tab])}]]))

                   (when (= "preview" service-details-tab)
                     ($ service-preview
                        {:source-id source-id
                         :sub-category-id sub-category-id}))

                   )]))]]]]])))

(defui service-location-details
  [{:keys [org-id tr site lipas-id sync-enabled name-conflict service-ids selected-tab set-selected-tab service-channel-ids]}]
  (let [services (use-subscribe [::subs/services org-id])
        org-languages (ptv-data/org-id->languages org-id)
        [selected-tab2 set-selected-tab2] (uix/use-state "descriptions")]
    ($ AccordionDetails
       {}
       (r/as-element
        [mui/stack {:spacing 2}

         ($ Tabs {:value selected-tab2
                  :indicatorColor "secondary"
                  :on-change #(set-selected-tab2 %2)}
            ($ Tab {:value "descriptions" :label "Syötä kuvaukset"})
            ($ Tab {:value "preview" :label "Esikatselu"}))

         (when (= selected-tab2 "preview")
           ($ service-location-preview
              {:org-id org-id
               :lipas-id lipas-id
               :site site
               :service-ids service-ids
               :service-channel-ids service-channel-ids}))

         (when (= selected-tab2 "descriptions")
           [mui/stack {:spacing 2}

            ;; Services selector
            ($ controls/services-selector
               {:options   services
                :value     service-ids
                :value-fn  :service-id
                :on-change (fn [v]
                             (rf/dispatch [::events/select-services site v]))
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
                {:org-id org-id
                 :value     service-channel-ids
                 :value-fn  :service-channel-id
                 :on-change #(==> [::events/select-service-channels site %])
                 :label     (tr :ptv/service-channel)})

             (when-let [id (first (seq service-channel-ids))]
               ($ Button
                  {:type "button"
                   :on-click (fn [_e] (rf/dispatch [::events/load-ptv-texts lipas-id org-id id]))}
                  "Lataa tekstit PTV:stä"))]

            [lang-selector
             {:value selected-tab
              :on-change set-selected-tab
              :enabled-languages org-languages}]

            ;; Summary
            [lui/text-field
             {:multiline  true
              :variant    "outlined"
              :on-change  #(==> [::events/set-summary site selected-tab %])
              :label      (tr :ptv/summary)
              :value      (get-in site [:summary selected-tab])}]

            ;; Description
            [lui/text-field
             {:variant    "outlined"
              :rows       5
              :multiline  true
              :on-change  #(==> [::events/set-description site selected-tab %])
              :label      (tr :ptv/description)
              :value      (get-in site [:description selected-tab])}]

            ;; Disclaimer and enable switch
            [lui/switch
             {:label     (tr :ptv.actions/export-disclaimer)
              :value     sync-enabled
              :on-change #(==> [::events/toggle-sync-enabled site %])}]])]))))

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
           ;; TODO: Should also show if already saved to ptv or not?
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
  (let [tr                      (<== [:lipas.ui.subs/translator])
        org-id                  (<== [::subs/selected-org-id])
        sports-sites            (<== [::subs/sports-sites org-id])
        setup-done?             (<== [::subs/sports-site-setup-done org-id])
        sports-sites-count      (<== [::subs/sports-sites-count org-id])
        sports-sites-count-sync (<== [::subs/sports-sites-count-sync org-id])
        sports-sites-filter     (<== [::subs/sports-sites-filter])

        [selected-tab set-selected-tab] (uix/use-state :fi)

        ;; TODO: Rename this so service-location-generation progress can also be
        ;; added to this level
        {:keys         [in-progress?
                processed-lipas-ids
                processed-count
                total-count
                processed-percent
                halt?] :as m}
        (<== [::subs/batch-descriptions-generation-progress])]

    [:> Stack
     #_
     [:> Typography
      {:variant "h4"}
      (str (tr :ptv.wizard/integrate-service-locations))]

     [mui/grid {:container true :spacing 4}
      [mui/grid {:item true :xs 12 :lg 4}

       ;; Settings
       [mui/stack {:spacing 4}
        [mui/typography {:variant "h6"} (tr :ptv.wizard/generate-descriptions)]
        [mui/typography (tr :ptv.wizard/generate-descriptions-helper2)]
        [mui/typography (tr :ptv.tools.ai/start-helper)]

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

        [mui/typography {:variant "body2" :sx #js{:mb 0 :mt 0}}
         (str "Valittuna " sports-sites-count-sync "/" sports-sites-count " liikuntapaikkaa")]

        ;; Export to PTV button
        [mui/button
         {:variant   "outlined"
          :disabled  (not (every? true? (map :valid sports-sites)))
          :color     "primary"
          :startIcon (r/as-element [mui/icon "ios_share"])
          :on-click  #(==> [::events/create-all-ptv-service-locations sports-sites])}
         (tr :ptv.wizard/export-service-locations-to-ptv)]

        (let [{:keys [in-progress?
                      processed-count
                      total-count
                      processed-percent
                      halt?]}
              (<== [::subs/service-location-creation-progress])]

          [:<>
           ;; TODO: Cancel?

           (when in-progress?
             [mui/stack {:direction "row" :spacing 2 :align-items "center"}
              [mui/circular-progress {:variant "indeterminate" :value processed-percent}]
              [mui/typography (str processed-count "/" total-count)]])

           (when halt?
             "Something went wrong, ask engineer.")])]]

      ;; Results

      [mui/grid {:item true :xs 12 :lg 8}
       [mui/stack {:spacing 4}

        [mui/stack {:spacing 1 :sx #js{:pb 2}}

         [mui/typography {:variant "h6"}
          (tr :ptv/sports-sites)]

         [mui/typography {:variant "body2"}
          "Kytke integraatio päälle valitsemalla liikuntapaikka listasta ja siirtämällä liukukytkin ON-asentoon – harmaa väri osoittaa, että integraatio on pois päältä."]

         [mui/typography {:variant "body2"}
          (str "Valittuna " sports-sites-count-sync "/" sports-sites-count " liikuntapaikkaa")]]]

       [mui/stack
        (for [{:keys [lipas-id valid name-conflict sync-enabled service-ids service-channel-ids service-name] :as site} sports-sites]
          ($ service-location
             {:key                 lipas-id
              :tr                  tr
              :site                site
              :org-id              org-id
              :lipas-id            lipas-id
              :name-conflict       name-conflict
              :sync-enabled        sync-enabled
              :valid               valid
              :service-ids         service-ids
              :selected-tab        selected-tab
              :set-selected-tab    set-selected-tab
              :service-channel-ids service-channel-ids}))]]]]))

(defn service-panel
  [{:keys [org-id service descriptions]}]
  (r/with-let [lang (r/atom "fi")]
    (let [tr        (<== [:lipas.ui.subs/translator])
          source-id (:source-id service)
          loading?  false
          org-languages (ptv-data/org-id->languages org-id)
          ;; Turn the PTV Service data structure back to Lipas API call for save!
          data      {:org-id org-id
                     :source-id source-id
                     :city-codes (:city-codes service)
                     :sub-category-id (ptv-data/parse-service-source-id (:source-id service))
                     :languages org-languages
                     :summary (or (:summary descriptions) (:summary service))
                     :description (or (:description descriptions) (:description service))}]
      [lui/expansion-panel {:label (:label service)}
       [mui/stack {:spacing 2}
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

          [lang-selector
           {:value     @lang
            :opts      (:languages service)
            :on-change #(reset! lang %2)}]

          ;; Summary
          [mui/typography (tr :ptv/summary)]
          [lui/text-field
           {:disabled  loading?
            :on-change #(==> [::events/set-service-candidate-summary source-id @lang %])
            :multiline true
            :label     (tr :ptv/summary)
            :value     (get-in data [:summary @lang])}]

          ;; Descriptions
          [mui/typography (tr :ptv/description)]
          [lui/text-field
           {:disabled  loading?
            :on-change #(==> [::events/set-service-candidate-description source-id @lang %])
            :multiline true
            :label     (tr :ptv/description)
            :value     (get-in data [:description @lang])}]

          ($ Button
             {:variant "contained"
              :disabled loading?
              :on-click (fn [_e]
                          (rf/dispatch [::events/create-ptv-service org-id source-id data [] []]))}
             "Tallenna")]

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
        org-id          (<== [::subs/selected-org-id])
        services        (<== [::subs/services-filtered org-id])
        descriptions    (<== [::subs/service-candidate-descriptions org-id])]
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
         [service-panel
          {:org-id org-id
           :service service
           :descriptions (get descriptions (:source-id service))}]))]))

(defn wizard
  []
  (let [tr (<== [:lipas.ui.subs/translator])
        ptv-step @(rf/subscribe [::subs/selected-step])
        set-step (fn [i _e]
                   (rf/dispatch [::events/set-step i]))

        org-id           (<== [::subs/selected-org-id])
        services-done? (empty? (<== [::subs/service-candidates org-id]))
        site-setup-done? (<== [::subs/sports-site-setup-done org-id])]
    [:> Stack
     [:> Stepper
      {:nonLinear true
       :activeStep ptv-step
       :sx (fn [theme]
             #js {:mt 2
                 :mb 4
                 ".Mui-completed" #js {:fontWeight "500 !important"}
                 ".Mui-completed.Mui-active" #js {:fontWeight "700 !important"}
                 ".Mui-active" #js {:fontWeight "700 !important"
                                    :color "primary.main"}
                  ".MuiStepIcon-root.Mui-active" #js {
                                                      :fill (.. theme -palette -secondary -main)}})}
      [:> Step
       {:key "1"
        :completed true}
       [:> StepButton
        {:color "inherit"
         :onClick (partial set-step 0)}
        (str "1. Valitse liikuntapaikat")]]
      [:> Step
       {:key "2"
        :completed services-done?}
       [:> StepButton
        {:color "inherit"
         :onClick (partial set-step 1)}
        (str "2. " (tr :ptv.tools.generate-services/headline))]]
      [:> Step
       {:key "3"
        :completed site-setup-done?}
       [:> StepButton
        {:color "inherit"
         :onClick (partial set-step 2)}
        (str "3. " (tr :ptv.wizard/integrate-service-locations))]]]
     (case ptv-step
       0 ($ set-types)
       1 [create-services]
       2 [:f> integrate-service-locations])]))

(defn dialog
  [{:keys [tr]}]
  (let [open?        (<== [::subs/dialog-open?])
        selected-tab (<== [::subs/selected-tab])
        loading?     (<== [::subs/loading-from-ptv?])
        org-id       (<== [::subs/selected-org-id])
        org-data     (<== [::subs/selected-org-data org-id])
        sites        (<== [::subs/sports-sites org-id])

        on-close #(==> [::events/close-dialog])]

    [:> Dialog
     {:open         open?
      ;; FIXME: This isn't implemented, what should this do?
      ; :on-save       #(==> [::events/save sites])
      ; :save-enabled? true
      ; :save-label    (tr :actions/save)
      :fullScreen true
      :max-width     "xl"}

     [:> AppBar
      {:sx #js {:position "relative"}}
      [:> Toolbar
       [:> IconButton
        {:edge "start"
         :onClick on-close
         :color "inherit"}
        [:> CloseIcon]]
       [:> Typography
        {:variant "h6"
         :component "div"
         :sx #js {:ml 2 :flex 1}}
        (tr :ptv/tooltip)]]]

     [:> DialogContent
      {:sx #js {:display "flex"
                :flexDirection "column"
                :gap 2}}

      [org-selector {:label (tr :ptv.actions/select-org)}]

      (when loading?
        [mui/stack
         {:direction "row"
          :spacing 2
          :alignItems "center"
          :justifyContent "center"}
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
          [mui/tab {:value "sports-sites" :label (tr :ptv/sports-sites)}]]

         (when (= selected-tab "wizard")
           [wizard])

         (when (= selected-tab "services")
           [services])

         (when (= selected-tab "sports-sites")
           [table])

         ])]]))

;; Juhan kommentit wizardiin
;; - mahdollisuus valita kielet
;; - mahdollisuus linkittää olemassa oleva palvelu
;; - pakotusmekanismi "olen lukenut kuvaukset"
;; - ohjaavammat ohjetekstit
