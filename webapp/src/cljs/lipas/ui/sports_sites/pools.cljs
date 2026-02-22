(ns lipas.ui.sports-sites.pools
  (:require [lipas.schema.swimming-pools :as pool-schema]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as text-fields]
            [malli.core :as m]
            ["@mui/material/FormGroup$default" :as FormGroup]
            [lipas.ui.sports-sites.hall-equipment :as hall]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn set-field [dialog field value]
  (#(==> [::hall/set-dialog-field dialog field value])))

(defn form [{:keys [tr data]}]
  (let [set-field       (partial set-field :pool)
        pool-types      (<== [::hall/pool-types])
        locale          (tr)]
    [:> FormGroup

     ;; Pool type
     [selects/select
      {:deselect? true
       :label     (tr :general/type)
       :value     (:type data)
       :items     pool-types
       :label-fn  (comp locale second)
       :value-fn  first
       :on-change #(set-field :type %)}]

     ;; Structure
     #_[selects/select
        {:label     (tr :general/structure)
         :deselect? true
         :value     (:structure data)
         :items     pool-structures
         :label-fn  (comp locale second)
         :value-fn  first
         :on-change #(set-field :structure %)}]

     ;; Outdoor pool?
     [checkboxes/checkbox
      {:label     (tr :lipas.swimming-pool.pool/outdoor-pool?)
       :value     (:outdoor-pool? data)
       :on-change #(set-field :outdoor-pool? %)}]

     ;; Length m
     [text-fields/text-field
      {:type      "number"
       :label     (tr :dimensions/length-m)
       :adornment (tr :physical-units/m)
       :value     (:length-m data)
       :spec      pool-schema/pool-length-m
       :on-change #(set-field :length-m %)}]

     ;; Width m
     [text-fields/text-field
      {:type      "number"
       :label     (tr :dimensions/width-m)
       :adornment (tr :physical-units/m)
       :value     (:width-m data)
       :spec      pool-schema/pool-width-m
       :on-change #(set-field :width-m %)}]

     ;; Area m2
     [text-fields/text-field
      {:type      "number"
       :label     (tr :dimensions/area-m2)
       :adornment (tr :physical-units/m2)
       :value     (:area-m2 data)
       :spec      pool-schema/pool-area-m2
       :on-change #(set-field :area-m2 %)}]

     ;; Depth min m
     [text-fields/text-field
      {:type      "number"
       :label     (tr :dimensions/depth-min-m)
       :adornment (tr :physical-units/m)
       :value     (:depth-min-m data)
       :spec      pool-schema/pool-depth-m
       :on-change #(set-field :depth-min-m %)}]

     ;; Depth max m
     [text-fields/text-field
      {:type      "number"
       :label     (tr :dimensions/depth-max-m)
       :adornment (tr :physical-units/m)
       :value     (:depth-max-m data)
       :spec      pool-schema/pool-depth-m
       :on-change #(set-field :depth-max-m %)}]

     ;; Temperature c
     [text-fields/text-field
      {:type      "number"
       :label     (tr :physical-units/temperature-c)
       :adornment (tr :physical-units/celsius)
       :value     (:temperature-c data)
       :spec      pool-schema/pool-temperature-c
       :on-change #(set-field :temperature-c %)}]

     ;; Volume m3
     #_[text-fields/text-field
        {:type      "number"
         :label     (tr :dimensions/volume-m3)
         :adornment (tr :physical-units/m3)
         :value     (:volume-m3 data)
         :spec      [:and number? [:fn #(<= 0 % 5000)]]
         :on-change #(set-field :volume-m3 %)}]

     ;; Accessibility features
     #_[selects/multi-select
        {:label     (tr :lipas.swimming-pool.pool/accessibility)
         :items     accessibility
         :value     (:accessibility data)
         :value-fn  first
         :label-fn  (comp locale second)
         :on-change #(set-field :accessibility %)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data   (<== [::hall/pool-form])
        reset  #(==> [::hall/reset-dialog :pool])
        close  #(==> [::hall/toggle-dialog :pool])
        valid? (m/validate pool-schema/pool-schema data)]
    [dialogs/dialog {:title         (if (:id data)
                                  (tr :lipas.swimming-pool.pools/edit-pool)
                                  (tr :lipas.swimming-pool.pools/add-pool))
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      #(==> [::hall/toggle-dialog :pool])
                 :save-enabled? valid?
                 :on-save       (comp reset
                                      close
                                      #(==> [::hall/save-pool lipas-id data]))}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:type (tr :general/type)]
   #_[:volume-m3 (tr :dimensions/volume-m3)]
   [:length-m (tr :dimensions/length-m)]
   [:width-m (tr :dimensions/width-m)]
   [:area-m2 (tr :dimensions/surface-area-m2)]
   [:depth-min-m (tr :dimensions/depth-min-m)]
   [:depth-max-m (tr :dimensions/depth-max-m)]
   [:temperature-c (tr :physical-units/temperature-c)]
   #_[:structure (tr :general/structure)]
   #_[:accessibility (tr :lipas.swimming-pool.pool/accessibility)]
   [:outdoor-pool? (tr :lipas.swimming-pool.pool/outdoor-pool?)]])

(defn- localize-accessibility [tr pool]
  (update pool :accessibility
          #(map (fn [f] (tr (keyword :accessibility f))) %)))

(defn table [{:keys [tr items lipas-id add-btn-size max-width]}]
  (let [localize (partial utils/localize-field tr)]
    [tables/form-table
     {:headers         (make-headers tr)
      :items
      (->> (vals items)
           (map (partial localize :type :pool-types))
           (map (partial localize :structure :pool-structures))
           (map (partial localize-accessibility tr))
           (sort-by :length-m utils/reverse-cmp))
      :max-width       max-width
      :add-tooltip     (tr :lipas.swimming-pool.pools/add-pool)
      :add-btn-size    add-btn-size
      :edit-tooltip    (tr :actions/edit)
      :delete-tooltip  (tr :actions/delete)
      :confirm-tooltip (tr :confirm/delete-confirm)
      :on-add          #(==> [::hall/toggle-dialog :pool {}])
      :on-edit         #(==> [::hall/toggle-dialog :pool (get items (:id %))])
      :on-delete       #(==> [::hall/remove-pool lipas-id %])}]))

(defn read-only-table [{:keys [tr items]}]
  [tables/table {:headers (make-headers tr)
              :items   (sort-by :length-m utils/reverse-cmp items)
              :key-fn  #(gensym)}])
