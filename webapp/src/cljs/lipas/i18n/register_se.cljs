(ns lipas.i18n.register-se
  "Entry namespace of the lazy :i18n-se module. Loading it registers the
   Swedish dictionary with the translator (see lipas.i18n.core)."
  (:require [lipas.i18n.core :as i18n]
            [lipas.i18n.se :as se]))

(def translations se/translations)

(i18n/register-dict! :se translations)
