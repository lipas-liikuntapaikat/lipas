(ns lipas.ui.loi.db
  (:require [lipas.data.loi :as data]))

(def default-db
  {:statuses   data/statuses
   :categories data/categories})
