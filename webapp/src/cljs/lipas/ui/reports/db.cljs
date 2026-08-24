(ns lipas.ui.reports.db
  (:require [lipas.reports :as reports]))

(def default-db
  {:dialog-open?      false
   :save-dialog-open? false
   :fields            reports/visible-fields
   :selected-fields   (keys reports/default-fields)
   :selected-format   "xlsx"})
