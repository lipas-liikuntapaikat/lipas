(ns lipas.ui.sports-sites.football.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::football
  (fn [db _]
    (-> db :sports-sites :football)))

(rf/reg-sub ::type-codes
  :<- [::football]
  (fn [football _]
    (:type-codes football)))
