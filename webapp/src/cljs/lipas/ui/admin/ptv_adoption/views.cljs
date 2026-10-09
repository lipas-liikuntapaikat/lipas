(ns lipas.ui.admin.ptv-adoption.views
  "Admin tab: PTV integration adoption over time and per municipality.
  Counts each site from its first revision carrying PTV data."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
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
            [lipas.ui.admin.ptv-adoption.events :as events]
            [lipas.ui.admin.ptv-adoption.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

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
                 :label "Kuntaa"}]
     [stat-tile {:value (:sync-enabled totals)
                 :label "Synkronointi päällä"
                 :caption (when (pos? (:sync-pending totals))
                            (str (:sync-pending totals) " ei vielä PTV:ssä"))}]
     [stat-tile {:value (:published totals)
                 :label "Julkaistu PTV:ssä"
                 :caption (when (pos? (:deleted totals))
                            (str (:deleted totals) " poistettu PTV:stä"))}]
     [stat-tile {:value (or (:new this-month) 0)
                 :label "Uusia tässä kuussa"
                 :caption (:month this-month)}]]))

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

(r/defc outside-managers-section
  "PTV managers (direct city-scoped role) who aren't members of the org covering
  their municipality. They can do PTV work without membership, but membership
  drives audit emails and the org's member list. Adding is plain membership —
  roles stay the org-admin's call."
  []
  (let [rows @(rf/subscribe [::subs/outside-managers])
        loading? @(rf/subscribe [::subs/outside-managers-loading?])]
    (hooks/use-effect
      (fn []
        (rf/dispatch [::events/fetch-outside-managers])
        js/undefined)
      [])
    [:<>
     [:> Typography {:variant "h6" :sx #js{:mt 3}}
      "PTV-käsittelijät, jotka eivät ole kuntansa organisaation jäseniä"]
     [:> Typography {:variant "body2" :color "textSecondary" :sx #js{:mb 1}}
      (str "Käyttäjillä on PTV-oikeudet kuntaan, mutta he eivät ole kunnan organisaation jäseniä. "
           "Jäseneksi lisääminen ei anna rooleja: organisaation ylläpitäjä päättää ne Jäsenet-välilehdellä. "
           "Jos kunnalle ei ole PTV-organisaatiota, sen PTV-asetukset puuttuvat.")]
     (when loading?
       [:> LinearProgress])
     (if (and (some? rows) (empty? rows))
       [:> Typography {:variant "body2"} "Ei puuttuvia jäsenyyksiä."]
       [:> Table {:size "small"}
        [:> TableHead
         [:> TableRow
          [:> TableCell "Käyttäjä"]
          [:> TableCell "Sähköposti"]
          [:> TableCell {:align "right"} "Kuntakoodi"]
          [:> TableCell "Organisaatio"]
          [:> TableCell]]]
        [:> TableBody
         (for [{:keys [user-id name username email city-code org] :as row} rows]
           ^{:key (str user-id "-" city-code)}
           [:> TableRow
            [:> TableCell (if (seq name) name username)]
            [:> TableCell email]
            [:> TableCell {:align "right"} city-code]
            [:> TableCell (or (:name org) [:em "Ei PTV-organisaatiota"])]
            [:> TableCell {:align "right"}
             (when org
               [:> Button {:size "small"
                           :variant "outlined"
                           :disabled @(rf/subscribe [::subs/adding? email])
                           :on-click #(rf/dispatch [::events/add-to-org row])}
                "Lisää jäseneksi"])]])]])]))

(r/defc ptv-adoption-tab []
  (let [stats @(rf/subscribe [::subs/stats])
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
       :subheader (r/as-element
                    [:<>
                     "Liikuntapaikka lasketaan sen ensimmäisen PTV-tietoja sisältävän version kuukaudelle."
                     (when-let [t (:generated-at stats)]
                       (str " Päivitetty " (.toLocaleString (js/Date. t) "fi-FI") "."))])
       :action (r/as-element
                 [:> Button {:on-click #(rf/dispatch [::events/fetch])
                             :disabled loading?}
                  "Päivitä"])}]
     [:> CardContent
      (when loading?
        [:> LinearProgress {:sx #js{:mb 2}}])
      (when error
        [:> Alert {:severity "error" :sx #js{:mb 2}} (str error)])
      (when stats
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
      [outside-managers-section]]]))
