(ns lipas.ui.assistant.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::assistant
  (fn [db _]
    (:assistant db)))

(rf/reg-sub ::open?
  :<- [::assistant]
  (fn [m _]
    (boolean (:open? m))))

(rf/reg-sub ::messages
  :<- [::assistant]
  (fn [m _]
    (:messages m [])))

(rf/reg-sub ::input
  :<- [::assistant]
  (fn [m _]
    (:input m "")))

(rf/reg-sub ::thinking?
  :<- [::assistant]
  (fn [m _]
    (boolean (:thinking? m))))

(rf/reg-sub ::pending-escalation
  :<- [::assistant]
  (fn [m _]
    (:pending-escalation m)))

(rf/reg-sub ::escalation-in-progress?
  :<- [::assistant]
  (fn [m _]
    (boolean (:escalation-in-progress? m))))
