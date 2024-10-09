(ns lipas.ui.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.events :as events]
   [lipas.ui.mui :as mui]
   [lipas.ui.navbar :as nav]
   [lipas.ui.reminders.views :as reminders]
   [lipas.ui.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn main-panel []
  (let [logged-in?   (<== [::subs/logged-in?])
        notification (<== [::subs/active-notification])
        confirmation (<== [::subs/active-confirmation])
        disclaimer   (<== [::subs/active-disclaimer])
        show-nav?    (<== [::subs/show-nav?])
        view         (<== [::subs/current-view])
        ;; TODO: Make tr available in React Context or something?
        ;; It should not be necessary to pass it into every component.
        ;; Or just an atom somewhere? It doesn't likely even need to be
        ;; reactive as we can force top-level re-render when the value
        ;; changes.
        tr           (<== [::subs/translator])

        width (mui/use-width)]

    ;; TODO: Juho later Would be better to just use responsive sx styles everywhere
    ;; app logic (re-frame event handlers) shouldn't care about screen size?
    (==> [::events/set-screen-size width])


    [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
     [mui/css-baseline]

     [mui/grid
      {:container true
       :style     (merge {:flex-direction "column" :background-color mui/gray3}
                         (when-not show-nav? {:height "100%"}))}

      ;; Drawer
      [nav/drawer {:tr tr :logged-in? logged-in?}]

      ;; Account menu
      [nav/account-menu {:tr tr :logged-in? logged-in?}]

      ;; Navbar
      (when show-nav?
        [mui/grid {:item true :xs 12 :style {:flex "0 1 auto"}}

         [nav/nav {:tr tr :logged-in? logged-in?}]

         ;; Dev-env disclaimer
         (when disclaimer
           [mui/grid {:item true :xs 12 :md 12 :lg 12}
            [mui/card {:square true
                       :style  {:background-color mui/secondary
                                :border-bottom    "2px solid white"}}
             [mui/card-header
              {:style  {:padding-bottom 0}
               :title  (tr :disclaimer/headline)
               :action (r/as-element
                         [mui/icon-button
                          {:on-click #(==> [::events/set-active-disclaimer nil])}
                          [mui/icon "close"]])}]
             [mui/card-content
              [mui/typography {:variant "body2"}
               disclaimer]]]])])

      [mui/mui-theme-provider {:theme mui/jyu-theme-light}

       ;; Main panel
       (when view
         [view])

       ;; Reminders dialog
       [reminders/dialog]

       ;; Global UI-blocking confirmation dialog
       (when confirmation
         [lui/confirmation-dialog confirmation])

       ;; Global ephmeral notifications
       (when notification
         [lui/notification
          {:notification notification
           :on-close     #(==> [::events/set-active-notification nil])}])]]]))
