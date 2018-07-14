(ns lipas.ui.ice-stadiums.rinks
  (:require [lipas.ui.components :as lui]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==> ->setter-fn]]))

(def set-field (->setter-fn ::events/set-field))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :rink :data)]
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

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/rink-form])
        reset #(==> [::events/reset-dialog :rink])
        close #(==> [::events/toggle-dialog :rink])]
    [lui/dialog {:title (if (:id data)
                          (tr :lipas.ice-stadium.rinks/edit-rink)
                          (tr :lipas.ice-stadium.rinks/add-rink))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close  #(==> [::events/toggle-dialog :rink])
                 :on-save (comp reset
                                close
                                #(==> [::events/save-rink data]))}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:length-m (tr :dimensions/length-m)]
   [:width-m (tr :dimensions/width-m)]])

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers        (make-headers tr)
                   :items          items
                   :on-add         #(==> [::events/toggle-dialog :rink {}])
                   :on-edit        #(==> [::events/toggle-dialog :rink %])
                   :on-delete      #(==> [::events/remove-rink %])
                   :add-tooltip    (tr :lipas.ice-stadium.rinks/add-rink)
                   :edit-tooltip   (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)}])

(defn read-only-table [{:keys [tr items]}]
  (lui/table {:headers (make-headers tr)
              :items items
              :key-fn #(gensym)}))
