(ns lipas.i18n.translations
  (:require [lipas.i18n.fi :as fi]
            [lipas.i18n.se :as se]
            [lipas.i18n.en :as en]))

(def dicts
  {:fi fi/translations
   :se se/translations
   :en en/translations})
