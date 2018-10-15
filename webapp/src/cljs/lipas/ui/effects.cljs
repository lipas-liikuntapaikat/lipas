(ns lipas.ui.effects
  (:require [re-frame.core :as re-frame]
            ["zipcelx"]))

(re-frame/reg-fx
 ::download-excel!
 (fn  [config]
   (zipcelx (clj->js config))))
