(ns lipas.ui.sports-sites.football.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::football
 (fn [db _]
   (-> db :sports-sites :football)))

(re-frame/reg-sub
 ::type-codes
 :<- [::football]
 (fn [football _]
   (:type-codes football)))
