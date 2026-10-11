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
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]
            [reitit.frontend.easy :as rfe]))

;; Single-series charts: one hue (the jobs view's validated blue), no legend.
(def series-color "#2a78d6")

(def chart-font
  {:fontFamily "lato" :fontSize 12})

(r/defc stat-tile [{:keys [value label caption tooltip]}]
  ;; height 100%: every tile fills its grid row, so a wrapping caption
  ;; doesn't make one box taller than the rest
  (let [tile [:> Paper {:sx #js{:p 2 :bgcolor "#f5f5f5" :height "100%" :boxSizing "border-box"}}
              [:> Typography {:variant "h4"} (str value)]
              [:> Typography {:color "textSecondary"} label]
              [:> Typography {:variant "caption" :color "textSecondary"}
               (or caption " ")]]]
    [:> Grid {:size {:xs 12 :sm 6 :md 2.4}}
     (if tooltip
       [:> Tooltip {:title tooltip} tile]
       tile)]))

(r/defc summary-tiles [{:keys [totals monthly]}]
  (let [this-month (peek monthly)]
    [:> Grid {:container true :spacing 2 :sx #js{:mb 2}}
     [stat-tile {:value (:sites totals)
                 :label "Liikuntapaikkaa"
                 :caption "integroitu elinkaarensa aikana"
                 :tooltip (str "Kaikki liikuntapaikat, joille on joskus tallennettu PTV-tiedot. "
                               "Sisältää myös paikat, joiden synkronointi on myöhemmin kytketty pois.")}]
     [stat-tile {:value (:municipalities totals)
                 :label "Kuntaa"
                 :tooltip "Kunnat, joissa vähintään yksi liikuntapaikka on integroitu elinkaarensa aikana."}]
     [stat-tile {:value (:sync-enabled totals)
                 :label "Synkronointi päällä"
                 :caption (when (pos? (:sync-pending totals))
                            (str (:sync-pending totals) " ei vielä PTV:ssä"))
                 :tooltip (str "Liikuntapaikat, joiden PTV-synkronointi on nyt päällä. "
                               "\"Ei vielä PTV:ssä\" ovat paikkoja, joille ei ole koskaan luotu palvelupaikkaa PTV:hen, "
                               "esimerkiksi koska tietoja puuttuu tai vienti epäonnistui.")}]
     [stat-tile {:value (:published totals)
                 :label "Julkaistu PTV:ssä"
                 :caption (when (pos? (:deleted totals))
                            (str (:deleted totals) " poistettu PTV:stä"))
                 ;; publishing status is what PTV returned at the last sync,
                 ;; not a live PTV read
                 :tooltip (str "Liikuntapaikat, joiden palvelupaikka on julkaistu PTV:ssä LIPASin viimeisimmän synkronoinnin mukaan. "
                               "\"Poistettu PTV:stä\" tarkoittaa, että palvelupaikka on arkistoitu PTV:ssä. "
                               "Luvut koskevat palvelupaikkoja, eivät PTV-palveluita.")}]
     [stat-tile {:value (or (:new this-month) 0)
                 :label "Uusia tässä kuussa"
                 :caption (:month this-month)
                 :tooltip "Liikuntapaikat, joille PTV-tiedot tallennettiin ensimmäisen kerran kuluvana kuukautena."}]]))

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
     [:> TableCell {:align "right"} "Liikuntapaikkoja"]
     [:> TableCell {:align "right"} "Synkronointi päällä"]
     [:> TableCell {:align "right"} "Julkaistu"]
     [:> TableCell {:align "right"} "Poistettu PTV:stä"]
     [:> TableCell "Ensimmäinen"]
     [:> TableCell "Viimeisin"]]]
   [:> TableBody
    (for [{:keys [city-code municipality sites sync-enabled published deleted
                  first-month latest-month]} municipalities]
      ^{:key (str city-code)}
      [:> TableRow
       [:> TableCell municipality]
       [:> TableCell {:align "right"} sites]
       [:> TableCell {:align "right"} sync-enabled]
       [:> TableCell {:align "right"} published]
       [:> TableCell {:align "right"} deleted]
       [:> TableCell first-month]
       [:> TableCell latest-month]])]])

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

(r/defc overview-tab [{:keys [stats chart-data]}]
  [:<>
   [summary-tiles stats]
   [:> Grid {:container true :spacing 2 :sx #js{:mb 2}}
    [:> Grid {:size {:xs 12 :lg 6}}
     [:> Typography {:variant "h6"} "Uudet liikuntapaikat kuukausittain"]
     [monthly-chart {:data chart-data}]]
    [:> Grid {:size {:xs 12 :lg 6}}
     [:> Typography {:variant "h6"} "Elinkaarensa aikana integroidut liikuntapaikat"]
     [cumulative-chart {:data chart-data}]]]
   [:> Typography {:variant "h6"} "Kunnittain"]
   [municipalities-table stats]])

(r/defc ptv-adoption-tab []
  (let [[tab set-tab] (hooks/use-state "overview")
        stats @(rf/subscribe [::subs/stats])
        chart-data @(rf/subscribe [::subs/chart-data])
        loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])]
    (hooks/use-effect
      (fn []
        (rf/dispatch [::events/fetch])
        js/undefined)
      [])
    [:> Card {:square true}
     [:> CardHeader
      {:title "PTV-integraation käyttöönotto"
       :subheader (when (= "overview" tab)
                    "Liikuntapaikka lasketaan sen ensimmäisen PTV-tietoja sisältävän version kuukaudelle.")
       :action (r/as-element
                 [:> Stack {:alignItems "flex-end"}
                  [:> Button {:on-click #(rf/dispatch (if (= "overview" tab)
                                                        [::events/fetch]
                                                        [::events/fetch-users]))
                              :disabled loading?}
                   "Päivitä"]
                  (when-let [t (and (= "overview" tab) (:generated-at stats))]
                    [:> Typography {:variant "caption" :color "textSecondary"}
                     (str "Päivitetty " (.toLocaleString (js/Date. t) "fi-FI"))])])}]
     [:> CardContent
      [:> Tabs {:value tab
                :on-change (fn [_e v] (set-tab v))
                :sx #js{:mb 2}}
       [:> Tab {:value "overview" :label "Yleiskatsaus"}]
       [:> Tab {:value "users" :label "Käyttäjät"}]]
      (case tab
        "users" [users-tab]
        [:<>
         (when loading?
           [:> LinearProgress {:sx #js{:mb 2}}])
         (when error
           [:> Alert {:severity "error" :sx #js{:mb 2}} (str error)])
         (when stats
           [overview-tab {:stats stats :chart-data chart-data}])])]]))
