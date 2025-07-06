(ns lipas.ui.bulk-operations.db)

(def default-db
  {:editable-sites []
   :selected-sites #{}
   :update-form {}
   :filters {}
   :loading? false
   :error nil
   :current-step 0
   :update-results nil})
