(ns lipas.ui.components
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.mui-icons :as mui-icons]
            [reagent.core :as r]))

(defn year-selector [{:keys [label value on-change]}]
  [mui/text-field {:label label
                   :type "number"
                   :select true
                   :on-change on-change
                   :value value}
   (for [year (range 2000 (inc (.getFullYear (js/Date.))))]
     [mui/menu-item {:key year
                     :value year}
      year])])

(defn checkbox [{:keys [label value on-change]}]
  [mui/form-control-label
   {:label label
    :control (r/as-element
              [mui/checkbox
               {:value (or (str value) "-")
                :checked value
                :on-change #(on-change (clj->js {:target
                                                 {:value %2}}))}])}])

(defn text-field-unit [{:keys [label value unit on-change]}]
  [mui/text-field {:label label
                   :type "number"
                   :value value
                   :on-change on-change
                   :Input-props
                   {:end-adornment
                    (r/as-element
                     [mui/input-adornment unit])}}])

(defn form-table [{:keys [headers
                          items
                          add-tooltip
                          edit-tooltip
                          delete-tooltip
                          on-add
                          on-edit
                          on-delete]}]
  [mui/grid {:container true
             :spacing 8
             :justify "flex-end"}
   [mui/grid {:item true :xs 12}
    [:div {:style {:overflow-x "auto"}}
     [mui/table
      [mui/table-head
       [mui/table-row
        (for [[idx [_ header]] (map-indexed vector headers)]
          ^{:key idx} [mui/table-cell header])
        [mui/table-cell ""]]]
      [mui/table-body
       (for [[idx item] (map-indexed vector items)]
         ^{:key idx} [mui/table-row
                      (for [k (keys headers)]
                        ^{:key k} [mui/table-cell {:padding "dense"}
                                   (let [v (get item k)]
                                     (cond
                                       (true? v) "✓"
                                       :else v))])
                      [mui/table-cell {:numeric true
                                       :padding "none"}
                       [mui/tooltip {:title (or edit-tooltip "")
                                     :placement "top"}
                        [mui/icon-button {:on-click #(on-edit item)}
                         [mui-icons/edit]]]
                       [mui/tooltip {:title (or delete-tooltip "")
                                     :placement "top"}
                        [mui/icon-button {:on-click #(on-delete item)}
                         [mui-icons/delete]]]]])]]]]
   [mui/grid {:item true
              :xs 2
              :style {:text-align "right"} }
    [mui/tooltip {:title (or add-tooltip "")
                  :placement "left"}
     [mui/button {:style {:margin-top "0.5em"}
                  :on-click on-add
                  :variant "fab"
                  :color "secondary"}
      [mui-icons/add]]]]])

(defn dialog [{:keys [title
                      on-save
                      on-close
                      save-label
                      cancel-label]} content]
  [mui/dialog {:open true
               :full-width true
               :on-close on-close}
   [mui/dialog-title title]
   [mui/dialog-content content]
   [mui/dialog-actions
    [mui/button {:on-click on-close}
     cancel-label]
    [mui/button {:on-click on-save}
     save-label]]])

(defn form-card [{:keys [title]} content]
  [mui/grid {:item true
             :xs 12
             :md 12}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    [mui/card-content content]]])

(defn select [{:keys [label value items on-change]}]
  [mui/text-field {:select true
                   :label label
                   :value value
                   :on-change on-change}
   (for [i items]
     [mui/menu-item {:key i
                     :value i}
      i])])

(defn notification [{:keys [notification on-close]}]
  [mui/snackbar {:key (gensym)
                 :auto-hide-duration 5000
                 :open true
                 :anchor-origin {:vertical "top"
                                 :horizontal "right"}
                 :on-close on-close}
   [mui/snackbar-content {:message (:message notification)
                          :action (r/as-element
                                   [mui/icon-button {:key "close"
                                                     :on-click on-close
                                                     :color "secondary"}
                                    (if (:success? notification)
                                      [mui-icons/done]
                                      [mui-icons/warning])])}]])
