(ns lipas.ui.help.views
  (:require
    ["@mui/icons-material/Close$default" :as CloseIcon]
    ["@mui/icons-material/ArrowForwardIos$default" :as ArrowForwadIosIcon]
    ["@mui/icons-material/Help$default" :as Help]
    ["@mui/icons-material/Edit$default" :as EditIcon]
    ["@mui/material/AppBar$default" :as AppBar]
    ["@mui/material/Breadcrumbs$default" :as Breadcrumbs]
    ["@mui/material/Button$default" :as Button]
    ["@mui/material/Dialog$default" :as Dialog]
    ["@mui/material/DialogContent$default" :as DialogContent]
    ["@mui/material/Divider$default" :as Divider]
    ["@mui/material/IconButton$default" :as IconButton]
    ["@mui/material/Link$default" :as Link]
    ["@mui/material/List$default" :as List]
    ["@mui/material/ListItem$default" :as ListItem]
    ["@mui/material/ListItemButton$default" :as ListItemButton]
    ["@mui/material/ListItemText$default" :as ListItemText]
    ["@mui/material/ListItemIcon$default" :as ListItemIcon]
    ["@mui/material/Stack$default" :as Stack]
    ["@mui/material/Tab$default" :as Tab]
    ["@mui/material/Tabs$default" :as Tabs]
    ["@mui/material/Toolbar$default" :as Toolbar]
    ["@mui/material/Tooltip$default" :as Tooltip]
    ["@mui/material/Typography$default" :as Typography]
    ["@mui/material/Grid$default" :as Grid]
    ["@mui/material/Card$default" :as Card]
    ["@mui/material/CardContent$default" :as CardContent]
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

      ;; Default case - unknown block type
      ($ Typography {:color "error"} (str "Unknown block type: " (:type block))))))

(defui HelpContent
  [{:keys [title blocks]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    ($ Stack {:direction "column" :spacing 2 :sx #js{:pl 4}}
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
