(ns lipas.ui.swimming-pools.slides
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.utils :refer [set-field
                                                   toggle-dialog]]
            [lipas.ui.utils :refer [<== ==>]]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :slide :data)]
    [mui/form-group
     [lui/text-field {:label (tr :general/structure)
                      :value (:structure data)
                      :on-change #(set-field :structure %)}]
     [lui/text-field {:label (tr :dimensions/length-m)
                      :value (:length-m data)
                      :on-change #(set-field :length-m %)}]]))

(defn reset [] #(==> [::events/reset-dialog :slide]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/slide-form])
        close #(toggle-dialog :slide)
        _ (prn data)]
    [lui/dialog {:title (if (:id data)
                          (tr :slides/edit-slide)
                          (tr :slides/add-slide))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(toggle-dialog :slide)
                 :on-save (comp reset
                                close
                                #(==> [::events/save-slide data]))}
     [form {:tr tr :data data}]]))

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers [[:structure (tr :general/structure)]
                             [:length-m (tr :dimensions/length-m)]]
                   :items items
                   :add-tooltip (tr :slides/add-slide)
                   :edit-tooltip (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)
                   :on-add (comp #(toggle-dialog :slide) reset)
                   :on-edit #(toggle-dialog :slide %)
                   :on-delete #(==> [::events/remove-slide %])}])
