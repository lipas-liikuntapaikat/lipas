(ns lipas.ui.admin.ai-workbench.subs
  (:require [re-frame.core :as rf]))

(def state-path [:admin :ai-workbench])

(rf/reg-sub ::state
  (fn [db _]
    (get-in db state-path)))

(rf/reg-sub ::flow
  :<- [::state]
  (fn [state _] (or (:flow state) :service-location)))

(rf/reg-sub ::lipas-id
  :<- [::state]
  (fn [state _] (:lipas-id state)))

(rf/reg-sub ::city-code
  :<- [::state]
  (fn [state _] (:city-code state)))

(rf/reg-sub ::sub-category-id
  :<- [::state]
  (fn [state _] (:sub-category-id state)))

(rf/reg-sub ::preview-data
  :<- [::state]
  (fn [state _] (:preview-data state)))

(rf/reg-sub ::preview-loading?
  :<- [::state]
  (fn [state _] (:preview-loading? state)))

(rf/reg-sub ::defaults
  :<- [::state]
  (fn [state _] (:defaults state)))

(rf/reg-sub ::system-prompt
  :<- [::state]
  (fn [state _] (:system-prompt state)))

(rf/reg-sub ::user-prompt
  :<- [::state]
  (fn [state _] (:user-prompt state)))

(rf/reg-sub ::params
  :<- [::state]
  (fn [state _] (:params state)))

(rf/reg-sub ::experiment-loading?
  :<- [::state]
  (fn [state _] (:experiment-loading? state)))

(rf/reg-sub ::results
  :<- [::state]
  (fn [state _] (:results state)))

(rf/reg-sub ::selected-lang
  :<- [::state]
  (fn [state _] (or (:selected-lang state) :fi)))

(rf/reg-sub ::preview-error
  :<- [::state]
  (fn [state _] (:preview-error state)))

(rf/reg-sub ::site-search-results
  :<- [::state]
  (fn [state _] (:site-search-results state)))

(rf/reg-sub ::experiment-error
  :<- [::state]
  (fn [state _] (:experiment-error state)))

(rf/reg-sub ::overview-mode
  :<- [::state]
  (fn [state _] (or (:overview-mode state) :list)))

(rf/reg-sub ::aggregate-fields
  :<- [::state]
  (fn [state _] (:aggregate-fields state)))
