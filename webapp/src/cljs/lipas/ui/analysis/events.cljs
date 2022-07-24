(ns lipas.ui.analysis.events
  (:require   
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::select-tool
 (fn [{:keys [db]} [_ tool]]   
   {:db         (assoc-in db [:analysis :selected-tool] tool)
    :dispatch-n [(when (= "diversity" tool)
                   [:lipas.ui.analysis.diversity.events/init])
                 (when (= "reachability" tool)
                   [:lipas.ui.map.events/show-analysis*])]}))
