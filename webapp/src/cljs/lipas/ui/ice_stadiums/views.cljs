(ns lipas.ui.ice-stadiums.views
  (:require [clojure.pprint :refer [pprint]]
            [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.energy :as energy]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.rinks :as rinks]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.ice-stadiums.utils :refer [set-field make-revision]]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==> resolve-year]]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn details-dialog [{:keys [tr site]}]
  (let [locale (tr)
        close  #(==> [::events/display-site nil])]
    [mui/dialog {:open       true
                 :full-width true
                 :max-width  "md"
                 :on-close   close}
     [mui/dialog-title (-> site :name)]
     [mui/dialog-content
      [:pre (with-out-str (pprint site))]]
     [mui/dialog-actions
      [mui/button {:on-click close}
       (tr :actions/close)]]]))

(defn info-tab [tr]
  (let [locale       (tr)
        pools        (<== [::subs/sites-list locale])
        display-site (<== [::subs/display-site locale])]

    [mui/grid {:container true}

     (when display-site
       [details-dialog {:tr tr :site display-site}])

     [mui/grid {:item true :xs 12}
      [mui/paper
       [lui/table
        {:headers   [[:name "Nimi"]
                     [:address "Osoite"]
                     [:postal-code "Postinumero"]
                     [:city "Kunta"]
                     [:type "Tyyppi"]
                     [:admin "Ylläpitäjä"]
                     [:owner "Omistaja"]]
         :items     pools
         :on-select #(==> [::events/display-site %])}]]]]))

(defn compare-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography (tr :ice-energy/description)]]])


(defn ice-stadium-form [{:keys [tr rev]}]
  (let [locale                (tr)
        data                  rev
        year                  (resolve-year (:timestamp rev))
        dialogs               (<== [::subs/dialogs])
        types                 (<== [::subs/types-list])
        size-categories       (<== [::subs/size-categories])
        cities                (<== [::subs/cities-list])
        owners                (<== [::subs/owners])
        admins                (<== [::subs/admins])
        energy-history        (<== [::subs/energy-consumption-history])
        base-floor-structures (<== [::subs/base-floor-structures])
        set-field             (partial set-field :editing :rev)]

    (r/with-let [renovations-done? (r/atom false)
                 monthly-energy?   (r/atom false)]

      [mui/grid {:container true}

       ;; Energy consumption
       [lui/form-card {:title (tr :energy/headline-year year)}

        [mui/typography {:variant "subheading"
                         :style   {:margin-bottom "1em"}}
         (tr :energy/yearly)]
        [energy/form
         {:tr        tr
          :disabled? @monthly-energy?
          :data      (:energy-consumption data)
          :on-change (partial set-field :energy-consumption)}]

        [lui/checkbox
         {:label     (tr :energy/monthly?)
          :checked   @monthly-energy?
          :on-change #(swap! monthly-energy? not)}]

        (when @monthly-energy?
          [energy/form-monthly
           {:tr        tr
            :data      (:energy-consumption-monthly data)
            :on-change #(==> [::events/set-monthly-energy-consumption %&])}])


        [lui/expansion-panel {:label (tr :actions/show-all-years)}
         [energy/table {:tr         tr
                        :read-only? true
                        :items      energy-history}]]]

       [lui/form-card {:title (tr :renovations/headline-year year)}
        [lui/checkbox
         {:label     (tr :renovations/renovations-done? year)
          :checked   @renovations-done?
          :on-change #(swap! renovations-done? not)}]]

       (when @renovations-done?
         ;; General info
         [lui/form-card {:title (tr :general/general-info)}
          [lui/sports-place-form
           {:tr              tr
            :data            data
            :types           types
            :size-categories size-categories
            :owners          owners
            :admins          admins
            :on-change       set-field}]])

       (when @renovations-done?
         ;; Location
         [lui/form-card {:title (tr :location/headline)}
          [lui/location-form
           {:tr        tr
            :data      (:location data)
            :cities    cities
            :on-change (partial set-field :location)}]])

       (when @renovations-done?
         ;; Building
         [lui/form-card {:title (tr :building/headline)}
          [mui/form-group
           [lui/year-selector
            {:label     (tr :building/construction-year)
             :value     (-> data :building :construction-year)
             :on-change #(set-field :building :construction-year %)}]
           [lui/text-field
            {:label     (tr :building/main-designers)
             :value     (-> data :building :main-designers)
             :spec      ::schema/main-designers
             :on-change #(set-field :building :main-designers %)}]
           [lui/text-field
            {:label     (tr :building/total-surface-area-m2)
             :type      "number"
             :value     (-> data :building :total-surface-area-m2)
             :spec      ::schema/total-surface-area-m2
             :adornment (tr :physical-units/m2)
             :on-change #(set-field :building :total-surface-area-m2 %)}]
           [lui/text-field
            {:label     (tr :building/total-volume-m3)
             :type      "number"
             :value     (-> data :building :total-volume-m3)
             :spec      ::schema/total-volume-m3
             :adornment (tr :physical-units/m3)
             :on-change #(set-field :building :total-volume-m3 %)}]
           [lui/text-field
            {:label     (tr :building/seating-capacity)
             :type      "number"
             :value     (-> data :building :seating-capacity)
             :spec      ::schema/seating-capacity
             :adornment (tr :units/person)
             :on-change #(set-field :building :seating-capacity %)}]]])

       (when @renovations-done?
         ;; Envelope structure
         [lui/form-card {:title (tr :envelope-structure/headline)}
          [mui/form-group
           [lui/select
            {:label     (tr :envelope-structure/base-floor-structure)
             :value     (-> data :envelope-structure :base-floor-structure)
             :on-change #(set-field :envelope-structure :base-floor-structure %)
             :items     base-floor-structures
             :value-fn  first
             :label-fn  (comp locale second)}]
           [lui/checkbox
            {:label     (tr :envelope-structure/insulated-exterior?)
             :value     (-> data :envelope-structure :insulated-exterior?)
             :on-change #(set-field :envelope-structure :insulated-exterior? %)}]
           [lui/checkbox
            {:label     (tr :envelope-structure/insulated-ceiling?)
             :value     (-> data :envelope-structure :insulated-ceiling?)
             :on-change #(set-field :envelope-structure :insulated-ceiling? %)}]
           [lui/checkbox
            {:label     (tr :envelope-structure/low-emissivity-coating?)
             :value     (-> data :envelope-structure :low-emissivity-coating?)
             :on-change #(set-field :envelope-structure :low-emissivity-coating? %)}]]])

       (when @renovations-done?
         ;; Rinks
         (when (-> dialogs :rink :open?)
           [rinks/dialog {:tr tr}]))

       (when @renovations-done?
         [lui/form-card {:title (tr :rinks/headline)}
          [rinks/table {:tr tr :items (-> data :rinks vals)}]])

       (when @renovations-done?
         ;; Refrigeration
         [lui/form-card {:title (tr :refrigeration/headline)}
          [mui/form-group
           [lui/checkbox
            {:label     (tr :refrigeration/original?)
             :value     (-> data :refrigeration :original?)
             :on-change #(set-field :refrigeration :original? %)}]
           [lui/checkbox
            {:label     (tr :refrigeration/individual-metering?)
             :value     (-> data :refrigeration :individual-metering?)
             :on-change #(set-field :refrigeration :individual-metering? %)}]
           [lui/checkbox
            {:label     (tr :refrigeration/condensate-energy-recycling?)
             :value     (-> data :refrigeration :condensate-energy-recycling?)
             :on-change #(set-field :refrigeration :condensate-energy-recycling? %)}]
           [lui/text-field
            {:label     (tr :refrigeration/condensate-energy-main-target)
             :value     (-> data :refrigeration :condensate-energy-main-target)
             :on-change #(set-field :refrigeration :condensate-energy-main-target %)}]
           [lui/text-field
            {:label     (tr :refrigeration/power-kw)
             :type      "number"
             :spec      ::schema/power-kw
             :value     (-> data :refrigeration :power-kw)
             :on-change #(set-field :refrigeration :power-kw %)}]
           [lui/text-field
            {:label     (tr :refrigeration/refrigerant)
             :value     (-> data :refrigeration :refrigerant)
             :on-change #(set-field :refrigeration :refrigerant %)}]
           [lui/text-field
            {:label     (tr :refrigeration/refrigerant-amount-kg)
             :type      "number"
             :spec      ::schema/refrigerant-amount-kg
             :value     (-> data :refrigeration :refrigerant-amount-kg)
             :on-change #(set-field :refrigeration :refrigerant-amount-kg %)}]
           [lui/text-field
            {:label     (tr :refrigeration/refrigerant-solution)
             :value     (-> data :refrigeration :refrigerant-solution)
             :on-change #(set-field :refrigeration :refrigerant-solution %)}]
           [lui/text-field
            {:label     (tr :refrigeration/refrigerant-solution-amount-l)
             :type      "number"
             :spec      ::schema/refrigerant-solution-amount-l
             :value     (-> data :refrigeration :refrigerant-solution-amount-l)
             :on-change #(set-field :refrigeration :refrigerant-solution-amount-l %)}]]])

       (when @renovations-done?
         ;; Ventilation
         [lui/form-card {:title (tr :ventilation/headline)}
          [mui/form-group
           [lui/text-field
            {:label     (tr :ventilation/heat-recovery-type)
             :value     (-> data :ventilation :heat-recovery-type)
             :on-change #(set-field :ventilation :heat-recovery-type %)}]
           [lui/text-field
            {:label     (tr :ventilation/heat-recovery-thermal-efficiency-percent)
             :type      "number"
             :spec      ::schema/heat-recovery-thermal-efficiency-percent
             :adornment (tr :units/percent)
             :value     (-> data :ventilation :heat-recovery-thermal-efficiency-percent)
             :on-change #(set-field :ventilation :heat-recovery-thermal-efficiency-percent %)}]
           [lui/text-field
            {:label     (tr :ventilation/dryer-type)
             :value     (-> data :ventilation :dryer-type)
             :on-change #(set-field :ventilation :dryer-type %)}]
           [lui/text-field
            {:label     (tr :ventilation/dryer-duty-type)
             :value     (-> data :ventilation :dryer-duty-type)
             :on-change #(set-field :ventilation :dryer-duty-type %)}]
           [lui/text-field
            {:label     (tr :ventilation/heat-pump-type)
             :value     (-> data :ventilation :heat-pump-type)
             :on-change #(set-field :ventilation :heat-pump-type %)}]]])

       ;; Conditions
       [lui/form-card {:title (tr :conditions/headline)}
        [mui/form-group
         [lui/text-field
          {:label     (tr :conditions/open-months)
           :type      "number"
           :spec      ::schema/open-months
           :adornment (tr :time/month)
           :value     (-> data :conditions :open-months)
           :on-change #(set-field :conditions :open-months %)}]
         [lui/text-field
          {:label     (tr :conditions/daily-open-hours)
           :type      "number"
           :spec      ::schema/daily-open-hours
           :adornment (tr :time/hour)
           :value     (-> data :conditions :daily-open-hours)
           :on-change #(set-field :conditions :daily-open-hours %)}]
         [lui/text-field
          {:label     (tr :conditions/air-humidity-min)
           :type      "number"
           :spec      ::schema/air-humidity-percent
           :value     (-> data :conditions :air-humidity :min)
           :on-change #(set-field :conditions :air-humidity :min %)}]
         [lui/text-field
          {:label     (tr :conditions/air-humidity-max)
           :type      "number"
           :spec      ::schema/air-humidity-percent
           :value     (-> data :conditions :air-humidity :max)
           :on-change #(set-field :conditions :air-humidity :max %)}]
         [lui/text-field
          {:label     (tr :conditions/ice-surface-temperature-c)
           :type      "number"
           :spec      ::schema/ice-surface-temperature-c
           :value     (-> data :conditions :ice-surface-temperature-c)
           :on-change #(set-field :conditions :ice-surface-temperature-c %)}]
         [lui/text-field
          {:label     (tr :conditions/skating-area-temperature-c)
           :type      "number"
           :spec      ::schema/skating-area-temperature-c
           :value     (-> data :conditions :skating-area-temperature-c)
           :on-change #(set-field :conditions :skating-area-temperature-c %)}]
         [lui/text-field
          {:label     (tr :conditions/stand-temperature-c)
           :type      "number"
           :spec      ::schema/stand-temperature-c
           :value     (-> data :conditions :stand-temperature-c)
           :on-change #(set-field :conditions :stand-temperature-c %)}]]]

       ;; Ice maintenance
       [lui/form-card {:title (tr :ice-maintenance/headline)}
        [mui/form-group
         [lui/text-field
          {:label     (tr :ice-maintenance/daily-maintenance-count-week-days)
           :type      "number"
           :spec      ::schema/daily-maintenance-count-week-days
           :value     (-> data :ice-maintenance :daily-maintenance-count-week-days)
           :on-change #(set-field :ice-maintenance :daily-maintenance-count-week-days %)}]
         [lui/text-field
          {:label     (tr :ice-maintenance/daily-maintenance-count-weekends)
           :type      "number"
           :spec      ::schema/daily-maintenance-count-weekends
           :value     (-> data :ice-maintenance :daily-maintenance-count-weekends)
           :on-change #(set-field :ice-maintenance :daily-maintenance-count-weekends %)}]
         [lui/text-field
          {:label     (tr :ice-maintenance/average-water-consumption-l)
           :type      "number"
           :spec      ::schema/average-water-consumption-l
           :value     (-> data :ice-maintenance :average-water-consumption-l)
           :on-change #(set-field :ice-maintenance :average-water-consumption-l %)}]
         [lui/text-field
          {:label     (tr :ice-maintenance/ice-average-thickness-mm)
           :type      "number"
           :spec      ::schema/ice-average-thickness-mm
           :value     (-> data :ice-maintenance :ice-average-thickness-mm)
           :on-change #(set-field :ice-maintenance :ice-average-thickness-mm %)}]]]

       ;; Actions
       [lui/form-card {}
        [mui/button {:full-width true
                     :color      "secondary"
                     :variant    "raised"
                     :on-click   #(==> [::events/submit data])}
         (tr :actions/save)]]])))

(defn edit-tab [tr]
  (let [data   (<== [::subs/sites-to-edit])
        site   (<== [::subs/editing-site])
        rev    (<== [::subs/editing-rev])
        locale (tr)]

    [mui/grid {:container true}

     (when-not data
       [mui/typography "Sinulla ei ole oikeuksia yhteenkään jäähalliin. :/"])

     (when data
       [lui/form-card {:title (tr :actions/select-hall)}
        [mui/form-group
         [lui/site-selector
          {:label     (tr :actions/select-hall)
           :locale    locale
           :value     (-> site :latest)
           :items     data
           :on-change #(==> [::events/set-edit-site %])}]]])

     (when site
       [lui/form-card {:title (tr :actions/select-year)}
        [mui/form-group
         [lui/rev-selector
          {:label       (tr :actions/select-year)
           :value       rev
           :items       (:history site)
           :template-fn (partial make-revision site)
           :on-change   #(==> [::events/set-edit-rev %])}]]])

     (when (and site rev)
       [ice-stadium-form
        {:tr  tr
         :rev rev}])]))

(defn create-panel [{:keys [tr url logged-in?]}]
  (let [active-tab (re-frame/subscribe [::subs/active-tab])
        card-props {:square true}]
    [mui/grid {:container true}

     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width false
                   :text-color "secondary"
                   :on-change #(==> [::events/set-active-tab %2])
                   :value @active-tab}

         ;; 0 Info tab
         [mui/tab {:label (tr :ice-rinks/headline)
                   :icon (r/as-element [mui/icon "info"])}]

         ;; 1 Compare tab
         [mui/tab {:label (tr :swim/visualizations)
                   :icon (r/as-element [mui/icon "compare"])}]

         ;; 2 Edit tab
         (when logged-in?
           [mui/tab {:label (tr :ice-basic-data/headline)
                     :icon (r/as-element [mui/icon "edit"])}])

         ;; 3 Energy tab
         [mui/tab {:label (tr :ice-energy/headline)
                   :icon (r/as-element [mui/icon "flash_on"])}]]]]]

     [mui/grid {:item true :xs 12}
      (case @active-tab
        0 (info-tab tr)
        1 (compare-tab url)
        2 (edit-tab tr)
        3 (energy-tab tr))]]))

(defn main [tr logged-in?]
  (create-panel {:tr tr
                 :url "https://liikuntaportaalit.sportvenue.net/Jaahalli"
                 :logged-in? logged-in?}))
