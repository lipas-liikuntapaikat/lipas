(ns lipas.ui.analysis.events
  (:require   
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::select-tool
 (fn [db [_ tool]]   
   (assoc-in db [:analysis :selected-tool] tool)))
