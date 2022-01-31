(ns lipas.ui.swimming-pools.slides
  (:require
   [clojure.spec.alpha :as s]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.swimming-pools.events :as events]
   [lipas.ui.swimming-pools.subs :as subs]
   [lipas.ui.utils :refer [<== ==> localize-field]]))

(defn set-field [dialog field value]
  (#(==> [::events/set-dialog-field dialog field value])))

(defn form [{:keys [tr data]}]
  (let [set-field  (partial set-field :slide)]
    [mui/form-group
     [lui/text-field
      {:label     (tr :dimensions/length-m)
       :adornment (tr :physical-units/m)
       :type      "number"
       :value     (:length-m data)
       :spec      :lipas.swimming-pool.slide/length-m
       :on-change #(set-field :length-m %)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data    (<== [::subs/slide-form])
        close   #(==> [::events/toggle-dialog :slide])
        reset   #(==> [::events/reset-dialog :slide])
        valid?  (s/valid? :lipas.swimming-pool/slide data)
        title   (if (:id data)
                  (tr :lipas.swimming-pool.slides/edit-slide)
                  (tr :lipas.swimming-pool.slides/add-slide))
        on-save (comp reset close #(==> [::events/save-slide lipas-id data]))]
    [lui/dialog {:title         title
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      #(==> [::events/toggle-dialog :slide])
                 :save-enabled? valid?
                 :on-save       on-save}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:length-m (tr :dimensions/length-m)]])

(defn table [{:keys [tr items lipas-id add-btn-size]}]
  [lui/form-table
   {:headers         (make-headers tr)
    :add-btn-size    add-btn-size
    :items           (vals items)
    :add-tooltip     (tr :lipas.swimming-pool.slides/add-slide)
    :edit-tooltip    (tr :actions/edit)
    :delete-tooltip  (tr :actions/delete)
    :confirm-tooltip (tr :confirm/press-again-to-delete)
    :on-add          #(==> [::events/toggle-dialog :slide {}])
    :on-edit         #(==> [::events/toggle-dialog :slide (get items (:id %))])
    :on-delete       #(==> [::events/remove-slide lipas-id %])}])

(defn read-only-table [{:keys [tr items]}]
  [lui/table {:headers (make-headers tr)
              :items   items
              :key-fn  #(gensym)}])
