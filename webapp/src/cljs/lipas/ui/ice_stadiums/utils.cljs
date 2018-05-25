(ns lipas.ui.ice-stadiums.utils
  (:require [lipas.ui.utils :refer [==>]]
            [lipas.ui.ice-stadiums.events :as events]))

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  [& args]
  (==> [::events/set-field (butlast args) (last args)]))
