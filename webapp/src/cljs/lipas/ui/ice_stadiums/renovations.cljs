(ns lipas.ui.ice-stadiums.renovations
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.ice-stadiums.events :as events]
            [lipas.ui.ice-stadiums.subs :as subs]
            [lipas.ui.ice-stadiums.utils :refer [set-field toggle-dialog]]
            [lipas.ui.utils :refer [<== ==>]]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :renovation :data)]
    [mui/form-group
     [lui/text-field {:label (tr :building/main-designers)
                      :value (:main-designers data)
                      :on-change #(set-field :main-designers %)}]
     [lui/text-field {:label (tr :general/description)
                      :value (:comment data)
                      :on-change #(set-field :comment %)
                      :multiline true
                      :rows 5}]]))

(defn dialog [{:keys [tr]}]
  (let [data (<== [::subs/renovation-form])
        reset #(==> [::events/reset-dialog :renovation])
        close #(toggle-dialog :renovation)]
    [lui/dialog {:title (if (:id data)
                          (tr :renovations/edit-renovation)
                          (tr :renovations/add-renovation))
                 :save-label (tr :actions/save)
                 :cancel-label (tr :actions/cancel)
                 :on-close #(toggle-dialog :renovation)
                 :on-save (comp reset
                                close
                                #(==> [::events/save-renovation data]))}
     [form {:tr tr :data data}]]))

(defn table [{:keys [tr items]}]
  [lui/form-table {:headers [ [:comment (tr :general/description)]
                             [:main-designers (tr :building/main-designers)]]
                   :items items
                   :on-add #(toggle-dialog :renovation)
                   :on-edit #(toggle-dialog :renovation %)
                   :on-delete #(==> [::events/remove-renovation %])
                   :add-tooltip (tr :renovations/add-renovation)
                   :edit-tooltip (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)}])
