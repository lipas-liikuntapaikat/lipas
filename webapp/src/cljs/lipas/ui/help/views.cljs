(ns lipas.ui.help.views
  (:require
    ["@mui/icons-material/Close$default" :as CloseIcon]
    ["@mui/icons-material/ArrowForwardIos$default" :as ArrowForwadIosIcon]
    ["@mui/icons-material/ArrowBack$default" :as ArrowBackIcon]
    ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
    ["@mui/icons-material/Help$default" :as Help]
    ["@mui/icons-material/Edit$default" :as EditIcon]
    ["@mui/material/Accordion$default" :as Accordion]
    ["@mui/material/AccordionSummary$default" :as AccordionSummary]
    ["@mui/material/AccordionDetails$default" :as AccordionDetails]
    ["@mui/material/AppBar$default" :as AppBar]
    ["@mui/material/Box$default" :as Box]
    ["@mui/material/Breadcrumbs$default" :as Breadcrumbs]
    ["@mui/material/Button$default" :as Button]
    ["@mui/material/Card$default" :as Card]
    ["@mui/material/CardContent$default" :as CardContent]
    ["@mui/material/Chip$default" :as Chip]
    ["@mui/material/Dialog$default" :as Dialog]
    ["@mui/material/DialogContent$default" :as DialogContent]
    ["@mui/material/Divider$default" :as Divider]
    ["@mui/material/Grid$default" :as Grid]
    ["@mui/material/IconButton$default" :as IconButton]
    ["@mui/material/Link$default" :as Link]
    ["@mui/material/List$default" :as List]
    ["@mui/material/ListItem$default" :as ListItem]
    ["@mui/material/ListItemButton$default" :as ListItemButton]
    ["@mui/material/ListItemText$default" :as ListItemText]
    ["@mui/material/ListItemIcon$default" :as ListItemIcon]
    ["@mui/material/Paper$default" :as Paper]
    ["@mui/material/Stack$default" :as Stack]
    ["@mui/material/Tab$default" :as Tab]
    ["@mui/material/Table$default" :as Table]
    ["@mui/material/TableBody$default" :as TableBody]
    ["@mui/material/TableCell$default" :as TableCell]
    ["@mui/material/TableContainer$default" :as TableContainer]
    ["@mui/material/TableHead$default" :as TableHead]
    ["@mui/material/TableRow$default" :as TableRow]
    ["@mui/material/Tabs$default" :as Tabs]
    ["@mui/material/TextField$default" :as TextField]
    ["@mui/material/Toolbar$default" :as Toolbar]
    ["@mui/material/Tooltip$default" :as Tooltip]
    ["@mui/material/Typography$default" :as Typography]
    [lipas.ui.help.events :as events]
    [lipas.ui.help.manage :as manage]
    [lipas.ui.help.subs :as subs]
    [lipas.ui.uix.hooks :refer [use-subscribe]]
    [lipas.ui.user.subs :as user-subs]
    [lipas.ui.utils :as utils :refer [==>]]
    [uix.core :as uix :refer [$ defui]]))

(defui YoutubeIframe
  [{:keys [video-id title]}]
  ($ :iframe
     {:width             "560"
      :height            "315"
      :src               (str "https://www.youtube.com/embed/" video-id)
      :title             (or title "YouTube video player")
      :frame-border      "0"
      :allow             "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      :referrer-policy   "strict-origin-when-cross-origin"
      :allow-full-screen true}))

;; Helper component to display a list of types
(defui TypesList
  [{:keys [types locale]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])]
    ($ :<>
       (for [type (sort-by :type-code types)]
         ($ Accordion {:key (:type-code type) :TransitionProps #js{:unmountOnExit true}}
            ($ AccordionSummary {:expandIcon ($ ExpandMoreIcon)}
               ($ Typography {:variant "body2"}
                  (str (:type-code type) " - " (get-in type [:name locale]))))

            ($ AccordionDetails

               ;; Type metadata
               ($ Grid {:container true :spacing 2}

                  ;; Description
                  (when-let [description (get-in type [:description locale])]
                    ($ Grid {:item true :xs 12}
                       ($ Paper {:variant "outlined" :sx #js{:p 2}}
                          ($ Typography {:variant "body1"} description))))

                  ;; Geometry type
                  ($ Grid {:item true :xs 12 :sm 6 :md 4}
                     ($ Box {:sx #js{:p 2 :border "1px solid" :borderColor "divider" :borderRadius 1}}
                        ($ Typography {:variant "subtitle2" :gutterBottom true}
                           (tr :type/geometry))
                        ($ Typography {:variant "body1"}
                           (:geometry-type type))))

                  ;; Tags if available
                  (when-let [tags (get-in type [:tags locale])]
                    ($ Grid {:item true :xs 12 :lg 8}
                       ($ Box {:sx #js{:p 2 :border "1px solid" :borderColor "divider" :borderRadius 1}}
                          ($ Typography {:variant "subtitle2" :gutterBottom true}
                             (tr :ptv/keywords))
                          ($ Stack {:direction "row" :spacing 1 :flexWrap "wrap" :gap 1}
                             (for [tag tags]
                               ($ Chip {:key tag :label tag :size "small"}))))))

                  ;; Properties
                  ($ Grid {:item true :xs 12}
                     ($ Box {:sx #js{:mt 3}}
                        ($ Typography {:variant "subtitle1" :gutterBottom true}
                           (or (tr :lipas.sports-site/properties2) "Properties"))

                        (if (seq (:props type))
                          ($ TableContainer {:component Paper}
                             ($ Table {:size "small" :aria-label "properties table"}
                                ($ TableHead
                                   ($ TableRow
                                      ($ TableCell (or (tr :lipas.sports-site/property) "Property"))
                                      ($ TableCell (or (tr :lipas.sports-site/type) "Type"))
                                      ($ TableCell (or (tr :general/description) "Description"))))
                                ($ TableBody
                                   (for [prop (:props type)]
                                     ($ TableRow {:key (:key prop)}
                                        ($ TableCell {:component "th" :scope "row"}
                                           ($ Tooltip {:title (str (:key prop)) :arrow true}
                                              ($ Typography {:variant "body1"}
                                                 (get-in prop [:name locale] (name (:key prop))))))
                                        ($ TableCell
                                           ($ Chip {:label (:data-type prop)
                                                    :size "small"
                                                    :color (case (:data-type prop)
                                                             "numeric" "primary"
                                                             "boolean" "secondary"
                                                             "enum" "success"
                                                             "enum-coll" "warning"
                                                             "string" "info"
                                                             "default")}))
                                        ($ TableCell
                                           ($ Typography {:variant "body1"}
                                              (get-in prop [:description locale] "-"))))))))

                          ($ Typography {:variant "body1" :color "text.secondary"}
                             "No specific properties defined"))

                        ;; If the property has enum options, display them
                        (for [prop (filter #(contains? #{"enum" "enum-coll"} (:data-type %)) (:props type))]
                          (when (seq (:opts prop))
                            ($ Box {:key (str "opts-" (:key prop)) :mt 2}
                               ($ Typography {:variant "subtitle2" :gutterBottom true}
                                  (str (get-in prop [:name locale]) " - "
                                       (or (tr :lipas.properties/allowed-values) "Allowed values")))
                               ($ TableContainer {:component Paper}
                                  ($ Table {:size "small" :aria-label "options table"}
                                     ($ TableHead
                                        ($ TableRow
                                           ($ TableCell (or (tr :lipas.properties/value) "Value"))
                                           ($ TableCell (or (tr :lipas.properties/label) "Label"))
                                           ($ TableCell (or (tr :general/description) "Description"))))
                                     ($ TableBody
                                        (for [[option-key option-data] (:opts prop)]
                                          ($ TableRow {:key option-key}
                                             ($ TableCell {:component "th" :scope "row"}
                                                ($ Typography {:variant "body1" :fontFamily "monospace"}
                                                   option-key))
                                             ($ TableCell
                                                (get-in option-data [:label locale] ""))
                                             ($ TableCell
                                                (get-in option-data [:description locale] "")))))))))))))))))))


(defui TypeCodeExplorer
  []
  (let [types-data (use-subscribe [:lipas.ui.subs/sports-site-types])
        tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)
        ;; State for selected items
        [selected-main-category set-selected-main-category!] (uix/use-state nil)
        [selected-sub-category set-selected-sub-category!] (uix/use-state nil)
        [search-term set-search-term!] (uix/use-state "")
        [view-mode set-view-mode!] (uix/use-state :categories) ; :categories, :search, or :details

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
    ($ Stack {:direction "column" :spacing 2 :sx #js{:flex 1}}


       ;; Search bar
       ($ Grid {:container true :spacing 2 :alignItems "center" :sx #js{:mb 2}}
          ($ Grid {:item true :xs 12 :md 9}
             ($ TextField {:fullWidth true
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
                                          (set-view-mode! :categories)))}))

          ($ Grid {:item true :xs 12 :md 3}
             ($ Button {:variant "contained"
                        :color "secondary"
                        :onClick #(==> [:lipas.ui.events/download-types-excel])}
                "Lataa Excel")))

       ;; Breadcrumbs navigation
       ($ Breadcrumbs {:sx #js{:mb 2 :pl 2}}
          ($ Link {:component "button"
                   :underline "hover"
                   :onClick #(do (set-selected-main-category! nil)
                                 (set-selected-sub-category! nil)
                                 (set-view-mode! :categories))}
             (tr :type/main-categories))

          (when selected-main-category
            ($ Link {:component "button"
                     :underline "hover"
                     :onClick #(do (set-selected-sub-category! nil)
                                   (set-view-mode! :categories))}
               (if-let [main-cat (->> types-data
                                      (filter #(= (get-in % [:main-category :type-code]) selected-main-category))
                                      first)]
                 (get-in main-cat [:main-category :name locale])
                 (str (tr :type/main-category) " " selected-main-category))))

          (when selected-sub-category
            ($ Typography {:color "text.primary"}
               (if-let [sub-cat (->> types-data
                                     (filter #(= (get-in % [:sub-category :type-code]) selected-sub-category))
                                     first)]
                 (get-in sub-cat [:sub-category :name locale])
                 (str (tr :ttype/sub-category) " " selected-sub-category)))))

       ;; Main content area
       ($ Box {:sx #js{:p 2 :border "1px solid" :borderColor "divider" :borderRadius 1 :flex 1}}
          (case view-mode
            ;; Category browsing view
            :categories
            (if selected-main-category
              ;; Show sub-categories if main category is selected
              (if (seq sub-categories)
                ($ Grid {:container true :spacing 2}
                   (for [[sub-cat-code sub-cat-types] sub-categories]
                     (let [first-type (first sub-cat-types)
                           sub-cat-name (get-in first-type [:sub-category :name locale])]
                       ($ Grid {:item true :xs 12 :sm 6 :md 4 :key sub-cat-code}
                          ($ Card {:sx #js{:height "100%"
                                           :cursor "pointer"
                                           :transition "all 0.2s"
                                           ":hover" #js{:transform "translateY(-3px)"
                                                        :boxShadow 3}}
                                   :onClick #(do (set-selected-sub-category! sub-cat-code)
                                                 (set-view-mode! :details))}
                             ($ CardContent
                                ($ Typography {:variant "h6" :component "div" :gutterBottom true}
                                   sub-cat-name)
                                ($ Typography {:variant "body1" :color "text.secondary"}
                                   (str (tr :type/count) ": " (count sub-cat-types)))
                                ($ Typography {:variant "caption" :display "block"}
                                   (str (tr :type/type-code) ": " sub-cat-code))))))))

                ;; No sub-categories found
                ($ Typography {:color "text.secondary"}
                   (tr :lipas.categories/no-sub-categories)))

              ;; Show main categories if no main category selected
              ($ Grid {:container true :spacing 2}
                 (for [[main-cat-code main-cat-types] main-categories]
                   (let [first-type (first main-cat-types)
                         main-cat-name (get-in first-type [:main-category :name locale])]
                     ($ Grid {:item true :xs 12 :sm 6 :md 4 :key main-cat-code}
                        ($ Card {:sx #js{:height "100%"
                                         :cursor "pointer"
                                         :transition "all 0.2s"
                                         ":hover" #js{:transform "translateY(-3px)"
                                                      :boxShadow 3}}
                                 :onClick #(do (set-selected-main-category! main-cat-code)
                                               (set-view-mode! :categories))}
                           ($ CardContent
                              ($ Typography {:variant "h6" :component "div" :gutterBottom true}
                                 main-cat-name)
                              ($ Typography {:variant "body1" :color "text.secondary"}
                                 (str (tr :type/count) ": " (count main-cat-types)))
                              ($ Typography {:variant "caption" :display "block"}
                                 (str (tr :type/type-code) ": " main-cat-code)))))))))

            ;; Search results view
            :search
            (if (seq searched-types)
              ($ :<>
                 ($ Typography {:variant "subtitle1" :gutterBottom true}
                    (str (tr :search/results) ": " (count searched-types)))
                 ($ TypesList {:types searched-types :locale locale}))
              ($ Typography {:color "text.secondary" :align "center" :py 4}
                 (tr :search/results-count 0)))

            ;; Details view for a specific sub-category
            :details
            ($ TypesList {:types filtered-types :locale locale})

            ;; Default fallback
            ($ Typography {:color "error"}
               "Unknown view mode")))

       ;; Back button when drilling down
       (when (or selected-main-category selected-sub-category)
         ($ Box {:mt 2}
            ($ Button {:variant "outlined"
                       :startIcon ($ ArrowBackIcon)
                       :onClick #(if selected-sub-category
                                   (do (set-selected-sub-category! nil)
                                       (set-view-mode! :categories))
                                   (do (set-selected-main-category! nil)
                                       (set-view-mode! :categories)))}
               (tr :actions/back)))))))


(defui ContentBlock
  [{:keys [block]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    (case (:type block)
      :text
      ($ Typography (locale (:content block)))

      :video
      ($ YoutubeIframe {:video-id (:video-id block)
                        :title (when (:title block) (locale (:title block)))})

      :image
      ($ :img {:src (:url block)
               :alt (locale (:alt block))
               :style #js{:maxWidth "100%"}})

      :type-code-explorer
      ($ TypeCodeExplorer {})

      ;; Default case - unknown block type
      ($ Typography {:color "error"} (str "Unknown block type: " (:type block))))))

(defui HelpContent
  [{:keys [title blocks]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    ($ Stack {:direction "column" :spacing 2 :sx #js{:pl 4 :flex 1}}
       ($ Typography {:variant :h6} (locale title))

       ;; Content blocks container
       ($ Stack {:direction "column" :spacing 2}
          (for [block blocks]
            ($ ContentBlock {:key (:block-id block)
                             :block block}))))))

(defui SummaryGrid
  [{:keys [pages on-page-select]}]
  (let [tr     (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    ($ Grid {:container true :spacing 2 :sx #js{:pl 4 :flex 1}}
       ($ Grid {:item true :xs 12}
          ($ Typography
             {:variant "subtitle1"
              :gutterBottom true}
             (tr :help/available-pages)))

       (map-indexed
         (fn [idx {:keys [slug title blocks]}]
           ;; Find the first text block to display as summary
           (let [summary-block (first (filter #(= :text (:type %)) blocks))
                 summary-text (when summary-block (:content summary-block))]
             ($ Grid {:item true :xs 12 :sm 6 :md 4 :key (name slug)}
                ($ Card {:sx #js{:height "100%"
                                :cursor "pointer"
                                :transition "transform 0.2s, box-shadow 0.2s, border-color 0.2s"
                                :boxShadow 3
                                :border "1px solid"
                                :borderColor "divider"
                                :background "linear-gradient(145deg, #ffffff, #f5f5f5)"
                                ":hover" #js{:transform "scale(1.03)"
                                            :boxShadow 6
                                            :borderColor "secondary.main"}} ;; Use secondary color for border on hover
                        :onClick #(on-page-select idx slug)}
                  ($ CardContent
                    ($ Typography
                      {:variant "subtitle2"
                        :gutterBottom true
                        :fontWeight "bold"}
                      (locale title))
                    ($ Typography
                      {:variant "body2"
                        :color "text.secondary"
                        :sx #js{:overflow "hidden"
                              :textOverflow "ellipsis"
                              :display "-webkit-box"
                              :-webkit-line-clamp 3
                              :-webkit-box-orient "vertical"}}
                      (locale summary-text)))))))
         pages))))

(defui HelpMenu
  [{:keys [pages selected-page on-page-select]}]
  (let [tr     (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    ($ Stack {:direction "column"}
       ($ List {:sx #js{:min-width "200px"}}
          (map-indexed
            (fn [idx {:keys [slug title]}]
              ($ :<> {:key (name slug)}
                 ($ Divider)
                 ($ ListItem
                    {:key (name slug)
                     :disablePadding true
                     :component "a"
                     :sx #js{:transition "border-color 0.2s"
                             :border "2px solid"
                             :borderColor (if (= selected-page idx) "secondary.main" "transparent")
                             ":hover" #js{:borderColor "secondary.main"}}}
                    ($ ListItemButton {:on-click #(on-page-select idx slug)
                                       :sx #js{:padding "8px 16px"}}
                       (when (= selected-page idx)
                         ($ ListItemIcon
                            ($ ArrowForwadIosIcon {:color "secondary"})))
                       ($ ListItemText {:primary (locale title)})))))
            pages)))))

(defui HelpSection
  [{:keys [pages] :as _section}]
  (let [selected-page-idx (use-subscribe [::subs/selected-page-idx])
        selected-page (when (and (number? selected-page-idx)
                                 (< selected-page-idx (count pages)))
                        (nth pages selected-page-idx))]
    ($ Stack {:direction "row" :spacing 2}

       ($ HelpMenu {:pages pages
                    :selected-page selected-page-idx
                    :on-page-select #(==> [::events/select-page %1 %2])})

       (if selected-page
         ($ HelpContent selected-page)
         ($ SummaryGrid {:pages pages
                         :on-page-select #(==> [::events/select-page %1 %2])})))))

(defui HelpManageButton []
  (let [has-permission? (use-subscribe [::user-subs/check-privilege nil :help/manage])
        tr              (use-subscribe [:lipas.ui.subs/translator])]
    (when has-permission?
      ($ Button
         {:variant  "contained"
          :color    "secondary"
          :size     "small"
          :sx       #js{:ml 2 :mr 2}
          :startIcon ($ EditIcon)
          :on-click  #(==> [::events/open-edit-mode])}
         (tr :help/manage-content)))))

(defui view
  [{:keys []}]
  (let [sections             (use-subscribe [::subs/help-data])
        mode                 (use-subscribe [::subs/mode])
        dialog-open?         (use-subscribe [::subs/dialog-open?])
        selected-section-idx (use-subscribe [::subs/selected-section-idx])
        selected-page-idx    (use-subscribe [::subs/selected-page-idx])
        selected-section     (when (and sections (number? selected-section-idx)
                                       (< selected-section-idx (count sections)))
                               (nth sections selected-section-idx))
        selected-pages       (when selected-section
                               (:pages selected-section))
        selected-page        (when (and selected-pages (number? selected-page-idx)
                                       (< selected-page-idx (count selected-pages)))
                               (nth selected-pages selected-page-idx))
        tr                   (use-subscribe [:lipas.ui.subs/translator])
        has-permission?      (use-subscribe [::user-subs/check-privilege nil :help/manage])
        locale-kw            (tr)]

    ;; Remove this check to make non-admins see help button
    (when has-permission?
      ($ :<>
         ;; Help button in main UI
         ($ Tooltip {:title (tr :help/headline)}
            ($ IconButton {:size     "large"
                           :on-click #(==> [::events/open-dialog])}
               ($ Help)))

         ;; Help dialog
         ($ Dialog
            {:fullScreen  true
             :keepMounted true
             :open        dialog-open?
             :onClose     #(==> [::events/close-dialog])}

            ($ AppBar {:sx #js {:position "relative"}}
               ($ Toolbar {}
                  ($ Typography {:variant "h6" :color "inherit" :sx #js{:flexGrow 1}}
                     (tr :help/headline))

                  ;; Manage content button (only visible with permission)
                  ($ HelpManageButton)

                  ($ IconButton
                     {:edge    "start"
                      :color   "inherit"
                      :onClick #(==> [::events/close-dialog])}
                     ($ CloseIcon))))

            ($ DialogContent {:sx #js {:display "flex" :flexDirection "column" :gap 2}}

               (when (= :edit mode)
                 ($ manage/view))

               (when (= :read mode)
                 ($ :<>
                    ($ Tabs {:value    selected-section-idx
                             :onChange #(==> [::events/select-section %2 (get-in (nth sections %2) [:slug])])}
                       (map-indexed
                         (fn [idx section]
                           ($ Tab {:key idx
                                  :value idx
                                  :label (locale-kw (:title section))}))
                         sections))

                    ($ Breadcrumbs {:sx #js{:mt 1}}
                       ($ Typography (tr :help/headline))

                       (when selected-section
                         ($ Link {:underline "hover"
                                 :color "inherit"
                                 :on-click #(==> [::events/select-page nil nil])}
                            (locale-kw (:title selected-section))))

                       (when selected-page
                         ($ Link {:underline "hover"
                                 :color "inherit"
                                 :href "/"}
                            (locale-kw (:title selected-page)))))

                    (when selected-section
                      ($ HelpSection selected-section))))))))))
