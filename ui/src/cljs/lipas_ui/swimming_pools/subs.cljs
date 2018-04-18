(ns lipas-ui.swimming-pools.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :swimming-pools :active-tab)))
