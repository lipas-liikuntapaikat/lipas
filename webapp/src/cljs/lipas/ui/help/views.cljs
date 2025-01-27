(ns lipas.ui.help.views
  (:require
    ["@mui/icons-material/Close$default" :as CloseIcon]
    ["@mui/icons-material/ArrowForwardIos$default" :as ArrowForwadIosIcon]
    ["@mui/icons-material/Help$default" :as Help]
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
    [lipas.data.help :as help-data]
    [lipas.ui.help.events :as events]
    [lipas.ui.help.subs :as subs]
    [lipas.ui.uix.hooks :refer [use-subscribe]]
    [lipas.ui.utils :as utils :refer [<== ==>]]
    [uix.core :as uix :refer [$ defui]]))

(def sections help-data/sections)

(defui YoutubeIframe
  [{:keys [url]}]
  ($ :iframe
     {:width             "560"
      :height            "315"
      :src               url
      :title             "YouTube video player"
      :frame-border      "0"
      :allow             "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      :referrer-policy   "strict-origin-when-cross-origin"
      :allow-full-screen true}))

(defui HelpContent
  [{:keys [header text videos]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    ($ Stack {:direction "column" :spacing 2 :sx #js{:pl 4}}
       ($ Typography {:variant :h6} (locale header))
       ($ Typography (locale text))

       ;; Videos container
       ($ Stack {:direction "column" :spacing 2}
          (for [[idx url] (map-indexed vector videos)]
            ($ YoutubeIframe {:key idx :url url}))))))

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

       (for [[k {:keys [title text]}] pages]
         ($ Grid {:item true :xs 12 :sm 6 :md 4 :key k}
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
                     :onClick #(on-page-select k)}
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
                     (locale text)))))))))

(defui HelpMenu
  [{:keys [pages selected-page on-page-select]}]
  (let [tr     (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)]
    ($ Stack {:direction "column"}
       ($ List {:sx #js{:min-width "200px"}}
          (interpose ($ Divider)
                    (for [[k {:keys [title]}] pages]
                      ($ ListItem
                         {:key k
                          :disablePadding true
                          :component "a"
                          :sx #js{:transition "border-color 0.2s"
                                  :border "2px solid"
                                  :borderColor (if (= selected-page k) "secondary.main" "transparent")
                                  ":hover" #js{:borderColor "secondary.main"}}}
                         ($ ListItemButton {:on-click #(on-page-select k)
                                            :sx #js{:padding "8px 16px"}}
                            (when (= selected-page k)
                              ($ ListItemIcon
                                 ($ ArrowForwadIosIcon {:color "secondary"})))
                            ($ ListItemText {:primary (locale title)})))))))))

(defui HelpSection
  [{:keys [pages] :as _section}]
  (let [selected-page (use-subscribe [::subs/selected-page])]
    ($ Stack {:direction "row" :spacing 2}

       ($ HelpMenu {:pages pages
                    :selected-page selected-page
                    :on-page-select #(==> [::events/select-page %])})

       (if selected-page
         ($ HelpContent (get pages selected-page))
         ($ SummaryGrid {:pages pages
                         :on-page-select #(==> [::events/select-page %])})))))

(defui view
  [{:keys []}]
  (let [dialog-open?     (use-subscribe [::subs/dialog-open?])
        selected-section (use-subscribe [::subs/selected-section])
        selected-page    (use-subscribe [::subs/selected-page])
        tr               (use-subscribe [:lipas.ui.subs/translator])
        locale-kw        (tr)]

    (tap> {:selected-tab  selected-section
           :selected-page selected-page})
    ($ :<>

       ($ Tooltip {:title (tr :help/headline)}
          ($ IconButton {:size "large"
                         :on-click #(==> [::events/open-dialog])}
             ($ Help)))

       ($ Dialog
          {:fullScreen  true
           :keepMounted true
           :open        dialog-open?
           :onClose     #(==> [::events/close-dialog])}

          ($ AppBar {:sx #js {:position "relative"}}

             ($ Toolbar {}

                ($ Typography {:variant "h6" :color "inherit" :sx #js{:flexGrow 1}}
                   (tr :help/headline))

                ($ IconButton
                   {:edge    "start"
                    :color   "inherit"
                    :onClick #(==> [::events/close-dialog])}
                   ($ CloseIcon))))

          ($ DialogContent {:sx #js {:display "flex" :flexDirection "column" :gap 2}}

             ($ Tabs {:value    selected-section
                      :onChange #(==> [::events/select-section (keyword %2)])}
                (for [[k {:keys [title]}] sections]
                  ($ Tab {:key k :value k :label (locale-kw title)})))

             ($ Breadcrumbs {:sx #js{:mt 1}}
                ($ Typography (tr :help/headline))

                ($ Link {:underline "hover" :color "inherit" :on-click #(==> [::events/select-page nil])}
                   (get-in sections [selected-section :title locale-kw]))

                (when selected-page
                  ($ Link {:underline "hover" :color "inherit" :href "/"}
                     (get-in sections [selected-section :pages selected-page :title locale-kw]))))

             #_($ Typography {:variant "h6"} selected-section)

             ($ HelpSection (get sections selected-section)))))))
