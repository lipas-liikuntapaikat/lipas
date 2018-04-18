(ns lipas-ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 ::active-panel
 (fn [db _]
   (:active-panel db)))

(re-frame/reg-sub
 ::menu-anchor
 (fn [db _]
   (:menu-anchor db)))

(re-frame/reg-sub
 ::drawer-open?
 (fn [db _]
   (:drawer-open? db)))

(re-frame/reg-sub
 ::active-ice-panel-tab
 (fn [db _]
   (-> db :ice-panel :active-tab)))
