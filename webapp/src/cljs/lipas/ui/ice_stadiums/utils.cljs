(ns lipas.ui.ice-stadiums.utils
  (:require [lipas.ui.utils :as utils]))

(defn make-editable [ice-stadium]
  (-> ice-stadium
      (utils/maybe-update-in [:rinks] utils/->indexed-map)))

(defn make-saveable [ice-stadium]
  (-> ice-stadium
      (utils/maybe-update-in [:rinks] (comp utils/remove-ids vals))
      (utils/clean)))
