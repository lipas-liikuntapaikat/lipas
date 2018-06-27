(ns lipas.ui.energy
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]))

(defn form [{:keys [tr data on-change disabled?]}]
  [mui/form-group
   [lui/text-field {:label     (tr :energy/electricity)
                    :disabled  disabled?
                    :type      "number"
                    :value     (:electricity-mwh data)
                    :spec      ::schema/electricity-mwh
                    :adornment (tr :physical-units/mwh)
                    :on-change #(on-change :electricity-mwh %)}]

   [lui/text-field {:label     (tr :energy/heat)
                    :disabled  disabled?
                    :type      "number"
                    :spec      ::schema/heat-mwh
                    :adornment (tr :physical-units/mwh)
                    :value     (:heat-mwh data)
                    :on-change #(on-change :heat-mwh %)}]

   [lui/text-field {:label     (tr :energy/water)
                    :disabled  disabled?
                    :type      "number"
                    :spec      ::schema/water-m3
                    :adornment (tr :physical-units/m3)
                    :value     (:water-m3 data)
                    :on-change #(on-change :water-m3 %)}]])

(comment ;; Example data grid
  {1  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   2  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   3  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   4  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   5  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   6  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   7  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   8  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   9  {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   10 {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   11 {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}
   12 {:electricity-mwh 1233 :heat-mwh 2323 :water-m3 5533}})
(defn form-monthly [{:keys [tr data on-change]}]
  [mui/form-group
   [mui/table
    [mui/table-head
     [mui/table-row
      [mui/table-cell (tr :time/month)]
      [mui/table-cell (tr :energy/electricity)]
      [mui/table-cell (tr :energy/heat)]
      [mui/table-cell (tr :energy/water)]]]
    (into [mui/table-body]
          (for [row  (range 12)
                :let [month (inc row)
                      month-data (get data month)]]
            [mui/table-row
             [lui/table-cell month]
             [lui/table-cell
              [lui/text-field {:type      "number"
                               :spec      ::schema/electricity-mwh
                               :value     (get month-data :electricity-mwh)
                               :on-change #(on-change month :electricity-mwh %)}]]
             [lui/table-cell
              [lui/text-field {:type      "number"
                               :spec      ::schema/heat-mwh
                               :value     (get month-data :heat-mwh)
                               :on-change #(on-change month :heat-mwh %)}]]
             [lui/table-cell
              [lui/text-field {:type      "number"
                               :spec      ::schema/water-m3
                               :value     (get month-data :water-m3)
                               :on-change #(on-change month :water-m3 %)}]]]))]])

(defn table [{:keys [tr items read-only?]}]
  [lui/form-table {:headers        [[:year (tr :time/year)]
                                    [:electricity-mwh (tr :energy/electricity)]
                                    [:heat-mwh (tr :energy/heat)]
                                    [:water-m3 (tr :energy/water)]]
                   :items          (reverse (sort-by :year items))
                   :key-fn         :year
                   :read-only?     read-only?}])
