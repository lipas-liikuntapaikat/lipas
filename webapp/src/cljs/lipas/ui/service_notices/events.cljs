(ns lipas.ui.service-notices.events
  (:require [re-frame.core :as rf]))

;; Dismissed notices stay hidden until the next page load.
(rf/reg-event-db ::dismiss
  (fn [db [_ id]]
    (update-in db [:service-notices :dismissed] (fnil conj #{}) id)))
