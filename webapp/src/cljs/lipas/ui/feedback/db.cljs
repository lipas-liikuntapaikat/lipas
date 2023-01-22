(ns lipas.ui.feedback.db
  (:require
   [lipas.data.feedback :as feedback]))

(def default-db
  {:modal-open? false
   :types       feedback/types
   :form        {:lipas.feedback/type :feedback.type/generic}})
