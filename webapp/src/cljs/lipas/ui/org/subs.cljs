(ns lipas.ui.org.subs 
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::user-orgs
  (fn [db _]
    (:orgs (:user db))))

(rf/reg-sub ::user-orgs
  (fn [db _]
    (:orgs (:user db))))

(rf/reg-sub ::user-orgs-by-id
  :<- [::user-orgs]
  (fn [orgs _]
    (into {} (map (juxt :id identity) orgs))))

(rf/reg-sub ::user-org-by-id
  :<- [::user-orgs-by-id]
  (fn [orgs [_ id]]
    (get orgs id)))

(rf/reg-sub ::org-users
  (fn [db _]
    (:users (:org db))))

(rf/reg-sub ::all-users
  (fn [db _]
    (:all-users (:org db))))

(rf/reg-sub ::all-users-options
  :<- [::all-users]
  (fn [users _]
    (map (fn [{:keys [id username]}]
           {:value id
            :label username})
         users)))
