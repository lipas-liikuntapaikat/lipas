(ns lipas.ui.ice-stadiums.rinks
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.ice-stadiums.utils :refer [set-field toggle-dialog]]
            [lipas.ui.utils :refer [<== ==>]]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :rink :data)]
    [mui/form-group
     [lui/text-field {:label (tr :dimensions/length-m)
                      :type "number"
                      :value (:length-m data)
                      :on-change #(set-field :length-m %)}]
     [lui/text-field {:label (tr :dimensions/width-m)
                      :type "number"
                      :value (:width-m data)
                      :on-change #(set-field :width-m %)}]]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/rink-form])
        reset #(==> [::events/reset-dialog :rink])
        close #(toggle-dialog :rink)]
    [lui/dialog {:title (if (:id data)
                          (tr :rinks/edit-rink)
                          (tr :rinks/add-rink))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(toggle-dialog :rink)
                 :on-save (comp reset
                                close
                                #(==> [::events/save-rink data]))}
     [form {:tr tr :data data}]]))

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers [[:length-m (tr :dimensions/length-m)]
                             [:width-m (tr :dimensions/width-m)]]
                   :items items
                   :on-add #(toggle-dialog :rink)
                   :on-edit #(toggle-dialog :rink %)
                   :on-delete #(==> [::events/remove-rink %])
                   :add-tooltip (tr :rinks/add-rink)
                   :edit-tooltip (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)}])
