(ns lipas.ui.org.subs 
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::user-orgs
  (fn [db _]
    (:orgs (:user db))))

(rf/reg-sub ::id->user-org
  :<- [::user-orgs]
  (fn [user-orgs _]
    (into {} (map (juxt :org/id identity) user-orgs))))
