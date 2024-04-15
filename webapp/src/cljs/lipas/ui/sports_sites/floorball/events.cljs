(ns lipas.ui.sports-sites.floorball.events
  (:require
   [clojure.string :as str]
   [lipas.data.materials :as materials]
   [lipas.ui.sports-sites.events :as sports-sites.events]
   [lipas.ui.sports-sites.floorball.db :as floorball]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(def surface-materials-set (into #{} (keys materials/surface-materials)))

(def prop-k->derive-fn
  {:field-length-m         (fn [sports-site]
                             (->> sports-site :fields first second :length-m))
   :field-width-m          (fn [sports-site]
                             (->> sports-site :fields first second :width-m))
   :height-m               (fn [sports-site]
                             (->> sports-site :fields first second :minimum-height-m))
   :area-m2                (fn [sports-site]
                             (->> sports-site :fields vals (keep :surface-area-m2) (apply +)))
   :surface-material       (fn [sports-site]
                             (->> sports-site
                                  :fields
                                  vals
                                  (keep :surface-material)
                                  distinct
                                  (filter surface-materials-set)))
   :surface-material-info  (fn [sports-site]
                             (->> sports-site :fields vals (keep :surface-material-product)
                                  (str/join ", ")))
   :floorball-fields-count (fn [sports-site]
                             (->> sports-site :fields count))
   :stand-capacity-person (fn [sports-site]
                            (->> sports-site :fields vals
                                 (keep :stands-total-capacity-person)
                                 (apply +)))})

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

(re-frame/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:sports-sites :floorball :dialogs dialog :data field]]
     (utils/set-field db path value))))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db
             [:sports-sites :floorball :dialogs dialog]
             (-> floorball/default-db
                 :dialogs dialog))))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :sports-sites :floorball :dialogs dialog :data)
                  (-> floorball/default-db :dialogs dialog :data))]
     (-> db
         (update-in [:sports-sites :floorball :dialogs dialog :open?] not)
         (assoc-in [:sports-sites :floorball :dialogs dialog :data] data)))))

(re-frame/reg-event-fx
 ::save-dialog
 (fn [{:keys [db]} [_ entities-k lipas-id value]]
   (let [path     (if lipas-id
                    [:sports-sites lipas-id :editing entities-k]
                    [:new-sports-site :data entities-k])
         new-db   (utils/save-entity db path value)
         entities (get-in new-db path)]
     {:db         new-db
      :dispatch-n [(when lipas-id
                     [:lipas.ui.sports-sites.events/edit-field lipas-id [entities-k] entities])
                   (when-not lipas-id
                     [:lipas.ui.sports-sites.events/edit-new-site-field [entities-k] entities])]})))

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
                [:sports-sites lipas-id :editing :locker-rooms]
                [:new-sports-site :data :locker-rooms])]
     (update-in db path dissoc id))))

(re-frame/reg-event-db
 ::remove-audit
 (fn [db [_ lipas-id {:keys [id]}]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :audits]
                [:new-sports-site :data :audits])]
     (update-in db path dissoc id))))