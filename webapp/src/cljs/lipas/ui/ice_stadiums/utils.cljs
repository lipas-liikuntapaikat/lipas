(ns lipas.ui.ice-stadiums.utils
  (:require [lipas.ui.utils :refer [==> ->indexed-map resolve-prev-rev]]
            [lipas.ui.ice-stadiums.events :as events]))

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  [& args]
  (==> [::events/set-field (butlast args) (last args)]))

(defn make-revision [site timestamp]
  (let [prev-rev (resolve-prev-rev (:history site) timestamp)]
    (-> prev-rev
        (assoc :timestamp timestamp)
        (dissoc :energy-consumption))))
