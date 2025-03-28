(ns lipas.ui.swimming-pools.saunas
  (:require [clojure.spec.alpha :as s]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.utils :refer [<== ==> localize-field]]))

(defn set-field [dialog field value]
  (#(==> [::events/set-dialog-field dialog field value])))

(defn form [{:keys [tr data]}]
  (let [sauna-types (<== [::subs/sauna-types])
        set-field   (partial set-field :sauna)
        locale      (tr)]
    [mui/form-group

     ;; Sauna type
     [lui/select {:required  true
                  :label     (tr :general/type)
                  :value     (:type data)
                  :items     sauna-types
                  :label-fn  (comp locale second)
                  :value-fn  first
                  :on-change #(set-field :type %)}]

     ;; Women?
     [lui/checkbox {:label     (tr :lipas.swimming-pool.saunas/women?)
                    :on-change #(set-field :women? %)
                    :value     (:women? data)}]

     ;; Men?
     [lui/checkbox {:label     (tr :lipas.swimming-pool.saunas/men?)
                    :on-change #(set-field :men? %)
                    :value     (:men? data)}]

     ;; Accessible?
     [lui/checkbox {:label     (tr :lipas.swimming-pool.saunas/accessible?)
                    :on-change #(set-field :accessible? %)
                    :value     (:accessible? data)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data    (<== [::subs/sauna-form])
        title   (if (:id data)
                  (tr :lipas.swimming-pool.saunas/edit-sauna)
                  (tr :lipas.swimming-pool.saunas/add-sauna))
        reset   #(==> [::events/reset-dialog :sauna])
        close   #(==> [::events/toggle-dialog :sauna])
        on-save (comp reset close #(==> [::events/save-sauna lipas-id data]))
        valid?  (s/valid? :lipas.swimming-pool/sauna data)]
    [lui/dialog {:title         title
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      close
                 :save-enabled? valid?
                 :on-save       on-save}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:type (tr :general/type)]
   [:women? (tr :lipas.swimming-pool.saunas/women?)]
   [:men? (tr :lipas.swimming-pool.saunas/men?)]
   [:accessible? (tr :lipas.swimming-pool.saunas/accessible?)]])

(defn table [{:keys [tr items lipas-id]}]
  (let [localize (partial localize-field tr :type :sauna-types)]
    [lui/form-table
     {:headers         (make-headers tr)
      :items           (map localize (vals items))
      :add-tooltip     (tr :lipas.swimming-pool.saunas/add-sauna)
      :edit-tooltip    (tr :actions/edit)
      :delete-tooltip  (tr :actions/delete)
      :confirm-tooltip (tr :confirm/delete-confirm)
      :on-add          #(==> [::events/toggle-dialog :sauna {}])
      :on-edit         #(==> [::events/toggle-dialog :sauna (get items (:id %))])
      :on-delete       #(==> [::events/remove-sauna lipas-id %])}]))

(defn read-only-table [{:keys [tr items]}]
  [lui/table {:headers (make-headers tr)
              :items   items
              :key-fn  #(gensym)}])
