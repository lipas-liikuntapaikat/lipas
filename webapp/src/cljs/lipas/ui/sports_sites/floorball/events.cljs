(ns lipas.ui.sports-sites.floorball.events
  (:require
   [clojure.string :as str]
   [lipas.ui.sports-sites.events :as sports-sites.events]
   [lipas.ui.utils :as utils :refer [==>]]
   [re-frame.core :as re-frame]))

(def prop-k->derive-fn
  {:field-length-m         (fn [sports-site]
                             (-> sports-site :fields first second :length-m))
   :field-width-m          (fn [sports-site]
                             (-> sports-site :fields first second :width-m))
   :height-m               (fn [sports-site]
                             (-> sports-site :fields first second :minimum-height-m))
   :area-m2                (fn [sports-site]
                             (-> sports-site :fields first second :surface-area-m2))
   :surface-material       (fn [sports-site]
                             (->> sports-site :fields vals (map :surface-material)))
   :surface-material-info  (fn [sports-site]
                             (->> sports-site :fields vals (map :surface-material-product)
                                  (str/join ", ")))
   :floorball-fields-count (fn [sports-site]
                             (->> sports-site :fields count))})

(defmethod sports-sites.events/calc-derived-fields 2240
  [sports-site]
  (-> sports-site
      (update :properties (fn [props]
                            (reduce-kv
                             (fn [props prop-k derive-fn]
                               (if-let [v (derive-fn sports-site)]
                                 (assoc props prop-k v)
                                 props))
                             props
                             prop-k->derive-fn)))))

(re-frame/reg-event-fx
 ::set-field-field
 (fn [_ [_ lipas-id field value]]
   (let [path [:fields 0 field]]
     {:dispatch [::sports-sites.events/edit-field lipas-id path value]})))

(re-frame/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:sports-sites :floorball :dialogs dialog :data field]]
     (utils/set-field db path value))))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db [:sports-sites :floorball :dialogs dialog] {})))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :sports-sites :floorball :dialogs dialog :data))]
     (-> db
         (update-in [:sports-sites :floorball :dialogs dialog :open?] not)
         (assoc-in [:sports-sites :floorball :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::save-dialog
 (fn [db [_ entities-k lipas-id value]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing entities-k]
                [:new-sports-site :data entities-k])]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-field
 (fn [db [_ lipas-id {:keys [id]}]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :fields]
                [:new-sports-site :data :fields])]
     (update-in db path dissoc id))))

(re-frame/reg-event-db
 ::remove-locker-room
 (fn [db [_ lipas-id {:keys [id]}]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :fields]
                [:new-sports-site :data :locker-rooms])]
     (update-in db path dissoc id))))
