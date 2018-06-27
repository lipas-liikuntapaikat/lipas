(ns lipas.ui.swimming-pools.views
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [clojure.pprint :refer [pprint]]
            [lipas.ui.mui :as mui]
            [lipas.ui.energy :as energy]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.pools :as pools]
            [lipas.ui.swimming-pools.saunas :as saunas]
            [lipas.ui.swimming-pools.slides :as slides]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.utils :refer [set-field]]
            [lipas.ui.utils :refer [<== ==> ->select-entries make-revision
                                    resolve-year]]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn details-card [{:keys [title] :as props} & content]
  [mui/grid {:item true :md 12 :xs 12}
   [mui/card {:square true}
    [mui/card-header {:title title}]
    (into [mui/card-content] content)]])

(defn details-dialog [{:keys [tr site]}]
  (let [locale          (tr)
        location        (:location site)
        building        (:building site)
        water-treatment (:water-treatment site)
        other-services  (:other-services site)
        facilities      (:facilities site)
        close           #(==> [::events/display-site nil])]
    [mui/dialog {:open       true
                 :full-width true
                 :max-width  "md"
                 :on-close   close}
     [mui/dialog-title (-> site :name)]
     [mui/dialog-content
      [mui/grid {:container true :spacing 16}

       ;; General info
       [details-card {:title (tr :general/general-info)}
        [lui/sports-site-info {:tr tr :site site}]]

       ;; Location
       [details-card {:title (tr :location/headline)}
        [lui/location-info {:tr tr :location location}]]


       ;; Building
       [details-card {:title (tr :building/headline)}
        [lui/info-table
         {:data
          [[(tr :building/construction-year) (-> building :construction-year)]
           [(tr :building/main-designers) (-> building :main-designers)]
           [(tr :building/total-surface-area-m2) (-> building :total-surface-area-m2)]
           [(tr :building/total-volume-m3) (-> building :total-volume-m3)]
           [(tr :building/seating-capacity) (-> building :seating-capacity)]
           [(tr :building/staff-count) (-> building :staff-count)]
           [(tr :building/pool-room-total-area-m2) (-> site :building :pool-room-total-area-m2)]
           [(tr :building/total-water-area-m2) (-> building :total-water-area-m2)]
           [(tr :building/heat-sections?) (-> building :heat-sections?)]
           [(tr :building/piled?) (-> building :piled?)]
           [(tr :building/heat-source) (-> building :heat-source)]
           [(tr :building/main-construction-materials) (-> building :main-construction-materials)]]}]]
       ;; Water treatment
       [details-card {:title (tr :water-treatment/headline)}
        [lui/info-table
         {:data
          [[(tr :water-treatment/ozonation?) (-> water-treatment :ozonation?)]
           [(tr :water-treatment/uv-treatment?) (-> water-treatment :uv-treatment?)]
           [(tr :water-treatment/uv-treatment?) (-> water-treatment :uv-treatment?)]
           [(tr :water-treatment/activated-carbon?) (-> water-treatment :activated-carbon?)]]}]]

       ;; Other services
       [details-card {:title (tr :other-services/headline)}
        [lui/info-table
         {:data
          [[(tr :other-services/platforms-1m-count) (-> other-services :platforms-1m-count?)]
           [(tr :other-services/platforms-3m-count) (-> other-services :platforms-3m-count?)]
           [(tr :other-services/platforms-5m-count) (-> other-services :platforms-5m-count?)]
           [(tr :other-services/platforms-7.5m-count) (-> other-services :platforms-7.5m-count?)]
           [(tr :other-services/platforms-10m-count) (-> other-services :platforms-10m-count?)]
           [(tr :other-services/hydro-massage-spots-count) (-> other-services :hydro-massage-spots-count)]
           [(tr :other-services/hydro-neck-massage-spots-count) (-> other-services :hydro-neck-massage-spots-count)]
           [(tr :other-services/kiosk?) (-> other-services :kiosk?)]]}]]

       ;; Showers and lockers
       [details-card {:title (tr :facilities/headline)}
        [lui/info-table
         {:data
          [[(tr :facilities/showers-men-count) (-> facilities :showers-men-count)]
           [(tr :facilities/showers-women-count) (-> facilities :showers-women-count)]
           [(tr :facilities/lockers-men-count) (-> facilities :lockers-men-count)]
           [(tr :facilities/lockers-women-count) (-> facilities :lockers-women-count)]]}]]

       ;; Pools
       [details-card {:title (tr :pools/headline)}
        [pools/read-only-table {:tr tr :items (:pools site)}]]

       ;; Slides
       [details-card {:title (tr :slides/headline)}
        [slides/read-only-table {:tr tr :items (:slides site)}]]

       ;; Saunas
       [details-card {:title (tr :saunas/headline)}
        [saunas/read-only-table {:tr tr :items (:saunas site)}]]

       ;; Energy consumption
       [details-card {:title (tr :energy/headline)}
        [energy/table {:read-only? true :tr tr :items (:energy-consumption site)}]]]]

     [mui/dialog-actions
      [mui/button {:on-click close}
       (tr :actions/close)]]]))

(defn info-tab [tr]
  (let [locale       (tr)
        pools        (<== [::subs/swimming-pools-list locale])
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

(defn swimming-pool-form [{:keys [tr rev]}]
  (let [data                  rev
        year                  (resolve-year (:timestamp rev))
        dialogs               (<== [::subs/dialogs])
        types                 (<== [::subs/types-list])
        cities                (<== [::subs/cities-list])
        owners                (<== [::subs/owners])
        admins                (<== [::subs/admins])
        heat-sources          (<== [::subs/heat-sources])
        filtering-methods     (<== [::subs/filtering-methods])
        building-materials    (<== [::subs/building-materials])
        supporting-structures (<== [::subs/supporting-structures])
        ceiling-structures    (<== [::subs/ceiling-structures])
        energy-history        (<== [::subs/energy-consumption-history])
        set-field             (partial set-field :editing :rev)]

    (r/with-let [renovations-done? (r/atom false)]
      [mui/grid {:container true}

       ;; Energy consumption
       [lui/form-card {:title (tr :energy/headline-year year)}
        [energy/form {:tr        tr
                      :data      (:energy-consumption data)
                      :on-change (partial set-field :energy-consumption)}]
        [lui/expansion-panel {:label (tr :actions/show-all-years)}
         [energy/table {:tr         tr
                        :read-only? true
                        :items      energy-history}]]]

       ;; Visitors
       [lui/form-card {:title (tr :visitors/headline-year year)}
        [mui/form-group
         [lui/text-field
          {:label     (tr :visitors/total-count)
           :type      "number"
           :value     (-> data :visitors :total-count)
           :spec      ::schema/visitors-total-count
           :adornment (tr :units/person)
           :on-change #(set-field :visitors :total-count %)}]]]

       [lui/form-card {:title (tr :renovations/headline-year year)}
        [lui/checkbox
         {:label     (tr :renovations/renovations-done? year)
          :checked   @renovations-done?
          :on-change #(swap! renovations-done? not)}]]

       ;; General info
       (when @renovations-done?
         [lui/form-card {:title (tr :general/general-info)}
          [lui/sports-place-form
           {:tr        tr
            :data      data
            :types     types
            :owners    owners
            :admins    admins
            :on-change set-field}]])

       ;; Location
       (when @renovations-done?
         [lui/form-card {:title (tr :location/headline)}
          [lui/location-form
           {:tr        tr
            :data      (:location data)
            :cities    cities
            :on-change (partial set-field :location)}]])

       ;; Building
       (when @renovations-done?
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
             :on-change #(set-field :building :seating-capacity %)}]
           [lui/text-field
            {:label     (tr :building/staff-count)
             :type      "number"
             :value     (-> data :building :staff-count)
             :spec      ::schema/staff-count
             :adornment (tr :units/person)
             :on-change #(set-field :building :staff-count %)}]
           [lui/text-field
            {:label     (tr :building/pool-room-total-area-m2)
             :type      "number"
             :value     (-> data :building :pool-room-total-area-m2)
             :spec      ::schema/pool-room-total-area-m2
             :adornment (tr :physical-units/m2)
             :on-change #(set-field :building :pool-room-total-area-m2 %)}]
           [lui/text-field
            {:label     (tr :building/total-water-area-m2)
             :type      "number"
             :value     (-> data :building :total-water-area-m2)
             :spec      ::schema/total-water-area-m2
             :adornment (tr :physical-units/m2)
             :on-change #(set-field :building :total-water-area-m2 %)}]
           [lui/checkbox
            {:label     (tr :building/heat-sections?)
             :value     (-> data :building :heat-sections?)
             :on-change #(set-field :building :heat-sections? %)}]
           [lui/checkbox
            {:label     (tr :building/piled?)
             :value     (-> data :building :piled?)
             :on-change #(set-field :building :piled? %)}]
           [lui/select
            {:label (tr :building/heat-source)
             :value (-> data :building :heat-source)
             :items (->select-entries tr :heat-sources heat-sources)}]
           [lui/multi-select
            {:label     (tr :building/main-construction-materials)
             :value     (-> data :building :main-construction-materials)
             :items     (->select-entries tr :building-materials building-materials)
             :on-change #(set-field :building :main-construction-materials %)}]
           [lui/multi-select
            {:label     (tr :building/supporting-structures)
             :value     (-> data :building :supporting-structures)
             :items     (->select-entries tr
                                          :supporting-structures
                                          supporting-structures)
             :on-change #(set-field :building :supporting-structures %)}]
           [lui/multi-select
            {:label     (tr :building/ceiling-structures)
             :value     (-> data :building :ceiling-structures)
             :items     (->select-entries tr :ceiling-structures ceiling-structures)
             :on-change #(set-field :building :ceiling-structures %)}]]])

       ;; Renovations
       ;; (when (-> dialogs :renovation :open?)
       ;;   [renovations/dialog {:tr tr}])

       ;; [lui/form-card {:title (tr :renovations/headline)}
       ;;  [renovations/table {:tr tr :items (-> data :renovations vals)}]]

       ;; Water treatment
       (when @renovations-done?
         [lui/form-card {:title (tr :water-treatment/headline)}
          [mui/form-group
           [lui/checkbox
            {:label     (tr :water-treatment/ozonation?)
             :value     (-> data :water-treatment :ozonation?)
             :on-change #(set-field :water-treatment :ozonation? %)}]
           [lui/checkbox
            {:label     (tr :water-treatment/uv-treatment?)
             :value     (-> data :water-treatment :uv-treatment?)
             :on-change #(set-field :water-treatment :uv-treatment? %)}]
           [lui/checkbox
            {:label     (tr :water-treatment/activated-carbon?)
             :value     (-> data :water-treatment :activated-carbon?)
             :on-change #(set-field :water-treatment :activated-carbon? %)}]
           [lui/multi-select
            {:label     (tr :water-treatment/filtering-method)
             :value     (-> data :water-treatment :filtering-method)
             :items     (map #(hash-map :value %
                                        :label (tr (keyword :filtering-methods %)))
                             (keys filtering-methods))
             :on-change #(set-field :water-treatment :filtering-method %)}]
           [lui/text-field
            {:label     (tr :general/comment)
             :value     (-> data :water-treatment :comment)
             :spec      ::schema/comment
             :on-change #(set-field :water-treatment :comment %)}]]])

       ;; Pools
       (when (-> dialogs :pool :open?)
         [pools/dialog {:tr tr}])

       (when @renovations-done?
         [lui/form-card {:title (tr :pools/headline)}
          [pools/table {:tr    tr
                        :items (-> data :pools)}]])

       ;; Saunas
       (when (-> dialogs :sauna :open?)
         [saunas/dialog {:tr tr}])

       (when @renovations-done?
         [lui/form-card {:title (tr :saunas/headline)}
          [saunas/table {:tr    tr
                         :items (-> data :saunas)}]])

       ;; Slides
       (when (-> dialogs :slide :open?)
         [slides/dialog {:tr tr}])

       (when @renovations-done?
         [lui/form-card {:title (tr :slides/headline)}
          [slides/table {:tr    tr
                         :items (-> data :slides)}]])

       ;; Other services
       (when @renovations-done?
         [lui/form-card {:title (tr :other-services/headline)}
          [mui/form-group
           [lui/text-field
            {:label     (tr :other-services/platforms-1m-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :platforms-1m-count)
             :spec      ::schema/platforms-1m-count
             :on-change #(set-field :other-services :platforms-1m-count %)}]
           [lui/text-field
            {:label     (tr :other-services/platforms-3m-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :platforms-3m-count)
             :spec      ::schema/platforms-3m-count
             :on-change #(set-field :other-services :platforms-3m-count %)}]
           [lui/text-field
            {:label     (tr :other-services/platforms-5m-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :platforms-5m-count)
             :spec      ::schema/platforms-5m-count
             :on-change #(set-field :other-services :platforms-5m-count %)}]
           [lui/text-field
            {:label     (tr :other-services/platforms-7.5m-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :platforms-7.5m-count)
             :spec      ::schema/platforms-7.5m-count
             :on-change #(set-field :other-services :platforms-7.5m-count %)}]
           [lui/text-field
            {:label     (tr :other-services/platforms-10m-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :platforms-10m-count)
             :spec      ::schema/platforms-10m-count
             :on-change #(set-field :other-services :platforms-10m-count %)}]
           [lui/text-field
            {:label     (tr :other-services/hydro-massage-spots-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :hydro-massage-spots-count)
             :spec      ::schema/hydro-massage-spots-count
             :on-change #(set-field :other-services :hydro-massage-spots-count %)}]
           [lui/text-field
            {:label     (tr :other-services/hydro-neck-massage-spots-count)
             :adornment (tr :units/pcs)
             :type      "number"
             :value     (-> data :other-services :hydro-neck-massage-spots-count)
             :spec      ::schema/hydro-neck-massage-spots-count
             :on-change #(set-field :other-services :hydro-neck-massage-spots-count %)}]
           [lui/checkbox
            {:label     (tr :other-services/kiosk?)
             :value     (-> data :other-services :kiosk?)
             :on-change #(set-field :other-services :kiosk? %)}]]])

       ;; Showers and lockers
       (when @renovations-done?
         [lui/form-card {:title (tr :facilities/headline)}
          [mui/form-group
           [lui/text-field
            {:label     (tr :facilities/showers-men-count)
             :type      "number"
             :adornment (tr :units/pcs)
             :value     (-> data :facilities :showers-men-count)
             :spec      ::schema/showers-men-count
             :on-change #(set-field :facilities :showers-men-count %)}]
           [lui/text-field
            {:label     (tr :facilities/showers-women-count)
             :type      "number"
             :adornment (tr :units/pcs)
             :value     (-> data :facilities :showers-women-count)
             :spec      ::schema/showers-women-count
             :on-change #(set-field :facilities :showers-women-count %)}]
           [lui/text-field
            {:label     (tr :facilities/lockers-men-count)
             :type      "number"
             :adornment (tr :units/pcs)
             :value     (-> data :facilities :lockers-men-count)
             :spec      ::schema/lockers-men-count
             :on-change #(set-field :facilities :lockers-men-count %)}]
           [lui/text-field
            {:label     (tr :facilities/lockers-women-count)
             :type      "number"
             :adornment (tr :units/pcs)
             :value     (-> data :facilities :lockers-women-count)
             :spec      ::schema/lockers-women-count
             :on-change #(set-field :facilities :lockers-women-count %)}]]])

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
       [mui/typography "Sinulla ei ole oikeuksia yhteenkään Uimahalliin. :/"])

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
       [swimming-pool-form
        {:tr  tr
         :rev rev}])]))

(defn create-panel [tr logged-in? url]
  (let [active-tab (re-frame/subscribe [::subs/active-tab])]
    [mui/grid {:container true}

     [mui/grid {:item true :xs 12}
      [mui/card
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width true
                   :text-color "secondary"
                   :on-change #(==> [::events/set-active-tab %2])
                   :value @active-tab}

         ;; 0 Info tab
         [mui/tab {:label (tr :swim/list)
                   :icon (r/as-element [mui/icon "list_alt"])}]

         ;; 1 Compare tab
         [mui/tab {:label (tr :swim/visualizations)
                   :icon (r/as-element [mui/icon "compare"])}]

         ;; 2 Edit tab
         (when logged-in?
           [mui/tab {:label (tr :swim/edit)
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
  (let [url "https://liikuntaportaalit.sportvenue.net/Uimahalli"]
    (create-panel tr logged-in? url)))
