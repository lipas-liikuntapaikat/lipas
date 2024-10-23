(ns lipas.data.prop-types
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace represents the changes made."
  (:require
   [lipas.data.types :as types]
   [lipas.data.prop-types-new :as new]
   [lipas.data.prop-types-old :as old]))

(def all old/all)

(def used
  (let [used (set (mapcat (comp keys :props second) types/all))]
    (select-keys all used)))
