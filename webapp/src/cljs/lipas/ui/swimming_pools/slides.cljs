(ns lipas.ui.swimming-pools.slides
  (:require [clojure.spec.alpha :as s]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.utils :refer [<== ==> localize-field ->setter-fn]]))

(def set-field (->setter-fn ::events/set-field))

(defn form [{:keys [tr data]}]
  (let [structures (<== [::subs/slide-structures])
        set-field  (partial set-field :dialogs :slide :data)
        locale     (tr)]
    [mui/form-group
     [lui/select {:required  true
                  :label     (tr :general/structure)
                  :value     (:structure data)
                  :items     structures
                  :label-fn  (comp locale second)
                  :value-fn  first
                  :on-change #(set-field :structure %)}]
     [lui/text-field {:label     (tr :dimensions/length-m)
                      :adornment (tr :physical-units/m)
                      :type      "number"
                      :value     (:length-m data)
                      :spec      :lipas.swimming-pool.slide/length-m
                      :on-change #(set-field :length-m %)}]]))

(defn dialog [{:keys [tr]}]
  (let [data    (<== [::subs/slide-form])
        close   #(==> [::events/toggle-dialog :slide])
        reset   #(==> [::events/reset-dialog :slide])
        valid?  (s/valid? :lipas.swimming-pool/slide data)
        title   (if (:id data)
                  (tr :lipas.swimming-pool.slides/edit-slide)
                  (tr :lipas.swimming-pool.slides/add-slide))
        on-save (comp reset close #(==> [::events/save-slide data]))]
    [lui/dialog {:title         title
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      #(==> [::events/toggle-dialog :slide])
                 :save-enabled? valid?
                 :on-save       on-save}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:structure (tr :general/structure)]
   [:length-m (tr :dimensions/length-m)]])

(defn table [{:keys [tr items]}]
  (let [localize (partial localize-field tr :structure :slide-structures)]
    [lui/form-table
     {:headers        (make-headers tr)
      :items          (map localize (vals items))
      :add-tooltip    (tr :lipas.swimming-pool.slides/add-slide)
      :edit-tooltip   (tr :actions/edit)
      :delete-tooltip (tr :actions/delete)
      :on-add         #(==> [::events/toggle-dialog :slide {}])
      :on-edit        #(==> [::events/toggle-dialog :slide (get items (:id %))])
      :on-delete      #(==> [::events/remove-slide %])}]))

(defn read-only-table [{:keys [tr items]}]
  [lui/table {:headers (make-headers tr)
              :items   items
              :key-fn  #(gensym)}])
