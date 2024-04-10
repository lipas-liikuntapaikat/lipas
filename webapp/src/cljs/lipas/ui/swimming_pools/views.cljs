(ns lipas.ui.swimming-pools.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.energy.views :as energy]
   [lipas.ui.mui :as mui]
   [lipas.ui.sports-sites.events :as site-events]
   [lipas.ui.sports-sites.subs :as site-subs]
   [lipas.ui.sports-sites.views :as sports-site]
   [lipas.ui.swimming-pools.events :as events]
   [lipas.ui.swimming-pools.pools :as pools]
   [lipas.ui.swimming-pools.saunas :as saunas]
   [lipas.ui.swimming-pools.slides :as slides]
   [lipas.ui.swimming-pools.subs :as subs]
   [lipas.ui.user.subs :as user-subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn stats-tab []
  (let [tr    (<== [:lipas.ui.subs/translator])
        year  (<== [::subs/stats-year])
        stats (<== [::subs/stats year])]

    [:<>
     [energy/energy-stats
      {:tr             tr
       :year           year
       :link           "/uimahallit/ilmoita-tiedot"
       :stats          stats
       :on-year-change #(==> [::events/display-stats %])}]

     [energy/hof
      {:tr             tr
       :year           year
       :stats          stats}]]))

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

(defn site-view []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [:lipas.ui.subs/logged-in?])

        locale       (tr)
        display-data (<== [::subs/display-site locale])

        lipas-id (:lipas-id display-data)

        close #(==> [::events/display-site nil])

        edit-data        (<== [::site-subs/editing-rev lipas-id])
        editing?         (<== [::site-subs/editing? lipas-id])
        edits-valid?     (<== [::site-subs/edits-valid? lipas-id])
        editing-allowed? (<== [::site-subs/editing-allowed? lipas-id])

        types                 (<== [::subs/types-list])
        dialogs               (<== [::subs/dialogs])
        cities                (<== [::site-subs/cities-list])
        owners                (<== [::site-subs/owners])
        admins                (<== [::site-subs/admins])
        heat-sources          (<== [::subs/heat-sources])
        filtering-methods     (<== [::subs/filtering-methods])
        building-materials    (<== [::site-subs/building-materials])
        supporting-structures (<== [::site-subs/supporting-structures])
        ceiling-structures    (<== [::site-subs/ceiling-structures])

        user-can-publish? (<== [::user-subs/permission-to-publish? lipas-id])

        set-field (partial set-field lipas-id)]

    [sports-site/site-view
     {:title       (-> display-data :name)
      :lipas-id    lipas-id
      :on-close    close
      :close-label (tr :actions/back-to-listing)

      :bottom-actions
      (conj
       (lui/edit-actions-list
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
            :color    "default"
            :on-click #(show-on-map lipas-id)}
           [mui/icon {:color "secondary"}
            "place"]]]))}

     [mui/grid {:container true :spacing 1}

      ;;; General info
      [lui/form-card {:title (tr :general/general-info)}
       [sports-site/form
        {:tr           tr
         :display-data display-data
         :edit-data    edit-data
         :read-only?   (not editing?)
         :types        types
         :admins       admins
         :owners       owners
         :on-change    set-field}]]

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

      ;;; Building
      (let [display-data (-> display-data :building)
            edit-data    (-> edit-data :building)
            on-change    (partial set-field :building)]
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

          ;; Seating capacity
          {:label (tr :lipas.building/seating-capacity)
           :value (-> display-data :seating-capacity)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :seating-capacity)
             :spec      :lipas.building/seating-capacity
             :adornment (tr :units/person)
             :on-change #(on-change :seating-capacity %)}]}

          ;; Staff count
          {:label (tr :lipas.building/staff-count)
           :value (-> display-data :staff-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :staff-count)
             :spec      :lipas.building/staff-count
             :adornment (tr :units/person)
             :on-change #(on-change :staff-count %)}]}

          ;; Pool room total area m2
          {:label (tr :lipas.building/total-pool-room-area-m2)
           :value (-> display-data :total-pool-room-area-m2)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :total-pool-room-area-m2)
             :spec      :lipas.building/total-pool-room-area-m2
             :adornment (tr :physical-units/m2)
             :on-change #(on-change :total-pool-room-area-m2 %)}]}

          ;; Total water area m2
          {:label (tr :lipas.building/total-water-area-m2)
           :value (-> display-data :total-water-area-m2)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :total-water-area-m2)
             :spec      :lipas.building/total-water-area-m2
             :adornment (tr :physical-units/m2)
             :on-change #(on-change :total-water-area-m2 %)}]}

          ;; Heat sections?
          {:label (tr :lipas.building/heat-sections?)
           :value (-> display-data :heat-sections?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :heat-sections?)
             :on-change #(on-change :heat-sections? %)}]}

          ;; Piled?
          {:label (tr :lipas.building/piled?)
           :value (-> display-data :piled?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :piled?)
             :on-change #(on-change :piled? %)}]}

          ;; Heat sources
          {:label (tr :lipas.building/heat-source)
           :value (-> display-data :heat-sources)
           :form-field
           [lui/multi-select
            {:value     (-> edit-data :heat-sources)
             :items     heat-sources
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :heat-sources %)}]}

          ;; Main construction materials
          {:label (tr :lipas.building/main-construction-materials)
           :value (-> display-data :main-construction-materials)
           :form-field
           [lui/multi-select
            {:value     (-> edit-data :main-construction-materials)
             :items     building-materials
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :main-construction-materials %)}]}

          ;; Supporting structures
          {:label (tr :lipas.building/supporting-structures)
           :value (-> display-data :supporting-structures)
           :form-field
           [lui/multi-select
            {:value     (-> edit-data :supporting-structures)
             :items     supporting-structures
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :supporting-structures %)}]}

          ;; Ceiling structures
          {:label (tr :lipas.building/ceiling-structures)
           :value (-> display-data :ceiling-structures)
           :form-field
           [lui/multi-select
            {:value     (-> edit-data :ceiling-structures)
             :items     ceiling-structures
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :ceiling-structures %)}]}]])

      ;;; Location
      [lui/form-card {:title (tr :lipas.location/headline)}
       [sports-site/location-form
        {:tr           tr
         :read-only?   (not editing?)
         :cities       cities
         :edit-data    (:location edit-data)
         :display-data (:location display-data)
         :on-change    (partial set-field :location)}]]

      ;;; Water treatment
      (let [display-data (-> display-data :water-treatment)
            edit-data    (-> edit-data :water-treatment)
            on-change    (partial set-field :water-treatment)]

        [lui/form-card {:title (tr :lipas.swimming-pool.water-treatment/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Ozonation?
          {:label (tr :lipas.swimming-pool.water-treatment/ozonation?)
           :value (-> display-data :ozonation?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :ozonation?)
             :on-change #(on-change :ozonation? %)}]}

          ;; UV-treatment?
          {:label (tr :lipas.swimming-pool.water-treatment/uv-treatment?)
           :value (-> display-data :uv-treatment?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :uv-treatment?)
             :on-change #(on-change :uv-treatment? %)}]}

          ;; Activated carbon?
          {:label (tr :lipas.swimming-pool.water-treatment/activated-carbon?)
           :value (-> display-data :activated-carbon?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :activated-carbon?)
             :on-change #(on-change :activated-carbon? %)}]}

          ;; Filtering methods
          {:label (tr :lipas.swimming-pool.water-treatment/filtering-methods)
           :value (-> display-data :filtering-methods)
           :form-field
           [lui/multi-select
            {:value     (-> edit-data :filtering-methods)
             :items     filtering-methods
             :label-fn  (comp locale second)
             :value-fn  first
             :on-change #(on-change :filtering-methods %)}]}

          ;; Comment
          {:label (tr :general/comment)
           :value (-> display-data :comment)
           :form-field
           [lui/text-field
            {:value     (-> edit-data :comment)
             :spec      :lipas.swimming-pool.water-treatment/comment
             :on-change #(on-change :comment %)}]}]])

      ;;; Pools
      [lui/form-card
       {:title (tr :lipas.swimming-pool.pools/headline) :md 12 :lg 12}

       (when (-> dialogs :pool :open?)
         [pools/dialog {:tr tr :lipas-id lipas-id}])

       (if editing?
         [pools/table {:tr tr :items (-> edit-data :pools) :lipas-id lipas-id}]
         [pools/read-only-table {:tr tr :items (-> display-data :pools)}])]

      ;;; Saunas
      [lui/form-card
       {:title (tr :lipas.swimming-pool.saunas/headline)}

       (when (-> dialogs :sauna :open?)
         [saunas/dialog {:tr tr :lipas-id lipas-id}])

       (if editing?
         [saunas/table {:tr tr :items (-> edit-data :saunas) :lipas-id lipas-id}]
         [saunas/read-only-table {:tr tr :items (-> display-data :saunas)}])]

      ;;; Slides
      [lui/form-card {:title (tr :lipas.swimming-pool.slides/headline)}

       (when (-> dialogs :slide :open?)
         [slides/dialog {:tr tr :lipas-id lipas-id}])

       (if editing?
         [slides/table {:tr tr :items (-> edit-data :slides) :lipas-id lipas-id}]
         [slides/read-only-table {:tr tr :items (-> display-data :slides)}])]

      (let [display-data (-> display-data :facilities)
            edit-data    (-> edit-data :facilities)
            on-change    (partial set-field :facilities)]
        [lui/form-card {:title "Hyppypaikat"}
         [lui/form {:read-only? (not editing?)}
          ;; Platforms 1m count
          {:label (tr :lipas.swimming-pool.facilities/platforms-1m-count)
           :value (-> display-data :platforms-1m-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :platforms-1m-count)
             :spec      :lipas.swimming-pool.facilities/platforms-1m-count
             :on-change #(on-change :platforms-1m-count %)}]}

          ;; Platforms 3m count
          {:label (tr :lipas.swimming-pool.facilities/platforms-3m-count)
           :value (-> display-data :platforms-3m-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :platforms-3m-count)
             :spec      :lipas.swimming-pool.facilities/platforms-3m-count
             :on-change #(on-change :platforms-3m-count %)}]}

          ;; Platforms 5m count
          {:label (tr :lipas.swimming-pool.facilities/platforms-5m-count)
           :value (-> display-data :platforms-5m-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :platforms-5m-count)
             :spec      :lipas.swimming-pool.facilities/platforms-5m-count
             :on-change #(on-change :platforms-5m-count %)}]}

          ;; Platforms 7.5m count
          {:label (tr :lipas.swimming-pool.facilities/platforms-7.5m-count)
           :value (-> display-data :platforms-7.5m-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :platforms-7.5m-count)
             :spec      :lipas.swimming-pool.facilities/platforms-7.5m-count
             :on-change #(on-change :platforms-7.5m-count %)}]}

          ;; Platforms 10m count
          {:label (tr :lipas.swimming-pool.facilities/platforms-10m-count)
           :value (-> display-data :platforms-10m-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :platforms-10m-count)
             :spec      :lipas.swimming-pool.facilities/platforms-10m-count
             :on-change #(on-change :platforms-10m-count %)}]}]])

      ;;; Facilities
      (let [display-data (-> display-data :facilities)
            edit-data    (-> edit-data :facilities)
            on-change    (partial set-field :facilities)]

        [lui/form-card {:title (tr :lipas.swimming-pool.facilities/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Hydro neck massage spots count
          {:label (tr :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count)
           :value (-> display-data :hydro-neck-massage-spots-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :hydro-neck-massage-spots-count)
             :spec      :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count
             :on-change #(on-change :hydro-neck-massage-spots-count %)}]}

          ;; Other Hydro massage spots count
          {:label (tr :lipas.swimming-pool.facilities/hydro-massage-spots-count)
           :value (-> display-data :hydro-massage-spots-count)
           :form-field
           [lui/text-field
            {:adornment (tr :units/pcs)
             :type      "number"
             :value     (-> edit-data :hydro-massage-spots-count)
             :spec      :lipas.swimming-pool.facilities/hydro-massage-spots-count
             :on-change #(on-change :hydro-massage-spots-count %)}]}

          ;; Kiosk?
          {:label (tr :lipas.swimming-pool.facilities/kiosk?)
           :value (-> display-data :kiosk?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :kiosk?)
             :on-change #(on-change :kiosk? %)}]}

          ;; Gym?
          {:label (tr :lipas.swimming-pool.facilities/gym?)
           :value (-> display-data :gym?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :gym?)
             :on-change #(on-change :gym? %)}]}

          ;; Showers men count
          {:label (tr :lipas.swimming-pool.facilities/showers-men-count)
           :value (-> display-data :showers-men-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :adornment (tr :units/pcs)
             :value     (-> edit-data :showers-men-count)
             :spec      :lipas.swimming-pool.facilities/showers-men-count
             :on-change #(on-change :showers-men-count %)}]}

          ;; Showers women count
          {:label (tr :lipas.swimming-pool.facilities/showers-women-count)
           :value (-> display-data :showers-women-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :adornment (tr :units/pcs)
             :value     (-> edit-data :showers-women-count)
             :spec      :lipas.swimming-pool.facilities/showers-women-count
             :on-change #(on-change :showers-women-count %)}]}

          ;; Showers unisex count
          {:label (tr :lipas.swimming-pool.facilities/showers-unisex-count)
           :value (-> display-data :showers-unisex-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :adornment (tr :units/pcs)
             :value     (-> edit-data :showers-unisex-count)
             :spec      :lipas.swimming-pool.facilities/showers-unisex-count
             :on-change #(on-change :showers-unisex-count %)}]}

          ;; Lockers men count
          {:label (tr :lipas.swimming-pool.facilities/lockers-men-count)
           :value (-> display-data :lockers-men-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :adornment (tr :units/pcs)
             :value     (-> edit-data :lockers-men-count)
             :spec      :lipas.swimming-pool.facilities/lockers-men-count
             :on-change #(on-change :lockers-men-count %)}]}

          ;; Lockers women count
          {:label (tr :lipas.swimming-pool.facilities/lockers-women-count)
           :value (-> display-data :lockers-women-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :adornment (tr :units/pcs)
             :value     (-> edit-data :lockers-women-count)
             :spec      :lipas.swimming-pool.facilities/lockers-women-count
             :on-change #(on-change :lockers-women-count %)}]}

          ;; Lockers unisex count
          {:label (tr :lipas.swimming-pool.facilities/lockers-unisex-count)
           :value (-> display-data :lockers-unisex-count)
           :form-field
           [lui/text-field
            {:type      "number"
             :adornment (tr :units/pcs)
             :value     (-> edit-data :lockers-unisex-count)
             :spec      :lipas.swimming-pool.facilities/lockers-unisex-count
             :on-change #(on-change :lockers-unisex-count %)}]}]])

      ;;; Conditions
      (let [display-data (-> display-data :conditions)
            edit-data    (-> edit-data :conditions)
            on-change    (partial set-field :conditions)]

        [lui/form-card {:title (tr :lipas.swimming-pool.conditions/headline)}

         [lui/form {:read-only? (not editing?)}

          ;; Open days in year
          {:label (tr :lipas.swimming-pool.conditions/open-days-in-year)
           :value (-> display-data :open-days-in-year)
           :form-field
           [lui/text-field
            {:type      "number"
             :value     (-> edit-data :open-days-in-year)
             :spec      :lipas.swimming-pool.conditions/open-days-in-year
             :adornment (tr :units/days-in-year)
             :on-change #(on-change :open-days-in-year %)}]}

          ;; Daily open hours total
          {:label (tr :lipas.swimming-pool.conditions/daily-open-hours)
           :value (-> display-data :daily-open-hours)
           :form-field
           [lui/text-field
            {:type      "number"
             :spec      :lipas.swimming-pool.conditions/daily-open-hours
             :adornment (tr :units/hours-per-day)
             :value     (-> edit-data :daily-open-hours)
             :on-change #(on-change :daily-open-hours %)}]}]

         ;; Daily open hours for each day
         ;; (for [day  ["mon" "tue" "wed" "thu" "fri" "sat" "sun"]
         ;;       :let [kw (keyword (str "open-hours-" day))]]

         ;;   {:label (tr (keyword :lipas.swimming-pool.conditions kw))
         ;;    :value (-> display-data kw)
         ;;    :form-field
         ;;    [lui/text-field
         ;;     {:type      "number"
         ;;      :spec      (keyword :lipas.swimming-pool.conditions kw)
         ;;      :adornment (tr :duration/hour)
         ;;      :value     (-> edit-data kw)
         ;;      :on-change #(on-change kw %)}]})
         ])

      ;;; Energy saving
      (let [display-data (-> display-data :energy-saving)
            edit-data    (-> edit-data :energy-saving)
            on-change    (partial set-field :energy-saving)]

        [lui/form-card {:title (tr :lipas.swimming-pool.energy-saving/headline)}
         [lui/form {:read-only? (not editing?)}

          ;; Shower water recycling?
          {:label (tr (keyword :lipas.swimming-pool.energy-saving
                               :shower-water-heat-recovery?))
           :value (-> display-data :shower-water-recovery)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :shower-water-recovery)
             :on-change #(on-change :shower-water-recovery %)}]}

          ;; Filter rinse water heat recovery?
          {:label (tr (keyword :lipas.swimming-pool.energy-saving
                               :filter-rinse-water-heat-recovery?))
           :value (-> display-data :filter-rinse-water-heat-recovery?)
           :form-field
           [lui/checkbox
            {:value     (-> edit-data :filter-rinse-water-heat-recovery?)
             :on-change #(on-change :filter-rinse-water-heat-recovery? %)}]}]])

      ;;; Visitors
      [lui/form-card {:title (tr :lipas.visitors/headline)
                      :md    12 :lg 12}
       [sports-site/visitors-view
        {:tr                tr
         :lipas-id          lipas-id
         :editing?          editing?
         :user-can-publish? user-can-publish?
         :close             close
         :display-data      display-data}]]

      ;;; Energy consumption
      [lui/form-card {:title (tr :lipas.energy-consumption/headline)
                      :md    12 :lg 12}
       [sports-site/energy-consumption-view
        {:tr                tr
         :lipas-id          lipas-id
         :editing?          editing?
         :user-can-publish? user-can-publish?
         :close             close
         :display-data      display-data}]]]]))

(defn swimming-pools-tab [tr logged-in?]
  (let [locale       (tr)
        types        #{3110 3130}
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
    [:iframe {:src "https://liikuntaportaalit.sportvenue.net/Uimahalli"
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-info-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card {:square true}
     [mui/card-header {:title (tr :swim-energy/headline)}]
     [mui/card-content
      [mui/typography
       (tr :swim-energy/description)]]
     [mui/card-actions
      [mui/button {:color :secondary
                   :href  "https://www.ukty.fi/index.php/ohjepankki"}
       (str "> " (tr :swim-energy/ukty-link))]
      [mui/button {:color :secondary
                   :href  "http://www.suh.fi/toiminta/tekninen_neuvonta"}
       (str "> " (tr :swim-energy/suh-link))]]]]])

(defn energy-form-tab [tr]
  (let [locale          (tr)
        editable-sites  (<== [::subs/sites-to-edit-list locale])]
    [energy/energy-consumption-form
     {:tr              tr
      :cold?           false
      :spectators?     false
      :editable-sites  editable-sites}]))

(defn reports-tab [tr]
  [sports-site/contacts-report
   {:tr    tr
    :types #{3110 3130}}])

(def tabs
  {0 :lipas.ui.routes.swimming-pools/front-page
   1 :lipas.ui.routes.swimming-pools/report-consumption
   2 :lipas.ui.routes.swimming-pools/list-view
   3 :lipas.ui.routes.swimming-pools/visualizations
   4 :lipas.ui.routes.swimming-pools/energy-info
   5 :lipas.ui.routes.swimming-pools/reports})

(defn create-panel [tr logged-in?]
  (let [active-tab (<== [::subs/active-tab])]
    [mui/grid {:container true}
     [mui/grid {:item       true :xs 12
                :class-name :no-print}
      [mui/card {:square true}
       [mui/card-content
        [mui/tabs
         {:variant    "scrollable"
          :text-color "secondary"
          :on-change  #(==> [:lipas.ui.events/navigate (get tabs %2)])
          :value      active-tab
          :indicator-color "secondary"}

         ;; 0 Stats
         ;; [mui/tab {:label (tr :swim/headline)
         ;;           :icon  (r/as-element [mui/icon "pool"])}]

         ;; 1 Energy form tab
         ;; [mui/tab {:label (tr :lipas.energy-consumption/report)
         ;;           :icon  (r/as-element [mui/icon "edit"])}]

         ;; 2 Halls tab
         [mui/tab
          {:label (tr :swim/list)
           :value 2
           :icon  (r/as-element [mui/icon "list_alt"])}]

         ;; 3 Compare tab
         ;; [mui/tab
         ;;  {:label (tr :swim/visualizations)
         ;;   :icon  (r/as-element [mui/icon "compare"])}]

         ;; 4 Energy info tab
         ;; [mui/tab {:label (tr :swim-energy/headline)
         ;;           :icon  (r/as-element [mui/icon "info"])}]

         ;; 5 Reports tab
         [mui/tab
          {:label (tr :reports/contacts)
           :value 5
           :icon  (r/as-element [mui/icon "table_chart"])}]]]]]

     [mui/grid {:item true :xs 12}
      (case active-tab
        ;;0 [stats-tab]
        ;;1 [energy-form-tab tr]
        2 [swimming-pools-tab tr logged-in?]
        ;;3 [compare-tab]
        ;;4 [energy-info-tab tr]
        5 [reports-tab tr]
        [swimming-pools-tab tr logged-in?])]]))

(defn main []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [:lipas.ui.subs/logged-in?])]
    [create-panel tr logged-in?]))
