(ns lipas.ui.feedback.subs
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]))

(rf/reg-sub ::feedback
  (fn [db _]
    (get-in db [:feedback])))

(rf/reg-sub ::modal-open?
  :<- [::feedback]
  (fn [feedback _]
    (:modal-open? feedback)))

(rf/reg-sub ::types
  :<- [::feedback]
  (fn [feedback _]
    (:types feedback)))

(rf/reg-sub ::types-select-items
  :<- [:lipas.ui.subs/locale]
  :<- [::types]
  (fn [[locale types] _]
    (map (fn [[k m]] {:value k :label (locale m)}) types)))

(rf/reg-sub ::form
  :<- [::feedback]
  (fn [feedback _]
    (:form feedback)))

(rf/reg-sub ::form-valid?
  :<- [::form]
  (fn [form _]
    (s/valid? :lipas.feedback/payload form)))
