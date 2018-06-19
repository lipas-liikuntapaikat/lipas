(ns lipas.ui.swimming-pools.slides
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.utils :refer [set-field toggle-dialog]]
            [lipas.ui.utils :refer [<== ==> localize-field ->select-entries]]))

(defn form [{:keys [tr data]}]
  (let [structures (<== [::subs/slide-structures])
        set-field  (partial set-field :dialogs :slide :data)]
    [mui/form-group
     [lui/select {:required true
                  :label (tr :general/structure)
                  :value (:structure data)
                  :items (->select-entries tr :slide-structures structures)
                  :on-change #(set-field :structure %)}]
     [lui/text-field {:label (tr :dimensions/length-m)
                      :adornment (tr :physical-units/m)
                      :type "number"
                      :value (:length-m data)
                      :spec ::schema/slide-length-m
                      :on-change #(set-field :length-m %)}]]))

(defn reset [] #(==> [::events/reset-dialog :slide]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/slide-form])
        close #(toggle-dialog :slide)]
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
  (let [localize (partial localize-field tr :structure :slide-structures)]
    [lui/form-table {:headers [[:structure (tr :general/structure)]
                               [:length-m (tr :dimensions/length-m)]]
                     :items (map localize (vals items))
                     :add-tooltip (tr :slides/add-slide)
                     :edit-tooltip (tr :actions/edit)
                     :delete-tooltip (tr :actions/delete)
                     :on-add (comp #(toggle-dialog :slide) reset)
                     :on-edit #(toggle-dialog :slide (get items (:id %)))
                     :on-delete #(==> [::events/remove-slide %])}]))
