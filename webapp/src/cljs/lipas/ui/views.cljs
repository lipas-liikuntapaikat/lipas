(ns lipas.ui.views
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/CssBaseline$default" :as CssBaseline]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.assistant.views :as assistant]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.notifications :as notifications]
            [lipas.ui.events :as events]
            [lipas.ui.help.views :as help]
            [lipas.ui.login.events :as login-events]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.reminders.views :as reminders]
            [lipas.ui.service-notices.subs :as service-notices-subs]
            [lipas.ui.service-notices.views :as service-notices]
            [lipas.ui.subs :as subs]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn impersonation-banner [{:keys [tr]}]
  (when-let [impersonator (<== [::user-subs/impersonator])]
    (let [user-email (<== [::user-subs/user-data])]
      ;; Full-width top bar. Kept in normal flex-flow (flex-shrink 0) so it
      ;; reserves its own height; the app content below establishes a
      ;; containing block so the map view's fixed sidebar/controls don't cover it.
      [:> Card {:square true
                :style {:background-color "#f57c00"
                        :border-bottom "2px solid white"
                        :width "100%"
                        :flex "0 0 auto"}}
       [:> CardContent
        {:style {:display "flex"
                 :align-items "center"
                 :gap "1em"
                 :flex-wrap "wrap"
                 :padding "0.5em 1em"}}
        [:> Icon "supervised_user_circle"]
        [:> Typography {:variant "body1"}
         (tr :login/impersonating (:email user-email))]
        [:> Button
         {:variant "contained"
          :size "small"
          :on-click #(==> [::login-events/exit-impersonation])}
         (tr :login/exit-impersonation (:email impersonator))]]])))

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
        impersonating? (some? (<== [::user-subs/impersonator]))
        notices?     (seq (<== [::service-notices-subs/visible]))
        ;; The map view hides the navbar and renders its sidebar/controls as
        ;; position:fixed elements anchored to the viewport top, which paint over
        ;; the banners (impersonation, service notices). On those full-bleed
        ;; views we make the banners a top bar and give the content its own
        ;; containing block so the fixed elements anchor below them. Normal
        ;; (navbar) views are untouched.
        full-bleed?  (and (or impersonating? notices?) (not show-nav?))

        width (mui/use-width)]

    ;; TODO: Juho later Would be better to just use responsive sx styles everywhere
    ;; app logic (re-frame event handlers) shouldn't care about screen size?
    (==> [::events/set-screen-size width])

    [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
     [:> CssBaseline]

     ;; On full-bleed views (map) lay the banners out as a top bar and give the
     ;; content its own containing block (contain:layout) so the view's
     ;; position:fixed sidebar/controls anchor below the banners instead of the
     ;; viewport top. On every other case these are inert pass-through wrappers,
     ;; so rendering without banners and on normal views is unchanged.
     [:div
      {:style (if full-bleed?
                {:display "flex" :flex-direction "column" :height "100%"}
                {:height "100%"})}

      ;; Impersonation banner - full-width top bar, above everything below it.
      ;; Visible while impersonating, also on views that hide the navbar (e.g. map).
      [impersonation-banner {:tr tr}]

      ;; Notices about ongoing problems in services Lipas depends on
      [mui/mui-theme-provider {:theme mui/jyu-theme-light}
       [service-notices/banners {:tr tr}]]

      [:div
       {:style (if full-bleed?
                 {:flex "1 1 0" :min-height 0 :contain "layout" :overflow "auto"}
                 {:height "100%"})}

       [:> Grid
        {:container true
         :style     (merge {:flex-direction "column" :background-color mui/gray3}
                           (when-not show-nav? {:height "100%"}))}

        ;; Drawer
        [nav/drawer {:tr tr :logged-in? logged-in?}]

        ;; Account menu
        [nav/account-menu {:tr tr :logged-in? logged-in?}]

        ;; Navbar
        (when show-nav?
          [:> Grid {:item true :xs 12 :style {:flex "0 1 auto"}}

           [nav/nav {:tr tr :logged-in? logged-in?}]

         ;; Dev-env disclaimer
           (when disclaimer
             [:> Grid {:item true :xs 12 :md 12 :lg 12}
              [:> Card {:square true
                        :style  {:background-color mui/secondary
                                 :border-bottom    "2px solid white"}}
               [:> CardHeader
                {:style  {:padding-bottom 0}
                 :title  (tr :disclaimer/headline)
                 :action (r/as-element
                           [:> IconButton
                            {:on-click #(==> [::events/set-active-disclaimer nil])}
                            [:> Icon "close"]])}]
               [:> CardContent
                [:> Typography {:variant "body2"}
                 disclaimer]]]])])

        [mui/mui-theme-provider {:theme mui/jyu-theme-light}

       ;; Help center dialog — global so ?ohje= deep links work on any
       ;; route. Lives inside the light theme so it matches the rest of
       ;; the app content.
         [help/dialog]

       ;; Main panel
         (when view
           [view])

       ;; Reminders dialog
         [reminders/dialog]

       ;; Global UI-blocking confirmation dialog
         (when confirmation
           [dialogs/confirmation-dialog confirmation])]]]]

     ;; Overlays that stay above everything, full-screen dialogs included.
     ;; They are position:fixed without a portal, so they live outside the
     ;; content wrapper: on full-bleed views its contain:layout would trap
     ;; them in its stacking context, beneath the dialogs that MUI portals to
     ;; <body> (e.g. PTV's).
     [mui/mui-theme-provider {:theme mui/jyu-theme-light}

      ;; AI assistant launcher + panel (privilege-gated inside)
      [assistant/view]

      ;; Global ephemeral notifications
      (when notification
        [notifications/notification
         {:notification notification
          :on-close     #(==> [::events/set-active-notification nil])}])]]))
