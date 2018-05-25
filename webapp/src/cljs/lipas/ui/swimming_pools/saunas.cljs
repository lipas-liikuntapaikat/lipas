(ns lipas.ui.swimming-pools.saunas
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.utils :refer [set-field
                                                   toggle-dialog]]
            [lipas.ui.utils :refer [<== ==>]]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :sauna :data)]
    [mui/form-group
     [mui/text-field {:label (tr :general/type)
                      :value (:type data)
                      :on-change #(set-field :type %)}]
     [lui/checkbox {:label (tr :saunas/women)
                    :on-change #(set-field :women %)
                    :value (:women data)}]
     [lui/checkbox {:label (tr :saunas/men)
                    :on-change #(set-field :men %)
                    :value (:men data)}]]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/sauna-form])
        reset #(==> [::events/reset-dialog :sauna])
        close #(toggle-dialog :sauna)]
    [lui/dialog {:title (if (:id data)
                          (tr :saunas/edit-sauna)
                          (tr :saunas/add-sauna))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(toggle-dialog :sauna)
                 :on-save (comp reset
                                close
                                #(==> [::events/save-sauna data]))}
     [form {:tr tr :data data}]]))

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers [[:type (tr :general/type)]
                             [:women (tr :saunas/women)]
                             [:men (tr :saunas/men)]]
                   :items items
                   :add-tooltip (tr :saunas/add-sauna)
                   :edit-tooltip (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)
                   :on-add #(toggle-dialog :sauna)
                   :on-edit #(toggle-dialog :sauna %)
                   :on-delete #(==> [::events/remove-sauna %])}])
