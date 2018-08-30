(ns lipas.ui.energy.views
  (:require [lipas.ui.charts :as charts]
            [lipas.ui.components :as lui]
            [lipas.ui.energy.events :as events]
            [lipas.ui.energy.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn form [{:keys [tr data on-change disabled? cold?]}]
  [mui/form-group

   ;; Electricity Mwh
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/electricity)
     :disabled  disabled?
     :type      "number"
     :value     (:electricity-mwh data)
     :spec      :lipas.energy-consumption/electricity-mwh
     :adornment (tr :physical-units/mwh)
     :on-change #(on-change :electricity-mwh %)}]

   ;; Heat Mwh
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/heat)
     :disabled  disabled?
     :type      "number"
     :spec      :lipas.energy-consumption/heat-mwh
     :adornment (tr :physical-units/mwh)
     :value     (:heat-mwh data)
     :on-change #(on-change :heat-mwh %)}]

   ;; Cold Mwh
   (when cold?
     [lui/text-field
      {:label     (tr :lipas.energy-consumption/cold)
       :disabled  disabled?
       :type      "number"
       :spec      :lipas.energy-consumption/cold-mwh
       :adornment (tr :physical-units/mwh)
       :value     (:cold-mwh data)
       :on-change #(on-change :cold-mwh %)}])

   ;; Water m³
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/water)
     :disabled  disabled?
     :type      "number"
     :spec      :lipas.energy-consumption/water-m3
     :adornment (tr :physical-units/m3)
     :value     (:water-m3 data)
     :on-change #(on-change :water-m3 %)}]

   ;; Contains other buildings?
   [:span {:style {:margin-top "1em"}}
    [lui/checkbox
     {:label     (tr :lipas.energy-consumption/contains-other-buildings?)
      :value     (:contains-other-buildings? data)
      :on-change #(on-change :contains-other-buildings? %)}]]])

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
             [mui/table-cell (tr (keyword :month month))]
             [mui/table-cell

              ;; Electricity Mwh
              [lui/text-field {:type      "number"
                               :spec      :lipas.energy-consumption/electricity-mwh
                               :value     (:electricity-mwh month-data)
                               :on-change #(on-change month :electricity-mwh %)}]]

             ;; Heat Mwh
             [mui/table-cell
              [lui/text-field {:type      "number"
                               :spec      :lipas.energy-consumption/heat-mwh
                               :value     (:heat-mwh month-data)
                               :on-change #(on-change month :heat-mwh %)}]]

             ;; Cold Mwh
             (when cold?
               [mui/table-cell
                [lui/text-field {:type      "number"
                                 :spec      :lipas.energy-consumption/cold-mwh
                                 :value     (:cold-mwh month-data)
                                 :on-change #(on-change month :cold-mwh %)}]])

             ;; Water m³
             [mui/table-cell
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


(defn set-field
  [lipas-id & args]
  (==> [:lipas.ui.sports-sites.events/edit-field lipas-id (butlast args) (last args)]))

(defn energy-form [{:keys [tr year draft? cold? visitors? monthly?]}]
  (let [data           (<== [::subs/energy-consumption-rev])
        lipas-id       (:lipas-id data)
        energy-history (<== [::subs/energy-consumption-history])
        edits-valid?   (<== [:lipas.ui.sports-sites.subs/edits-valid? lipas-id])

        set-field      (partial set-field lipas-id)]

    (r/with-let [monthly-energy? (r/atom false)]

      [mui/grid {:container true}

       ;; Energy consumption
       [lui/form-card {:title (tr :lipas.energy-consumption/headline-year year)
                       :xs 12 :md 12 :lg 12}

        [mui/typography {:variant "subheading"
                         :style   {:margin-bottom "1em"}}
         (tr :lipas.energy-consumption/yearly)]
        [form
         {:tr        tr
          :disabled? @monthly-energy?
          :cold?     cold?
          :data      (:energy-consumption data)
          :on-change (partial set-field :energy-consumption)}]

        (when monthly?
          [lui/checkbox
           {:label     (tr :lipas.energy-consumption/monthly?)
            :checked   @monthly-energy?
            :on-change #(swap! monthly-energy? not)}])

        (when @monthly-energy?
          [form-monthly
           {:tr        tr
            :cold?     cold?
            :data      (:energy-consumption-monthly data)
            :on-change #(==> [::events/set-monthly-energy-consumption
                             lipas-id %1 %2 %3])}])

        [lui/expansion-panel {:label (tr :actions/show-all-years)}
         [table {:tr         tr
                        :cold?      true
                        :read-only? true
                 :items      energy-history}]]]

       (when visitors?
         [lui/form-card
          {:title (tr :lipas.swimming-pool.visitors/headline-year year)
           :xs 12 :md 12 :lg 12}
          [mui/form-group
           [lui/text-field
            {:label     (tr :lipas.swimming-pool.visitors/total-count)
             :type      "number"
             :value     (-> data :visitors :total-count)
             :spec      :lipas.swimming-pool.visitors/total-count
             :adornment (tr :units/person)
             :on-change #(set-field :visitors :total-count %)}]]])

       ;; Actions
       [lui/form-card {:xs 12 :md 12 :lg 12}
        [mui/button
         {:full-width true
          :disabled   (not edits-valid?)
          :color      "secondary"
          :variant    "raised"
          :on-click   #(==> [::events/commit-energy-consumption data draft?])}
         (if draft?
           (tr :actions/save-draft)
           (tr :actions/save))]]])))

(defn energy-consumption-form [{:keys [tr editable-sites draftable-sites
                                       visitors? cold?  monthly?]}]
  (let [logged-in? (<== [:lipas.ui.user.subs/logged-in?])
        site       (<== [::subs/energy-consumption-site])
        years      (<== [::subs/energy-consumption-years-list])
        year       (<== [::subs/energy-consumption-year])

        sites  (or editable-sites draftable-sites)
        draft? (empty? editable-sites)]

    (if-not logged-in?

      [mui/paper {:style {:padding "1em"}}
       [mui/grid {:container true :spacing 16}

        [mui/grid {:item true}
         [mui/typography {:variant "headline"
                          :color   "secondary"}
          (tr :restricted/login-or-register)]]

        [mui/grid {:item true :xs 12}
         [lui/login-button
          {:label    (tr :login/headline)
           :on-click #(==> [:lipas.ui.events/navigate "/#/kirjaudu"
                            :comeback? true])}]]

        [mui/grid {:item true}
         [lui/register-button
          {:label    (tr :register/headline)
           :on-click #(==> [:lipas.ui.events/navigate "/#/rekisteroidy"])}]]]]

      [mui/grid {:container true}

       (when sites
         [lui/form-card {:title (tr :actions/select-hall)
                         :xs    12 :md 12 :lg 12}
          [mui/form-group
           [lui/select
            {:label     (tr :actions/select-hall)
             :value     (get-in site [:history (:latest site) :lipas-id])
             :items     sites
             :label-fn  :name
             :value-fn  :lipas-id
             :on-change #(==> [::events/select-energy-consumption-site %])}]]])

       (when (and sites site)
         [lui/form-card {:title (tr :actions/select-year)
                         :xs    12 :md 12 :lg 12}
          [mui/form-group
           [lui/select
            {:label         (tr :actions/select-year)
             :value         year
             :items         years
             :sort-cmp      utils/reverse-cmp
             :on-change     #(==> [::events/select-energy-consumption-year %])}]]])

       (when (and sites site year)
         [energy-form
          {:tr        tr
           :year      year
           :draft?    draft?
           :visitors? visitors?
           :monthly?  monthly?
           :cold?     cold?}])])))

(defn energy-stats [{:keys [tr year stats link]}]
  (let [energy-type (<== [::subs/chart-energy-type])]
    [mui/grid {:container true}

     ;;; Energy chart
     [mui/grid {:item true :xs 12 :md 12}
      [mui/card {:square true}
       [mui/card-header {:title (tr :lipas.energy-stats/headline year)}]
       [mui/card-content
        [mui/form-group {:style {:min-width     120
                                 :max-width     200
                                 :margin-bottom "2em"}}

         ;; Select energy to display in the chart
         [lui/select
          {:label     (tr :actions/choose-energy)
           :items     [{:value :electricity-mwh
                        :label (tr :lipas.energy-stats/electricity-mwh)}
                       {:value :heat-mwh
                        :label (tr :lipas.energy-stats/heat-mwh)}
                       {:value :water-m3
                        :label (tr :lipas.energy-stats/water-m3)}]
           :value     energy-type
           :on-change #(==> [::events/select-energy-type %])}]]

        ;; The Chart
        (when (seq (:data-points stats))
          [charts/energy-chart
           {:energy       energy-type
            :energy-label (tr (keyword :lipas.energy-stats energy-type))
            :data         (:data-points stats)}])

        ;; Is your hall missing from the chart? -> Report consumption
        [mui/typography {:variant :display1}
         (tr :lipas.energy-stats/hall-missing?)]
        [mui/button {:color   :secondary
                     :size    :large
                     :variant :flat
                     :href    link}
         (str "> " (tr :lipas.energy-stats/report))]]]]

     ;;; Hall of Fame (all energy info for previous year reported)
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      [mui/grid  {:item true :xs 12 :md 12 :lg 12}
       [mui/card {:square true
                  :style  {:background-color mui/primary}}
        [mui/card-content
         [mui/typography {:variant :display3
                          :style   {:color mui/gold}}
          "Hall of Fame"]
         [mui/typography {:variant :title
                          :style   {:margin-top "0.75em"
                                    :color      mui/gray1}}
          (tr :lipas.energy-stats/energy-reported-for year)]
         [:div {:style {:margin-top   "1em"
                        :column-width "300px"}}
          (into [:ul]
                (for [m (:hall-of-fame stats)]
                  [:li
                   [mui/typography {:variant :body2
                                    :color   :default}
                    (:name m)]]))]]]]]]))
