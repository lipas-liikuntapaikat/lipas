(ns lipas.schema.search
  "Schemas for search results table field validation."
  (:require [lipas.schema.sports-sites :as sports-sites]
            [lipas.schema.users :as users]
            [lipas.utils :as utils]))

;; Individual field schemas matching the spec definitions from schema/core.cljc

(def email-schema sports-sites/email)
(def phone-number-schema sports-sites/phone-number)
(def www-schema sports-sites/www)
(def construction-year-schema sports-sites/construction-year)

;; Results table specs map - maps field keys to their malli schemas
(def results-table-schemas
  {:email {:schema email-schema}
   :phone-number {:schema phone-number-schema}
   :www {:schema www-schema}
   :construction-year {:schema construction-year-schema}})
