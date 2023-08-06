(ns lipas.ui.sports-sites.activities.db
  (:require
   [lipas.data.activities :as data]))

(def default-db
  {:mode         :default
   :by-type-code data/by-type-code})
