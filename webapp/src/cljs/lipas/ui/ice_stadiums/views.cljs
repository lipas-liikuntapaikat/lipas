(ns lipas.ui.ice-stadiums.views
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.renovations :as renovations]
            [lipas.ui.ice-stadiums.rinks :as rinks]
            [lipas.ui.ice-stadiums.energy :as energy]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.ice-stadiums.utils :refer [set-field]]
            [lipas.ui.mui :as mui]
            [lipas.ui.mui-icons :as mui-icons]
            [lipas.ui.utils :refer [<== ==>]]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn info-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography (tr :ice-energy/description)]]])

(defn ice-stadium-form [tr data]
  (let [data      (<== [::subs/editing])
        dialogs   (<== [::subs/dialogs])
        types     (<== [::subs/types])
        cities    (<== [::subs/cities])
        owners    (<== [::subs/owners])
        admins    (<== [::subs/admins])
        set-field (partial set-field :editing)]

    [mui/grid {:container true}

     ;; Energy consumption
     (when (-> dialogs :energy :open?)
       [energy/dialog {:tr tr}])

     [lui/form-card {:title (tr :energy/headline)}
      [energy/table {:tr tr :items (-> data :energy-consumption)}]]


     ;; General info
     [lui/form-card {:title (tr :general/general-info)}
      [lui/sports-place-form
       {:tr        tr
        :data      data
        :types     types
        :owners    owners
        :admins    admins
        :on-change set-field}]]

     ;; Location
     [lui/form-card {:title (tr :location/headline)}
      [lui/location-form
       {:tr        tr
        :data      (:location data)
        :cities    cities
        :on-change (partial set-field :location)}]]

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
         :on-change #(set-field :building :seating-capacity %)}]]]

     ;; Envelope structure
     [lui/form-card {:title (tr :envelope-structure/headline)}
      [mui/form-group
       [lui/select
        {:label     (tr :envelope-structure/base-floor-structure)
         :value     (-> data :envelope-structure :base-floor-structure)
         :on-change #(set-field :envelope-structure :base-floor-structure %)
         :items     [{:value :concrete :label "Betoni"}
                     {:value :asphalt :label "Asfaltti"}
                     {:value :sand :label "Hiekka"}]}]
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
         :on-change #(set-field :envelope-structure :low-emissivity-coating? %)}]]]

     ;; Renovations
     (when (-> dialogs :renovation :open?)
       [renovations/dialog {:tr tr}])

     [lui/form-card {:title (tr :renovations/headline)}
      [renovations/table {:tr tr :items (-> data :renovations vals)}]]

     ;; Rinks
     (when (-> dialogs :rink :open?)
       [rinks/dialog {:tr tr}])

     [lui/form-card {:title (tr :rinks/headline)}
      [rinks/table {:tr tr :items (-> data :rinks vals)}]]

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
         :value     (-> data :refrigeration :power-kw)
         :on-change #(set-field :refrigeration :power-kw %)}]
       [lui/text-field
        {:label     (tr :refrigeration/refrigerant)
         :value     (-> data :refrigeration :refrigerant)
         :on-change #(set-field :refrigeration :refrigerant %)}]
       [lui/text-field
        {:label     (tr :refrigeration/refrigerant-amount-kg)
         :type      "number"
         :value     (-> data :refrigeration :refrigerant-amount-kg)
         :on-change #(set-field :refrigeration :refrigerant-amount-kg %)}]
       [lui/text-field
        {:label     (tr :refrigeration/refrigerant-solution)
         :value     (-> data :refrigeration :refrigerant-solution)
         :on-change #(set-field :refrigeration :refrigerant-solution %)}]
       [lui/text-field
        {:label     (tr :refrigeration/refrigerant-solution-amount-l)
         :type      "number"
         :value     (-> data :refrigeration :refrigerant-solution-amount-l)
         :on-change #(set-field :refrigeration :refrigerant-solution-amount-l %)}]]]

     ;; Conditions
     [lui/form-card {:title (tr :conditions/headline)}
      [mui/form-group
       [lui/text-field
        {:label     (tr :conditions/air-humidity-min)
         :type      "number"
         :value     (-> data :conditions :air-humidity :min)
         :on-change #(set-field :conditions :air-humidity :min %)}]
       [lui/text-field
        {:label     (tr :conditions/air-humidity-max)
         :type      "number"
         :value     (-> data :conditions :air-humidity :max)
         :on-change #(set-field :conditions :air-humidity :max %)}]
       [lui/text-field
        {:label     (tr :conditions/ice-surface-temperature-c)
         :type      "number"
         :value     (-> data :conditions :ice-surface-temperature-c)
         :on-change #(set-field :conditions :ice-surface-temperature-c %)}]
       [lui/text-field
        {:label     (tr :conditions/skating-area-temperature-c)
         :type      "number"
         :value     (-> data :conditions :skating-area-temperature-c)
         :on-change #(set-field :conditions :skating-area-temperature-c %)}]
       [lui/text-field
        {:label     (tr :conditions/stand-temperature-c)
         :type      "number"
         :value     (-> data :conditions :stand-temperature-c)
         :on-change #(set-field :conditions :stand-temperature-c %)}]]]

     ;; Ice maintenance
     [lui/form-card {:title (tr :ice-maintenance/headline)}
      [mui/form-group
       [lui/text-field
        {:label     (tr :ice-maintenance/daily-maintenance-count-week-days)
         :type      "number"
         :value     (-> data :ice-maintenance :daily-maintenance-count-week-days)
         :on-change #(set-field :ice-maintenance :daily-maintenance-count-week-days %)}]
       [lui/text-field
        {:label     (tr :ice-maintenance/daily-maintenance-count-weekends)
         :type      "number"
         :value     (-> data :ice-maintenance :daily-maintenance-count-weekends)
         :on-change #(set-field :ice-maintenance :daily-maintenance-count-weekends %)}]
       [lui/text-field
        {:label     (tr :ice-maintenance/average-water-consumption-l)
         :type      "number"
         :value     (-> data :ice-maintenance :average-water-consumption-l)
         :on-change #(set-field :ice-maintenance :average-water-consumption-l %)}]
       [lui/text-field
        {:label     (tr :ice-maintenance/ice-average-thickness-mm)
         :type      "number"
         :value     (-> data :ice-maintenance :ice-average-thickness-mm)
         :on-change #(set-field :ice-maintenance :ice-average-thickness-mm %)}]]]

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
         :on-change #(set-field :ventilation :heat-pump-type %)}]]]

     ;; Actions
     [lui/form-card {}
      [mui/button {:full-width true
                   :color      "secondary"
                   :variant    "raised"
                   :on-click   #(==> [::events/submit data])}
       (tr :actions/save)]]]))

(defn edit-tab [tr]
  (let [data    (<== [::subs/sites-to-edit])
        editing (<== [::subs/editing])
        locale  (tr)]
    [:div
     [mui/grid {:container true}
      (if data
        [lui/form-card {:title (tr :actions/select-hall)}
         [mui/form-group
          [lui/select {:label     (tr :actions/select-hall)
                       :value     (:lipas-id editing)
                       :items     (map #(hash-map :label (-> % :name locale)
                                                  :value (-> % :lipas-id))
                                       (vals data))
                       :on-change #(when-let [pool (get data %)]
                                     (==> [::events/edit pool]))}]]]
        [mui/typography "Sinulla ei ole oikeuksia yhteenkään jäähalliin. :/"])]
     (when editing
       [ice-stadium-form tr editing])]))

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

         ;; Info tab
         [mui/tab {:label (tr :ice-rinks/headline)
                   :icon (r/as-element [mui-icons/info])}]

         ;; Edit tab
         (when logged-in?
           [mui/tab {:label (tr :ice-basic-data/headline)
                     :icon (r/as-element [mui-icons/edit])}])

         ;; Energy tab
         [mui/tab {:label (tr :ice-energy/headline)
                   :icon (r/as-element [mui-icons/flash-on])}]]]]]

     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        (case @active-tab
          0 (info-tab url)
          1 (edit-tab tr)
          2 (energy-tab tr))]]]]))

(defn main [tr logged-in?]
  (create-panel {:tr tr
                 :url "https://liikuntaportaalit.sportvenue.net/Jaahalli"
                 :logged-in? logged-in?}))
