(ns lipas.ui.components.notifications
  (:require ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Snackbar$default" :as Snackbar]
            ["@mui/material/SnackbarContent$default" :as SnackbarContent]
            [reagent.core :as r]))

;; Three severities supported. New call sites should set `:severity` on the
;; notification map (`:success` / `:error` / `:warning`). Legacy `:success?`
;; (true/false) still works and maps to `:success`/`:error` respectively.
(def ^:private severity-bg
  {:success "#43a047"
   :error   "#d32f2f"
   :warning "#ed6c02"})

(def ^:private severity-icon
  ;; The Icon shown on the action button. Sticky notifications override this
  ;; with "close" since the user must explicitly dismiss them.
  {:success "done"
   :error   "warning"
   :warning "warning"})

(defn- resolve-severity [{:keys [severity success?]}]
  (or severity (if success? :success :error)))

(defn notification [{:keys [notification on-close]}]
  ;; `:sticky?` on the notification map opts out of the 5s auto-hide and
  ;; requires the user to click the explicit X (close icon). Click-away is
  ;; also suppressed so the user can read longer actionable messages without
  ;; losing them. Escape still dismisses.
  ;;
  ;; `:title` (optional) is rendered bold above `:message`. Use it for long
  ;; actionable notifications where a one-line summary helps the user scan;
  ;; otherwise just set `:message`.
  (let [sticky?  (:sticky? notification)
        severity (resolve-severity notification)
        title    (:title notification)
        body     (:message notification)]
    [:> Snackbar
     {:key                (gensym)
      :auto-hide-duration (when-not sticky? 5000)
      :open               true
      :anchor-origin      {:vertical "top" :horizontal "right"}
      :on-close           (fn [_e reason]
                            (when-not (and sticky? (= reason "clickaway"))
                              (on-close)))}
     [:> SnackbarContent
      {:style   {:background-color (severity-bg severity)}
       :message (r/as-element
                  (if title
                    [:div
                     [:div {:style {:font-weight 700 :margin-bottom "0.4em"}} title]
                     [:div body]]
                    body))
       :action  (r/as-element
                  [:> IconButton
                   {:key      "close"
                    :on-click on-close
                    :style    {:color "white"}}
                   [:> Icon (if sticky? "close" (severity-icon severity))]])}]]))
