(ns lipas.ui.energy
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]))

(defn form [{:keys [tr data on-change]}]
  [mui/form-group

   ;; [lui/date-picker {:label     (tr :time/start)
   ;;                   :value     (:start data)
   ;;                   :on-change #(set-field :start %)}]

   ;; [lui/date-picker {:label     (tr :time/end)
   ;;                   :value     (:end data)
   ;;                   :on-change #(set-field :end %)}]

   [lui/text-field {:label     (tr :energy/electricity)
                    :type      "number"
                    :value     (:electricity-mwh data)
                    :spec      ::schema/electricity-mwh
                    :adornment (tr :physical-units/mwh)
                    :on-change #(on-change :electricity-mwh %)}]

   [lui/text-field {:label     (tr :energy/heat)
                    :type      "number"
                    :spec      ::schema/heat-mwh
                    :adornment (tr :physical-units/mwh)
                    :value     (:heat-mwh data)
                    :on-change #(on-change :heat-mwh %)}]

   [lui/text-field {:label     (tr :energy/water)
                    :type      "number"
                    :spec      ::schema/water-m3
                    :adornment (tr :physical-units/m3)
                    :value     (:water-m3 data)
                    :on-change #(on-change :water-m3 %)}]])


(defn table [{:keys [tr items read-only?]}]
  [lui/form-table {:headers        [[:year (tr :time/year)]
                                    [:electricity-mwh (tr :energy/electricity)]
                                    [:heat-mwh (tr :energy/heat)]
                                    [:water-m3 (tr :energy/water)]]
                   :items          (reverse (sort-by :year items))
                   :read-only?     read-only?}])
