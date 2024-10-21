(ns lipas.ui.analysis.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::analysis
  (fn [db _]
    (-> db :analysis)))

(rf/reg-sub ::selected-tool
  :<- [::analysis]
  (fn [analysis _]
    (:selected-tool analysis)))
