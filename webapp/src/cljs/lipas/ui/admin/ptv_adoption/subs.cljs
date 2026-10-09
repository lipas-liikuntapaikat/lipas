(ns lipas.ui.admin.ptv-adoption.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::state
  (fn [db _]
    (get-in db [:admin :ptv-adoption])))

(rf/reg-sub ::stats
  :<- [::state]
  (fn [state _]
    (:stats state)))

(rf/reg-sub ::loading?
  :<- [::state]
  (fn [state _]
    (:loading? state)))

(rf/reg-sub ::error
  :<- [::state]
  (fn [state _]
    (:error state)))

(rf/reg-sub ::chart-data
  :<- [::stats]
  (fn [stats _]
    (mapv (fn [{:keys [by-municipality] :as m}]
            (-> (select-keys m [:month :new :total])
                ;; Pre-rendered for the custom tooltip
                (assoc :details (mapv (fn [{:keys [municipality new]}]
                                        (str municipality " " new))
                                      by-municipality))))
          (:monthly stats))))

(rf/reg-sub ::outside-managers
  :<- [::state]
  (fn [state _]
    (:outside-managers state)))

(rf/reg-sub ::outside-managers-loading?
  :<- [::state]
  (fn [state _]
    (:outside-managers-loading? state)))

(rf/reg-sub ::adding?
  :<- [::state]
  (fn [state [_ email]]
    (boolean (get-in state [:adding email]))))
