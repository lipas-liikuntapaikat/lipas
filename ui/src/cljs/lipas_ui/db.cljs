(ns lipas-ui.db)

(def default-db
  {:locale :fi
   :ice-stadiums {:active-tab 0}
   :swimming-pools {:active-tab 0}
   :user
   {:logged-in? false
    :registration-form
    {:email ""
     :password ""
     :username ""
     :user-data
     {:firstname ""
      :lastname ""
      :permissions ""}}}})
