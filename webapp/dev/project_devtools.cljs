(ns project-devtools
  (:require [lipas.ui.user.subs :as user-subs]
            [re-frame.core :as rf]
            [re-frame.db]
            [reagent-dev-tools.core :as dev-tools]
            [lipas.roles :as roles]))

(rf/reg-event-db ::set-privilege-override
  (fn [db [_ k value]]
    (if (some? value)
      (assoc-in db [::privilege-override k] value)
      (update db ::privilege-override dissoc k))))

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
    (for [{:as role} @(rf/subscribe [::user-subs/roles])]
      [:li {:key role} role])]

   [:h2 "Override privileges"]
   [:table
    [:thead
     [:th "Privilege"]
     [:th "User (empty role context)"]
     [:th "Override On"]
     [:th "Override Off"]
     [:th "Effective value"]]
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

(dev-tools/start!
  {:state-atom re-frame.db/app-db
   :panels [{:key :roles
             :label "Roles"
             :view [roles]}]})
