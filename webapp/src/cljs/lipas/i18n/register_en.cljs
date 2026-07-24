(ns lipas.i18n.register-en
  "Entry namespace of the lazy :i18n-en module. Loading it registers the
   English dictionary with the translator (see lipas.i18n.core)."
  (:require [lipas.i18n.core :as i18n]
            [lipas.i18n.en :as en]))

(def translations en/translations)

(i18n/register-dict! :en translations)
