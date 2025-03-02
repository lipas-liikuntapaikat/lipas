(ns lipas.ui.help.db
  (:require [lipas.data.help :as help-data]))

(def default-db
  {:dialog
   {:open? false
    :selected-section :general
    :selected-page nil
    :mode :read}
   :data help-data/sections})
