(ns lipas.ui.swimming-pools.utils
  (:require [re-frame.core :as re-frame]
            [lipas.ui.swimming-pools.events :as events]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(defn toggle-dialog
  ([dialog]
   (toggle-dialog dialog {}))
  ([dialog data]
   (==> [::events/toggle-dialog dialog data])))

(defn set-field
  ":k1 :k2 ... :kn event

  Event is always the last argument."
  [& args]
  (let [path (into [] (butlast args))
        event (last args)
        value (-> event
                  .-target
                  .-value)]
    (prn "Setting field: " path " value " value)
    (re-frame/dispatch [::events/set-field path value])))
