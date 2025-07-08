(ns lipas.ui.bulk-operations.db)

(def default-db
  {:editable-sites []
   :selected-sites #{}
   :selected-fields #{}
   :update-form {}
   :filters {}
   :loading? false
   :error nil
   :current-step 0
   :update-results nil})
