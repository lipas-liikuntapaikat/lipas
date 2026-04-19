(ns lipas.ui.ptv.components
  "Shared PTV UI components to avoid circular dependencies"
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

(defn ptv-link-field
  "Shows PTV items as links with an edit button to switch to selector mode.
   When an item has no :url, renders as plain text instead of a link.
   Set :can-edit? false to hide the edit affordance (e.g. single-org case).
   Props: :label, :items [{:id :name :url}], :editing?, :on-edit, :on-cancel,
          :selector-component, :tooltip, :tr, :can-edit? (default true)"
  [{:keys [label items editing? on-edit on-cancel selector-component tooltip tr can-edit?]
    :or {can-edit? true}}]
  (if (and (seq items) (not editing?))
    [:> Stack {:spacing 0.5}
     [:> Stack {:direction "row" :align-items "center" :spacing 0.5}
      [:> Typography {:variant "caption" :color "text.secondary"} label]
      (when can-edit?
        [:> Tooltip {:title (or tooltip "")}
         [:> IconButton {:size "small" :on-click on-edit
                         :sx #js {:p 0}}
          [:> Icon {:sx #js {:fontSize "1rem"}} "edit"]]])]
     (for [{:keys [id name url]} items]
       ^{:key id}
       (if url
         [:> Link {:href url :target "_blank" :variant "body2"} (or name id)]
         [:> Typography {:variant "body2"} (or name id)]))]
    [:> Stack {:spacing 0.5}
     selector-component
     (when (and editing? (seq items))
       [:> Button {:size "small" :variant "text"
                   :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                   :on-click on-cancel}
        (tr :actions/cancel)])]))

(r/defc service-location-preview
  "Preview component showing how a sports site will appear in PTV as a service location"
  [{:keys [org-id lipas-id]}]
  (let [preview @(rf/subscribe [::subs/service-location-preview org-id lipas-id])
        sports-sites @(rf/subscribe [::subs/sports-sites org-id])
        site-data (some #(when (= lipas-id (:lipas-id %)) %) sports-sites)
        synced? (= :ok (:sync-status site-data))
        [expanded? set-expanded] (hooks/use-state false)

        row (fn [{:keys [label value tooltip]}]
              [:> Tooltip {:title (or tooltip "Selite puuttuu")}
               [:> TableRow
                [:> TableCell [:> Typography {:variant "caption"} label]]
                [:> TableCell [:> Typography value]]]])
        join (fn [coll] (when (seq coll) (str/join ", " coll)))
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

    [:> Stack {:spacing 2}
     ;; Collapsible header with expand/collapse button
     [:> Paper
      {:sx #js{:p 2 :bgcolor mui/gray3 :cursor "pointer"}
       :onClick #(set-expanded (not expanded?))}
      [:> Stack
       {:direction "row"
        :alignItems "center"
        :justifyContent "space-between"}
       [:> Typography
        {:variant "subtitle2" :fontWeight "medium"}
        "PTV-integraation sisältö"]
       [:> IconButton
        {:size "small"
         :sx #js{:transform (if expanded? "rotate(180deg)" "rotate(0deg)")
                 :transition "transform 0.2s"}}
        [:> ExpandMoreIcon]]]]

     ;; Collapsible content
     [:> Collapse {:in expanded? :timeout "auto" :unmountOnExit true}
      [:> Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
       [:> Typography
        {:variant "body2" :mb 2}
        "Näyttää kuinka liikuntapaikka on integroitu PTV-järjestelmään. Vie hiiren osoitin rivin päälle nähdäksesi tiedon alkuperän."]]

      [:> Table {:variant "dense"}
       [:> TableHead
        [:> TableRow
         [:> TableCell "PTV-tietue"]
         [:> TableCell "Arvo"]]]

       [:> TableBody
        (row {:label "Tila"
              :value (if synced?
                       (:publishingStatus preview)
                       (str (:publishingStatus preview) " (esikatselu, ei vielä viety)"))
              :tooltip "Integraation PTV:hen viemät kohteet julkaistaan automaattisesti. Vedokseksi vieminen ei ole tuettu."})

        (row {:label "Kielet"
              :value (join (:languages preview))
              :tooltip "Kielet, joilla kunta on ilmoittanut haluavansa kuvata palvelut ja palvelupaikat kun integraatio on otettu käyttöön ensimmäisen kerran. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."})

        (row {:label "Nimi suomeksi"
              :value (get-name "Name" "fi")
              :tooltip "Liikuntapaikan nimi Lipaksessa."})

        (row {:label "Nimi ruotsiksi"
              :value (get-name "Name" "sv")
              :tooltip (str "Liikuntapaikan nimi ruotsiksi Lipaksessa."
                            " "
                            lang-disclaimer)})

        (row {:label "Nimi englanniksi"
              :value (get-name "Name" "en")
              :tooltip (str "Liikuntapaikan nimi englanniksi Lipaksessa."
                            " "
                            lang-disclaimer)})

        (row {:label "Vaihtoehtoinen nimi"
              :value (get-name "AlternativeName" "fi")
              :tooltip "Liikuntapaikan markkinointinimi Lipaksessa."})

        (row {:label "Ensisijainen nimitieto"
              :value (->> preview :displayNameType first :type)
              :tooltip "Tämä arvo on aina \"Name\"."})

        (row {:label "Maa"
              :value (->> preview :addresses first :country)
              :tooltip "Tämä arvo on aina \"FI\""})

        (row {:label "Katuosoite"
              :value (-> preview :addresses first :streetAddress :street first :value)
              :tooltip "Liikuntapaikan katuosoite Lipaksessa."})

        (row {:label "Postinumero"
              :value (-> preview :addresses first :streetAddress :postalCode)
              :tooltip "Liikuntapaikan postinumero Lipaksessa."})

        (row {:label "Koordinaatit"
              :value (str "(E) "
                          (-> preview :addresses first :streetAddress :longitude)
                          " (N) "
                          (-> preview :addresses first :streetAddress :latitude))
              :tooltip "Liikuntapaikan geometrian koordinaatit Lipaksessa muutettuna PTV:n käyttämään ETRS-TM35FIN koordinaatistoon. Aluemaisissa liikuntapaikoissa valitaan keskipiste, reiteissä aloituspiste."})

        (row {:label "Sähköpostiosoitteet"
              :value (->> preview :emails (map :value) join)
              :tooltip "Liikuntapaikan sähköpostiosoite Lipaksessa."})

        (row {:label "Web-sivut"
              :value (->> preview :webPages (map :url) join)
              :tooltip "Liikuntapaikan www-osoite Lipaksessa."})

        (row {:label "Puhelinnumerot"
              :value (->> preview
                          :phoneNumbers
                          (map (fn [{:keys [number prefixNumber isFinnishServiceNumber]}]
                                 (str prefixNumber " " number (when isFinnishServiceNumber " (suomalainen palvelunumero)"))))
                          join)
              :tooltip "Liikuntapaikan puhelinnumero Lipaksessa."})

        (row {:label "Tiivistelmä suomeksi"
              :value (get-desc "Summary" "fi")
              :tooltip tt-summary})

        (row {:label "Tiivistelmä ruotsiksi"
              :value (get-desc "Summary" "sv")
              :tooltip (str tt-summary " " lang-disclaimer)})

        (row {:label "Tiivistelmä englanniksi"
              :value (get-desc "Summary" "en")
              :tooltip (str tt-summary " " lang-disclaimer)})

        (row {:label "Kuvaus suomeksi"
              :value (get-desc "Description" "fi")
              :tooltip tt-description})

        (row {:label "Kuvaus ruotsiksi"
              :value (get-desc "Description" "sv")
              :tooltip (str tt-description " " lang-disclaimer)})

        (row {:label "Kuvaus englanniksi"
              :value (get-desc "Description" "en")
              :tooltip (str tt-description " " lang-disclaimer)})

        (row {:label "Organisaation ID"
              :value (:organizationId preview)
              :tooltip "Organisaatio (kunta) joka käyttää integraatiota."})

        (row {:label "Palveluiden ID:t"
              :value (join (:services preview))
              :tooltip "Palvelut joihin palvelupaikka liitetään PTV:ssä."})]]]]))

(defn add-service-create-form
  "Form for creating a new PTV service for a sub-category.
   Props:
   - :org-id (required) — PTV org id
   - :on-cancel (required) — close handler
   - :locked-sub-category-id (optional) — if set, pre-select this sub-category
     and hide the selector. Used from site context where the site type dictates
     which service is needed.
   - :lipas-id (optional) — when set, the created service-id is auto-added to
     that site's :ptv :service-ids on success."
  [{:keys [org-id on-cancel locked-sub-category-id lipas-id]}]
  (r/with-let [source-id (r/atom (when locked-sub-category-id
                                   (ptv-data/->service-source-id org-id locked-sub-category-id)))
               selected-tab (r/atom :fi)
               editing-sub-category? (r/atom false)
               _seed-locked
               (when locked-sub-category-id
                 (let [sid (ptv-data/->service-source-id org-id locked-sub-category-id)
                       sub-cat (get types/sub-categories locked-sub-category-id)]
                   (rf/dispatch [::events/set-manual-services org-id [sid]
                                 [{:source-id sid
                                   :sub-category-id locked-sub-category-id
                                   :sub-category (-> sub-cat :name :fi)}]])))]
    (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
          missing-subcategories @(rf/subscribe [::subs/missing-subcategories org-id])
          org-languages @(rf/subscribe [::subs/org-languages org-id])
          descriptions @(rf/subscribe [::subs/service-candidate-descriptions org-id])
          desc (when @source-id (get descriptions @source-id))
          generating? @(rf/subscribe [::subs/generating-service-descriptions? @source-id])
          syncing? (when @source-id @(rf/subscribe [::subs/syncing-service? @source-id]))
          valid? (and @source-id
                      (some-> desc :summary :fi count (> 5))
                      (some-> desc :description :fi count (> 5))
                      (some-> desc :user-instruction :fi count (> 5)))]
      [:> Stack {:spacing 2}
       [:> Typography {:variant "subtitle1" :fontWeight "medium"}
        (tr :ptv.service/create-new)]
       [:> Grid {:container true :spacing 3}

        ;; Left column: selector and action buttons
        [:> Grid {:item true :xs 12 :md 5}
         [:> Stack {:spacing 2}
          (let [selector [controls/services-selector
                          {:options missing-subcategories
                           :multiple false
                           :value @source-id
                           :value-fn :source-id
                           :on-change (fn [v]
                                        (reset! source-id v)
                                        (when v
                                          (rf/dispatch [::events/set-manual-services org-id [v] missing-subcategories]))
                                        (reset! editing-sub-category? false))
                           :label (tr :ptv.service/select-sub-category)}]]
            (if locked-sub-category-id
              ;; Site context: pre-filled from the site's type. Edit button lets
              ;; the user swap in a different sub-category if needed.
              [ptv-link-field
               {:tr tr
                :label (tr :ptv.service/select-sub-category)
                :items [{:id (or @source-id locked-sub-category-id)
                         :name (get-in types/sub-categories
                                       [(ptv-data/parse-service-source-id (or @source-id ""))
                                        :name (tr)]
                                       (get-in types/sub-categories
                                               [locked-sub-category-id :name (tr)]
                                               (get-in types/sub-categories
                                                       [locked-sub-category-id :name :fi])))}]
                :editing? @editing-sub-category?
                :on-edit #(reset! editing-sub-category? true)
                :on-cancel #(reset! editing-sub-category? false)
                :selector-component selector}]
              selector))

          (when @source-id
            [:<>
             ;; AI generate + translate buttons
             (let [from-lang @selected-tab
                   other-langs (disj (set (map keyword org-languages)) from-lang)
                   has-text? (and (seq (get-in desc [:summary from-lang]))
                                  (seq (get-in desc [:description from-lang])))]
               [:> Stack {:direction "row" :spacing 1 :flex-wrap "wrap" :align-items "flex-start"}
                [:> Button
                 {:variant "outlined" :size "small" :disabled generating?
                  :sx #js {:textTransform "none"}
                  :startIcon (r/as-element
                              (if generating?
                                [:> CircularProgress {:size 16 :color "inherit"}]
                                [:> Icon "auto_fix_high"]))
                  :on-click #(rf/dispatch [::events/generate-service-descriptions
                                           org-id @source-id nil [] []])}
                 (tr :ptv.actions/generate-with-ai)]
                (when (> (count org-languages) 1)
                  [:> Tooltip {:title (tr :ptv.wizard/translate-to-other-langs-tooltip)}
                   [:> Button
                    {:size "small" :variant "outlined"
                     :disabled (or generating? (not has-text?))
                     :startIcon (r/as-element
                                 (if generating?
                                   [:> CircularProgress {:size 16 :color "inherit"}]
                                   [:> Icon "translate"]))
                     :sx #js {:textTransform "none"}
                     :on-click #(rf/dispatch [::events/translate-service-candidate-with-texts
                                              @source-id from-lang other-langs
                                              {:summary (get-in desc [:summary from-lang])
                                               :description (get-in desc [:description from-lang])
                                               :user-instruction (get-in desc [:user-instruction from-lang])}])}
                    (str (tr :ptv.wizard/translate-to-other-langs) " ("
                         (str/join ", " (map (comp str/upper-case name) (sort other-langs))) ")")]])])

             [:> Button
              {:variant "contained" :color "secondary" :size "small" :full-width true
               :disabled (or syncing? (not valid?))
               :sx #js {:textTransform "none"}
               :startIcon (r/as-element
                           (if syncing?
                             [:> CircularProgress {:size 16 :color "inherit"}]
                             [:> Icon "ios_share"]))
               :on-click #(let [data (merge {:org-id org-id
                                             :source-id @source-id
                                             :sub-category-id (ptv-data/parse-service-source-id @source-id)
                                             :languages org-languages}
                                            desc)]
                            (rf/dispatch [::events/create-ptv-service
                                          org-id @source-id data [] [] lipas-id])
                            (on-cancel))}
              (tr :ptv.wizard/export-services-to-ptv)]])

          [:> Button
           {:size "small" :variant "text"
            :sx #js {:textTransform "none" :alignSelf "flex-start"}
            :on-click on-cancel}
           (tr :actions/cancel)]]]

        ;; Right column: language tabs + text fields
        (when @source-id
          [:> Grid {:item true :xs 12 :md 7}
           [:> Stack {:spacing 2}
            [controls/lang-selector
             {:value @selected-tab
              :on-change #(reset! selected-tab %)
              :enabled-languages (set org-languages)}]

            (let [v (or (get-in desc [:summary @selected-tab]) "")]
              [text-fields/text-field
               {:on-change #(rf/dispatch [::events/set-service-candidate-summary @source-id @selected-tab %])
                :multiline true :variant "outlined" :label (tr :ptv/summary) :value v
                :helperText (str (count v) "/150") :error (> (count v) 150)}])

            (let [v (or (get-in desc [:description @selected-tab]) "")]
              [text-fields/text-field
               {:on-change #(rf/dispatch [::events/set-service-candidate-description @source-id @selected-tab %])
                :variant "outlined" :rows 5 :multiline true :label (tr :ptv/description) :value v
                :helperText (str (count v) "/2500") :error (> (count v) 2500)}])

            (let [v (or (get-in desc [:user-instruction @selected-tab]) "")]
              [text-fields/text-field
               {:on-change #(rf/dispatch [::events/set-service-candidate-user-instruction @source-id @selected-tab %])
                :variant "outlined" :rows 3 :multiline true :label (tr :ptv/user-instruction) :value v
                :helperText (str (count v) "/2500") :error (> (count v) 2500)}])]])]])))

(defn add-service-link-form
  "Form for linking (adopting) an existing PTV service into LIPAS management.
   Props:
   - :org-id (required)
   - :on-cancel (required)
   - :lipas-id (optional) — when set, the linked service-id is auto-added to
     that site's :ptv :service-ids on success."
  [{:keys [org-id on-cancel lipas-id]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        all-services @(rf/subscribe [::subs/services org-id])
        non-lipas-services (remove #(some-> (:source-id %) (str/starts-with? "lipas-")) all-services)
        org-languages @(rf/subscribe [::subs/org-languages org-id])]
    [:> Stack {:spacing 2}
     [:> Typography {:variant "subtitle1" :fontWeight "medium"}
      (tr :ptv.service/link-existing)]
     [controls/services-selector
      {:options non-lipas-services
       :multiple false
       :value nil
       :value-fn :service-id
       :on-change (fn [service-id]
                    (when service-id
                      (let [service (some #(when (= (:service-id %) service-id) %) all-services)
                            data (merge {:org-id org-id
                                         :service-id service-id
                                         :languages org-languages}
                                        (select-keys service [:summary :description :user-instruction]))]
                        (rf/dispatch [::events/create-ptv-service org-id nil data [] [] lipas-id])
                        (on-cancel))))
       :label (tr :ptv.service/select-service)}]
     [:> Button
      {:size "small" :variant "text" :sx #js {:textTransform "none"}
       :on-click on-cancel}
      (tr :actions/cancel)]]))

(defn add-service-panel
  "Two-button entry point for adding a PTV service. Default state shows
   \"Create new\" / \"Link existing\"; click switches to the corresponding form.
   Props:
   - :org-id (required)
   - :locked-sub-category-id (optional) — passed to add-service-create-form
   - :lipas-id (optional) — passed to both forms for auto-attach"
  [{:keys [org-id locked-sub-category-id lipas-id]}]
  (r/with-let [mode (r/atom nil)]
    (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
      [:> Stack {:spacing 2 :sx #js {:p 2}}
       (case @mode
         :create [add-service-create-form {:org-id org-id
                                           :locked-sub-category-id locked-sub-category-id
                                           :lipas-id lipas-id
                                           :on-cancel #(reset! mode nil)}]
         :link   [add-service-link-form {:org-id org-id
                                         :lipas-id lipas-id
                                         :on-cancel #(reset! mode nil)}]
         [:> Stack {:direction "row" :spacing 1}
          [:> Button
           {:variant "outlined" :size "small" :sx #js {:textTransform "none"}
            :startIcon (r/as-element [:> Icon "add"])
            :on-click #(reset! mode :create)}
           (tr :ptv.service/create-new)]
          [:> Button
           {:variant "outlined" :size "small" :sx #js {:textTransform "none"}
            :startIcon (r/as-element [:> Icon "link"])
            :on-click #(reset! mode :link)}
           (tr :ptv.service/link-existing)]])])))
