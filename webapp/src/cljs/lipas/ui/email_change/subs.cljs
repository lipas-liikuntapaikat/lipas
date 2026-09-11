(ns lipas.ui.email-change.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::confirmation
  (fn [db _]
    (:email-change-confirmation db)))
