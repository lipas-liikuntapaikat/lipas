(ns lipas.ui.energy.views
  (:require
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.energy.events :as events]
   [lipas.ui.energy.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn form [{:keys [tr data on-change disabled? spectators? cold?]}]
  [mui/form-group

   ;; Electricity Mwh
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/electricity)
     :disabled  disabled?
     :type      "number"
     :value     (-> data :energy-consumption :electricity-mwh)
     :spec      :lipas.energy-consumption/electricity-mwh
     :adornment (tr :physical-units/mwh)
     :on-change #(on-change [:energy-consumption :electricity-mwh] %)}]

   ;; Heat Mwh
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/heat)
     :disabled  disabled?
     :type      "number"
     :spec      :lipas.energy-consumption/heat-mwh
     :adornment (tr :physical-units/mwh)
     :value     (-> data :energy-consumption :heat-mwh)
     :on-change #(on-change [:energy-consumption :heat-mwh] %)}]

   ;; Cold Mwh
   (when cold?
     [lui/text-field
      {:label     (tr :lipas.energy-consumption/cold)
       :disabled  disabled?
       :type      "number"
       :spec      :lipas.energy-consumption/cold-mwh
       :adornment (tr :physical-units/mwh)
       :value     (-> data :energy-consumption :cold-mwh)
       :on-change #(on-change [:energy-consumption :cold-mwh] %)}])

   ;; Water m³
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/water)
     :disabled  disabled?
     :type      "number"
     :spec      :lipas.energy-consumption/water-m3
     :adornment (tr :physical-units/m3)
     :value     (-> data :energy-consumption :water-m3)
     :on-change #(on-change [:energy-consumption :water-m3] %)}]

   ;; Spectators
   (when spectators?
     [lui/text-field
      {:label     (tr :lipas.visitors/spectators-count)
       :type      "number"
       :spec      :lipas.visitors/spectators-count
       :adornment (tr :units/person)
       :value     (-> data :visitors :spectators-count)
       :on-change #(on-change [:visitors :spectators-count] %)}])

   ;; Visitors
   [lui/text-field
    {:label     (tr :lipas.visitors/total-count)
     :type      "number"
     :spec      :lipas.visitors/total-count
     :adornment (tr :units/person)
     :value     (-> data :visitors :total-count)
     :on-change #(on-change [:visitors :total-count] %)}]

   ;; Operating hours
   [lui/text-field
    {:label     (tr :lipas.energy-consumption/operating-hours)
     :type      "number"
     :spec      :lipas.energy-consumption/operating-hours
     :adornment (tr :duration/hour)
     :value     (-> data :energy-consumption :operating-hours)
     :on-change #(on-change [:energy-consumption :operating-hours] %)}]])

(defn form-monthly [{:keys [tr data on-change spectators? cold?]}]
  [mui/form-group
   [:div {:style {:overflow-x "auto"}}
    [mui/table

     ;; Headers
     [mui/table-head
      [mui/table-row
       [mui/table-cell (tr :time/month)]
       [mui/table-cell (tr :lipas.energy-consumption/electricity)]
       [mui/table-cell (tr :lipas.energy-consumption/heat)]
       (when cold?
         [mui/table-cell (tr :lipas.energy-consumption/cold)])
       [mui/table-cell (tr :lipas.energy-consumption/water)]
       (when spectators?
         [mui/table-cell (tr :lipas.visitors/spectators-count)])
       [mui/table-cell (tr :lipas.visitors/total-count)]
       [mui/table-cell (tr :lipas.energy-consumption/operating-hours)]]]

     ;; Body
     (into [mui/table-body]
           (for [month [:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec]
                 :let  [energy-kw :energy-consumption-monthly
                        visitors-kw :visitors-monthly
                        energy-data (get-in data [:energy-consumption-monthly month])
                        visitors-data (get-in data [:visitors-monthly month])]]
             [mui/table-row
              [mui/table-cell (tr (keyword :month month))]
              [mui/table-cell

               ;; Electricity Mwh
               [lui/text-field
                {:type      "number"
                 :spec      :lipas.energy-consumption/electricity-mwh
                 :value     (:electricity-mwh energy-data)
                 :on-change #(on-change [energy-kw month :electricity-mwh] %)}]]

              ;; Heat Mwh
              [mui/table-cell
               [lui/text-field
                {:type      "number"
                 :spec      :lipas.energy-consumption/heat-mwh
                 :value     (:heat-mwh energy-data)
                 :on-change #(on-change [energy-kw month :heat-mwh] %)}]]

              ;; Cold Mwh
              (when cold?
                [mui/table-cell
                 [lui/text-field
                  {:type      "number"
                   :spec      :lipas.energy-consumption/cold-mwh
                   :value     (:cold-mwh energy-data)
                   :on-change #(on-change [energy-kw month :cold-mwh] %)}]])

              ;; Water m³
              [mui/table-cell
               [lui/text-field
                {:type      "number"
                 :spec      :lipas.energy-consumption/water-m3
                 :value     (:water-m3 energy-data)
                 :on-change #(on-change [energy-kw month :water-m3] %)}]]

              ;; Spectators
              (when spectators?
                [mui/table-cell
                 [lui/text-field
                  {:type      "number"
                   :spec      :lipas.visitors/spectators-count
                   :value     (:spectators-count visitors-data)
                   :on-change #(on-change [visitors-kw month :spectators-count] %)}]])

              ;; Visitors
              [mui/table-cell
               [lui/text-field
                {:type      "number"
                 :spec      :lipas.visitors/total-count
                 :value     (:total-count visitors-data)
                 :on-change #(on-change [visitors-kw month :total-count] %)}]]

              ;; Operating hours
              [mui/table-cell
               [lui/text-field
                {:type      "number"
                 :spec      :lipas.energy-consumption/operating-hours
                 :value     (:operating-hours energy-data)
                 :on-change #(on-change [energy-kw month :operating-hours] %)}]]]))]]])

(defn make-headers [tr cold?]
  (filter some?
          [[:year (tr :time/year)]
           [:electricity-mwh (tr :lipas.energy-consumption/electricity)]
           [:heat-mwh (tr :lipas.energy-consumption/heat)]
           (when cold? [:cold-mwh (tr :lipas.energy-consumption/cold)])
           [:water-m3 (tr :lipas.energy-consumption/water)]]))

(defn table [{:keys [tr items read-only? cold? on-select]}]
  [lui/table {:headers          (make-headers tr cold?)
              :items            items
              :key-fn           :year
              :sort-fn          :year
              :sort-asc?        true
              :on-select        on-select
              :hide-action-btn? true
              :read-only?       read-only?}])

(defn set-field
  [lipas-id path value]
  (==> [::events/edit-field lipas-id path value]))

(defn tab-container [& children]
  (into [:div {:style {:margin-top "1em"
                       :margin-bottom "1em"}}]
        children))

(defn energy-form [{:keys [tr year draft? cold? spectators?]}]
  (let [data           (<== [::subs/energy-consumption-rev])
        lipas-id       (:lipas-id data)
        energy-history (<== [::subs/energy-consumption-history])
        edits-valid?   (<== [::subs/edits-valid? lipas-id])
        ;; monthly-data?  (<== [::subs/monthly-data-exists?])
        set-field      (partial set-field lipas-id)]

    (r/with-let [monthly-energy? (r/atom false)]

      [mui/grid {:container true}

       ;; Energy consumption
       [lui/form-card {:title (tr :lipas.energy-consumption/headline-year year)
                       :xs    12 :md 12 :lg 12}

        ;; Contains other buildings?

        [mui/tabs {:value     (int @monthly-energy?)
                   :on-change #(swap! monthly-energy? not)}
         [mui/tab {:label (tr :lipas.energy-consumption/yearly)}]
         [mui/tab {:label (tr :lipas.energy-consumption/monthly)}]]

        (case @monthly-energy?

          false
          [tab-container
           [lui/checkbox
            {:style     {:margin-bottom "1em"}
             :label     (tr :lipas.energy-consumption/contains-other-buildings?)
             :value     (-> data :energy-consumption :contains-other-buildings?)
             :on-change #(set-field [:energy-consumption :contains-other-buildings?] %)}]

           ^{:key year}
           [form
            {:tr          tr
             :disabled?   @monthly-energy?
             :cold?       cold?
             :spectators? spectators?
             :data        (select-keys data [:energy-consumption :visitors])
             :on-change   set-field}]]

          true
          [tab-container
           [lui/checkbox
            {:label     (tr :lipas.energy-consumption/contains-other-buildings?)
             :value     (-> data :energy-consumption :contains-other-buildings?)
             :on-change #(set-field [:energy-consumption :contains-other-buildings?] %)}]

           ^{:key year}
           [form-monthly
            {:tr          tr
             :cold?       cold?
             :spectators? spectators?
             :data        (select-keys data [:energy-consumption-monthly
                                             :visitors-monthly])
             :on-change   #(==> [::events/set-monthly-value lipas-id %1 %2])}]])

        [lui/expansion-panel {:label (tr :actions/show-all-years)}
         [table {:tr         tr
                 :cold?      true
                 :read-only? true
                 :items      energy-history}]]]

       ;; Actions
       [lui/floating-container
        {:right            16
         :bottom           16
         :background-color "transparent"}
        [lui/save-button
         {:variant  "extendedFab"
          :disabled (not edits-valid?)
          :color    "secondary"
          :on-click #(==> [::events/commit-energy-consumption data draft?])
          :tooltip  (if draft?
                      (tr :actions/save-draft)
                      (tr :actions/save))}]]

       ;; Small footer on top of which floating container may scroll
       [mui/grid {:item  true :xs 12
                  :style {:height           "5em"
                          :background-color mui/gray1}}]])))

(defn energy-consumption-form [{:keys [tr editable-sites draftable-sites
                                       spectators? cold?]}]
  (let [logged-in? (<== [:lipas.ui.user.subs/logged-in?])
        site       (<== [::subs/energy-consumption-site])
        years      (<== [::subs/energy-consumption-years-list])
        year       (<== [::subs/energy-consumption-year])

        sites (or editable-sites draftable-sites)

        lipas-id (get-in site [:history (:latest site) :lipas-id])

        ;; Fix stale data when jumping between swimming-pool and
        ;; ice-stadium portals
        _ (when-not (some #{lipas-id} (map :lipas-id sites))
            (==> [::events/select-energy-consumption-site nil]))

        draft? (empty? editable-sites)]

    (if-not logged-in?

      [mui/paper {:style {:padding "1em"}}
       [mui/grid {:container true :spacing 16}

        [mui/grid {:item true}
         [mui/typography {:variant "h5" :color "secondary"}
          (tr :restricted/login-or-register)]]

        [mui/grid {:item true :xs 12}
         [lui/login-button
          {:label    (tr :login/headline)
           :on-click #(utils/navigate! "/#/kirjaudu" :comeback? true)}]]

        [mui/grid {:item true}
         [lui/register-button
          {:label    (tr :register/headline)
           :on-click #(utils/navigate! "/#/rekisteroidy")}]]]]

      [mui/grid {:container true}

       (when sites
         [lui/form-card {:title (tr :actions/select-hall)
                         :xs    12 :md 12 :lg 12}
          [mui/form-group
           [lui/select
            {:label     (tr :actions/select-hall)
             :value     lipas-id
             :items     sites
             :label-fn  :name
             :value-fn  :lipas-id
             :on-change #(==> [::events/select-energy-consumption-site %])}]]])

       (when (and sites site)
         [lui/form-card {:title (tr :actions/select-year)
                         :xs    12 :md 12 :lg 12}
          [mui/form-group
           [lui/select
            {:label     (tr :actions/select-year)
             :value     year
             :items     years
             :sort-cmp  utils/reverse-cmp
             :on-change #(==> [::events/select-energy-consumption-year %])}]]])

       (when (and sites site year)
         [energy-form
          {:tr          tr
           :year        year
           :draft?      draft?
           :spectators? spectators?
           :cold?       cold?}])])))

(defn energy-stats [{:keys [tr year on-year-change stats link]}]
  (let [energy-type (<== [::subs/chart-energy-type])]
    [mui/grid {:container true}

     ;;; Energy chart
     [mui/grid {:item true :xs 12 :md 12}
      [mui/card {:square true}
       ;; [mui/card-header {:title (tr :lipas.energy-stats/headline year)}]
       [mui/card-content

        [mui/grid {:container true :spacing 16 :style {:margin-bottom "1em"}}

         [mui/grid {:item true :xs 12 :style {:margin-top "1em"}}
          [mui/typography {:variant "h3" :color "secondary"}
           (tr :lipas.energy-stats/headline year)]]

         ;; Select year for stats
         [mui/grid {:item true}
          [lui/year-selector
           {:style     {:min-width "100px"}
            :label     (tr :actions/select-year)
            :years     (range 2000 utils/this-year)
            :value     year
            :on-change on-year-change}]]

         ;; Select energy to display in the chart
         [mui/grid {:item true}
          [lui/select
           {:style     {:min-width "150px"}
            :label     (tr :actions/choose-energy)
            :items     [{:value :energy-mwh
                         :label (tr :lipas.energy-stats/energy-mwh)}
                        {:value :electricity-mwh
                         :label (tr :lipas.energy-stats/electricity-mwh)}
                        {:value :heat-mwh
                         :label (tr :lipas.energy-stats/heat-mwh)}
                        {:value :water-m3
                         :label (tr :lipas.energy-stats/water-m3)}]
            :value     energy-type
            :on-change #(==> [::events/select-energy-type %])}]]]

        (when-not (seq (:data-points stats))
          [mui/typography {:color "error"}
           (tr :error/no-data)])

        ;; The Chart
        [charts/energy-chart
         {:energy       energy-type
          :energy-label (tr (keyword :lipas.energy-stats energy-type))
          :data         (:data-points stats)}]

        [mui/grid {:container true :spacing 16 :align-items "center" :justify "flex-start"}
         [mui/grid {:item true :xs 12}

          ;; Is your hall missing from the chart? -> Report consumption
          [mui/typography {:style {:margin-top "0.5em" :opacity 0.7} :variant "h3"}
           (tr :lipas.energy-stats/hall-missing?)]]

         [mui/grid {:item true :xs 12 :sm 6 :md 4 :lg 4}
          [charts/energy-totals-gauge
           (let [total        (-> stats :counts :sites)
                 reported     (get-in stats [:counts energy-type])
                 not-reported (- total reported)]
             {:energy-type energy-type
              :data
              [{:name  (tr :lipas.energy-stats/reported reported)
                :value reported}
               {:name  (tr :lipas.energy-stats/not-reported not-reported)
                :value not-reported}]})]]

         ;; Report conssumption button
         [mui/grid {:item true :xs 12 :sm 6 :lg 4}
          [mui/button
           {:color   "secondary"
            :size    "large"
            :variant "extendedFab"
            :href    link}
           [mui/icon {:style {:margin-right "0.25em"}} "edit"]
           (tr :lipas.energy-stats/report)]]]]]]

     ;;; Hall of Fame (all energy info for previous year reported)
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      [mui/grid  {:item true :xs 12 :md 12 :lg 12}
       [mui/card {:square true :style {:background-color mui/primary}}
        [mui/card-content
         [mui/typography {:variant "h2" :style {:color mui/gold}}
          "Hall of Fame"]
         [mui/typography {:variant :title
                          :style   {:margin-top "0.75em" :color mui/gray1}}
          (tr :lipas.energy-stats/energy-reported-for year)]
         [:div {:style {:margin-top "1em"}}
          (into [mui/list {:dense true
                           :style {:column-width "300px"}}]
                (for [m (:hall-of-fame stats)]
                  [mui/list-item {:style {:break-inside :avoid}}
                   [mui/list-item-icon {:style {:margin-right 0
                                                :color        mui/gold}}
                    [mui/icon "star"]]
                   [mui/list-item-text {:variant :body2
                                        :color   :default}
                    (:name m)]]))]]]]]]))

(defn localize-months [tr]
  (let [months [:jan :feb :mar :apr :may :jun
                :jul :aug :sep :oct :nov :dec]]
    (reduce (fn [m k] (assoc m k (tr (keyword :month k))))
            {}
            months)))

(defn monthly-chart [{:keys [tr lipas-id year]}]
  (let [data   (<== [::subs/monthly-chart-data lipas-id year])
        labels (merge
                {:electricity-mwh (tr :lipas.energy-stats/electricity-mwh)
                 :heat-mwh        (tr :lipas.energy-stats/heat-mwh)
                 :cold-mwh        (tr :lipas.energy-stats/cold-mwh)
                 :water-m3        (tr :lipas.energy-stats/water-m3)}
                (localize-months tr))]
    [mui/paper {:style     {:margin-top "1em"}
                :elevation 0}
     [mui/typography {:variant "h6" :color :secondary}
      (tr :lipas.energy-consumption/monthly-readings-in-year year)]
     (if (not-empty data)
       [:div {:style {:padding-top "1em"}}
        [charts/monthly-chart
         {:data   data
          :labels labels}]]
       [mui/typography
        (tr :lipas.energy-consumption/not-reported-monthly)])]))

(defn monthly-visitors-chart [{:keys [tr lipas-id year]}]
  (let [data   (<== [::subs/monthly-visitors-chart-data lipas-id year])
        labels (merge
                {:total-count      (tr :lipas.visitors/total-count)
                 :spectators-count (tr :lipas.visitors/spectators-count)}
                (localize-months tr))]
    [mui/paper {:style {:margin-top "1em"} :elevation 0}
     [mui/typography {:variant "h6" :color :secondary}
      (tr :lipas.visitors/monthly-visitors-in-year year)]
     (if (not-empty data)
       [:div {:style {:padding-top "1em"}}
        [charts/monthly-chart
         {:data   data
          :labels labels}]]
       [mui/typography
        (tr :lipas.visitors/not-reported-monthly)])]))
