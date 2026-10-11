(ns lipas.ui.admin.ptv-adoption.views
  "Admin tab: PTV integration adoption over time and per municipality.
  Counts each site from its first revision carrying PTV data."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/TableSortLabel$default" :as TableSortLabel]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            ["recharts/es6/cartesian/Bar" :refer [Bar]]
            ["recharts/es6/cartesian/CartesianGrid" :refer [CartesianGrid]]
            ["recharts/es6/cartesian/Line" :refer [Line]]
            ["recharts/es6/cartesian/XAxis" :refer [XAxis]]
            ["recharts/es6/cartesian/YAxis" :refer [YAxis]]
            ["recharts/es6/chart/BarChart" :refer [BarChart]]
            ["recharts/es6/chart/LineChart" :refer [LineChart]]
            ["recharts/es6/component/ResponsiveContainer" :refer [ResponsiveContainer]]
            ["recharts/es6/component/Tooltip" :refer [Tooltip] :rename {Tooltip ChartTooltip}]
            [clojure.string :as str]
            [lipas.ui.admin.ptv-adoption.events :as events]
            [lipas.ui.admin.ptv-adoption.subs :as subs]
            [lipas.ui.utils :as ui-utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]
            [reitit.frontend.easy :as rfe]))

;; Single-series charts: one hue (the jobs view's validated blue), no legend.
(def series-color "#2a78d6")

(def chart-font
  {:fontFamily "lato" :fontSize 12})

(r/defc stat-tile [{:keys [value label caption tooltip on-click alert?]}]
  ;; height 100%: every tile fills its grid row, so a wrapping caption
  ;; doesn't make one box taller than the rest
  (let [tile [:> Paper {:sx #js{:p 2
                                :bgcolor (if alert? "#fdecea" "#f5f5f5")
                                :height "100%"
                                :boxSizing "border-box"
                                :cursor (when on-click "pointer")}
                        :on-click on-click}
              [:> Typography {:variant "h4" :color (when alert? "error")} (str value)]
              [:> Typography {:color "textSecondary"} label]
              [:> Typography {:variant "caption" :color "textSecondary"}
               (or caption " ")]]]
    [:> Grid {:size {:xs 12 :sm 6 :md 3}}
     (if tooltip
       [:> Tooltip {:title tooltip} tile]
       tile)]))

(defn- join-parts [& parts]
  (not-empty (str/join " · " (remove nil? parts))))

(r/defc summary-tiles
  "Four current-state numbers that read as one sentence: N sites in PTV from N
  municipalities, N need attention, N new this month. History is in the charts."
  [{:keys [totals on-attention]}]
  (let [{:keys [in-ptv in-ptv-sync-off municipalities attention attention-never-reached
                attention-failing warnings new-this-month]} totals]
    [:> Grid {:container true :spacing 2 :sx #js{:mb 2}}
     [stat-tile {:value in-ptv
                 :label "Liikuntapaikkaa PTV:ssä"
                 :caption (when (pos? in-ptv-sync-off)
                            (str in-ptv-sync-off " ei enää päivity LIPASista"))
                 :tooltip (str "Liikuntapaikat, joiden palvelupaikka on julkaistu PTV:ssä LIPASin viimeisimmän synkronoinnin mukaan. "
                               "Luvut koskevat palvelupaikkoja, eivät PTV-palveluita. "
                               "\"Ei enää päivity\" ovat julkaistuja paikkoja, joiden synkronointi on pois päältä.")}]
     [stat-tile {:value municipalities
                 :label "Kuntaa"
                 :tooltip "Kunnat, joilla on vähintään yksi liikuntapaikka julkaistuna PTV:ssä."}]
     [stat-tile {:value attention
                 :label "Vaatii huomiota"
                 :alert? (pos? attention)
                 :on-click on-attention
                 :caption (join-parts
                            (when (pos? attention-never-reached)
                              (str attention-never-reached " ei koskaan viety"))
                            (when (pos? attention-failing)
                              (str attention-failing " päivitys epäonnistui"))
                            (when (pos? warnings)
                              (str "+ " warnings " huomautusta")))
                 :tooltip (str "Liikuntapaikat, joiden synkronointi on päällä, mutta jotka eivät ole PTV:ssä ajan tasalla: "
                               "vienti ei ole koskaan onnistunut tai viimeisin päivitys epäonnistui. "
                               "Huomautukset ovat PTV:ssä olevia paikkoja, joiden tilaa kannattaa tarkistaa. "
                               "Napsauta nähdäksesi syyt.")}]
     [stat-tile {:value new-this-month
                 :label "Uusia PTV:ssä tässä kuussa"
                 :tooltip "Liikuntapaikat, jotka julkaistiin PTV:ssä ensimmäisen kerran kuluvana kuukautena."}]]))

(defn- month-tooltip
  "Recharts custom tooltip content: month, the value, and for the monthly
  chart the per-municipality breakdown."
  [^js props value-label value-key]
  (when-let [^js row (and (.-active props) (some-> (.-payload props) (aget 0) .-payload))]
    (r/as-element
      [:> Paper {:sx #js{:p 1 :maxWidth 260}}
       [:> Typography {:variant "subtitle2"} (unchecked-get row "month")]
       [:> Typography {:variant "body2"}
        (str value-label ": " (unchecked-get row value-key))]
       (for [d (unchecked-get row "details")]
         ^{:key d}
         [:> Typography {:variant "caption" :component "div" :color "textSecondary"} d])])))

(r/defc monthly-chart [{:keys [data]}]
  [:> ResponsiveContainer {:width "100%" :height 260}
   [:> BarChart {:data data :margin #js{:top 8 :right 8 :left 0 :bottom 0}}
    [:> CartesianGrid {:vertical false :stroke "#e1e0d9"}]
    [:> XAxis {:dataKey :month :tick chart-font :interval "preserveStartEnd"}]
    [:> YAxis {:tick chart-font :allowDecimals false}]
    [:> ChartTooltip {:content #(month-tooltip % "Uusia" "new")
                      :cursor #js{:fill "rgba(0,0,0,0.04)"}}]
    [:> Bar {:dataKey :new :name "Uusia" :fill series-color
             :radius #js[4 4 0 0] :maxBarSize 24}]]])

(r/defc cumulative-chart [{:keys [data]}]
  [:> ResponsiveContainer {:width "100%" :height 260}
   [:> LineChart {:data data :margin #js{:top 8 :right 8 :left 0 :bottom 0}}
    [:> CartesianGrid {:vertical false :stroke "#e1e0d9"}]
    [:> XAxis {:dataKey :month :tick chart-font :interval "preserveStartEnd"}]
    [:> YAxis {:tick chart-font :allowDecimals false}]
    [:> ChartTooltip {:content #(month-tooltip % "Yhteensä" "total")}]
    [:> Line {:dataKey :total :name "Yhteensä" :type "stepAfter"
              :stroke series-color :strokeWidth 2 :dot false
              :activeDot #js{:r 4}}]]])

(r/defc municipalities-table [{:keys [municipalities]}]
  [:> Table {:size "small"}
   [:> TableHead
    [:> TableRow
     [:> TableCell "Kunta"]
     [:> TableCell {:align "right"} "PTV:ssä"]
     [:> TableCell {:align "right"} "Vaatii huomiota"]
     [:> TableCell {:align "right"} "Huomautuksia"]
     [:> TableCell {:align "right"} "Synkronointi päällä"]
     [:> TableCell {:align "right"} "Poistettu PTV:stä"]
     [:> TableCell "Ensimmäinen"]
     [:> TableCell "Viimeisin"]]]
   [:> TableBody
    (for [{:keys [city-code municipality in-ptv attention warnings sync-enabled deleted
                  first-month latest-month]} municipalities]
      ^{:key (str city-code)}
      [:> TableRow
       [:> TableCell municipality]
       [:> TableCell {:align "right"} in-ptv]
       [:> TableCell {:align "right"}
        (if (pos? attention)
          [:> Typography {:variant "body2" :color "error" :fontWeight 600} attention]
          attention)]
       [:> TableCell {:align "right"} warnings]
       [:> TableCell {:align "right"} sync-enabled]
       [:> TableCell {:align "right"} deleted]
       [:> TableCell (or first-month "–")]
       [:> TableCell (or latest-month "–")]])]])

(r/defc users-filters []
  (let [{:keys [search source not-member?]} @(rf/subscribe [::subs/users-filter])
        set-filter (fn [k v] (rf/dispatch [::events/set-users-filter k v]))]
    [:> Stack {:direction "row" :spacing 2 :alignItems "center"
               :sx #js{:mb 2 :flexWrap "wrap" :rowGap 1}}
     [:> TextField {:size "small"
                    :label "Hae"
                    :placeholder "Nimi, sähköposti, kunta tai organisaatio"
                    :value (or search "")
                    :on-change #(set-filter :search (.. % -target -value))
                    :sx #js{:minWidth 320}}]
     [:> TextField {:size "small"
                    :select true
                    :label "Oikeuden lähde"
                    :value (or source "all")
                    :on-change #(set-filter :source (.. % -target -value))
                    :sx #js{:minWidth 220}}
      [:> MenuItem {:value "all"} "Kaikki"]
      [:> MenuItem {:value "org"} "Organisaation kautta"]
      [:> MenuItem {:value "direct"} "Suora oikeus kuntaan"]]
     [:> FormControlLabel
      {:label "Vain ne, jotka eivät ole kuntansa organisaatiossa"
       :control (r/as-element
                  [:> Switch {:checked (boolean not-member?)
                              :on-change #(set-filter :not-member? (.. % -target -checked))}])}]]))

(r/defc sortable-header [{:keys [column label sort-by sort-dir]}]
  (let [active? (= column sort-by)]
    [:> TableCell {:sortDirection (if active? sort-dir false)}
     [:> TableSortLabel {:active active?
                         :direction (if active? sort-dir "asc")
                         :on-click #(rf/dispatch [::events/sort-users column])}
      label]]))

(r/defc users-table [{:keys [rows]}]
  (let [{:keys [sort-by sort-dir]} @(rf/subscribe [::subs/users-filter])
        sort-props {:sort-by (or sort-by "user") :sort-dir (or sort-dir "asc")}]
    [:> Table {:size "small"}
     [:> TableHead
      [:> TableRow
       [sortable-header (assoc sort-props :column "user" :label "Käyttäjä")]
       [sortable-header (assoc sort-props :column "cities" :label "Kunnat")]
       [sortable-header (assoc sort-props :column "orgs" :label "Organisaatiot")]
       [sortable-header (assoc sort-props :column "source" :label "Oikeuden lähde")]
       [:> TableCell "Toiminnot"]]]
     [:> TableBody
      (for [{:keys [user-id name username email cities orgs org member? direct? via-org?] :as row} rows]
        ^{:key (str user-id "-" (or (:id org) (first cities)))}
        [:> TableRow
         [:> TableCell
          [:> Typography {:variant "body2"} (if (seq name) name username)]
          [:> Typography {:variant "caption" :color "textSecondary"} email]]
         [:> TableCell (str/join ", " cities)]
         [:> TableCell
          (if (seq orgs)
            (str/join ", " (map :name orgs))
            [:> Typography {:variant "body2" :color "textSecondary"} "–"])]
         [:> TableCell
          [:> Stack {:direction "row" :spacing 0.5 :sx #js{:flexWrap "wrap" :rowGap 0.5}}
           (when via-org?
             [:> Tooltip {:title "PTV-oikeudet tulevat organisaation roolipohjasta."}
              [:> Chip {:size "small" :label "Organisaatio"}]])
           (when direct?
             [:> Tooltip {:title "PTV-oikeudet on annettu suoraan käyttäjälle kuntaan (vanha tapa)."}
              [:> Chip {:size "small" :variant "outlined" :label "Suora (kunta)"}]])]]
         [:> TableCell
          ;; Only for the org the user's PTV municipality points to (no
          ;; covering org, or several and no membership: nothing to offer).
          ;; Not a member yet → add (silently); a member → its member
          ;; management, where the org-admin picks roles.
          (when org
            (if member?
              [:> Button {:size "small"
                          :variant "text"
                          :href (rfe/href :lipas.ui.routes/org
                                          {:org-id (:id org)}
                                          {:tab "members"})
                          :sx #js{:textTransform "none"}}
               (str "Hallitse jäseniä: " (:name org))]
              [:> Button {:size "small"
                          :variant "outlined"
                          :disabled @(rf/subscribe [::subs/adding? user-id])
                          ;; Confirmed first: changes membership, no undo here
                          :on-click #(rf/dispatch
                                       [:lipas.ui.events/confirm
                                        (str "Lisätäänkö " (if (seq name) name username)
                                             " jäseneksi organisaatioon \"" (:name org) "\"? "
                                             "Jäsenyys ei anna rooleja, eikä käyttäjälle lähetetä sähköpostia.")
                                        (fn [] (rf/dispatch [::events/add-to-org row]))])
                          :sx #js{:textTransform "none"}}
               (str "Lisää jäseneksi: " (:name org))]))]])]]))

(r/defc users-tab
  "Accounts with PTV rights, by either route: an org role template or a direct
  city-scoped role (the legacy way). Adding a direct-role user to their city's
  org is plain membership — roles stay the org-admin's call."
  []
  (let [rows @(rf/subscribe [::subs/filtered-ptv-users])
        all-rows @(rf/subscribe [::subs/ptv-users])]
    (hooks/use-effect
      (fn []
        (rf/dispatch [::events/fetch-users])
        js/undefined)
      [])
    [:<>
     [:> Typography {:variant "body2" :color "textSecondary" :sx #js{:mb 2}}
      (str "Käyttäjät, joilla on PTV-oikeudet johonkin kuntaan joko organisaation roolipohjan kautta "
           "tai suoraan käyttäjälle annettuna (vanha tapa). Jäseneksi lisääminen ei anna rooleja: "
           "organisaation ylläpitäjä päättää ne Jäsenet-välilehdellä.")]
     [users-filters]
     (if (nil? all-rows)
       [:> LinearProgress]
       [:<>
        [:> Typography {:variant "body2" :sx #js{:mb 1}}
         (str (count rows) " / " (count all-rows) " riviä")]
        (if (empty? rows)
          [:> Typography {:variant "body2"} "Ei hakuehtoja vastaavia käyttäjiä."]
          [users-table {:rows rows}])])]))

(def ^:private reason-info
  "Per lipas.data.ptv/attention reason: chip label, what's wrong, what to do."
  {"no-org" ["PTV-organisaatio puuttuu"
             "Synkronointi on päällä, mutta liikuntapaikalla ei ole PTV-organisaatiota, joten sitä ei voi viedä PTV:hen."
             "Varmista, että kunnalla on PTV-organisaatio. Kun PTV-tiedot tallennetaan seuraavan kerran, organisaatio täydentyy kunnan perusteella."]
   "missing-texts" ["Tekstit puuttuvat"
                    "Suomenkielinen tiivistelmä ja kuvaus puuttuvat, joten liikuntapaikkaa ei voi viedä PTV:hen."
                    "Kunta kirjoittaa tekstit (tai luo ne tekoälyllä) liikuntapaikan PTV-välilehdellä ja tallentaa."]
   "sync-error" ["Vienti epäonnistui"
                 "Viimeisin vienti PTV:hen epäonnistui (virheilmoitus alla)."
                 "Korjaa virheilmoituksen mukainen tieto ja tallenna liikuntapaikka uudelleen. Jos virhe on PTV:n päässä (5xx-virhe), pelkkä uudelleentallennus yleensä riittää."]
   "never-sent" ["Ei viety PTV:hen"
                 "Synkronointi on päällä ja tiedot ovat valmiina, mutta liikuntapaikkaa ei ole viety PTV:hen."
                 "Tallenna liikuntapaikka uudelleen."]
   "published-not-candidate" ["Ei enää PTV-kelpoinen"
                              "LIPASin mukaan palvelupaikka on julkaistu PTV:ssä, vaikka liikuntapaikka ei enää kuulu sinne (esim. merkitty virheelliseksi tai omistaja ei ole kunta). LIPASin tieto voi myös olla vanhentunut."
                              "Tarkista palvelupaikan tila PTV:ssä ja arkistoi se tarvittaessa."]
   "no-service" ["Palvelu puuttuu"
                 "Palvelupaikka on julkaistu PTV:ssä, mutta sitä ei ole liitetty PTV-palveluun."
                 "Valitse palvelu liikuntapaikan PTV-välilehdellä ja tallenna."]
   "published-sync-off" ["Synkronointi pois"
                         "Palvelupaikka on julkaistu PTV:ssä, mutta synkronointi on pois päältä, joten LIPASin muutokset eivät päivity PTV:hen."
                         "Kytke synkronointi päälle tai arkistoi palvelupaikka PTV:ssä."]
   "archived-sync-on" ["Arkistoitu, synkronointi päällä"
                       "Palvelupaikka on arkistoitu PTV:ssä, vaikka synkronointi on päällä ja liikuntapaikka kuuluisi PTV:hen."
                       "Tarkista, pitäisikö palvelupaikka palauttaa PTV:hen; tallennus vie sen uudelleen."]})

(def ^:private publishing-status-label
  {"Published" "Julkaistu" "Deleted" "Poistettu" "Draft" "Luonnos" "Modified" "Muokattu"})

(defn- ptv-channel-url [channel-id]
  (str (if (ui-utils/prod?)
         "https://palvelutietovaranto.suomi.fi"
         "https://palvelutietovaranto.trn.suomi.fi")
       "/channels/serviceLocation/" channel-id))

(defn- site-org
  "The LIPAS org holding the site's PTV config: by the site's PTV org-id, else
  the org covering its municipality."
  [ptv-orgs {:keys [org-id city-code]}]
  (or (some #(when (and org-id (= org-id (get-in % [:ptv-data :org-id]))) %) ptv-orgs)
      (some #(when (some #{city-code} (get-in % [:ptv-data :city-codes])) %) ptv-orgs)))

(r/defc attention-filters []
  (let [{:keys [search severity]} @(rf/subscribe [::subs/attention-filter])
        set-filter (fn [k v] (rf/dispatch [::events/set-attention-filter k v]))]
    [:> Stack {:direction "row" :spacing 2 :alignItems "center"
               :sx #js{:mb 2 :flexWrap "wrap" :rowGap 1}}
     [:> TextField {:size "small"
                    :label "Hae"
                    :placeholder "Liikuntapaikka, kunta tai LIPAS-ID"
                    :value (or search "")
                    :on-change #(set-filter :search (.. % -target -value))
                    :sx #js{:minWidth 320}}]
     [:> TextField {:size "small"
                    :select true
                    :label "Vakavuus"
                    :value (or severity "all")
                    :on-change #(set-filter :severity (.. % -target -value))
                    :sx #js{:minWidth 200}}
      [:> MenuItem {:value "all"} "Kaikki"]
      [:> MenuItem {:value "error"} "Vaatii huomiota"]
      [:> MenuItem {:value "warning"} "Huomautukset"]]]))

(r/defc attention-row [{:keys [row ptv-orgs]}]
  (let [{:keys [lipas-id name municipality sync-enabled? publishing-status last-sync
                service-channel-id error-message reasons]} row
        org (site-org ptv-orgs row)
        ;; "ptv/no-org" (JSON) -> "no-org"
        reason-key #(last (str/split (str (:reason %)) #"/"))]
    [:> TableRow {:sx #js{:verticalAlign "top"}}
     [:> TableCell
      [:> Typography {:variant "body2" :fontWeight 600} (or name (str lipas-id))]
      [:> Typography {:variant "caption" :color "textSecondary"}
       (str municipality " · " lipas-id)]]
     [:> TableCell
      [:> Typography {:variant "body2"}
       (str "Synkronointi " (if sync-enabled? "päällä" "pois"))]
      [:> Typography {:variant "body2"}
       (str "PTV: " (get publishing-status-label publishing-status "ei PTV:ssä"))]
      (when last-sync
        [:> Typography {:variant "caption" :color "textSecondary"}
         (str "Viimeisin vienti " (.toLocaleDateString (js/Date. last-sync) "fi-FI"))])]
     [:> TableCell
      [:> Stack {:spacing 1}
       (for [r reasons
             :let [[label what _] (get reason-info (reason-key r))]]
         ^{:key (reason-key r)}
         [:> Stack {:spacing 0.5 :alignItems "flex-start"}
          [:> Chip {:size "small"
                    :color (if (= "error" (clojure.core/name (:severity r))) "error" "warning")
                    :label (or label (reason-key r))}]
          [:> Typography {:variant "body2"} what]])
       (when error-message
         [:> Typography {:variant "caption" :component "div"
                         :sx #js{:fontFamily "monospace" :wordBreak "break-word"
                                 :bgcolor "#f5f5f5" :p 0.5}}
          error-message])]]
     [:> TableCell
      [:> Stack {:spacing 1 :alignItems "flex-start"}
       (for [r reasons
             :let [[_ _ todo] (get reason-info (reason-key r))]
             :when todo]
         ^{:key (reason-key r)}
         [:> Typography {:variant "body2"} todo])
       [:> Stack {:direction "row" :spacing 1 :sx #js{:flexWrap "wrap" :rowGap 1}}
        [:> Button {:size "small" :variant "outlined" :sx #js{:textTransform "none"}
                    :href (rfe/href :lipas.ui.routes.map/details-view {:lipas-id lipas-id})}
         "Avaa liikuntapaikka"]
        (when service-channel-id
          [:> Button {:size "small" :variant "outlined" :sx #js{:textTransform "none"}
                      :href (ptv-channel-url service-channel-id)
                      :target "_blank" :rel "noopener"}
           "Avaa PTV:ssä"])
        (when org
          [:> Button {:size "small" :variant "outlined" :sx #js{:textTransform "none"}
                      :href (rfe/href :lipas.ui.routes/org {:org-id (:id org)} {:tab "ptv"})}
           (str "Organisaation PTV: " (:name org))])]]]]))

(r/defc attention-tab
  "Sites whose PTV state needs an admin's attention (lipas.data.ptv/attention):
  what's wrong, why, and what to do. Errors first, then warnings."
  []
  (let [rows @(rf/subscribe [::subs/attention-rows])
        stats @(rf/subscribe [::subs/stats])
        ptv-orgs @(rf/subscribe [::subs/ptv-orgs])]
    (hooks/use-effect
      (fn []
        (rf/dispatch [::events/fetch-ptv-orgs])
        js/undefined)
      [])
    [:<>
     [:> Typography {:variant "body2" :color "textSecondary" :sx #js{:mb 2}}
      (str "\"Vaatii huomiota\" -paikkojen synkronointi on päällä, mutta ne eivät ole PTV:ssä ajan tasalla. "
           "Huomautukset ovat PTV:ssä olevia paikkoja, joiden tilaa kannattaa tarkistaa. "
           "Tila perustuu LIPASin viimeisimpään synkronointiin, ei PTV:n reaaliaikaiseen tietoon.")]
     [attention-filters]
     (cond
       (nil? stats) [:> LinearProgress]
       (empty? (:attention stats)) [:> Alert {:severity "success"} "Kaikki kunnossa: yksikään liikuntapaikka ei vaadi huomiota."]
       (empty? rows) [:> Typography {:variant "body2"} "Ei hakuehtoja vastaavia liikuntapaikkoja."]
       :else
       [:<>
        [:> Typography {:variant "body2" :sx #js{:mb 1}}
         (str (count rows) " / " (count (:attention stats)) " liikuntapaikkaa")]
        [:> Table {:size "small"}
         [:> TableHead
          [:> TableRow
           [:> TableCell "Liikuntapaikka"]
           [:> TableCell "Tila"]
           [:> TableCell "Syy"]
           [:> TableCell "Toimenpiteet"]]]
         [:> TableBody
          (for [row rows]
            ^{:key (:lipas-id row)}
            [attention-row {:row row :ptv-orgs ptv-orgs}])]]])]))

(r/defc overview-tab [{:keys [stats chart-data on-attention]}]
  [:<>
   [summary-tiles {:totals (:totals stats) :on-attention on-attention}]
   [:> Grid {:container true :spacing 2 :sx #js{:mb 2}}
    [:> Grid {:size {:xs 12 :lg 6}}
     [:> Typography {:variant "h6"} "Uudet liikuntapaikat PTV:ssä kuukausittain"]
     [monthly-chart {:data chart-data}]]
    [:> Grid {:size {:xs 12 :lg 6}}
     [:> Typography {:variant "h6"} "PTV:hen viedyt liikuntapaikat yhteensä"]
     [cumulative-chart {:data chart-data}]]]
   [:> Typography {:variant "h6"} "Kunnittain"]
   [municipalities-table stats]])

(r/defc ptv-adoption-tab []
  (let [[tab set-tab] (hooks/use-state "overview")
        stats @(rf/subscribe [::subs/stats])
        chart-data @(rf/subscribe [::subs/chart-data])
        loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])
        stats-tab? (contains? #{"overview" "attention"} tab)]
    (hooks/use-effect
      (fn []
        (rf/dispatch [::events/fetch])
        js/undefined)
      [])
    [:> Card {:square true}
     [:> CardHeader
      {:title "PTV-integraation käyttöönotto"
       :subheader (when (= "overview" tab)
                    "Liikuntapaikka lasketaan sen kuukauden lukuihin, jolloin se julkaistiin ensimmäisen kerran PTV:ssä.")
       :action (r/as-element
                 [:> Stack {:alignItems "flex-end"}
                  [:> Button {:on-click #(rf/dispatch (if stats-tab?
                                                        [::events/fetch]
                                                        [::events/fetch-users]))
                              :disabled loading?}
                   "Päivitä"]
                  (when-let [t (and stats-tab? (:generated-at stats))]
                    [:> Typography {:variant "caption" :color "textSecondary"}
                     (str "Päivitetty " (.toLocaleString (js/Date. t) "fi-FI"))])])}]
     [:> CardContent
      [:> Tabs {:value tab
                :on-change (fn [_e v] (set-tab v))
                :sx #js{:mb 2}}
       [:> Tab {:value "overview" :label "Yleiskatsaus"}]
       [:> Tab {:value "attention"
                :label (if-let [n (get-in stats [:totals :attention])]
                         (str "Vaatii huomiota (" n ")")
                         "Vaatii huomiota")}]
       [:> Tab {:value "users" :label "Käyttäjät"}]]
      (when (and stats-tab? loading?)
        [:> LinearProgress {:sx #js{:mb 2}}])
      (when (and stats-tab? error)
        [:> Alert {:severity "error" :sx #js{:mb 2}} (str error)])
      (case tab
        "users" [users-tab]
        "attention" [attention-tab]
        (when stats
          [overview-tab {:stats stats
                         :chart-data chart-data
                         :on-attention #(set-tab "attention")}]))]]))
