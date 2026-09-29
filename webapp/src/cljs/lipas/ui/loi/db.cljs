(ns lipas.ui.loi.db
  (:require [lipas.data.loi :as data]
            [lipas.utils :as utils]))

;; Props are rendered in this order. Keys missing from the list are
;; rendered last, so a new prop shows up even if it's forgotten here.
(def default-sort-order
  [:name
   :description
   :images
   :arrival
   :accessible?
   :accessibility
   :contacts
   :additional-info-link
   :use-structure-during-fire-warning
   :itrs-exposure
   :protected-area-specification])

(def default-db
  {:statuses     data/statuses
   :categories   data/categories
   :field-sorter (utils/make-field-sorter default-sort-order)})
