(ns lipas.ui.swimming-pools.renovations
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.components :as lui]
            [lipas.ui.swimming-pools.utils :refer [set-field
                                                   toggle-dialog
                                                   <==
                                                   ==>]]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.events :as events]))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :dialogs :renovation :data)]
    [mui/form-group
     [lui/year-selector {:label (tr :renovations/end-year)
                         :value (:year data)
                         :on-change #(set-field :year %)}]
     [mui/text-field {:label (tr :building/main-designers)
                      :value (:main-designers data)
                      :on-change #(set-field :main-designers %)}]
     [mui/text-field {:label (tr :general/description)
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
  [lui/form-table {:headers [[:year (tr :time/year)]
                             [:comment (tr :general/description)]
                             [:main-designers (tr :building/main-designers)]]
                   :items items
                   :on-add #(toggle-dialog :renovation)
                   :on-edit #(toggle-dialog :renovation %)
                   :on-delete #(==> [::events/remove-renovation %])
                   :add-tooltip (tr :renovations/add-renovation)
                   :edit-tooltip (tr :actions/edit)
                   :delete-tooltip (tr :actions/delete)}])
