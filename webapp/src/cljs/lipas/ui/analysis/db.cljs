(ns lipas.ui.analysis.db
  (:require
   [lipas.ui.analysis.reachability.db :as reachability-db]))

(def default-db
  {:selected-tool "reachability"
   :reachability  reachability-db/default-db})
