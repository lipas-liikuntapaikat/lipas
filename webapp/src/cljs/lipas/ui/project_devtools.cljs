(ns lipas.ui.project-devtools
  (:require [lipas.roles :as roles]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]
            [re-frame.db]
            [reagent-dev-tools.core :as dev-tools]
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
                                           :style {:margin-bottom "75px"
                                                   :margin-right "5px"
                                                   :box-shadow "1px 1px 5px rgba(0, 0, 0, 0.5)"}}
                                          "dev"])
                           :panels (into (dev-tools/create-default-panels {:state-atom  re-frame.db/app-db})
                                         [{:key :roles
                                           :label "Roles"
                                           :view [roles]}])}])
    :started))

(defn ^:export enable []
  (js/localStorage.setItem K "true")
  (start!))
