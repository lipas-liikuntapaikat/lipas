(ns lipas.ui.swimming-pools.utils
  (:require [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.utils :refer [==>]]))

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  [& args]
  (==> [::events/set-field (butlast args) (last args)]))
