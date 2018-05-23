(ns lipas.ui.swimming-pools.pools
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.components :as lui]
            [lipas.ui.swimming-pools.utils :refer [set-field
                                                   toggle-dialog
                                                   <==
                                                   ==>]]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.events :as events]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :pool :data)]
    [mui/form-group
     [mui/text-field {:required true
                      :label (tr :general/type)
                      :value (:type data)
                      :on-change #(set-field :type %)}]
     [mui/text-field {:type "number"
                      :label (tr :physical-units/temperature-c)
                      :value (:temperature-c data)
                      :on-change #(set-field :temperature-c %)}]
     [mui/text-field {:type "number"
                      :label (tr :dimensions/volume-m3)
                      :value (:volume-m3 data)
                      :on-change #(set-field :volume-m3 %)}]
     [mui/text-field {:type "number"
                      :label (tr :dimensions/area-m2)
                      :value (:area-m2 data)
                      :on-change #(set-field :area-m2 %)}]
     [mui/text-field {:type "number"
                      :label (tr :dimensions/length-m)
                      :value (:length-m data)
                      :on-change #(set-field :length-m %)}]
     [mui/text-field {:type "number"
                      :label (tr :dimensions/width-m)
                      :value (:width-m data)
                      :on-change #(set-field :width-m %)}]
     [mui/text-field {:type "number"
                      :label (tr :dimensions/depth-min-m)
                      :value (:depth-min-m data)
                      :on-change #(set-field :depth-min-m %)}]
     [mui/text-field {:type "number"
                      :label (tr :dimensions/depth-max-m)
                      :value (:depth-max-m data)
                      :on-change #(set-field :depth-max-m %)}]
     [mui/text-field {:label (tr :pools/structure)
                      :value (:structure data)
                      :on-change #(set-field :structure %)
                      :multiline true
                      :rows 5}]]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/pool-form])
        reset #(==> [::events/reset-dialog :pool])
        close #(toggle-dialog :pool)]
    [lui/dialog {:title (if (:id data)
                          (tr :pools/edit-pool)
                          (tr :pools/add-pool))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(toggle-dialog :pool)
                 :on-save (comp reset
                                close
                                #(==> [::events/save-pool data]))}
     [form {:tr tr :data data}]]))

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers [[:type (tr :general/type)]
                             [:temperature-c (tr :physical-units/temperature-c)]
                             [:volume-m3 (tr :dimensions/volume-m3)]
                             [:area-m2 (tr :dimensions/surface-area-m2)]
                             [:length-m (tr :dimensions/length-m)]
                             [:width-m (tr :dimensions/width-m)]
                             [:depth-min-m (tr :dimensions/depth-min-m)]
                             [:depth-max-m (tr :dimensions/depth-max-m)]
                             [:structure (tr :pools/structure)]]
                   :items items
                   :add-tooltip (tr :pools/add-pool)
                   :edit-tooltip (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)
                   :on-add #(toggle-dialog :pool)
                   :on-edit #(toggle-dialog :pool %)
                   :on-delete #(==> [::events/remove-pool %])}])
