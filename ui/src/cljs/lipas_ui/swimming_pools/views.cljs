(ns lipas-ui.swimming-pools.views
  (:require [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.swimming-pools.events :as events]
            [lipas-ui.swimming-pools.subs :as subs]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(defn set-field
  ":k1 :k2 ... :kn event

  Event is always the last argument."
  [& args]
  (let [path (butlast args)
        path (into [] (flatten [path]))
        event (last args)
        value (-> event
                  .-target
                  .-value)]
    (re-frame/dispatch [::events/set-field path value])))

(defn info-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography (tr :ice-energy/description)]]])

(defn hall-selector [tr]
  [mui/text-field {:select true
                   :label (tr :ice-form/select-rink)
                   :value "Halli 1"}
   (for [hall ["Halli 1" "Halli 2" "Halli 3"]]
     [mui/menu-item {:key hall
                     :value hall}
      hall])])

(defn year-selector [{:keys [label value on-change]}]
  [mui/text-field {:label label
                   :type "number"
                   :select true
                   :on-change on-change
                   :value value}
   (for [year (range 2000 (inc (.getFullYear (js/Date.))))]
     [mui/menu-item {:key year
                     :value year}
      year])])

(defn form-card [{:keys [title]} content]
  [mui/grid {:item true
             :xs 12
             :md 12}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    [mui/card-content content]]])

(defn checkbox [{:keys [label value on-change]}]
  [mui/form-control-label
   {:label label
    :control (r/as-element
              [mui/checkbox {:value (str value)
                             :checked value
                             :on-change on-change}])}])

(defn text-field-unit [{:keys [label value unit on-change]}]
  [mui/text-field {:label label
                   :type "number"
                   :value value
                   :on-change on-change
                   :Input-props
                   {:end-adornment
                    (r/as-element
                     [mui/input-adornment unit])}}])

(defn form-table [{:keys [headers
                          items
                          add-tooltip
                          delete-tooltip
                          on-add
                          on-delete]}]
  [mui/grid {:container true
             :spacing 8
             :justify "flex-end"}
   [mui/grid {:item true :xs 12}
    [:div {:style {:overflow-x "auto"}}
     [mui/table
      [mui/table-head
       [mui/table-row
        (for [[idx [_ header]] (map-indexed vector headers)]
          ^{:key idx} [mui/table-cell header])
        [mui/table-cell ""]]]
      [mui/table-body
       (for [[idx item] (map-indexed vector items)]
         ^{:key idx} [mui/table-row
                      (for [k (keys headers)]
                        ^{:key k} [mui/table-cell {:padding "dense"}
                                   (let [v (get item k)]
                                     (cond
                                       (true? v) "✓"
                                       :else v))])
                      [mui/table-cell {:numeric true
                                       :padding "none"}
                       [mui/tooltip {:title (or delete-tooltip "")
                                     :placement "left"}
                        [mui/icon-button {:on-click #(on-delete item)}
                         [mui-icons/delete]]]]])]]]]
   [mui/grid {:item true
              :xs 2
              :style {:text-align "right"} }
    [mui/tooltip {:title (or add-tooltip "")
                  :placement "left"}
     [mui/button {:style {:margin-top "0.5em"}
                  :on-click on-add
                  :variant "fab"
                  :color "secondary"}
      [mui-icons/add]]]]])

(defn toggle-dialog [dialog]
  (==> [::events/toggle-dialog dialog]))

(defn add-dialog [{:keys [dialog title on-add add-label cancel-label]} content]
  [mui/dialog {:open true
               :full-width true
               :on-close #(toggle-dialog dialog)}
   [mui/dialog-title title]
   [mui/dialog-content content]
   [mui/dialog-actions
    [mui/button {:on-click #(toggle-dialog dialog)}
     cancel-label]
    [mui/button {:on-click on-add}
     add-label]]])

(defn temp-fn [&]
  (js/alert "FIXME"))

(defn basic-data-tab [tr]
  (let [data (<== [::subs/editing])
        dialogs (<== [::subs/dialogs])]
   [mui/grid {:container true}

    ;; General info
    [form-card {:title (tr :general/general-info)}
     [mui/form-group
      [mui/text-field {:label (tr :sports-place/name-fi)
                       :value (-> data :name :fi)
                       :on-change temp-fn}]
      [mui/text-field {:label (tr :sports-place/name-se)
                       :value (-> data :name :se)
                       :on-change temp-fn}]
      [mui/text-field {:label (tr :sports-place/name-en)
                       :value (-> data :name :en)
                       :on-change temp-fn}]
      [mui/text-field {:label (tr :sports-place/owner)
                       :value (-> data :owner)
                       :on-change temp-fn}]
      [mui/text-field {:label (tr :sports-place/admin)
                       :value (-> data :admin)}]
      [mui/text-field {:label (tr :sports-place/phone-number)
                       :value (-> data :phone-number)}]
      [mui/text-field {:label (tr :sports-place/www)
                       :value (-> data :www)}]
      [mui/text-field {:label (tr :sports-place/email-public)
                       :value (-> data :email)}]]]

    ;; Location
    [form-card {:title (tr :location/headline)}
     [mui/form-group
      [mui/text-field {:label (tr :location/address)
                       :value (-> data :location :address)}]
      [mui/text-field {:label (tr :location/postal-code)
                       :value (-> data :location :postal-code)}]
      [mui/text-field {:label (tr :location/postal-office)
                       :value (-> data :location :postal-office)}]
      [mui/text-field {:label (tr :location/city)
                       :value (-> data :location :city :name)}]]]

    ;; Building
    [form-card {:title (tr :building/headline)}
     [mui/form-group
      [mui/text-field {:label (tr :building/construction-year)
                       :type "number"
                       :value (-> data :building :construction-year)}]
      [mui/text-field {:label (tr :building/main-designers)
                       :value (-> data :building :main-designer)}]
      [text-field-unit {:label (tr :building/total-surface-area-m2)
                        :value (-> data :building :total-surface-area-m2)
                        :unit (tr :physical-units/m2)}]
      [text-field-unit {:label (tr :building/total-volume-m3)
                        :value (-> data :building :total-volume-m3)
                        :unit (tr :physical-units/m3)}]
      [text-field-unit {:label (tr :building/pool-room-total-area-m2)
                        :unit (tr :physical-units/m2)
                        :value (-> data :building :pool-room-total-area-m2)}]
      [text-field-unit {:label (tr :building/total-water-area-m2)
                        :unit (tr :physical-units/m2)
                        :value (-> data :building :total-water-area-m2)}]
      [checkbox {:label (tr :building/heat-sections?)
                 :value (-> data :building :heat-sections?)
                 :on-change #(js/alert "kiskis")}]
      [checkbox {:label (tr :building/piled?)
                 :value (-> data :building :piled?)
                 :on-change #(js/alert "kiskis")}]
      [mui/text-field {:label (tr :building/main-construction-materials)
                       :value (-> data :building :main-construction-materials)}]
      [mui/text-field {:label (tr :building/supporting-structures)
                       :value (-> data :building :supporting-structures)}]
      [mui/text-field {:label (tr :building/ceiling-material)
                       :value (-> data :building :ceiling-material)}]]]

    ;; Renovations
    (when (-> dialogs :add-renovation :open?)
      (let [data (<== [::subs/add-renovation-form])
            set-field (partial set-field :dialogs :add-renovation :data)
            reset #(==> [::events/reset-dialog :add-renovation])
            close #(toggle-dialog :add-renovation)]
        [add-dialog {:dialog :add-renovation
                     :title (tr :renovations/add-renovation)
                     :add-label (tr :actions/add)
                     :cancel-label (tr :actions/cancel)
                     :on-add (comp reset
                                   close
                                   #(==> [::events/add-renovation data]))}
         [mui/form-group
          [year-selector {:label (tr :renovations/end-year)
                          :value (:year data)
                          :on-change #(set-field :year %)}]
          [mui/text-field {:label (tr :renovations/designers)
                           :value (:designer data)
                           :on-change #(set-field :designer %)}]
          [mui/text-field {:label (tr :general/description)
                           :value (:comment data)
                           :on-change #(set-field :comment %)
                           :multiline true
                           :rows 5}]]]))

    [form-card {:title (tr :renovations/headline)}
     [form-table {:headers {:year (tr :time/year)
                            :comment (tr :general/description)
                            :designer (tr :renovations/designers)}
                  :items (-> data :renovations vals)
                  :add-tooltip (tr :renovations/add-renovation)
                  :on-add #(toggle-dialog :add-renovation)
                  :on-delete #(==> [::events/remove-renovation %])
                  :delete-tooltip (tr :actions/delete)}]]

    ;; Water treatment
    [form-card {:title (tr :water-treatment/headline)}
     [mui/form-group
      [checkbox {:label "Otsonointi"
                 :value (-> data :water-treatment :ozonation?)}]
      [checkbox {:label "UV-käsittely"
                 :value (-> data :water-treatment :uv-treatment?)}]
      [checkbox {:label "Aktiivihiili"
                 :value (-> data :water-treatment :activated-carbon?)}]
      [mui/text-field {:label (tr :water-treatment/filtering-method)
                       :value (-> data :water-treatment :filtering-method)}]
      [mui/text-field {:label (tr :general/comment)
                       :value (-> data :water-treatment :comment)}]]]

    ;; Pools
    (when (-> dialogs :add-pool :open?)
      (let [data (<== [::subs/add-pool-form])
            set-field (partial set-field :dialogs :add-pool :data)
            reset #(==> [::events/reset-dialog :add-pool])
            close #(toggle-dialog :add-pool)]
        [add-dialog {:dialog :add-pool
                     :title (tr :pools/pool)
                     :add-label (tr :actions/add)
                     :cancel-label (tr :actions/cancel)
                     :on-add (comp reset
                                   close
                                   #(==> [::events/add-pool data]))}
         [mui/form-group
          [mui/text-field {:required true
                           :label (tr :general/name)
                           :value (:name data)
                           :on-change #(set-field :name %)}]
          [mui/text-field {:type "number"
                           :label (tr :physical-units/temperature-c)
                           :value (:temperature-c data)
                           :on-change #(set-field :temperature-c %)}]
          [mui/text-field {:type "number"
                           :label (tr :dimensions/volume-m3)
                           :value (:volume-m3 data)
                           :on-change #(set-field :volume-m3 %)}]
          [mui/text-field {:type "number"
                           :label (tr :dimensions/area-m2)
                           :value (:area-m2 data)
                           :on-change #(set-field :area-m2 %)}]
          [mui/text-field {:type "number"
                           :label (tr :dimensions/length-m)
                           :value (:length-m data)
                           :on-change #(set-field :length-m %)}]
          [mui/text-field {:type "number"
                           :label (tr :dimensions/width-m)
                           :value (:width-m data)
                           :on-change #(set-field :width-m %)}]
          [mui/text-field {:type "number"
                           :label (tr :dimensions/depth-min-m)
                           :value (:depth-min-m data)
                           :on-change #(set-field :depth-min-m %)}]
          [mui/text-field {:type "number"
                           :label (tr :dimensions/depth-max-m)
                           :value (:depth-max-m data)
                           :on-change #(set-field :depth-max-m %)}]
          ]]))

    [form-card {:title (tr :pools/headline)}
     [form-table {:headers [[:name (tr :general/name)]
                            [:temperature-c (tr :physical-units/temperature-c)]
                            [:volume-m3 (tr :dimensions/volume-m3)]
                            [:area-m2 (tr :dimensions/surface-area-m2)]
                            [:length-m (tr :dimensions/length-m)]
                            [:width-m (tr :dimensions/width-m)]
                            [:depth-min-m (tr :dimensions/depth-min-m)]
                            [:depth-max-m (tr :dimensions/depth-max-m)]
                            [:structure (tr :pools/structure)]]
                  :items (-> data :pools vals)
                  :add-tooltip (tr :renovations/add-pool)
                  :on-add #(toggle-dialog :add-pool)
                  :on-delete #(==> [::events/remove-pool %])
                  :delete-tooltip (tr :actions/delete)}]]

    ;; Slides
    [form-card {:title (tr :slides/headline)}
     [mui/form-group
      [form-table {:headers {:name (tr :general/name)
                             :length-m (tr :dimensions/length-m)}
                   :items (-> data :slides vals)}]]]

    ;; Saunas
    [form-card {:title (tr :saunas/headline)}
     [form-table {:headers {:type (tr :general/type)
                            :women (tr :saunas/women)
                            :men (tr :saunas/men)}
                  :items (-> data :saunas vals)}]]

    ;; Showers and lockers
    [form-card {:title (tr :facilities/headline)}
     [mui/form-group
      [mui/text-field {:label (tr :facilities/showers-men-count)
                       :value (-> data :facilities :showers-men-count)}]
      [mui/text-field {:label (tr :facilities/showers-women-count)
                       :value (-> data :facilities :showers-women-count)}]
      [mui/text-field {:label (tr :facilities/lockers-men-count)
                       :value (-> data :facilities :lockers-men-count)}]
      [mui/text-field {:label (tr :facilities/lockers-women-count)
                       :value (-> data :facilities :lockers-women-count)}]]]]))

(defn form-tab [tr]
  [form-card (tr :energy/consumption-info)
   [mui/form-group
    [hall-selector tr]
    [year-selector {:label (tr :time/year)
                    :value 2018
                    :on-change #(js/console.log "FIXME")}]
    [mui/text-field {:label (tr :energy/electricity)
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment (tr :physical-units/mwh)])}}]
    [mui/text-field {:label (tr :energy/heat)
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment (tr :physical-units/mwh)])}}]
    [mui/text-field {:label (tr :energy/water)
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment (tr :physical-units/m3)])}}]
    [mui/button {:color "secondary"
                 :size "large"}
     (tr :actions/save)]]])

(defn change-tab [_ value]
  (re-frame/dispatch [::events/set-active-tab value]))

(defn create-panel [tr url]
  (let [active-tab (re-frame/subscribe [::subs/active-tab])]
    [mui/grid {:container true}
     [mui/grid {:item true
                :xs 12}
      [mui/card
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width true
                   :text-color "secondary"
                   :on-change change-tab
                   :value @active-tab}
         [mui/tab {:label (tr :ice-rinks/headline)
                   :icon (r/as-element [mui-icons/info])}]
         [mui/tab {:label (tr :ice-energy/headline)
                   :icon (r/as-element [mui-icons/flash-on])}]
         [mui/tab {:label (tr :ice-basic-data/headline)
                   :icon (r/as-element [mui-icons/edit])}]
         [mui/tab {:label (tr :ice-form/headline)
                   :icon (r/as-element [mui-icons/add])}]]]]]
     [mui/grid {:item true
                :xs 12}
      (case @active-tab
        0 (info-tab url)
        1 (energy-tab tr)
        2 (basic-data-tab tr)
        3 (form-tab tr))]]))

(defn main [tr]
  (let [url "https://liikuntaportaalit.sportvenue.net/Uimahalli"]
    (create-panel tr url)))
