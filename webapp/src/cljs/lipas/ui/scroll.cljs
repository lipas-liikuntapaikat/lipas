(ns lipas.ui.scroll
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-fx
 ::reset!
 (fn  [_]
   (js/window.scrollTo 0 0)))
