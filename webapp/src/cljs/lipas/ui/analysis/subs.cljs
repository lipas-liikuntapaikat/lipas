(ns lipas.ui.analysis.subs
  (:require [re-frame.core :as rf]
            [lipas.roles :as roles]))

(rf/reg-sub ::analysis
  (fn [db _]
    (-> db :analysis)))

(rf/reg-sub ::selected-tool
  :<- [::analysis]
  (fn [analysis _]
    (:selected-tool analysis)))

(rf/reg-sub ::privilege-to-experimental-tools?
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user _]
    (roles/check-privilege user {} :analysis-tool/experimental)))
