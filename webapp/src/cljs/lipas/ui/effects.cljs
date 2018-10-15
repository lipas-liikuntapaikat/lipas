(ns lipas.ui.effects
  (:require [re-frame.core :as re-frame]
            ["zipcelx"]))

(re-frame/reg-fx
 ::reset-scroll!
 (fn  [_]
   (js/window.scrollTo 0 0)))

(re-frame/reg-fx
 ::download-excel!
 (fn  [config]
   (zipcelx (clj->js config))))
