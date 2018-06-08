(ns lipas.ui.swimming-pools.energy
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.utils :refer [set-field
                                                   toggle-dialog]]
            [lipas.ui.utils :refer [<== ==> localize-field ->select-entries]]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :energy :data)]
    [mui/form-group

     [lui/date-picker {:label     (tr :time/start)
                       :value     (:start data)
                       :on-change #(set-field :start %)}]

     [lui/date-picker {:label     (tr :time/end)
                       :value     (:end data)
                       :on-change #(set-field :end %)}]

     [lui/text-field {:label     (tr :energy/electricity)
                      :type      "number"
                      :value     (:electicity-mwh data)
                      :spec      ::schema/electricity-mwh
                      :adornment (tr :physical-units/mwh)
                      :on-change #(set-field :electricity-mwh %)}]

     [lui/text-field {:label     (tr :energy/heat)
                      :type      "number"
                      :spec      ::schema/heat-mwh
                      :adornment (tr :physical-units/mwh)
                      :value     (:heat-mwh data)
                      :on-change #(set-field :heat-mwh %)}]

     [lui/text-field {:label     (tr :energy/water)
                      :type      "number"
                      :spec      ::schema/water-m3
                      :adornment (tr :physical-units/m3)
                      :value     (:water-m3 data)
                      :on-change #(set-field :water-m3 %)}]]))

(defn reset [] #(==> [::events/reset-dialog :energy]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/energy-form])
        close #(toggle-dialog :energy)]
    [lui/dialog {:title (if (:id data)
                          (tr :energy/edit-energy-entry)
                          (tr :energy/add-energy-entry))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(toggle-dialog :energy)
                 :on-save (comp reset
                                close
                                #(==> [::events/save-energy data]))}
     [form {:tr tr :data data}]]))

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers        [[:start (tr :time/start)]
                                    [:end   (tr :time/end)]
                                    [:electricity-mwh (tr :energy/electricity)]
                                    [:heat-mwh (tr :energy/heat)]
                                    [:water-m3 (tr :energy/water)]]
                   :items          (reverse (sort-by :start (vals items)))
                   :add-tooltip    (tr :energy/add-energy-entry)
                   :edit-tooltip   (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)
                   :on-add         (comp #(toggle-dialog :energy) reset)
                   :on-edit        #(toggle-dialog :energy (get items (:id %)))
                   :on-delete      #(==> [::events/remove-energy %])}])
