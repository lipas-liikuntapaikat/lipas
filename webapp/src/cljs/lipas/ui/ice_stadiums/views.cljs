(ns lipas.ui.ice-stadiums.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.components.misc :as misc]
            [lipas.ui.energy.views :as energy]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.rinks :as rinks]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.sports-sites.events :as site-events]
            [lipas.ui.sports-sites.subs :as site-subs]
            [lipas.ui.sports-sites.views :as sports-site]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn stats-tab* []
  (let [tr    (<== [:lipas.ui.subs/translator])
        year  (<== [::subs/stats-year])
        stats (<== [::subs/stats year])

        width (mui/use-width)]

    [mui/grid {:container true :style {:background-color "#fff"}}

     [energy/energy-stats
      {:tr             tr
       :year           year
       :link           "/jaahalliportaali/ilmoita-tiedot"
       :stats          stats
       :on-year-change #(==> [::events/display-stats %])}]

     [mui/grid {:item true :xs 12 :style {:padding "1em"}}
      [mui/typography {:variant "h4" :style {:margin "0.5em 0 0 0" :opacity 0.7}}
       (str (tr :ice/video-description) " - " (str (tr :ice/watch) " " (tr :ice/video)))]]

     [mui/grid {:item true :style {:padding "1em"}}
      [:iframe
       {:src               "https://www.youtube.com/embed/-tIpUzQXZB8"
        :width             (case width
                             ;;"xl" 1920
                             "xl" 1280
                             "lg" 960
                             "md" 640
                             "100%")
        :height            (case width
                             ;;"xl" 1080
                             "xl" 720
                             "lg" 540
                             "md" 360
                             "100%")
        :frameBorder       0
        :allow             (str "accelerometer; autoplay; encrypted-media; "
                                "gyroscope; picture-in-picture")
        :allow-full-screen true}]]

     [energy/hof
      {:tr    tr
       :year  year
       :stats stats}]]))

(defn stats-tab []
  [:f> stats-tab*])

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  [lipas-id & args]
  (==> [::site-events/edit-field lipas-id (butlast args) (last args)]))

(defn show-on-map [lipas-id]
  (let [params {:lipas-id lipas-id}]
    (==> [:lipas.ui.events/navigate :lipas.ui.routes.map/details-view params])))

(defn site-view [{:keys [tr logged-in?]}]
  (let [locale       (tr)
        display-data (<== [::subs/display-site locale])

        lipas-id (:lipas-id display-data)

        close #(==> [::events/display-site nil])

        edit-data        (utils/make-editable (<== [::site-subs/editing-rev lipas-id]))
        editing?         (<== [::site-subs/editing? lipas-id])
        edits-valid?     (<== [::site-subs/edits-valid? lipas-id])
        editing-allowed? (<== [::site-subs/editing-allowed? lipas-id])

        dialogs               (<== [::subs/dialogs])
        types                 (<== [::subs/types-list])
        size-categories       (<== [::subs/size-categories])
        cities                (<== [::site-subs/cities-list])
        owners                (<== [::site-subs/owners])
        admins                (<== [::site-subs/admins])
        base-floor-structures (<== [::subs/base-floor-structures])
        cets                  (<== [::subs/condensate-energy-targets])
        refrigerants          (<== [::subs/refrigerants])
        refrigerant-solutions (<== [::subs/refrigerant-solutions])
        heat-recovery-types   (<== [::subs/heat-recovery-types])
        dryer-types           (<== [::subs/dryer-types])
        dryer-duty-types      (<== [::subs/dryer-duty-types])
        heat-pump-types       (<== [::subs/heat-pump-types])
        ice-resurfacer-fuels  (<== [::subs/ice-resurfacer-fuels])

        user-can-publish? (<== [::user-subs/permission-to-publish? lipas-id])

        set-field (partial set-field lipas-id)]

    [sports-site/site-view
     {:title    (-> display-data :name)
      :lipas-id lipas-id

      :close-label (tr :actions/back-to-listing)
      :on-close    close

      :bottom-actions
      ;; FIXME: Just reagent elements, maybe :<>
      (conj
        (misc/edit-actions-list
          {:editing?          editing?
           :valid?            edits-valid?
           :logged-in?        logged-in?
           :user-can-publish? user-can-publish?
           :editing-allowed?  editing-allowed?
           :on-discard        #(==> [:lipas.ui.events/confirm
                                     (tr :confirm/discard-changes?)
                                     (fn []
                                       (==> [::site-events/discard-edits lipas-id]))])
           :discard-tooltip   (tr :actions/discard)
           :on-edit-start     #(==> [::site-events/edit-site lipas-id])
           :edit-tooltip      (tr :actions/edit)
           :on-publish        #(==> [::site-events/save-edits lipas-id])
           :publish-tooltip   (tr :actions/save)
         ;;:on-delete          #(==> [::site-events/toggle-delete-dialog])
         ;;:delete-tooltip     (tr :actions/delete)
           :invalid-message   (tr :error/invalid-form)})

        (when-not editing?
          [mui/tooltip {:title (tr :map/zoom-to-site)}
           [mui/fab
            {:size     "small"
             :on-click #(show-on-map lipas-id)}
            [mui/icon {:color "secondary"}
             "place"]]]))}

     [mui/grid {:container true :spacing 1}

      ;;; General info
      [lui/form-card {:title (tr :general/general-info)}
       [sports-site/form
        {:tr              tr
         :display-data    display-data
         :edit-data       edit-data
         :read-only?      (not editing?)
         :types           types
         :size-categories size-categories
         :admins          admins
         :owners          owners
         :on-change       set-field}]]

      [lui/form-card {:title (tr :lipas.sports-site/properties)}
       [sports-site/properties-form
        {:tr           tr
         :edit-data    (:properties edit-data)
         :display-data (:properties display-data)
         :type-code    (or
                         (-> edit-data :type :type-code)
                         (-> display-data :type :type-code))
         :on-change    (partial set-field :properties)
         :read-only?   (not editing?)}]]

      ;;; Conditions
      (let [on-change    (partial set-field :conditions)
            display-data (-> display-data :conditions)
            edit-data    (-> edit-data :conditions)]
        [lui/form-card {:title (tr :lipas.ice-stadium.conditions/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Open months
          {:label (tr :lipas.ice-stadium.conditions/open-months)
           :value (-> display-data :open-months)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/open-months
             :adornment (tr :duration/month)
             :value     (-> edit-data :open-months)
             :on-change #(on-change :open-months %)}]}

          ;; Daily open hours
          {:label (tr :lipas.ice-stadium.conditions/daily-open-hours)
           :value (-> display-data :daily-open-hours)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/daily-open-hours
             :adornment (tr :units/hours-per-day)
             :value     (-> edit-data :daily-open-hours)
             :on-change #(on-change :daily-open-hours %)}]}

          ;; Air humidity min %
          {:label (tr :lipas.ice-stadium.conditions/air-humidity-min)
           :value (-> display-data :air-humidity-min)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/air-humidity-min
             :adornment (tr :units/percent)
             :value     (-> edit-data :air-humidity-min)
             :on-change #(on-change :air-humidity-min %)}]}

          ;; Air humidity max %
          {:label (tr :lipas.ice-stadium.conditions/air-humidity-max)
           :value (-> display-data :air-humidity-max)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/air-humidity-max
             :adornment (tr :units/percent)
             :value     (-> edit-data :air-humidity-max)
             :on-change #(on-change :air-humidity-max %)}]}

          ;; Ice surface temperature c
          {:label (tr :lipas.ice-stadium.conditions/ice-surface-temperature-c)
           :value (-> display-data :ice-surface-temperature-c)
           :form-field
           [lui/number-selector
            {:items     (range -10 (inc 0))
             :unit      (tr :physical-units/celsius)
             :deselect? true
             :value     (-> edit-data :ice-surface-temperature-c)
             :on-change #(on-change :ice-surface-temperature-c %)}]}

          ;; Skating area temperature c
          {:label (tr :lipas.ice-stadium.conditions/skating-area-temperature-c)
           :value (-> display-data :skating-area-temperature-c)
           :form-field
           [lui/number-selector
            {:items     (range -8 (inc 20))
             :unit      (tr :physical-units/celsius)
             :deselect? true
             :value     (-> edit-data :skating-area-temperature-c)
             :on-change #(on-change :skating-area-temperature-c %)}]}

          ;; Stand temperature c
          {:label (tr :lipas.ice-stadium.conditions/stand-temperature-c)
           :value (-> display-data :stand-temperature-c)
           :form-field
           [lui/number-selector
            {:items     (range -8 (inc 20))
             :unit      (tr :physical-units/celsius)
             :deselect? true
             :value     (-> edit-data :stand-temperature-c)
             :on-change #(on-change :stand-temperature-c %)}]}

          ;; ;; Daily maintenance count week days
          ;; {:label (tr :lipas.ice-stadium.conditions/daily-maintenances-week-days)
          ;;  :value (-> display-data :daily-maintenances-week-days)
          ;;  :form-field
          ;;  [lui/text-field
          ;;   {:type      "number"
          ;;    :spec      :lipas.ice-stadium.conditions/daily-maintenances-week-days
          ;;    :adornment (tr :units/times-per-day)
          ;;    :value     (-> edit-data :daily-maintenances-week-days)
          ;;    :on-change #(on-change :daily-maintenances-week-days %)}]}

          ;; ;; Daily maintenance count weekends
          ;; {:label (tr :lipas.ice-stadium.conditions/daily-maintenances-weekends)
          ;;  :value (-> display-data :daily-maintenances-weekends)
          ;;  :form-field
          ;;  [lui/text-field
          ;;   {:type      "number"
          ;;    :spec      :lipas.ice-stadium.conditions/daily-maintenances-weekends
          ;;    :adornment (tr :units/times-per-day)
          ;;    :value     (-> edit-data :daily-maintenances-weekends)
          ;;    :on-change #(on-change :daily-maintenances-weekends %)}]}

          ;; Weekly maintenance count
          {:label (tr :lipas.ice-stadium.conditions/weekly-maintenances)
           :value (-> display-data :weekly-maintenances)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/weekly-maintenances
             :adornment (tr :units/times-per-week)
             :value     (-> edit-data :weekly-maintenances)
             :on-change #(on-change :weekly-maintenances %)}]}

          ;; Average water consumption l
          {:label (tr :lipas.ice-stadium.conditions/average-water-consumption-l)
           :value (-> display-data :average-water-consumption-l)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/average-water-consumption-l
             :adornment (tr :physical-units/l)
             :value     (-> edit-data :average-water-consumption-l)
             :on-change #(on-change :average-water-consumption-l %)}]}

          ;; Maintenance water temperature c
          {:label (tr :lipas.ice-stadium.conditions/maintenance-water-temperature-c)
           :value (-> display-data :maintenance-water-temperature-c)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/maintenance-water-temperature-c
             :adornment (tr :physical-units/celsius)
             :value     (-> edit-data :maintenance-water-temperature-c)
             :on-change #(on-change :maintenance-water-temperature-c %)}]}

          ;; Ice resurfacer fuel
          {:label (tr :lipas.ice-stadium.conditions/ice-resurfacer-fuel)
           :value (-> display-data :ice-resurfacer-fuel)
           :form-field
           [lui/select
            {:value     (-> edit-data :ice-resurfacer-fuel)
             :on-change #(on-change :ice-resurfacer-fuel %)
             :deselect? true
             :items     ice-resurfacer-fuels
             :value-fn  first
             :label-fn  (comp locale second)}]}

          ;; Ice average thickness mm
          {:label (tr :lipas.ice-stadium.conditions/ice-average-thickness-mm)
           :value (-> display-data :ice-average-thickness-mm)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.conditions/ice-average-thickness-mm
             :adornment (tr :physical-units/mm)
             :value     (-> edit-data :ice-average-thickness-mm)
             :on-change #(on-change :ice-average-thickness-mm %)}]}]])

      ;;; Location
      [lui/form-card {:title (tr :lipas.location/headline)}
       [sports-site/location-form
        {:tr           tr
         :read-only?   (not editing?)
         :cities       cities
         :edit-data    (:location edit-data)
         :display-data (:location display-data)
         :on-change    (partial set-field :location)}]]

      ;;; Building
      (let [on-change    (partial set-field :building)
            edit-data    (:building edit-data)
            display-data (:building display-data)]
        [lui/form-card {:title (tr :lipas.building/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Main designers
          {:label (tr :lipas.building/main-designers)
           :value (-> display-data :main-designers)
           :form-field
           [lui/text-field
            {:value     (-> edit-data :main-designers)
             :spec      :lipas.building/main-designers
             :on-change #(on-change :main-designers %)}]}

          ;; Total surface area m2
          {:label (tr :lipas.building/total-surface-area-m2)
           :value (-> display-data :total-surface-area-m2)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :total-surface-area-m2)
             :spec      :lipas.building/total-surface-area-m2
             :adornment (tr :physical-units/m2)
             :on-change #(on-change :total-surface-area-m2 %)}]}

          ;; Total volume m3
          {:label (tr :lipas.building/total-volume-m3)
           :value (-> display-data :total-volume-m3)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :total-volume-m3)
             :spec      :lipas.building/total-volume-m3
             :adornment (tr :physical-units/m3)
             :on-change #(on-change :total-volume-m3 %)}]}

          ;; Total ice area m2
          {:label (tr :lipas.building/total-ice-area-m2)
           :value (-> display-data :total-ice-area-m2)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :total-ice-area-m2)
             :spec      :lipas.building/total-ice-area-m2
             :adornment (tr :physical-units/m3)
             :on-change #(on-change :total-ice-area-m2 %)}]}

          {:label (tr :lipas.building/seating-capacity)
           :value (-> display-data :seating-capacity)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :seating-capacity)
             :spec      :lipas.building/seating-capacity
             :adornment (tr :units/person)
             :on-change #(on-change :seating-capacity %)}]}]])

      ;;; Envelope structure
      (let [on-change    (partial set-field :envelope)
            display-data (:envelope display-data)
            edit-data    (:envelope edit-data)]
        [lui/form-card {:title (tr :lipas.ice-stadium.envelope/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Base floor structure
          {:label (tr :lipas.ice-stadium.envelope/base-floor-structure)
           :value (-> display-data :base-floor-structure)
           :form-field
           [lui/select
            {:value     (-> edit-data :base-floor-structure)
             :on-change #(on-change :base-floor-structure %)
             :deselect? true
             :items     base-floor-structures
             :value-fn  first
             :label-fn  (comp locale second)}]}

          ;; Insulated exterior?
          {:label (tr :lipas.ice-stadium.envelope/insulated-exterior?)
           :value (-> display-data :insulated-exterior?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :insulated-exterior?)
             :on-change #(on-change :insulated-exterior? %)}]}

          ;; Insulated ceiling?
          {:label (tr :lipas.ice-stadium.envelope/insulated-ceiling?)
           :value (-> display-data :insulated-ceiling?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :insulated-ceiling?)
             :on-change #(on-change :insulated-ceiling? %)}]}

          ;; Low emissivity coating?
          {:label (tr :lipas.ice-stadium.envelope/low-emissivity-coating?)
           :value (-> display-data :low-emissivity-coating?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :low-emissivity-coating?)
             :on-change #(on-change :low-emissivity-coating? %)}]}]])

      ;;; Rinks
      [lui/form-card {:title (tr :lipas.ice-stadium.rinks/headline)}

       (when (-> dialogs :rink :open?)
         [rinks/dialog {:tr       tr
                        :lipas-id lipas-id}])

       (if editing?
         [rinks/table
          {:tr           tr
           :add-btn-size "small"
           :lipas-id     lipas-id
           :items        (-> edit-data :rinks vals)}]
         [rinks/read-only-table {:tr tr :items (-> display-data :rinks)}])]

      ;;; Refrigeration
      (let [on-change    (partial set-field :refrigeration)
            display-data (:refrigeration display-data)
            edit-data    (:refrigeration edit-data)]
        [lui/form-card {:title (tr :lipas.ice-stadium.refrigeration/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Original?
          {:label (tr :lipas.ice-stadium.refrigeration/original?)
           :value (-> display-data :original?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :original?)
             :on-change #(on-change :original? %)}]}

          ;; Individual metering?
          {:label (tr :lipas.ice-stadium.refrigeration/individual-metering?)
           :value (-> display-data :individual-metering?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :individual-metering?)
             :on-change #(on-change :individual-metering? %)}]}

          ;; Condensate energy recycling?
          {:label (tr :lipas.ice-stadium.refrigeration/condensate-energy-recycling?)
           :value (-> display-data :condensate-energy-recycling?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :condensate-energy-recycling?)
             :on-change #(on-change :condensate-energy-recycling? %)}]}

          ;; Condensate energy main target
          {:label (tr :lipas.ice-stadium.refrigeration/condensate-energy-main-targets)
           :value (-> display-data :condensate-energy-main-targets)
           :form-field
           [lui/multi-select
            {:value     (-> edit-data :condensate-energy-main-targets)
             :items     cets
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :condensate-energy-main-targets %)}]}

          ;; Power kW
          {:label (tr :lipas.ice-stadium.refrigeration/power-kw)
           :value (-> display-data :power-kw)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.refrigeration/power-kw
             :value     (-> edit-data :power-kw)
             :on-change #(on-change :power-kw %)}]}

          ;; Refrigerant
          {:label (tr :lipas.ice-stadium.refrigeration/refrigerant)
           :value (-> display-data :refrigerant)
           :form-field
           [lui/select
            {:value     (-> edit-data :refrigerant)
             :items     refrigerants
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :refrigerant %)}]}

          ;; Refrigerant amount kg
          {:label (tr :lipas.ice-stadium.refrigeration/refrigerant-amount-kg)
           :value (-> display-data :refrigerant-amount-kg)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.refrigeration/refrigerant-amount-kg
             :value     (-> edit-data :refrigerant-amount-kg)
             :on-change #(on-change :refrigerant-amount-kg %)}]}

          ;; Refrigerant solution
          {:label (tr :lipas.ice-stadium.refrigeration/refrigerant-solution)
           :value (-> display-data :refrigerant-solution)
           :form-field
           [lui/select
            {:value     (-> edit-data :refrigerant-solution)
             :items     refrigerant-solutions
             :deselect? true
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :refrigerant-solution %)}]}

          ;; Refrigerant solution amount l
          {:label (tr :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l)
           :value (-> display-data :refrigerant-solution-amount-l)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l
             :value     (-> edit-data :refrigerant-solution-amount-l)
             :on-change #(on-change :refrigerant-solution-amount-l %)}]}]])

      ;;; Ventilation
      (let [on-change    (partial set-field :ventilation)
            edit-data    (:ventilation edit-data)
            display-data (:ventilation display-data)]
        [lui/form-card {:title (tr :lipas.ice-stadium.ventilation/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Heat recovery type
          {:label (tr :lipas.ice-stadium.ventilation/heat-recovery-type)
           :value (-> display-data :heat-recovery-type)
           :form-field
           [lui/select
            {:value     (-> edit-data :heat-recovery-type)
             :items     heat-recovery-types
             :deselect? true
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :heat-recovery-type %)}]}

          ;; NOTE: Removed from form 4.3.2019 but still valid in data
          ;; Heat recovery thermal efficiency percent
          ;; {:label (tr :lipas.ice-stadium.ventilation/heat-recovery-efficiency)
          ;;  :value (-> display-data :heat-recovery-efficiency)
          ;;  :form-field
          ;;  [lui/text-field
          ;;   {:type      "number"
          ;;    :spec      :lipas.ice-stadium.ventilation/heat-recovery-efficiency
          ;;    :adornment (tr :units/percent)
          ;;    :value     (-> edit-data :heat-recovery-efficiency)
          ;;    :on-change #(on-change :heat-recovery-efficiency %)}]}

          ;; Dryer type
          {:label (tr :lipas.ice-stadium.ventilation/dryer-type)
           :value (-> display-data :dryer-type)
           :form-field
           [lui/select
            {:value     (-> edit-data :dryer-type)
             :items     dryer-types
             :deselect? true
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :dryer-type %)}]}

          ;; Dryer duty type
          {:label (tr :lipas.ice-stadium.ventilation/dryer-duty-type)
           :value (-> display-data :dryer-duty-type)
           :form-field
           [lui/select
            {:value     (-> edit-data :dryer-duty-type)
             :items     dryer-duty-types
             :deselect? true
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :dryer-duty-type %)}]}

          ;; Heat pump type
          {:label (tr :lipas.ice-stadium.ventilation/heat-pump-type)
           :value (-> display-data :heat-pump-type)
           :form-field
           [lui/select
            {:value     (-> edit-data :heat-pump-type)
             :items     heat-pump-types
             :deselect? true
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :heat-pump-type %)}]}]])

      ;;; Visitors
      [lui/form-card {:title (tr :lipas.visitors/headline)
                      :md    12 :lg 12}
       [sports-site/visitors-view
        {:tr                tr
         :lipas-id          lipas-id
         :spectators?       true
         :editing?          editing?
         :user-can-publish? user-can-publish?
         :close             close
         :display-data      display-data}]]

      ;;; Energy consumption
      [lui/form-card {:title (tr :lipas.energy-consumption/headline)
                      :md    12 :lg 12}
       [sports-site/energy-consumption-view
        {:tr                tr
         :cold?             true
         :lipas-id          lipas-id
         :editing?          editing?
         :user-can-publish? user-can-publish?
         :close             close
         :display-data      display-data}]]]]))

(defn ice-stadiums-tab [tr logged-in?]
  (let [locale       (tr)
        types        #{2510 2520}
        sites-filter (<== [::subs/sites-filter])
        sites        (<== [::site-subs/sites-list locale types sites-filter])
        display-data (<== [::subs/display-site locale])]

    [mui/grid {:container true}

     (if display-data

       ;; Display individual site
       [site-view {:tr tr :logged-in? logged-in?}]

       ;; Display site list
       [mui/grid {:item true :xs 12}
        [mui/paper

         ;; Sites filter
         [mui/grid {:container true :justify-content "flex-end"}
          [mui/grid {:item true :style {:padding "1em 1em 0em 0em"}}
           [lui/text-field
            {:label     (tr :search/search)
             :on-change #(==> [::events/filter-sites %])
             :value     sites-filter}]]]

         [lui/table
          {:headers
           [[:name (tr :lipas.sports-site/name)]
            [:city (tr :lipas.location/city)]
            [:type (tr :lipas.sports-site/type)]
            [:construction-year (tr :lipas.sports-site/construction-year)]
            [:renovation-years (tr :lipas.sports-site/renovation-years)]]
           :items     sites
           :sort-fn   :city
           :sort-asc? true
           :on-select #(==> [::events/display-site %])}]]])]))

(defn compare-tab []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe
     {:src "https://liikuntaportaalit.sportvenue.net/Jaahalli"
      :style {:min-height "800px" :width "100%"}}]]])

(defn energy-info-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}

    [mui/card {:square true}
     [mui/card-header {:title (tr :ice-energy/headline)}]
     [mui/card-content
      [mui/typography
       (tr :ice-energy/description)]]

     ;; Finnhockey link
     [mui/card-actions
      [mui/button
       {:color "secondary"
        :href  "http://www.finhockey.fi/index.php/info/jaeaehallit"}
       (str "> " (tr :ice-energy/finhockey-link))]

      ;; Energy calculator excel link
      [mui/button
       {:color "secondary"
        :href  (str "https://jyu-my.sharepoint.com/"
                    ":x:/g/personal/vaotjuha_jyu_fi/"
                    "EYBaJ4HG1PdNpcLN3j9lSEkBkfRhirWmNPvD6lufrAUWUw?e=CwAIvn"
                    "&download=1")}
       (str "> " (tr :ice-energy/energy-calculator))]]]]])

(defn energy-form-tab [tr]
  (let [locale         (tr)
        editable-sites (<== [::subs/sites-to-edit-list locale])]
    [energy/energy-consumption-form
     {:tr             tr
      :cold?          true
      :spectators?    true
      :editable-sites editable-sites}]))

(defn reports-tab [tr]
  [sports-site/contacts-report
   {:tr    tr
    :types #{2510 2520}}])

(def tabs
  {0 :lipas.ui.routes.ice-stadiums/front-page
   1 :lipas.ui.routes.ice-stadiums/report-consumption
   2 :lipas.ui.routes.ice-stadiums/list-view
   3 :lipas.ui.routes.ice-stadiums/visualizations
   4 :lipas.ui.routes.ice-stadiums/energy-info
   5 :lipas.ui.routes.ice-stadiums/reports})

(defn create-panel [tr logged-in?]
  (let [active-tab (<== [::subs/active-tab])
        card-props {:square true}]
    [mui/grid {:container true}

     [mui/grid {:item true :xs 12 :class-name :no-print}
      [mui/card card-props
       [mui/card-content
        [mui/tabs
         {:variant    "scrollable"
          :indicator-color "secondary"
          :text-color "secondary"
          :on-change  #(==> [:lipas.ui.events/navigate (get tabs %2)])
          :value      active-tab}

         ;; 0 Stats tab
         [mui/tab {:label (tr :ice/headline)
                   :icon  (r/as-element [mui/icon "ac_unit"])}]

         ;; 1 Energy form tab
         [mui/tab {:label (tr :lipas.energy-consumption/report)
                   :icon  (r/as-element [mui/icon "edit"])}]

         ;; 2 Ice stadiums tab
         [mui/tab
          {:label (tr :ice-rinks/headline)
           :value 2
           :icon  (r/as-element [mui/icon "list_alt"])}]

         ;; 3 Compare tab
         [mui/tab {:label (tr :ice/comparison)
                   :icon  (r/as-element [mui/icon "compare"])}]

         ;; 4 Energy info tab
         [mui/tab
          {:label (tr :ice-energy/headline)
           :value 4
           :icon  (r/as-element [mui/icon "info"])}]

         ;; 5 Reports tab
         [mui/tab
          {:label (tr :reports/contacts)
           :value 5
           :icon  (r/as-element [mui/icon "table_chart"])}]]]]]

     [mui/grid {:item true :xs 12}
      (case active-tab
        0 [stats-tab]
        1 [energy-form-tab tr]
        2 [ice-stadiums-tab tr logged-in?]
        3 [compare-tab]
        4 [energy-info-tab tr]
        5 [reports-tab tr]
        [ice-stadiums-tab tr logged-in?])]]))

(defn main []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [:lipas.ui.subs/logged-in?])]
    [create-panel tr logged-in?]))

(comment
  (utils/navigate! "jaahallit"))
