(ns lipas.ui.map2.utils
  (:require-macros lipas.ui.map2.utils) 
  (:require [uix.core :as uix]))

(def MapContext (uix/create-context))
(def MapContextProvider (.-Provider MapContext))

(defn ^js use-ol []
  (let [ctx (uix/use-context MapContext)]
    (.-current (:ol-ref ctx))))
