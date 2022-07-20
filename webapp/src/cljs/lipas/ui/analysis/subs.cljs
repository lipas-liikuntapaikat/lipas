(ns lipas.ui.analysis.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::analysis
 (fn [db _]
   (-> db :analysis)))

(re-frame/reg-sub
 ::selected-tool
 :<- [::analysis]
 (fn [analysis _]
   (:selected-tool analysis)))
