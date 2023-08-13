(ns lipas.ui.sports-sites.activities.events
  (:require
   [re-frame.core :as re-frame]
   [lipas.utils :as utils]
   #_[lipas.data.activities :as data]))

(re-frame/reg-event-fx
 ::add-route
 (fn [{:keys [db]} [_ lipas-id]]
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :add-route)
            (assoc-in [:sports-sites :activities :selected-route-id] (str (random-uuid))))
    :fx [[:dispatch
          [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]}))

(re-frame/reg-event-fx
 ::finish-route
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sports-sites :activities :mode] :route-details)
    :fx [#_[:dispatch [:lipas.ui.map.events/continue-editing]]]}))

(re-frame/reg-event-fx
 ::clear
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :default)
            (assoc-in [:sports-sites :activities :selected-route-id] nil))}))

(re-frame/reg-event-fx
 ::finish-route-details
 (fn [{:keys [db]} [_ {:keys [fids route id lipas-id activity-k]}]]
   (let [edits                (get-in db [:sports-sites lipas-id :editing])
         current-routes       (get-in edits [:activities activity-k :routes] [])
         current-routes-by-id (utils/index-by :id current-routes)

         mode       (if (get current-routes-by-id id) :update :add)
         new-routes (condp = mode
                      :add    (conj current-routes
                                    (assoc route :fids fids :id id))
                      :update (-> current-routes-by-id
                                  (assoc id (assoc route :fids fids))
                                  vals
                                  vec))]
     {:db (-> db
              (assoc-in [:sports-sites :activities :mode] :default)
              (assoc-in [:sports-sites :activities :selected-route-id] nil))
      :fx [[:dispatch [:lipas.ui.map.events/continue-editing]]
           [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                       new-routes]]]})))

(re-frame/reg-event-fx
 ::cancel-route-details
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sports-sites :activities :mode] :default)}))

(re-frame/reg-event-fx
 ::select-route
 (fn [{:keys [db]} [_ lipas-id {:keys [fids id] :as route}]]
   (println "Select route FIDS:" fids)
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :route-details)
            (assoc-in [:sports-sites :activities :selected-route-id] id))
    :fx [[:dispatch [:lipas.ui.map.events/highlight-features fids]]]}))

(re-frame/reg-event-fx
 ::delete-route
 (fn [{:keys [db]} [_ lipas-id route-id]]
   (let [current-routes       (get-in db [:sports-sites lipas-id :editing :activities :routes] [])
         current-routes-by-id (utils/index-by :id current-routes)
         new-routes           (-> (dissoc current-routes-by-id route-id) vals vec)]
     {:db (-> db
              (assoc-in [:sports-sites :activities :mode] :default))
      :fx [[:dispatch [:lipas.ui.map.events/continue-editing]]
           [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities :routes]
                       new-routes]]
           [:dispatch [:lipas.ui.map.events/highlight-features #{}]]]})))
