(ns lipas.ui.project-devtools
  (:require [lipas.roles :as roles]
            [lipas.ui.subs :as ui-subs]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]
            [re-frame.db]
            [reagent-dev-tools.core :as dev-tools]
            [reagent.core :as reagent]
            [reagent.dom.client :as rdomc]))

(rf/reg-event-db ::set-privilege-override
  (fn [db [_ k value]]
    (if (some? value)
      (assoc-in db [::privilege-override k] value)
      (update db ::privilege-override dissoc k))))

(rf/reg-event-db ::reset-overrides
  (fn [db _]
    (dissoc db ::privilege-override)))

(rf/reg-sub ::privilege-override
  (fn [db [_ k]]
    (get-in db [::privilege-override k])))

(defn override [{:keys [k value]}]
  (let [x @(rf/subscribe [::privilege-override k])]
    [:input
     {:type "checkbox"
      :checked (= value x)
      :disabled (and (some? x)
                     (not= value x))
      :on-change (fn [_]
                   (rf/dispatch [::set-privilege-override k (if (= value x)
                                                              nil
                                                              value)]))}]))

(defn roles []
  [:div
   [:h4 "User roles"]
   [:ul
    (for [[i role] (map-indexed vector @(rf/subscribe [::user-subs/roles]))]
      [:li {:key i} role])]

   [:h2 "Override privileges"]
   [:button
    {:on-click (fn [_e] (rf/dispatch [::reset-overrides]))}
    "Reset"]
   [:table
    [:thead
     [:tr
      [:th "Privilege"]
      [:th "User (empty role context)"]
      [:th "Override On"]
      [:th "Override Off"]
      [:th "Effective value"]]]
    [:tbody
     (doall
       (for [[k _x] (sort-by first roles/privileges)]
         [:tr
          {:key (str (namespace k) "/" (name k))}
          [:td (namespace k) "/" (name k)]
          [:td (if @(rf/subscribe [::user-subs/check-privilege nil k true])
                 "Yes"
                 "No")]
          [:td
           [override {:k k
                      :value true}]]
          [:td
           [override {:k k
                      :value false}]]
          [:td (if @(rf/subscribe [::user-subs/check-privilege nil k])
                 "Yes"
                 "No")]]))]]])

(defn- impersonate-selector []
  (reagent/with-let [_ (rf/dispatch [:lipas.ui.lazy/load-then :admin
                                     [:lipas.ui.admin.events/get-users]])
                     email* (reagent/atom "")]
    (let [users (vals @(rf/subscribe [::ui-subs/admin-users]))
          by-email (into {} (keep (fn [u] (when (:email u) [(:email u) u])) users))]
      [:div
       [:p (count users) " users loaded"]
       [:input {:type "text"
                :list "impersonate-users-datalist"
                :placeholder "user@example.com"
                :size 40
                :value @email*
                :on-change (fn [e] (reset! email* (.. e -target -value)))}]
       [:datalist {:id "impersonate-users-datalist"}
        (for [email (sort (keys by-email))]
          [:option {:key email :value email}])]
       [:button
        {:disabled (nil? (get by-email @email*))
         :on-click (fn [_]
                     (rf/dispatch [:lipas.ui.login.events/impersonate
                                   (:id (get by-email @email*))]))}
        "Impersonate"]])))

(defn impersonation []
  (let [login @(rf/subscribe [::user-subs/user-data])
        impersonator @(rf/subscribe [::user-subs/impersonator])
        ;; Gate on the real privilege (overrides disabled) - the backend
        ;; enforces it anyway, overrides would only fake the UI.
        can-impersonate? @(rf/subscribe [::user-subs/check-privilege nil :users/impersonate true])]
    [:div
     [:h4 "Current identity"]
     [:p [:b (or (:email login) "(not logged in)")]]
     (cond
       impersonator
       [:div
        [:p "Impersonating on behalf of " [:b (:email impersonator)]]
        [:button
         {:on-click (fn [_] (rf/dispatch [:lipas.ui.login.events/exit-impersonation]))}
         "Exit impersonation"]]

       can-impersonate?
       [impersonate-selector]

       :else
       [:p "Log in with a user that has the :users/impersonate privilege (admin) to use this."])]))

(def K "lipas.ui.dev-tools")

(defonce react-root (delay
                      (let [el (.createElement js/document "div")]
                        (set! (.-id el) "rdt")
                        (.appendChild (.-body js/document) el)
                        (rdomc/create-root el))))

(defn start! []
  (when (or (= "localhost" (.. js/window -location -hostname))
            (and (= "true" (js/localStorage.getItem K))
                 (not (utils/prod?))))
    (rdomc/render
      @react-root
      [dev-tools/dev-tool {:toggle-btn (fn [open-fn]
                                         [:button.reagent-dev-tools__nav-li-a.reagent-dev-tools__toggle-btn
                                          {:on-click open-fn
                                           ;; right edge, vertically centered: both bottom
                                           ;; corners are taken (assistant Fab, map controls)
                                           :style {:position "fixed"
                                                   :top "50%"
                                                   :right "0px"
                                                   :bottom "auto"
                                                   :left "auto"
                                                   :transform "translateY(-50%)"
                                                   :box-shadow "1px 1px 5px rgba(0, 0, 0, 0.5)"}}
                                          "dev"])
                           :panels (into (dev-tools/create-default-panels {:state-atom  re-frame.db/app-db})
                                         [{:key :roles
                                           :label "Roles"
                                           :view [roles]}
                                          {:key :impersonate
                                           :label "Impersonate"
                                           :view [impersonation]}])}])
    :started))

(defn ^:export enable []
  (js/localStorage.setItem K "true")
  (start!))
