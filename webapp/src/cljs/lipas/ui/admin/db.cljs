(ns lipas.ui.admin.db
  (:require [lipas.data.styles :as styles]))

(def default-db
  {:users-status                "active"
   :magic-link-dialog-open?     false
   :magic-link-variants         [{:value "lipas" :label "Lipas"}
                                 {:value "portal" :label "Portaali"}]
   :selected-magic-link-variant "lipas"
   :color-picker                styles/symbols})
