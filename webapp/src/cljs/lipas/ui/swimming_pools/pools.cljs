(ns lipas.ui.swimming-pools.pools
  (:require [clojure.spec.alpha :as s]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn set-field [dialog field value]
  (#(==> [::events/set-dialog-field dialog field value])))

(defn form [{:keys [tr data]}]
  (let [set-field       (partial set-field :pool)
        pool-types      (<== [::subs/pool-types])
        pool-structures (<== [::subs/pool-structures])
        accessibility   (<== [::subs/accessibility])
        locale          (tr)]
    [mui/form-group

     ;; Pool type
     [lui/select
      {:deselect? true
       :label     (tr :general/type)
       :value     (:type data)
       :items     pool-types
       :label-fn  (comp locale second)
       :value-fn  first
       :on-change #(set-field :type %)}]

     ;; Structure
     #_[lui/select
        {:label     (tr :general/structure)
         :deselect? true
         :value     (:structure data)
         :items     pool-structures
         :label-fn  (comp locale second)
         :value-fn  first
         :on-change #(set-field :structure %)}]

     ;; Outdoor pool?
     [lui/checkbox
      {:label     (tr :lipas.swimming-pool.pool/outdoor-pool?)
       :value     (:outdoor-pool? data)
       :on-change #(set-field :outdoor-pool? %)}]

     ;; Length m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/length-m)
       :adornment (tr :physical-units/m)
       :value     (:length-m data)
       :spec      :lipas.swimming-pool.pool/length-m
       :on-change #(set-field :length-m %)}]

     ;; Width m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/width-m)
       :adornment (tr :physical-units/m)
       :value     (:width-m data)
       :spec      :lipas.swimming-pool.pool/width-m
       :on-change #(set-field :width-m %)}]

     ;; Area m2
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/area-m2)
       :adornment (tr :physical-units/m2)
       :value     (:area-m2 data)
       :spec      :lipas.swimming-pool.pool/area-m2
       :on-change #(set-field :area-m2 %)}]

     ;; Depth min m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/depth-min-m)
       :adornment (tr :physical-units/m)
       :value     (:depth-min-m data)
       :spec      :lipas.swimming-pool.pool/depth-min-m
       :on-change #(set-field :depth-min-m %)}]

     ;; Depth max m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/depth-max-m)
       :adornment (tr :physical-units/m)
       :value     (:depth-max-m data)
       :spec      :lipas.swimming-pool.pool/depth-max-m
       :on-change #(set-field :depth-max-m %)}]

     ;; Temperature c
     [lui/text-field
      {:type      "number"
       :label     (tr :physical-units/temperature-c)
       :adornment (tr :physical-units/celsius)
       :value     (:temperature-c data)
       :spec      :lipas.swimming-pool.pool/temperature-c
       :on-change #(set-field :temperature-c %)}]

     ;; Volume m3
     #_[lui/text-field
        {:type      "number"
         :label     (tr :dimensions/volume-m3)
         :adornment (tr :physical-units/m3)
         :value     (:volume-m3 data)
         :spec      :lipas.swimming-pool.pool/volume-m3
         :on-change #(set-field :volume-m3 %)}]

     ;; Accessibility features
     #_[lui/multi-select
        {:label     (tr :lipas.swimming-pool.pool/accessibility)
         :items     accessibility
         :value     (:accessibility data)
         :value-fn  first
         :label-fn  (comp locale second)
         :on-change #(set-field :accessibility %)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data   (<== [::subs/pool-form])
        reset  #(==> [::events/reset-dialog :pool])
        close  #(==> [::events/toggle-dialog :pool])
        valid? (s/valid? :lipas.swimming-pool/pool data)]
    [lui/dialog {:title         (if (:id data)
                                  (tr :lipas.swimming-pool.pools/edit-pool)
                                  (tr :lipas.swimming-pool.pools/add-pool))
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      #(==> [::events/toggle-dialog :pool])
                 :save-enabled? valid?
                 :on-save       (comp reset
                                      close
                                      #(==> [::events/save-pool lipas-id data]))}
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
    [lui/form-table
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
      :on-add          #(==> [::events/toggle-dialog :pool {}])
      :on-edit         #(==> [::events/toggle-dialog :pool (get items (:id %))])
      :on-delete       #(==> [::events/remove-pool lipas-id %])}]))

(defn read-only-table [{:keys [tr items]}]
  [lui/table {:headers (make-headers tr)
              :items   (sort-by :length-m utils/reverse-cmp items)
              :key-fn  #(gensym)}])
