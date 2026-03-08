(ns lipas.ui.sports-sites.slides
  (:require [lipas.schema.swimming-pools :as pool-schema]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as text-fields]
            [malli.core :as m]
            ["@mui/material/FormGroup$default" :as FormGroup]
            [lipas.ui.sports-sites.hall-equipment :as hall]
            [lipas.ui.utils :refer [<== ==>]]))

(defn set-field [dialog field value]
  (#(==> [::hall/set-dialog-field dialog field value])))

(defn form [{:keys [tr data]}]
  (let [set-field  (partial set-field :slide)]
    [:> FormGroup
     [text-fields/text-field
      {:label     (tr :dimensions/length-m)
       :adornment (tr :physical-units/m)
       :type      "number"
       :value     (:length-m data)
       :spec      pool-schema/pool-length-m
       :on-change #(set-field :length-m %)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data    (<== [::hall/slide-form])
        close   #(==> [::hall/toggle-dialog :slide])
        reset   #(==> [::hall/reset-dialog :slide])
        valid?  (m/validate pool-schema/slide-schema data)
        title   (if (:id data)
                  (tr :lipas.swimming-pool.slides/edit-slide)
                  (tr :lipas.swimming-pool.slides/add-slide))
        on-save (comp reset close #(==> [::hall/save-slide lipas-id data]))]
    [dialogs/dialog {:title         title
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      #(==> [::hall/toggle-dialog :slide])
                 :save-enabled? valid?
                 :on-save       on-save}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:length-m (tr :dimensions/length-m)]])

(defn table [{:keys [tr items lipas-id add-btn-size max-width]}]
  [tables/form-table
   {:headers         (make-headers tr)
    :add-btn-size    add-btn-size
    :max-width       max-width
    :items           (vals items)
    :add-tooltip     (tr :lipas.swimming-pool.slides/add-slide)
    :edit-tooltip    (tr :actions/edit)
    :delete-tooltip  (tr :actions/delete)
    :confirm-tooltip (tr :confirm/delete-confirm)
    :on-add          #(==> [::hall/toggle-dialog :slide {}])
    :on-edit         #(==> [::hall/toggle-dialog :slide (get items (:id %))])
    :on-delete       #(==> [::hall/remove-slide lipas-id %])}])

(defn read-only-table [{:keys [tr items]}]
  [tables/table {:headers (make-headers tr)
              :items   items
              :key-fn  #(gensym)}])
