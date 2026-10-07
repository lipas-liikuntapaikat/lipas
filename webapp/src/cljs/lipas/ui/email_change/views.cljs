(ns lipas.ui.email-change.views
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.email-change.events :as events]
            [lipas.ui.email-change.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [==>]]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn- notice [text]
  [:> Paper {:style {:background-color mui/gray3 :padding "1em"}}
   [:> Typography {:variant "body2"} text]])

(r/defc main []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        {:keys [status type]} @(rf/subscribe [::subs/confirmation])]
    [:> Grid {:container true :justify-content "center" :style {:padding "1em"}}
     [:> Grid {:item true :xs 12 :md 8 :lg 6}
      [:> Card {:square true}
       [:> CardHeader {:title (tr :lipas.user/email-change-headline)}]
       [:> CardContent
        (case status
          :pending [notice (tr :lipas.user/email-change-confirming)]
          :done [:> Stack {:spacing 2}
                 [notice (tr :lipas.user/email-change-done)]
                 [:> Button {:variant "contained"
                             :color "secondary"
                             :sx {:align-self "flex-start"}
                             :on-click #(==> [::events/go-to-login])}
                  (tr :register/go-to-login)]]
          :error [notice (if (= "email-conflict" type)
                           (tr :error/email-conflict)
                           (tr :lipas.user/email-change-failed))]
          nil)]]]]))
