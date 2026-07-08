(ns lipas.ui.assistant.views
  (:require
   ["@mui/icons-material/Close$default" :as CloseIcon]
   ["@mui/icons-material/Send$default" :as SendIcon]
   ["@mui/icons-material/SmartToy$default" :as SmartToyIcon]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/Button$default" :as Button]
   ["@mui/material/Card$default" :as Card]
   ["@mui/material/CardActions$default" :as CardActions]
   ["@mui/material/CardContent$default" :as CardContent]
   ["@mui/material/Chip$default" :as Chip]
   ["@mui/material/CircularProgress$default" :as CircularProgress]
   ["@mui/material/Fab$default" :as Fab]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/material/Paper$default" :as Paper]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Tooltip$default" :as Tooltip]
   ["@mui/material/Typography$default" :as Typography]
   ["react-markdown$default" :as ReactMarkdown]
   [clojure.string :as str]
   [lipas.ui.assistant.events :as events]
   [lipas.ui.assistant.subs :as subs]
   [lipas.ui.user.subs :as user-subs]
   [lipas.ui.utils :refer [==>]]
   [re-frame.core :as rf]
   [reagent.core :as r]))

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
  [{:keys [message]}]
  (let [{:keys [role text sources error?]} message
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
      (when (seq sources)
        [:> Stack {:direction "row" :spacing 0.5 :useFlexGap true
                   :sx #js{:flexWrap "wrap" :mt 1}}
         (for [{:keys [id title deep-link]} (take 4 sources)]
           ^{:key id}
           [:> Chip {:label title
                     :size "small"
                     :variant "outlined"
                     :clickable true
                     :onClick #(==> [::events/open-source deep-link])}])])]]))

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
        [:> TextField {:value summary
                       :onChange #(==> [::events/edit-escalation
                                        (.. % -target -value)])
                       :multiline true
                       :minRows 2
                       :maxRows 6
                       :fullWidth true
                       :size "small"}]
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
        thinking? @(rf/subscribe [::subs/thinking?])]
    [:> Paper
     {:elevation 8
      :sx #js{:position "fixed"
              :bottom 88
              :right 16
              :zIndex 1250
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
       "LIPAS-avustaja"]
      [:> IconButton {:size "small"
                      :sx #js{:color "inherit"}
                      :onClick #(==> [::events/toggle-panel])}
       [:> CloseIcon {:fontSize "small"}]]]

     ;; Messages
     [:> Stack {:spacing 1.5
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
        [Message {:message m}])
      (when thinking?
        [:> Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
         [:> CircularProgress {:size 16}]
         [:> Typography {:variant "caption" :color "text.secondary"}
          "Etsin vastausta…"]])
      [EscalationCard]]

     ;; Input
     [:> Box {:sx #js{:display "flex" :gap 1 :p 1.5 :pt 0.5}}
      [:> TextField {:value input
                     :onChange #(==> [::events/set-input (.. % -target -value)])
                     :onKeyDown (fn [e]
                                  (when (and (= "Enter" (.-key e))
                                             (not (.-shiftKey e)))
                                    (.preventDefault e)
                                    (==> [::events/send-message])))
                     :placeholder "Kirjoita kysymys…"
                     :multiline true
                     :maxRows 4
                     :fullWidth true
                     :size "small"}]
      [:> IconButton {:color "secondary"
                      :disabled (str/blank? input)
                      :onClick #(==> [::events/send-message])}
       [:> SendIcon]]]]))

(r/defc view
  "Floating assistant launcher + chat panel. Mounted once at app root;
   rendered only for users with the :ai-assistant/use privilege."
  []
  (let [can-use? @(rf/subscribe [::user-subs/check-privilege nil :ai-assistant/use])
        open? @(rf/subscribe [::subs/open?])]
    (when can-use?
      [:<>
       (when open? [Panel])
       [:> Tooltip {:title "LIPAS-avustaja"}
        [:> Fab {:color "secondary"
                 :size "medium"
                 :sx #js{:position "fixed"
                         :bottom 24
                         :right 16
                         :zIndex 1250}
                 :onClick #(==> [::events/toggle-panel])}
         [:> SmartToyIcon]]]])))
