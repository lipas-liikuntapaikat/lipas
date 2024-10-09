(ns lipas.permissions
  {:deprecated true}
  (:require
   [lipas.schema.core]))

(defn activities?
  [permissions]
  (or (:admin? permissions)
      (some? (seq (:activities permissions)))))

