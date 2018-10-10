(ns lipas.ui.ice-stadiums.events
  (:require cljsjs.filesaverjs
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :ice-stadiums :dialogs dialog :data))]
     (-> db
         (update-in [:ice-stadiums :dialogs dialog :open?] not)
         (assoc-in [:ice-stadiums :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db [:ice-stadiums :dialogs dialog] {})))

(re-frame/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:ice-stadiums :dialogs dialog :data field]]
     (utils/set-field db path value))))

(re-frame/reg-event-db
 ::save-rink
 (fn [db [_ lipas-id value]]
   (let [path [:sports-sites lipas-id :editing :rinks]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-rink
 (fn [db [_ lipas-id {:keys [id]}]]
   (update-in db [:sports-sites lipas-id :editing :rinks] dissoc id)))

(re-frame/reg-event-fx
 ::display-site
 (fn [{:keys [db]} [_ {:keys [lipas-id]}]]
   {:db       (assoc-in db [:ice-stadiums :display-site] lipas-id)
    :dispatch-n
    [(when lipas-id
       [:lipas.ui.sports-sites.events/get-history lipas-id])
     (when lipas-id
       [:lipas.ui.events/navigate (str "/#/jaahalliportaali/hallit/" lipas-id)])
     (when-not lipas-id
       [:lipas.ui.events/navigate "/#/jaahalliportaali/hallit"])]
    :lipas.ui.scroll/reset! nil}))

(re-frame/reg-event-fx
 ::download-contacts-report
 (fn [{:keys [db]} [_ data headers]]
   (let [tr       (:translator db)
         filename (str (tr :reports/contacts) ".csv")
         mime     (str "text/plain;charset=" (.-characterSet js/document))
         blob     (new js/Blob
                       [(utils/->csv data headers)]
                       (clj->js {:type mime}))
         _        (js/saveAs blob filename)]
     {})))
