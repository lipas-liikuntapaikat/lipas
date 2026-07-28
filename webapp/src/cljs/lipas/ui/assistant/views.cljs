(ns lipas.ui.assistant.views
  (:require ["@mui/icons-material/AddComment$default" :as AddCommentIcon]
            ["@mui/icons-material/Close$default" :as CloseIcon]
            ["@mui/icons-material/Done$default" :as DoneIcon]
            ["@mui/icons-material/ExpandLess$default" :as ExpandLessIcon]
            ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
            ["@mui/icons-material/PlayArrow$default" :as PlayArrowIcon]
            ["@mui/icons-material/Send$default" :as SendIcon]
            ["@mui/icons-material/SmartToy$default" :as SmartToyIcon]
            ;; clj-kondo false positive: it resolves the bare symbol `Box`
            ;; against a builtin and never sees the `[:> Box ...]` usages
            ;; below as referencing this alias, so it always reports the
            ;; require as unused even though Box is used extensively.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardActions$default" :as CardActions]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            ["react-markdown$default" :as ReactMarkdown]
            [clojure.string :as str]
            [lipas.ui.assistant.events :as events]
            [lipas.ui.assistant.subs :as subs]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.help.subs :as help-subs]
            [lipas.ui.subs :as root-subs]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [==>]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

(defn- md-link
  "Markdown link renderer: ?ohje= links open the in-app help center,
   everything else opens in a new tab."
  [^js props]
  (let [href (.-href props)]
    (r/as-element
      [:a {:href href
           :on-click (fn [e]
                       (when (str/starts-with? (str href) "?ohje=")
                         (.preventDefault e)
                         (==> [::events/open-source href])))
           :target (when-not (str/starts-with? (str href) "?ohje=") "_blank")
           :rel "noopener"}
       (.-children props)])))

(r/defc Message
  [{:keys [message idx]}]
  (let [[sources-open? set-sources-open!] (hooks/use-state false)
        {:keys [role text sources actions error?]} message
        user? (= "user" role)]
    [:> Box {:sx #js{:display "flex"
                     :justifyContent (if user? "flex-end" "flex-start")}}
     [:> Paper {:elevation 1
                :sx #js{:p 1.5
                        :maxWidth "85%"
                        :bgcolor (cond
                                   error? "error.light"
                                   user? "secondary.light"
                                   :else "grey.100")}}
      (if user?
        [:> Typography {:variant "body2"} text]
        [:> Box {:sx #js{:typography "body2"
                         "& p" #js{:mt 0 :mb 1}
                         "& p:last-child" #js{:mb 0}
                         "& ul, & ol" #js{:mt 0 :mb 1 :pl 2.5}
                         "& a" #js{:color "secondary.dark"}}}
         [:> ReactMarkdown {:components #js{:a md-link}} text]])
      ;; Sources collapsed by default — they earn their space on demand.
      (when (seq sources)
        (let [shown (take 4 sources)]
          [:<>
           [:> Button {:size "small"
                       :variant "text"
                       :sx #js{:textTransform "none"
                               :color "text.secondary"
                               :mt 0.5
                               :p 0
                               :minWidth 0}
                       :endIcon (r/as-element
                                  (if sources-open?
                                    [:> ExpandLessIcon {:fontSize "small"}]
                                    [:> ExpandMoreIcon {:fontSize "small"}]))
                       :onClick #(set-sources-open! (not sources-open?))}
            (str "Lähteet (" (count shown) ")")]
           [:> Collapse {:in sources-open? :timeout 150}
            [:> Stack {:direction "row" :spacing 0.5 :useFlexGap true
                       :sx #js{:flexWrap "wrap" :mt 0.5}}
             (for [{:keys [id title deep-link]} shown]
               ^{:key id}
               [:> Chip {:label title
                         :size "small"
                         :variant "outlined"
                         :clickable true
                         :onClick #(==> [::events/open-source deep-link])}])]]]))
      ;; Actions the assistant proposes — nothing runs until clicked.
      (when (seq actions)
        [:> Stack {:direction "row" :spacing 0.5 :useFlexGap true
                   :sx #js{:flexWrap "wrap" :mt 1}}
         (for [[action-idx {:keys [label executed?] :as action}]
               (map-indexed vector actions)]
           ^{:key action-idx}
           [:> Button {:size "small"
                       :variant (if executed? "text" "contained")
                       :color "secondary"
                       :disabled executed?
                       :startIcon (r/as-element
                                    (if executed?
                                      [:> DoneIcon {:fontSize "small"}]
                                      [:> PlayArrowIcon {:fontSize "small"}]))
                       :sx #js{:textTransform "none"}
                       :onClick #(==> [::events/run-action idx action-idx action])}
            label])])]]))

(r/defc EscalationCard []
  (let [summary @(rf/subscribe [::subs/pending-escalation])
        in-progress? @(rf/subscribe [::subs/escalation-in-progress?])
        email (:email @(rf/subscribe [::user-subs/user-data]))]
    (when summary
      [:> Card {:variant "outlined"
                :sx #js{:borderColor "secondary.main"}}
       [:> CardContent {:sx #js{:pb 0}}
        [:> Typography {:variant "subtitle2" :gutterBottom true}
         "Tukipyyntö LIPAS-tuelle"]
        [text-fields/text-field
         {:value summary
          :on-change #(==> [::events/edit-escalation (or % "")])
          :multiline true
          :rows 4
          :fullWidth true
          :size "small"
          :variant "outlined"}]
        [:> Typography {:variant "caption" :color "text.secondary"}
         (str "Kysymyksesi ja keskustelun tiivistelmä lähetetään LIPAS-tuelle "
              "(lipasinfo@jyu.fi). Saat vastauksen osoitteeseen " email ".")]]
       [:> CardActions
        [:> Button {:size "small"
                    :variant "contained"
                    :color "secondary"
                    :disabled in-progress?
                    :onClick #(==> [::events/confirm-escalation])}
         "Lähetä tukipyyntö"]
        [:> Button {:size "small"
                    :disabled in-progress?
                    :onClick #(==> [::events/dismiss-escalation])}
         "Peruuta"]]])))

(r/defc Panel []
  (let [messages @(rf/subscribe [::subs/messages])
        input @(rf/subscribe [::subs/input])
        thinking? @(rf/subscribe [::subs/thinking?])
        pending-escalation @(rf/subscribe [::subs/pending-escalation])
        scroll-ref (hooks/use-ref nil)]
    ;; Keep the newest message — and especially the escalation card, which
    ;; appears below the answer — in view.
    (hooks/use-effect
      (fn []
        (when-let [el (.-current scroll-ref)]
          (set! (.-scrollTop el) (.-scrollHeight el))))
      [(count messages) thinking? pending-escalation])
    [:> Paper
     {:elevation 8
      ;; bottom clears the map's bottom-right control cluster; zIndex
      ;; above MUI modal (1300) so the assistant stays usable inside
      ;; fullscreen dialogs (help center); below snackbar (1400).
      :sx #js{:position "fixed"
              :bottom 88
              :right 16
              :zIndex 1350
              :width #js{:xs "calc(100vw - 32px)" :sm 420}
              :height "min(600px, calc(100vh - 120px))"
              :display "flex"
              :flexDirection "column"}}

     ;; Header
     [:> Box {:sx #js{:display "flex"
                      :alignItems "center"
                      :p 1.5
                      :bgcolor "primary.main"
                      :color "primary.contrastText"}}
      [:> SmartToyIcon {:fontSize "small" :sx #js{:mr 1}}]
      [:> Typography {:variant "subtitle1" :sx #js{:flexGrow 1}}
       "Lipastaja"]
      (when (seq messages)
        [:> Tooltip {:title "Uusi keskustelu"}
         [:> IconButton {:size "small"
                         :sx #js{:color "inherit"}
                         :onClick #(==> [::events/new-chat])}
          [:> AddCommentIcon {:fontSize "small"}]]])
      [:> IconButton {:size "small"
                      :sx #js{:color "inherit"}
                      :onClick #(==> [::events/toggle-panel])}
       [:> CloseIcon {:fontSize "small"}]]]

     ;; Messages
     [:> Stack {:ref scroll-ref
                :spacing 1.5
                :sx #js{:flexGrow 1
                        :overflowY "auto"
                        :p 1.5}}
      (when (empty? messages)
        [:> Typography {:variant "body2" :color "text.secondary"}
         "Kysy LIPAS-järjestelmän käytöstä, liikuntapaikkatyypeistä tai
          tietojen ylläpidosta. Vastaan ohjeiden pohjalta ja linkitän
          lähteet."])
      (for [[idx m] (map-indexed vector messages)]
        ^{:key idx}
        [Message {:message m :idx idx}])
      (when thinking?
        [:> Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
         [:> CircularProgress {:size 16}]
         [:> Typography {:variant "caption" :color "text.secondary"}
          "Etsin vastausta…"]])
      [EscalationCard]]

     ;; Input — the shared text-field routes typing through reagent's
     ;; patched textarea (caret survives async re-render).
     [:> Box {:sx #js{:display "flex" :gap 1 :p 1.5 :pt 0.5}}
      [text-fields/text-field
       {:value input
        :on-change #(==> [::events/set-input (or % "")])
        :onKeyDown (fn [e]
                     (when (and (= "Enter" (.-key e))
                                (not (.-shiftKey e)))
                       (.preventDefault e)
                       (==> [::events/send-message])))
        :placeholder "Kirjoita kysymys…"
        :multiline true
        :rows 2
        :fullWidth true
        :size "small"
        :variant "outlined"}]
      [:> IconButton {:color "secondary"
                      :disabled (str/blank? input)
                      :onClick #(==> [::events/send-message])}
       [:> SendIcon]]]]))

(r/defc LauncherFab
  ;; fixed? true = floating in the viewport corner; false = in normal
  ;; flow (embedded in the map-control container). zIndex sits above MUI
  ;; modal (1300) so the launcher works inside fullscreen dialogs (help
  ;; center), below snackbar (1400).
  [{:keys [fixed?]}]
  [:> Tooltip {:title "Lipastaja"}
   [:> Fab {:color "secondary"
            :size "medium"
            :sx (if fixed?
                  #js{:position "fixed"
                      :bottom 24
                      :right 16
                      :zIndex 1350}
                  #js{})
            :onClick #(==> [::events/toggle-panel])}
    [:> SmartToyIcon]]])

(r/defc MapLauncher
  "Assistant launcher for the map view's bottom-right control container —
   the corner itself is crowded there. Renders nothing without the
   :ai-assistant/use privilege."
  []
  (let [can-use? @(rf/subscribe [::user-subs/check-privilege nil :ai-assistant/use])]
    (when can-use?
      [LauncherFab {:fixed? false}])))

(r/defc view
  "Floating assistant launcher + chat panel. Mounted once at app root;
   rendered only for users with the :ai-assistant/use privilege."
  []
  (let [can-use? @(rf/subscribe [::user-subs/check-privilege nil :ai-assistant/use])
        open? @(rf/subscribe [::subs/open?])
        map-route? @(rf/subscribe [::subs/map-route?])
        help-open? @(rf/subscribe [::help-subs/dialog-open?])
        ;; Base-module sub — the real PTV subs live in the lazy :ptv module.
        ptv-open? @(rf/subscribe [::root-subs/ptv-dialog-open?])]
    (when can-use?
      [:<>
       (when open? [Panel])
       ;; On the map route the launcher lives inside the bottom-right
       ;; map-control container (see MapLauncher) — except when a
       ;; fullscreen dialog (help center, PTV) covers the map controls,
       ;; in which case the floating Fab takes over again.
       (when (or (not map-route?) help-open? ptv-open?)
         [LauncherFab {:fixed? true}])])))
