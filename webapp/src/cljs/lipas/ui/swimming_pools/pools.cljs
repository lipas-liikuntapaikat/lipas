(ns lipas.ui.swimming-pools.pools
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.utils :refer [<== ==> localize-field ->setter-fn]]))

(def set-field (->setter-fn ::events/set-field))

(defn form [{:keys [tr data]}]
  (let [set-field       (partial set-field :dialogs :pool :data)
        pool-types      (<== [::subs/pool-types])
        pool-structures (<== [::subs/pool-structures])
        locale          (tr)]
    [mui/form-group

     ;; Pool type
     [lui/select
      {:required  true
       :label     (tr :general/type)
       :value     (:type data)
       :items     pool-types
       :label-fn  (comp locale second)
       :value-fn  first
       :on-change #(set-field :type %)}]

     ;; Structure
     [lui/select
      {:label     (tr :pools/structure)
       :value     (:structure data)
       :items     pool-structures
       :label-fn  (comp locale second)
       :value-fn  first
       :on-change #(set-field :structure %)}]

     ;; Temperature c
     [lui/text-field
      {:type      "number"
       :label     (tr :physical-units/temperature-c)
       :adornment (tr :physical-units/celsius)
       :value     (:temperature-c data)
       :spec      ::schema/pool-temperature-c
       :on-change #(set-field :temperature-c %)}]

     ;; Volume m3
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/volume-m3)
       :adornment (tr :physical-units/m3)
       :value     (:volume-m3 data)
       :spec      ::schema/pool-volume-m3
       :on-change #(set-field :volume-m3 %)}]

     ;; Area m2
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/area-m2)
       :adornment (tr :physical-units/m2)
       :value     (:area-m2 data)
       :spec      ::schema/pool-area-m2
       :on-change #(set-field :area-m2 %)}]

     ;; Length m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/length-m)
       :adornment (tr :physical-units/m)
       :value     (:length-m data)
       :spec      ::schema/pool-length-m
       :on-change #(set-field :length-m %)}]

     ;; Width m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/width-m)
       :adornment (tr :physical-units/m)
       :value     (:width-m data)
       :spec      ::schema/pool-width-m
       :on-change #(set-field :width-m %)}]

     ;; Depth min m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/depth-min-m)
       :adornment (tr :physical-units/m)
       :value     (:depth-min-m data)
       :spec      ::schema/pool-depth-min-m
       :on-change #(set-field :depth-min-m %)}]

     ;; Depth max m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/depth-max-m)
       :adornment (tr :physical-units/m)
       :value     (:depth-max-m data)
       :spec      ::schema/pool-depth-max-m
       :on-change #(set-field :depth-max-m %)}]]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/pool-form])
        reset #(==> [::events/reset-dialog :pool])
        close #(==> [::events/toggle-dialog :pool])]
    [lui/dialog {:title (if (:id data)
                          (tr :pools/edit-pool)
                          (tr :pools/add-pool))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(==> [::events/toggle-dialog :pool])
                 :on-save (comp reset
                                close
                                #(==> [::events/save-pool data]))}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:type (tr :general/type)]
   [:temperature-c (tr :physical-units/temperature-c)]
   [:volume-m3 (tr :dimensions/volume-m3)]
   [:area-m2 (tr :dimensions/surface-area-m2)]
   [:length-m (tr :dimensions/length-m)]
   [:width-m (tr :dimensions/width-m)]
   [:depth-min-m (tr :dimensions/depth-min-m)]
   [:depth-max-m (tr :dimensions/depth-max-m)]
   [:structure (tr :pools/structure)]])

(defn table [{:keys [tr items]}]
  (let [localize (partial localize-field tr)]
    [lui/form-table
     {:headers        (make-headers tr)
      :items          (->> (vals items)
                           (map (partial localize :type :pool-types))
                           (map (partial localize :structure :pool-structures)))
      :add-tooltip    (tr :pools/add-pool)
      :edit-tooltip   (tr :actions/edit)
      :delete-tooltip (tr :actions/delete)
      :on-add         #(==> [::events/toggle-dialog :pool {}])
      :on-edit        #(==> [::events/toggle-dialog :pool (get items (:id %))])
      :on-delete      #(==> [::events/remove-pool %])}]))

(defn read-only-table [{:keys [tr items]}]
  [lui/table {:headers (make-headers tr)
              :items   items
              :key-fn  #(gensym)}])
