(ns lipas.ui.swimming-pools.utils
  (:require [lipas.ui.utils :as utils]))

(defn make-editable [swimming-pool]
  (-> swimming-pool
      (utils/maybe-update-in [:pools] utils/->indexed-map)
      (utils/maybe-update-in [:saunas] utils/->indexed-map)
      (utils/maybe-update-in [:slides] utils/->indexed-map)))

(defn make-saveable [swimming-pool]
  (-> swimming-pool
      (utils/maybe-update-in [:pools] (comp utils/remove-ids vals))
      (utils/maybe-update-in [:saunas] (comp utils/remove-ids vals))
      (utils/maybe-update-in [:slides] (comp utils/remove-ids vals))
      utils/clean))
