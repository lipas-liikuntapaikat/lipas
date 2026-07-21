(ns lipas.ui.bulk-operations.db)

(def default-db
  {:editable-sites []
   :selected-sites #{}
   :selected-fields #{}
   :update-form {}
   ;; Default the status filter to the statuses worth maintaining — in use, or
   ;; temporarily out of service — mirroring the map-view search default. Users
   ;; can widen it (or clear it) via the status filter. Planning/planned drafts,
   ;; permanently closed and incorrect-data sites are hidden until selected.
   :filters {:statuses #{"active" "out-of-service-temporarily"}}
   :loading? false
   :error nil
   :current-step 0
   :update-results nil})
