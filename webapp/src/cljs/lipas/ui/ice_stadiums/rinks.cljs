(ns lipas.ui.ice-stadiums.rinks
  (:require
   [clojure.spec.alpha :as s]
   [lipas.ui.components :as lui]
   [lipas.ui.ice-stadiums.events :as events]
   [lipas.ui.ice-stadiums.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>]]))

(defn set-field [dialog field value]
  (#(==> [::events/set-dialog-field dialog field value])))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :rink)]
    [mui/form-group
     [lui/text-field {:label     (tr :dimensions/length-m)
                      :adornment (tr :physical-units/m)
                      :type      "number"
                      :spec      :lipas.ice-stadium.rink/length-m
                      :value     (:length-m data)
                      :on-change #(set-field :length-m %)}]
     [lui/text-field {:label     (tr :dimensions/width-m)
                      :adornment (tr :physical-units/m)
                      :type      "number"
                      :spec      :lipas.ice-stadium.rink/width-m
                      :value     (:width-m data)
                      :on-change #(set-field :width-m %)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data    (<== [::subs/rink-form])
        title   (if (:id data)
                  (tr :lipas.ice-stadium.rinks/edit-rink)
                  (tr :lipas.ice-stadium.rinks/add-rink))
        reset   #(==> [::events/reset-dialog :rink])
        close   #(==> [::events/toggle-dialog :rink])
        valid?  (s/valid? :lipas.ice-stadium/rink data)
        on-save (comp reset close #(==> [::events/save-rink lipas-id data]))]
    [lui/dialog {:title         title
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      close
                 :save-enabled? valid?
                 :on-save       on-save}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:length-m (tr :dimensions/length-m)]
   [:width-m (tr :dimensions/width-m)]])

(defn table [{:keys [tr items lipas-id add-btn-size]}]
  [lui/form-table
   {:headers         (make-headers tr)
    :items           (vals items)
    :add-btn-size    add-btn-size
    :on-add          #(==> [::events/toggle-dialog :rink {}])
    :on-edit         #(==> [::events/toggle-dialog :rink %])
    :on-delete       #(==> [::events/remove-rink lipas-id %])
    :add-tooltip     (tr :lipas.ice-stadium.rinks/add-rink)
    :edit-tooltip    (tr :actions/edit)
    :delete-tooltip  (tr :actions/delete)
    :confirm-tooltip (tr :confirm/press-again-to-delete)}])

(defn read-only-table [{:keys [tr items]}]
  (lui/table
   {:headers (make-headers tr)
    :items items
    :key-fn #(gensym)}))
