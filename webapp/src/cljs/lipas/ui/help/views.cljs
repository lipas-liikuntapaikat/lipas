(ns lipas.ui.help.views
  (:require
   ["@mui/icons-material/ArrowBack$default" :as ArrowBackIcon]
   ["@mui/material/Accordion$default" :as Accordion]
   ["@mui/material/AccordionSummary$default" :as AccordionSummary]
   ["@mui/material/AccordionDetails$default" :as AccordionDetails]
   ["@mui/icons-material/Close$default" :as CloseIcon]
   ["@mui/icons-material/Edit$default" :as EditIcon]
   ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
   ["@mui/icons-material/Help$default" :as Help]
   ["@mui/icons-material/Menu$default" :as MenuIcon]
   ["@mui/icons-material/OpenInNew$default" :as OpenInNewIcon]
   ["@mui/icons-material/PictureAsPdf$default" :as PdfIcon]
   ["@mui/icons-material/Search$default" :as SearchIcon]
   ["@mui/material/Alert$default" :as Alert]
   ["@mui/material/AppBar$default" :as AppBar]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/Breadcrumbs$default" :as Breadcrumbs]
   ["@mui/material/Button$default" :as Button]
   ["@mui/material/Card$default" :as Card]
   ["@mui/material/CardContent$default" :as CardContent]
   ["@mui/material/Chip$default" :as Chip]
   ["@mui/material/Collapse$default" :as Collapse]
   ["@mui/material/Dialog$default" :as Dialog]
   ["@mui/material/DialogContent$default" :as DialogContent]
   ["@mui/material/Divider$default" :as Divider]
   ["@mui/material/Drawer$default" :as Drawer]
   ["@mui/material/GridLegacy$default" :as Grid]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/material/InputAdornment$default" :as InputAdornment]
   ["@mui/material/Link$default" :as Link]
   ["@mui/material/List$default" :as List]
   ["@mui/material/ListItemButton$default" :as ListItemButton]
   ["@mui/material/ListItemText$default" :as ListItemText]
   ["@mui/material/ListSubheader$default" :as ListSubheader]
   ["@mui/material/Paper$default" :as Paper]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/Table$default" :as Table]
   ["@mui/material/TableBody$default" :as TableBody]
   ["@mui/material/TableCell$default" :as TableCell]
   ["@mui/material/TableContainer$default" :as TableContainer]
   ["@mui/material/TableHead$default" :as TableHead]
   ["@mui/material/TableRow$default" :as TableRow]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Toolbar$default" :as Toolbar]
   ["@mui/material/Tooltip$default" :as Tooltip]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/useMediaQuery$default" :as useMediaQuery]
   ["react-markdown$default" :as ReactMarkdown]
   [lipas.ui.help.events :as events]
   [lipas.ui.help.manage :as manage]
   [lipas.ui.help.subs :as subs]
   [lipas.ui.user.subs :as user-subs]
   [lipas.ui.utils :as utils :refer [==>]]
   [reagent.core :as r]
   [reagent.hooks :as hooks]
   [re-frame.core :as rf]))

;; The app theme uppercases every heading variant — help content titles
;; are sentence-length, so they opt out explicitly.
(def title-sx
  #js{:textTransform "none"
      :fontWeight 700
      :lineHeight 1.25})

;;; ——— Type code explorer (unchanged from v1) ————————————————————————

;; Helper component to display a list of types
(r/defc TypesList
  [{:keys [types locale]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:<>
     (for [type (sort-by :type-code types)]
       [:> Accordion {:key (:type-code type) :TransitionProps #js{:unmountOnExit true}}
        [:> AccordionSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
         [:> Typography {:variant "body2"}
          (str (:type-code type) " - " (get-in type [:name locale]))]]

        [:> AccordionDetails

         ;; Type metadata
         [:> Grid {:container true :spacing 2}

          ;; Description
          (when-let [description (get-in type [:description locale])]
            [:> Grid {:item true :xs 12}
             [:> Paper {:variant "outlined" :sx #js{:p 2}}
              [:> Typography {:variant "body1"} description]]])

          ;; Geometry type
          [:> Grid {:item true :xs 12 :sm 6 :md 4}
           [:> Box {:sx #js{:p 2 :border "1px solid" :borderColor "divider" :borderRadius 1}}
            [:> Typography {:variant "subtitle2" :gutterBottom true}
             (tr :type/geometry)]
            [:> Typography {:variant "body1"}
             (:geometry-type type)]]]

          ;; Tags if available
          (when-let [tags (get-in type [:tags locale])]
            [:> Grid {:item true :xs 12 :lg 8}
             [:> Box {:sx #js{:p 2 :border "1px solid" :borderColor "divider" :borderRadius 1}}
              [:> Typography {:variant "subtitle2" :gutterBottom true}
               (tr :ptv/keywords)]
              [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap" :gap 1}
               (for [tag tags]
                 [:> Chip {:key tag :label tag :size "small"}])]]])

          ;; Properties
          [:> Grid {:item true :xs 12}
           [:> Box {:sx #js{:mt 3}}
            [:> Typography {:variant "subtitle1" :gutterBottom true}
             (or (tr :lipas.sports-site/properties2) "Properties")]

            (if (seq (:props type))
              [:> TableContainer {:component Paper}
               [:> Table {:size "small" :aria-label "properties table"}
                [:> TableHead
                 [:> TableRow
                  [:> TableCell (or (tr :lipas.sports-site/property) "Property")]
                  [:> TableCell (or (tr :lipas.sports-site/type) "Type")]
                  [:> TableCell (or (tr :general/description) "Description")]]]
                [:> TableBody
                 (for [prop (:props type)]
                   [:> TableRow {:key (:key prop)}
                    [:> TableCell {:component "th" :scope "row"}
                     [:> Tooltip {:title (str (:key prop)) :arrow true}
                      [:> Typography {:variant "body1"}
                       (get-in prop [:name locale] (name (:key prop)))]]]
                    [:> TableCell
                     [:> Chip {:label (:data-type prop)
                               :size "small"
                               :color (case (:data-type prop)
                                        "numeric" "primary"
                                        "boolean" "secondary"
                                        "enum" "success"
                                        "enum-coll" "warning"
                                        "string" "info"
                                        "default")}]]
                    [:> TableCell
                     [:> Typography {:variant "body1"}
                      (get-in prop [:description locale] "-")]]])]]]

              [:> Typography {:variant "body1" :color "text.secondary"}
               "No specific properties defined"])

            ;; If the property has enum options, display them
            (for [prop (filter #(contains? #{"enum" "enum-coll"} (:data-type %)) (:props type))]
              (when (seq (:opts prop))
                [:> Box {:key (str "opts-" (:key prop)) :mt 2}
                 [:> Typography {:variant "subtitle2" :gutterBottom true}
                  (str (get-in prop [:name locale]) " - "
                       (or (tr :lipas.properties/allowed-values) "Allowed values"))]
                 [:> TableContainer {:component Paper}
                  [:> Table {:size "small" :aria-label "options table"}
                   [:> TableHead
                    [:> TableRow
                     [:> TableCell (or (tr :lipas.properties/value) "Value")]
                     [:> TableCell (or (tr :lipas.properties/label) "Label")]
                     [:> TableCell (or (tr :general/description) "Description")]]]
                   [:> TableBody
                    (for [[option-key option-data] (:opts prop)]
                      [:> TableRow {:key option-key}
                       [:> TableCell {:component "th" :scope "row"}
                        [:> Typography {:variant "body1" :fontFamily "monospace"}
                         option-key]]
                       [:> TableCell
                        (get-in option-data [:label locale] "")]
                       [:> TableCell
                        (get-in option-data [:description locale] "")]])]]]]))]]]]])]))

(r/defc TypeCodeExplorer
  []
  (let [types-data @(rf/subscribe [:lipas.ui.subs/sports-site-types])
        tr @(rf/subscribe [:lipas.ui.subs/translator])
        locale (tr)
        ;; State for selected items
        [selected-main-category set-selected-main-category!] (hooks/use-state nil)
        [selected-sub-category set-selected-sub-category!] (hooks/use-state nil)
        [search-term set-search-term!] (hooks/use-state "")
        [view-mode set-view-mode!] (hooks/use-state :categories) ; :categories, :search, or :details

        ;; Group types by categories
        main-categories (when types-data
                          (->> types-data
                               (group-by #(get-in % [:main-category :type-code]))
                               (sort-by first)))

        ;; Filter sub-categories based on selected main category
        sub-categories (when (and types-data selected-main-category)
                         (->> types-data
                              (filter #(= (get-in % [:main-category :type-code]) selected-main-category))
                              (group-by #(get-in % [:sub-category :type-code]))
                              (sort-by first)))

        ;; Filter types based on selected sub-category
        filtered-types (when (and types-data selected-sub-category)
                         (->> types-data
                              (filter #(= (get-in % [:sub-category :type-code]) selected-sub-category))
                              (sort-by :type-code)))

        ;; Full text search across all types
        searched-types (when (and types-data (not-empty search-term))
                         (let [term (clojure.string/lower-case search-term)]
                           (->> types-data
                                (filter #(or
                                          (clojure.string/includes?
                                           (clojure.string/lower-case (get-in % [:name locale] ""))
                                           term)
                                          (clojure.string/includes?
                                           (clojure.string/lower-case (str (:type-code %)))
                                           term)
                                          (when-let [desc (get-in % [:description locale])]
                                            (clojure.string/includes?
                                             (clojure.string/lower-case desc)
                                             term))))
                                (sort-by :type-code))))]

    ;; Main component view
    [:> Stack {:direction "column" :spacing 2 :sx #js{:flex 1}}

     ;; Search bar
     [:> Grid {:container true :spacing 2 :alignItems "center" :sx #js{:mb 2}}
      [:> Grid {:item true :xs 12 :md 9}
       [:> TextField {:fullWidth true
                      :variant "outlined"
                      :size "small"
                      :label (tr :search/search)
                      :placeholder "Pallokenttä"
                      :value search-term
                      :onChange #(let [value (.. % -target -value)]
                                   (set-search-term! value)
                                   (when (not-empty value)
                                     (set-view-mode! :search))
                                   (when (empty? value)
                                     (set-view-mode! :categories)))}]]

      [:> Grid {:item true :xs 12 :md 3}
       [:> Button {:variant "contained"
                   :color "secondary"
                   :onClick #(==> [:lipas.ui.events/download-types-excel])}
        "Lataa Excel"]]]

     ;; Breadcrumbs navigation
     [:> Breadcrumbs {:sx #js{:mb 2 :pl 2}}
      [:> Link {:component "button"
                :underline "hover"
                :onClick #(do (set-selected-main-category! nil)
                              (set-selected-sub-category! nil)
                              (set-view-mode! :categories))}
       (tr :type/main-categories)]

      (when selected-main-category
        [:> Link {:component "button"
                  :underline "hover"
                  :onClick #(do (set-selected-sub-category! nil)
                                (set-view-mode! :categories))}
         (if-let [main-cat (->> types-data
                                (filter #(= (get-in % [:main-category :type-code]) selected-main-category))
                                first)]
           (get-in main-cat [:main-category :name locale])
           (str (tr :type/main-category) " " selected-main-category))])

      (when selected-sub-category
        [:> Typography {:color "text.primary"}
         (if-let [sub-cat (->> types-data
                               (filter #(= (get-in % [:sub-category :type-code]) selected-sub-category))
                               first)]
           (get-in sub-cat [:sub-category :name locale])
           (str (tr :type/sub-category) " " selected-sub-category))])]

     ;; Main content area
     [:> Box {:sx #js{:p 2 :border "1px solid" :borderColor "divider" :borderRadius 1 :flex 1}}
      (case view-mode
        ;; Category browsing view
        :categories
        (if selected-main-category
          ;; Show sub-categories if main category is selected
          (if (seq sub-categories)
            [:> Grid {:container true :spacing 2}
             (for [[sub-cat-code sub-cat-types] sub-categories]
               (let [first-type (first sub-cat-types)
                     sub-cat-name (get-in first-type [:sub-category :name locale])]
                 [:> Grid {:item true :xs 12 :sm 6 :md 4 :key sub-cat-code}
                  [:> Card {:sx #js{:height "100%"
                                    :cursor "pointer"
                                    :transition "all 0.2s"
                                    ":hover" #js{:transform "translateY(-3px)"
                                                 :boxShadow 3}}
                            :onClick #(do (set-selected-sub-category! sub-cat-code)
                                          (set-view-mode! :details))}
                   [:> CardContent
                    [:> Typography {:variant "h6" :component "div" :gutterBottom true}
                     sub-cat-name]
                    [:> Typography {:variant "body1" :color "text.secondary"}
                     (str (tr :type/count) ": " (count sub-cat-types))]
                    [:> Typography {:variant "caption" :display "block"}
                     (str (tr :type/type-code) ": " sub-cat-code)]]]]))]

            ;; No sub-categories found
            [:> Typography {:color "text.secondary"}
             (tr :type/no-sub-categories)])

          ;; Show main categories if no main category selected
          [:> Grid {:container true :spacing 2}
           (for [[main-cat-code main-cat-types] main-categories]
             (let [first-type (first main-cat-types)
                   main-cat-name (get-in first-type [:main-category :name locale])]
               [:> Grid {:item true :xs 12 :sm 6 :md 4 :key main-cat-code}
                [:> Card {:sx #js{:height "100%"
                                  :cursor "pointer"
                                  :transition "all 0.2s"
                                  ":hover" #js{:transform "translateY(-3px)"
                                               :boxShadow 3}}
                          :onClick #(do (set-selected-main-category! main-cat-code)
                                        (set-view-mode! :categories))}
                 [:> CardContent
                  [:> Typography {:variant "h6" :component "div" :gutterBottom true}
                   main-cat-name]
                  [:> Typography {:variant "body1" :color "text.secondary"}
                   (str (tr :type/count) ": " (count main-cat-types))]
                  [:> Typography {:variant "caption" :display "block"}
                   (str (tr :type/type-code) ": " main-cat-code)]]]]))])

        ;; Search results view
        :search
        (if (seq searched-types)
          [:<>
           [:> Typography {:variant "subtitle1" :gutterBottom true}
            (str (tr :search/results) ": " (count searched-types))]
           [TypesList {:types searched-types :locale locale}]]
          [:> Typography {:color "text.secondary" :align "center" :py 4}
           (tr :search/results-count 0)])

        ;; Details view for a specific sub-category
        :details
        [TypesList {:types filtered-types :locale locale}]

        ;; Default fallback
        [:> Typography {:color "error"}
         "Unknown view mode"])]

     ;; Back button when drilling down
     (when (or selected-main-category selected-sub-category)
       [:> Box {:mt 2}
        [:> Button {:variant "outlined"
                    :startIcon (r/as-element [:> ArrowBackIcon])
                    :onClick #(if selected-sub-category
                                (do (set-selected-sub-category! nil)
                                    (set-view-mode! :categories))
                                (do (set-selected-main-category! nil)
                                    (set-view-mode! :categories)))}
         (tr :actions/back)]])]))

(r/defc DataModelExcelDownload
  []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Stack {:spacing 1}
     [:> Typography {:variant "h6"} (tr :help/data-model)]
     [:> Typography (tr :help/data-model-excel)]
     [:> Box
      [:> Button {:variant "contained"
                  :on-click #(rf/dispatch [:lipas.ui.reports.events/create-data-model-report])}
       (tr :actions/download-excel)]]]))

;;; ——— Content blocks —————————————————————————————————————————————————

(r/defc ResponsiveVideo
  [{:keys [video-id title]}]
  [:> Box {:sx #js{:maxWidth 760}}
   (when-not (empty? title)
     [:> Typography {:variant "subtitle1" :sx #js{:fontWeight 700 :mb 1}}
      title])
   [:> Box {:sx #js{:position "relative"
                    :pt "56.25%" ; 16:9
                    :borderRadius 1
                    :overflow "hidden"
                    :bgcolor "common.black"}}
    [:iframe
     {:style {:position "absolute" :top 0 :left 0
              :width "100%" :height "100%" :border 0}
      :src (str "https://www.youtube.com/embed/" video-id)
      :title (or title "YouTube video player")
      :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      :referrer-policy "strict-origin-when-cross-origin"
      :allow-full-screen true}]]])

(r/defc PdfCard
  [{:keys [url title caption]}]
  [:> Paper {:variant "outlined" :sx #js{:overflow "hidden"}}
   [:> Stack {:direction "row" :spacing 1 :alignItems "center"
              :sx #js{:px 2 :py 1 :bgcolor "action.hover"}}
    [:> PdfIcon {:color "action"}]
    [:> Typography {:variant "subtitle1" :sx #js{:fontWeight 700 :flexGrow 1}}
     (if (empty? title) "PDF" title)]
    [:> Tooltip {:title "Avaa uuteen välilehteen"}
     [:> IconButton {:component "a" :href url :target "_blank" :rel "noopener"
                     :size "small"}
      [:> OpenInNewIcon {:fontSize "small"}]]]]
   [:iframe
    {:style {:width "100%" :height "70vh" :border 0 :display "block"}
     :src url
     :title (or title "PDF")
     :allow-full-screen true}]
   (when-not (empty? caption)
     [:> Typography {:variant "body2" :color "text.secondary" :sx #js{:p 1.5}}
      caption])])

(r/defc BlockView
  [{:keys [block]}]
  (case (:type block)
    :text
    [:> Box {:sx #js{:typography "body1"
                     :maxWidth 760
                     "& p" #js{:mt 0 :mb 1.5}
                     "& p:last-child" #js{:mb 0}
                     "& a" #js{:color "secondary.main"}
                     "& img" #js{:maxWidth "100%"}}}
     [:> ReactMarkdown (:content block)]]

    :video
    [ResponsiveVideo {:video-id (:video-id block) :title (:title block)}]

    :image
    [:> Box {:sx #js{:maxWidth 760}}
     [:img {:src (:url block)
            :alt (:alt block)
            :style {:maxWidth "100%" :borderRadius "4px"}}]
     (when-not (empty? (:caption block))
       [:> Typography {:variant "body2" :color "text.secondary"}
        (:caption block)])]

    :pdf
    [PdfCard {:url (:url block) :title (:title block) :caption (:caption block)}]

    :type-code-explorer
    [TypeCodeExplorer]

    :data-model-excel-download
    [DataModelExcelDownload]

    ;; Default case - unknown block type
    [:> Typography {:color "error"} (str "Unknown block type: " (:type block))]))

;;; ——— Content area ———————————————————————————————————————————————————

(r/defc PageView
  [{:keys [page]}]
  [:> Stack {:spacing 3}
   [:> Box
    [:> Typography {:variant "h4" :component "h1" :sx title-sx}
     (:title page)]
    (when-not (empty? (:summary page))
      [:> Typography {:variant "body1" :color "text.secondary" :sx #js{:mt 1}}
       (:summary page)])]
   (for [block (:blocks page)]
     ^{:key (:block-id block)}
     [BlockView {:block block}])])

(r/defc PageList
  ;; Landing list: page titles + summaries, theme-colored (the v1 cards
  ;; hardcoded a light gradient that was unreadable in dark mode).
  [{:keys [section]}]
  [:> Paper {:variant "outlined"}
   [:> List {:disablePadding true}
    (map-indexed
     (fn [idx {:keys [slug title summary blocks]}]
       ^{:key slug}
       [:<>
        (when (pos? idx) [:> Divider])
        [:> ListItemButton
         {:onClick #(==> [::events/select-page (:slug section) slug])
          :sx #js{:py 1.5}}
         [:> ListItemText
          {:primary title
           :secondary (if (empty? summary)
                        (some #(when (= :text (:type %)) (:content %)) blocks)
                        summary)
           :primaryTypographyProps #js{:sx #js{:fontWeight 700}}
           :secondaryTypographyProps #js{:sx #js{:overflow "hidden"
                                                 :textOverflow "ellipsis"
                                                 :display "-webkit-box"
                                                 :WebkitLineClamp 2
                                                 :WebkitBoxOrient "vertical"}}}]]])
     (:pages section))]])

(r/defc SectionLanding
  [{:keys [section]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Stack {:spacing 2}
     [:> Typography {:variant "h4" :component "h1" :sx title-sx}
      (:title section)]
     (when-not (empty? (:summary section))
       [:> Typography {:variant "body1" :color "text.secondary"}
        (:summary section)])
     [:> Typography {:variant "subtitle2" :color "text.secondary" :sx #js{:pt 1}}
      (tr :help/available-pages)]
     [PageList {:section section}]]))

(r/defc FrontPage
  [{:keys [tree]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Stack {:spacing 2}
     [:> Typography {:variant "h4" :component "h1" :sx title-sx}
      (tr :help/headline)]
     [:> Paper {:variant "outlined"}
      [:> List {:disablePadding true}
       (map-indexed
        (fn [idx {:keys [slug title summary pages]}]
          ^{:key slug}
          [:<>
           (when (pos? idx) [:> Divider])
           [:> ListItemButton
            {:onClick #(==> [::events/select-section slug])
             :sx #js{:py 1.5}}
            [:> ListItemText
             {:primary title
              :secondary (if (empty? summary)
                           (str (count pages) " " (if (= 1 (count pages)) "sivu" "sivua"))
                           summary)
              :primaryTypographyProps #js{:sx #js{:fontWeight 700}}}]]])
        tree)]]]))

;;; ——— Navigation ————————————————————————————————————————————————————

(r/defc NavTree
  [{:keys [tree on-navigate]}]
  (let [selected-section-slug @(rf/subscribe [::subs/selected-section-slug])
        selected-page-slug @(rf/subscribe [::subs/selected-page-slug])
        expanded @(rf/subscribe [::subs/expanded-sections])]
    [:> List {:disablePadding true :sx #js{:pb 2}}
     (for [{:keys [slug title pages]} tree]
       (let [expanded? (contains? expanded slug)]
         ^{:key slug}
         [:<>
          [:> ListItemButton
           {:onClick #(do (==> [::events/select-section slug])
                          (when on-navigate (on-navigate)))
            :selected (and (= slug selected-section-slug)
                           (nil? selected-page-slug))
            :sx #js{:alignItems "flex-start" :py 1}}
           [:> ListItemText
            {:primary title
             :primaryTypographyProps
             #js{:sx #js{:fontWeight 700 :fontSize "0.875rem" :lineHeight 1.35}}}]
           [:> IconButton
            {:size "small"
             :edge "end"
             :aria-label (if expanded? "collapse" "expand")
             :onClick (fn [e]
                        (.stopPropagation e)
                        (==> [::events/toggle-section slug]))
             :sx #js{:mt 0.25
                     :transform (if expanded? "rotate(180deg)" "rotate(0deg)")
                     :transition "transform 0.2s"}}
            [:> ExpandMoreIcon {:fontSize "small"}]]]
          [:> Collapse {:in expanded? :timeout "auto"}
           [:> List {:disablePadding true}
            (for [page pages]
              ^{:key (:slug page)}
              [:> ListItemButton
               {:onClick #(do (==> [::events/select-page slug (:slug page)])
                              (when on-navigate (on-navigate)))
                :selected (and (= slug selected-section-slug)
                               (= (:slug page) selected-page-slug))
                :sx #js{:pl 4 :py 0.75}}
               [:> ListItemText
                {:primary (:title page)
                 :primaryTypographyProps
                 #js{:sx #js{:fontSize "0.85rem" :lineHeight 1.35}}}]])]]]))]))

(r/defc SearchResults
  [{:keys [on-navigate]}]
  (let [results @(rf/subscribe [::subs/search-results])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    (if (empty? results)
      [:> Typography {:variant "body2" :color "text.secondary" :sx #js{:p 2}}
       (tr :search/results-count 0)]
      [:> List {:disablePadding true :sx #js{:pb 2}}
       ;; results come in tree order; partition-by keeps it
       (for [matches (partition-by :section-slug results)]
         ^{:key (:section-slug (first matches))}
         [:<>
          [:> ListSubheader {:disableSticky true} (:section-title (first matches))]
          (for [{:keys [section-slug page]} matches]
            ^{:key (:slug page)}
            [:> ListItemButton
             {:onClick #(do (==> [::events/select-page section-slug (:slug page)])
                            (when on-navigate (on-navigate)))
              :sx #js{:py 0.75}}
             [:> ListItemText
              {:primary (:title page)
               :primaryTypographyProps
               #js{:sx #js{:fontSize "0.85rem" :lineHeight 1.35}}}]])])])))

(r/defc NavPanel
  [{:keys [tree on-navigate]}]
  (let [search-term @(rf/subscribe [::subs/search-term])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Box
     [:> Box {:sx #js{:p 2 :pb 1}}
      [:> TextField
       {:fullWidth true
        :size "small"
        :placeholder (tr :search/search)
        :value search-term
        :onChange #(==> [::events/set-search-term (.. % -target -value)])
        :InputProps #js{:startAdornment
                        (r/as-element
                         [:> InputAdornment {:position "start"}
                          [:> SearchIcon {:fontSize "small"}]])}}]]
     (if (empty? search-term)
       [NavTree {:tree tree :on-navigate on-navigate}]
       [SearchResults {:on-navigate on-navigate}])]))

;;; ——— Dialog ————————————————————————————————————————————————————————

(r/defc HelpManageButton []
  (let [has-permission? @(rf/subscribe [::user-subs/check-privilege nil :help/manage])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    (when has-permission?
      [:> Button
       {:variant "contained"
        :color "secondary"
        :size "small"
        :sx #js{:ml 2 :mr 2}
        :startIcon (r/as-element [:> EditIcon])
        :on-click #(==> [::events/open-edit-mode])}
       (tr :help/manage-content)])))

(r/defc ReadView
  [{:keys [mobile?]}]
  (let [tree @(rf/subscribe [::subs/display-tree])
        fallback? @(rf/subscribe [::subs/fallback?])
        selected-section @(rf/subscribe [::subs/selected-section])
        selected-page @(rf/subscribe [::subs/selected-page])
        tr @(rf/subscribe [:lipas.ui.subs/translator])
        [drawer-open? set-drawer-open!] (hooks/use-state false)
        close-drawer! #(set-drawer-open! false)]
    [:> Box {:sx #js{:display "flex" :flex 1 :minHeight 0}}

     ;; Navigation: permanent panel on desktop, drawer on mobile
     (if mobile?
       [:> Drawer {:open drawer-open?
                   :onClose close-drawer!
                   :ModalProps #js{:keepMounted true}
                   :PaperProps #js{:sx #js{:width 300}}}
        [NavPanel {:tree tree :on-navigate close-drawer!}]]
       [:> Box {:sx #js{:width 320
                        :minWidth 280
                        :borderRight 1
                        :borderColor "divider"
                        :overflowY "auto"}}
        [NavPanel {:tree tree}]])

     ;; Content column
     [:> Box {:sx #js{:flex 1 :overflowY "auto" :minWidth 0}}
      [:> Box {:sx #js{:maxWidth 900 :px #js{:xs 2 :md 4} :py 3}}

       ;; Mobile: nav opener + breadcrumb back
       (when mobile?
         [:> Stack {:direction "row" :spacing 1 :alignItems "center" :sx #js{:mb 2}}
          [:> Button {:variant "outlined"
                      :size "small"
                      :startIcon (r/as-element [:> MenuIcon])
                      :onClick #(set-drawer-open! true)}
           (tr :help/headline)]
          (when selected-page
            [:> Button {:size "small"
                        :startIcon (r/as-element [:> ArrowBackIcon])
                        :onClick #(==> [::events/select-section (:slug selected-section)])}
             (tr :actions/back)])])

       (when fallback?
         [:> Alert {:severity "info" :sx #js{:mb 2}}
          (tr :help/only-in-finnish)])

       (cond
         selected-page [PageView {:page selected-page}]
         selected-section [SectionLanding {:section selected-section}]
         :else [FrontPage {:tree tree}])]]]))

(r/defc dialog
  ;; Mounted once at app root (lipas.ui.views/main-panel) so ?ohje= deep
  ;; links open the help center on any route.
  []
  (let [mode @(rf/subscribe [::subs/mode])
        dialog-open? @(rf/subscribe [::subs/dialog-open?])
        tr @(rf/subscribe [:lipas.ui.subs/translator])
        mobile? (useMediaQuery "(max-width:899.95px)")]

    [:> Dialog
     {:fullScreen true
      :keepMounted true
      :open dialog-open?
      :onClose #(==> [::events/close-dialog])}

     [:> AppBar {:sx #js{:position "relative"}}
      [:> Toolbar {}
       [:> Typography {:variant "h6" :color "inherit" :sx #js{:flexGrow 1}
                       :component "button"
                       :onClick #(==> [::events/go-home])
                       :style {:background "none" :border 0 :cursor "pointer"
                               :textAlign "left" :color "inherit"}}
        (tr :help/headline)]

       ;; Manage content button (only visible with permission)
       [HelpManageButton]

       [:> IconButton
        {:edge "start"
         :color "inherit"
         :onClick #(==> [::events/close-dialog])}
        [:> CloseIcon]]]]

     (if (= :edit mode)
       [:> DialogContent {:sx #js{:display "flex" :flexDirection "column"}}
        [manage/view]]
       [:> DialogContent {:sx #js{:p 0 :display "flex" :overflow "hidden"}}
        [ReadView {:mobile? mobile?}]])]))

(r/defc view
  ;; Help icon button for the (mini-)navbar; the dialog itself is mounted
  ;; at app root.
  [{:keys []}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Tooltip {:title (tr :help/headline)}
     [:> IconButton {:size "large"
                     :on-click #(==> [::events/open-dialog])}
      [:> Help]]]))
