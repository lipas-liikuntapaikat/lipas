(ns lipas.ui.ice-stadiums.views
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.energy :as energy]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.rinks :as rinks]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>]]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  [& args]
  (==> [::events/set-field (butlast args) (last args)]))

(defn site-view [{:keys [tr logged-in?]}]
  (let [locale                (tr)
        site                  (<== [::subs/display-site locale])
        editing-rev           (<== [::subs/editing-rev])
        types                 (<== [::subs/types-list])
        dialogs               (<== [::subs/dialogs])
        types                 (<== [::subs/types-list])
        size-categories       (<== [::subs/size-categories])
        cities                (<== [::subs/cities-list])
        owners                (<== [::subs/owners])
        admins                (<== [::subs/admins])
        base-floor-structures (<== [::subs/base-floor-structures])
        cets                  (<== [::subs/condensate-energy-targets])
        refrigerants          (<== [::subs/refrigerants])
        refrigerant-solutions (<== [::subs/refrigerant-solutions])
        heat-recovery-types   (<== [::subs/heat-recovery-types])
        dryer-types           (<== [::subs/dryer-types])
        dryer-duty-types      (<== [::subs/dryer-duty-types])
        heat-pump-types       (<== [::subs/heat-pump-types])

        lipas-id           (:lipas-id site)
        user-can-publish?  (<== [::subs/permission-to-publish? lipas-id])
        uncommitted-edits? (<== [::subs/uncommitted-edits? lipas-id])

        set-field (partial set-field :editing :rev)]

    (r/with-let [editing? (r/atom false)]

      [lui/full-screen-dialog
       {:open? ((complement empty?) site)

        :title (if uncommitted-edits?
                 (tr :statuses/edited (-> site :name))
                 (-> site :name))

        :close-label (tr :actions/close)
        :on-close    #(==> [::events/display-site nil])

        :actions (lui/edit-actions-list
                  {:uncommitted-edits? uncommitted-edits?
                   :editing?           @editing?
                   :logged-in?         logged-in?
                   :user-can-publish?  user-can-publish?
                   :on-discard         #(==> [::events/discard-edits])
                   :discard-tooltip    (tr :actions/discard)
                   :on-edit-start      #(do (==> [::events/edit-site site])
                                            (swap! editing? not))
                   :on-edit-end        #(do (==> [::events/save-edits])
                                            (swap! editing? not))
                   :edit-tooltip       (tr :actions/edit)
                   :on-save-draft      #(==> [::events/commit-draft])
                   :save-draft-tooltip (tr :actions/save-draft)
                   :on-publish         #(==> [::events/commit-edits])
                   :publish-tooltip    (tr :actions/publish)})}

       [mui/grid {:container true}

        ;;; General info
        [lui/form-card {:title (tr :general/general-info)}
         [lui/sports-site-form {:tr              tr
                                :display-data    site
                                :edit-data       editing-rev
                                :read-only?      (not @editing?)
                                :types           types
                                :size-categories size-categories
                                :admins          admins
                                :owners          owners
                                :on-change       set-field}]]

        ;;; Location
        [lui/form-card {:title (tr :location/headline)}
         [lui/location-form {:tr           tr
                             :read-only?   (not @editing?)
                             :cities       cities
                             :edit-data    (:location editing-rev)
                             :display-data (:location site)
                             :on-change    (partial set-field :location)}]]

        ;;; Building
        (let [on-change    (partial set-field :building)
              edit-data    (:building editing-rev)
              display-data (:building site)]
          [lui/form-card {:title (tr :building/headline)}
           [lui/form {:read-only? (not @editing?)}

            ;; Construction year
            {:label (tr :building/construction-year)
             :value (-> display-data :construction-year)
             :form-field
             [lui/year-selector
              {:value     (-> edit-data :construction-year)
               :on-change #(on-change :construction-year %)}]}

            ;; Main designers
            {:label      (tr :building/main-designers)
             :value      (-> display-data :main-designers)
             :form-field [lui/text-field
                          {:value     (-> edit-data :main-designers)
                           :spec      ::schema/main-designers
                           :on-change #(on-change :main-designers %)}]}

            ;; Total surface area m2
            {:label (tr :building/total-surface-area-m2)
             :value (-> display-data :total-surface-area-m2)
             :form-field
             [lui/text-field
              {:type      "number"
               :value     (-> edit-data :total-surface-area-m2)
               :spec      ::schema/total-surface-area-m2
               :adornment (tr :physical-units/m2)
               :on-change #(on-change :total-surface-area-m2 %)}]}

            ;; Total volume m3
            {:label (tr :building/total-volume-m3)
             :value (-> display-data :total-volume-m3)
             :form-field
             [lui/text-field
              {:type      "number"
               :value     (-> edit-data :total-volume-m3)
               :spec      ::schema/total-volume-m3
               :adornment (tr :physical-units/m3)
               :on-change #(on-change :total-volume-m3 %)}]}

            {:label (tr :building/seating-capacity)
             :value (-> display-data :seating-capacity)
             :form-field
             [lui/text-field
              {:type      "number"
               :value     (-> edit-data :seating-capacity)
               :spec      ::schema/seating-capacity
               :adornment (tr :units/person)
               :on-change #(on-change :seating-capacity %)}]}]])

        ;;; Envelope structure
        (let [on-change    (partial set-field :envelope-structure)
              display-data (:envelope-structure site)
              edit-data    (:envelope-structure editing-rev)]
          [lui/form-card {:title (tr :envelope-structure/headline)}
           [lui/form {:read-only? (not @editing?)}

            ;; Base floor structure
            {:label (tr :envelope-structure/base-floor-structure)
             :value (-> display-data :base-floor-structure)
             :form-field
             [lui/select
              {:value     (-> edit-data :base-floor-structure)
               :on-change #(on-change :base-floor-structure %)
               :items     base-floor-structures
               :value-fn  first
               :label-fn  (comp locale second)}]}

            ;; Insulated exterior?
            {:label (tr :envelope-structure/insulated-exterior?)
             :value (-> display-data :insulated-exterior?)
             :form-field
             [lui/checkbox
              {:value     (-> edit-data :insulated-exterior?)
               :on-change #(on-change :insulated-exterior? %)}]}

            ;; Insulated ceiling?
            {:label (tr :envelope-structure/insulated-ceiling?)
             :value (-> display-data :insulated-ceiling?)
             :form-field
             [lui/checkbox
              {:value     (-> edit-data :insulated-ceiling?)
               :on-change #(on-change :insulated-ceiling? %)}]}

            ;; Low emissivity coating?
            {:label (tr :envelope-structure/low-emissivity-coating?)
             :value (-> display-data :low-emissivity-coating?)
             :form-field
             [lui/checkbox
              {:value     (-> edit-data :low-emissivity-coating?)
               :on-change #(on-change :low-emissivity-coating? %)}]}]])

        ;;; Rinks
        [lui/form-card {:title (tr :rinks/headline)}

         (when (-> dialogs :rink :open?)
           [rinks/dialog {:tr tr}])

         (if @editing?
           [rinks/table {:tr tr :items (-> editing-rev :rinks vals)}]
           [rinks/read-only-table {:tr tr :items (-> site :rinks)}])]

        ;;; Refrigeration
        (let [on-change    (partial set-field :refrigeration)
              display-data (:refrigeration site)
              edit-data    (:refrigeration editing-rev)]
          [lui/form-card {:title (tr :refrigeration/headline)}
           [lui/form {:read-only? (not @editing?)}

            ;; Original?
            {:label (tr :refrigeration/original?)
             :value (-> display-data :original?)
             :form-field
             [lui/checkbox
              {:value     (-> edit-data :original?)
               :on-change #(on-change :original? %)}]}

            ;; Individual metering?
            {:label (tr :refrigeration/individual-metering?)
             :value (-> display-data :individual-metering?)
             :form-field
             [lui/checkbox
              {:value     (-> edit-data :individual-metering?)
               :on-change #(on-change :individual-metering? %)}]}

            ;; Condensate energy recycling?
            {:label (tr :refrigeration/condensate-energy-recycling?)
             :value (-> display-data :condensate-energy-recycling?)
             :form-field
             [lui/checkbox
              {:value     (-> edit-data :condensate-energy-recycling?)
               :on-change #(on-change :condensate-energy-recycling? %)}]}

            ;; Condensate energy main target
            {:label (tr :refrigeration/condensate-energy-main-targets)
             :value (-> display-data :condensate-energy-main-targets)
             :form-field
             [lui/multi-select
              {:value     (-> edit-data :condensate-energy-main-targets)
               :items     cets
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(on-change :condensate-energy-main-targets %)}]}

            ;; Power kW
            {:label (tr :refrigeration/power-kw)
             :value (-> display-data :power-kw)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/power-kw
               :value     (-> edit-data :power-kw)
               :on-change #(on-change :power-kw %)}]}

            ;; Refrigerant
            {:label (tr :refrigeration/refrigerant)
             :value (-> display-data :refrigerant)
             :form-field
             [lui/select
              {:value     (-> edit-data :refrigerant)
               :items     refrigerants
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(on-change :refrigerant %)}]}

            ;; Refrigerant amount kg
            {:label (tr :refrigeration/refrigerant-amount-kg)
             :value (-> display-data :refrigerant-amount-kg)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/refrigerant-amount-kg
               :value     (-> edit-data :refrigerant-amount-kg)
               :on-change #(on-change :refrigerant-amount-kg %)}]}

            ;; Refrigerant solution
            {:label (tr :refrigeration/refrigerant-solution)
             :value (-> display-data :refrigerant-solution)
             :form-field
             [lui/select
              {:value     (-> edit-data :refrigerant-solution)
               :items     refrigerant-solutions
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(on-change :refrigerant-solution %)}]}

            ;; Refrigerant solution amount l
            {:label (tr :refrigeration/refrigerant-solution-amount-l)
             :value (-> display-data :refrigerant-solution-amount-l)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/refrigerant-solution-amount-l
               :value     (-> edit-data :refrigerant-solution-amount-l)
               :on-change #(on-change :refrigerant-solution-amount-l %)}]}]])

        ;;; Ventilation
        (let [on-change    (partial set-field :ventilation)
              edit-data    (:ventilation editing-rev)
              display-data (:ventilation site)]
          [lui/form-card {:title (tr :ventilation/headline)}
           [lui/form {:read-only? (not @editing?)}

            ;; Heat recovery type
            {:label (tr :ventilation/heat-recovery-type)
             :value (-> display-data :heat-recovery-type)
             :form-field
             [lui/select
              {:value     (-> edit-data :heat-recovery-type)
               :items     heat-recovery-types
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(on-change :heat-recovery-type %)}]}

            ;; Heat recovery thermal efficiency percent
            {:label (tr :ventilation/heat-recovery-thermal-efficiency-percent)
             :value (-> display-data :heat-recovery-thermal-efficiency-percent)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/heat-recovery-thermal-efficiency-percent
               :adornment (tr :units/percent)
               :value     (-> edit-data :heat-recovery-thermal-efficiency-percent)
               :on-change #(on-change :heat-recovery-thermal-efficiency-percent %)}]}

            ;; Dryer type
            {:label (tr :ventilation/dryer-type)
             :value (-> display-data :dryer-type)
             :form-field
             [lui/select
              {:value     (-> edit-data :dryer-type)
               :items     dryer-types
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(on-change :dryer-type %)}]}

            ;; Dryer duty type
            {:label (tr :ventilation/dryer-duty-type)
             :value (-> display-data :dryer-duty-type)
             :form-field
             [lui/select
              {:value     (-> edit-data :dryer-duty-type)
               :items     dryer-duty-types
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(on-change :dryer-duty-type %)}]}

            ;; Heat pump type
            {:label (tr :ventilation/heat-pump-type)
             :value (-> display-data :heat-pump-type)
             :form-field
             [lui/select
              {:value     (-> edit-data :heat-pump-type)
               :items     heat-pump-types
               :label-fn  (comp locale second)
               :value-fn  first
               :on-change #(set-field :heat-pump-type %)}]}]])

        ;;; Conditions
        (let [on-change    (partial set-field :conditions)
              display-data (-> site :conditions)
              edit-data    (-> editing-rev :conditions)]
          [lui/form-card {:title (tr :conditions/headline)}
           [lui/form {:read-only? (not @editing?)}

            ;; Open months
            {:label (tr :conditions/open-months)
             :value (-> display-data :open-months)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/open-months
               :adornment (tr :duration/month)
               :value     (-> edit-data :open-months)
               :on-change #(on-change :open-months %)}]}

            ;; Daily open hours
            {:label (tr :conditions/daily-open-hours)
             :value (-> display-data :daily-open-hours)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/daily-open-hours
               :adornment (tr :units/hours-per-day)
               :value     (-> edit-data :daily-open-hours)
               :on-change #(on-change :daily-open-hours %)}]}

            ;; Air humidity min %
            {:label (tr :conditions/air-humidity-min)
             :value (-> display-data :air-humidity :min)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/air-humidity-percent
               :adornment (tr :units/percent)
               :value     (-> edit-data :air-humidity :min)
               :on-change #(on-change :air-humidity :min %)}]}

            ;; Air humidity max %
            {:label (tr :conditions/air-humidity-max)
             :value (-> display-data :air-humidity :max)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/air-humidity-percent
               :adornment (tr :units/percent)
               :value     (-> edit-data :air-humidity :max)
               :on-change #(on-change :air-humidity :max %)}]}

            ;; Ice surface temperature c
            {:label (tr :conditions/ice-surface-temperature-c)
             :value (-> display-data :ice-surface-temperature-c)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/ice-surface-temperature-c
               :adornment (tr :physical-units/celsius)
               :value     (-> edit-data :ice-surface-temperature-c)
               :on-change #(on-change :ice-surface-temperature-c %)}]}

            ;; Skating area temperature c
            {:label (tr :conditions/skating-area-temperature-c)
             :value (-> display-data :skating-area-temperature-c)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/skating-area-temperature-c
               :adornment (tr :physical-units/celsius)
               :value     (-> edit-data :skating-area-temperature-c)
               :on-change #(on-change :skating-area-temperature-c %)}]}

            ;; Stand temperature c
            {:label (tr :conditions/stand-temperature-c)
             :value (-> display-data :stand-temperature-c)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/stand-temperature-c
               :adornment (tr :physical-units/celsius)
               :value     (-> edit-data :stand-temperature-c)
               :on-change #(on-change :stand-temperature-c %)}]}

            ;; Daily maintenance count week days
            {:label (tr :conditions/daily-maintenance-count-week-days)
             :value (-> display-data :daily-maintenance-count-week-days)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/daily-maintenance-count-week-days
               :adornment (tr :units/times-per-day)
               :value     (-> edit-data :daily-maintenance-count-week-days)
               :on-change #(on-change :daily-maintenance-count-week-days %)}]}

            ;; Daily maintenance count weekends
            {:label (tr :conditions/daily-maintenance-count-weekends)
             :value (-> display-data :daily-maintenance-count-weekends)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/daily-maintenance-count-weekends
               :adornment (tr :units/times-per-day)
               :value     (-> edit-data :daily-maintenance-count-weekends)
               :on-change #(on-change :daily-maintenance-count-weekends %)}]}

            ;; Average water consumption l
            {:label (tr :conditions/average-water-consumption-l)
             :value (-> display-data :average-water-consumption-l)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/average-water-consumption-l
               :adornment (tr :physical-units/l)
               :value     (-> edit-data :average-water-consumption-l)
               :on-change #(on-change :average-water-consumption-l %)}]}

            ;; Ice average thickness mm
            {:label (tr :conditions/ice-average-thickness-mm)
             :value (-> display-data :ice-average-thickness-mm)
             :form-field
             [lui/text-field
              {:type      "number"
               :spec      ::schema/ice-average-thickness-mm
               :adornment (tr :physical-units/mm)
               :value     (-> edit-data :ice-average-thickness-mm)
               :on-change #(on-change :ice-average-thickness-mm %)}]}]])

        ;;; Energy consumption
        [lui/form-card {:title (tr :energy/headline)}
         [energy/table {:read-only? true
                        :cold?      true
                        :tr         tr
                        :items      (:energy-consumption site)}]]]])))

(defn ice-stadiums-tab [tr logged-in?]
  (let [locale (tr)
        sites  (<== [::subs/sites-list locale])]

    [mui/grid {:container true}

     [site-view {:tr tr :logged-in? logged-in?}]

     [mui/grid {:item true :xs 12}
      [mui/paper
       [lui/table
        {:headers   [[:name (tr :sports-site/name)]
                     [:address (tr :location/address)]
                     [:postal-code (tr :location/postal-code)]
                     [:city (tr :location/city)]
                     [:admin (tr :sports-site/admin)]
                     [:owner (tr :sports-site/owner)]]
         :items     sites
         :on-select #(==> [::events/display-site %])}]]]]))

(defn compare-tab []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src "https://liikuntaportaalit.sportvenue.net/Jaahalli"
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-info-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography (tr :ice-energy/description)]]])

(defn energy-form [{:keys [tr year]}]
  (let [data           (<== [::subs/editing-rev])
        energy-history (<== [::subs/energy-consumption-history])
        set-field      (partial set-field :editing :rev)]

    (r/with-let [monthly-energy? (r/atom false)]

      [mui/grid {:container true}

       ;; Energy consumption
       [lui/form-card {:title (tr :energy/headline-year year)}

        [mui/typography {:variant "subheading"
                         :style   {:margin-bottom "1em"}}
         (tr :energy/yearly)]
        [energy/form
         {:tr        tr
          :disabled? @monthly-energy?
          :cold?     true
          :data      (:energy-consumption data)
          :on-change (partial set-field :energy-consumption)}]

        [lui/checkbox
         {:label     (tr :energy/monthly?)
          :checked   @monthly-energy?
          :on-change #(swap! monthly-energy? not)}]

        (when @monthly-energy?
          [energy/form-monthly
           {:tr        tr
            :cold?     true
            :data      (:energy-consumption-monthly data)
            :on-change #(==> [::events/set-monthly-energy-consumption %&])}])

        [lui/expansion-panel {:label (tr :actions/show-all-years)}
         [energy/table {:tr         tr
                        :cold?      true
                        :read-only? true
                        :items      energy-history}]]]

       ;; Actions
       [lui/form-card {}
        [mui/button {:full-width true
                     :color      "secondary"
                     :variant    "raised"
                     :on-click   #(==> [::events/commit-energy-consumption data])}
         (tr :actions/save)]]])))

(defn energy-form-tab [tr]
  (let [data   (<== [::subs/sites-to-edit-list])
        site   (<== [::subs/editing-site])
        years  (<== [::subs/energy-consumption-years-list])
        year   (<== [::subs/editing-year])
        locale (tr)]

    [mui/grid {:container true}

     (when-not data
       [mui/typography "Sinulla ei ole oikeuksia yhteenkään jäähalliin. :/"])

     (when data
       [lui/form-card {:title (tr :actions/select-hall)}
        [mui/form-group
         [lui/select
          {:label     (tr :actions/select-hall)
           :value     (-> site :latest :lipas-id)
           :items     data
           :label-fn  (comp locale :name)
           :value-fn  :lipas-id
           :on-change #(==> [::events/set-edit-site {:lipas-id %}])}]]])

     (when site
       [lui/form-card {:title (tr :actions/select-year)}
        [mui/form-group
         [lui/select
          {:label     (tr :actions/select-year)
           :value     year
           :items     years
           :on-change #(==> [::events/select-energy-consumption-year %])}]]])

     (when (and site year)
       [energy-form
        {:tr   tr
         :year year}])]))

(defn create-panel [{:keys [tr logged-in?]}]
  (let [active-tab (re-frame/subscribe [::subs/active-tab])
        card-props {:square true}]
    [mui/grid {:container true}

     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width true
                   :text-color "secondary"
                   :on-change  #(==> [::events/set-active-tab %2])
                   :value      @active-tab}

         ;; 0 Ice stadiums tab
         [mui/tab {:label (tr :ice-rinks/headline)
                   :icon  (r/as-element [mui/icon "info"])}]

         ;; 1 Energy form tab
         [mui/tab {:label    (tr :ice-basic-data/headline)
                   :icon     (r/as-element [mui/icon "edit"])
                   :disabled (not logged-in?)}]

         ;; 2 Compare tab
         [mui/tab {:label (tr :swim/visualizations)
                   :icon  (r/as-element [mui/icon "compare"])}]

         ;; 3 Energy info tab
         [mui/tab {:label (tr :ice-energy/headline)
                   :icon  (r/as-element [mui/icon "flash_on"])}]]]]]

     [mui/grid {:item true :xs 12}
      (case @active-tab
        0 (ice-stadiums-tab tr logged-in?)
        1 (energy-form-tab tr)
        2 (compare-tab)
        3 (energy-info-tab tr))]]))

(defn main [tr logged-in?]
  (create-panel {:tr tr :logged-in? logged-in?}))
