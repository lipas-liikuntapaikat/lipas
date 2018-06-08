(ns lipas.ui.swimming-pools.utils
  (:require [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.utils :refer [==> ->indexed-map] :as utils]))

;;; TODO refactor events away from here

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  [& args]
  (==> [::events/set-field (butlast args) (last args)]))

(def localize-field utils/localize-field)
