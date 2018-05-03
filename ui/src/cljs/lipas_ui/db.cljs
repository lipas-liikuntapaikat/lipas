(ns lipas-ui.db
  (:require [lipas-ui.i18n :as i18n]))

(def default-db
  {:logged-in? false
   :translator (i18n/->tr-fn :fi)
   :ice-stadiums {:active-tab 0}
   :swimming-pools {:active-tab 0}
   :user
   {:login-form
    {:username ""
     :password ""}
    :registration-form
    {:email ""
     :password ""
     :username ""
     :user-data
     {:firstname ""
      :lastname ""
      :permissions-request ""}}}})
