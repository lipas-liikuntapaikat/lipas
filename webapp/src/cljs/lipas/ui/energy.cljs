(ns lipas.ui.energy
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]))

(defn form [{:keys [tr data on-change disabled? cold?]}]
  [mui/form-group
   [lui/text-field {:label     (tr :lipas.energy-consumption/electricity)
                    :disabled  disabled?
                    :type      "number"
                    :value     (:electricity-mwh data)
                    :spec      :lipas.energy-consumption/electricity-mwh
                    :adornment (tr :physical-units/mwh)
                    :on-change #(on-change :electricity-mwh %)}]

   [lui/text-field {:label     (tr :lipas.energy-consumption/heat)
                    :disabled  disabled?
                    :type      "number"
                    :spec      :lipas.energy-consumption/heat-mwh
                    :adornment (tr :physical-units/mwh)
                    :value     (:heat-mwh data)
                    :on-change #(on-change :heat-mwh %)}]

   (when cold?
     [lui/text-field {:label     (tr :lipas.energy-consumption/cold)
                      :disabled  disabled?
                      :type      "number"
                      :spec      :lipas.energy-consumption/cold-mwh
                      :adornment (tr :physical-units/mwh)
                      :value     (:cold-mwh data)
                      :on-change #(on-change :cold-mwh %)}])

   [lui/text-field {:label     (tr :lipas.energy-consumption/water)
                    :disabled  disabled?
                    :type      "number"
                    :spec      :lipas.energy-consumption/water-m3
                    :adornment (tr :physical-units/m3)
                    :value     (:water-m3 data)
                    :on-change #(on-change :water-m3 %)}]])

(comment ;; Example data grid
  {:jan {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :feb {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :mar {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :apr {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :may {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :jun {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :jul {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :aug {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :sep {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :oct {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :nov {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}
   :dec {:electricity-mwh 1233 :heat-mwh 2323 :cold-mwh 2323 :water-m3 5533}})
(defn form-monthly [{:keys [tr data on-change cold?]}]
  [mui/form-group
   [mui/table
    [mui/table-head
     [mui/table-row
      [mui/table-cell (tr :time/month)]
      [mui/table-cell (tr :lipas.energy-consumption/electricity)]
      [mui/table-cell (tr :lipas.energy-consumption/heat)]
      (when cold?
        [mui/table-cell (tr :lipas.energy-consumption/cold)])
      [mui/table-cell (tr :lipas.energy-consumption/water)]]]
    (into [mui/table-body]
          (for [month [:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec]
                :let  [month-data (get data month)]]
            [mui/table-row
             [lui/table-cell (tr (keyword :month month))]
             [lui/table-cell

              ;; Electricity Mwh
              [lui/text-field {:type      "number"
                               :spec      :lipas.energy-consumption/electricity-mwh
                               :value     (:electricity-mwh month-data)
                               :on-change #(on-change month :electricity-mwh %)}]]

             ;; Heat Mwh
             [lui/table-cell
              [lui/text-field {:type      "number"
                               :spec      :lipas.energy-consumption/heat-mwh
                               :value     (:heat-mwh month-data)
                               :on-change #(on-change month :heat-mwh %)}]]

             ;; Cold Mwh
             (when cold?
               [lui/table-cell
                [lui/text-field {:type      "number"
                                 :spec      :lipas.energy-consumption/cold-mwh
                                 :value     (:cold-mwh month-data)
                                 :on-change #(on-change month :cold-mwh %)}]])

             ;; Water m³
             [lui/table-cell
              [lui/text-field {:type      "number"
                               :spec      :lipas.energy-consumption/water-m3
                               :value     (:water-m3 month-data)
                               :on-change #(on-change month :water-m3 %)}]]]))]])

(defn make-headers [tr cold?]
  (filter some?
          [[:year (tr :time/year)]
           [:electricity-mwh (tr :lipas.energy-consumption/electricity)]
           [:heat-mwh (tr :lipas.energy-consumption/heat)]
           (when cold? [:cold-mwh (tr :lipas.energy-consumption/cold)])
           [:water-m3 (tr :lipas.energy-consumption/water)]]))

(defn table [{:keys [tr items read-only? cold?]}]
  [lui/form-table {:headers    (make-headers tr cold?)
                   :items      items
                   :key-fn     :year
                   :read-only? read-only?}])
