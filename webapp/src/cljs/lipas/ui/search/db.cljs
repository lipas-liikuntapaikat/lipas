(ns lipas.ui.search.db)

(def default-db
  {:string       nil
   :filters
   {:statuses      #{"active" "out-of-service-temporarily"}
    :type-codes    #{}
    :city-codes    #{}
    :bounding-box? true}
   :sort
   {:asc?    true
    :sort-fn :score}
   :results-view :list
   :selected-results-table-columns
   [:name :event-date :admin.name :owner.name :type.name :location.city.name]
   :pagination
   {:page       0
    :page-size  250
    :page-sizes [25 50 100 250 500 1000]}})
