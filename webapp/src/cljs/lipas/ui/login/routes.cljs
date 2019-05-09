(ns lipas.ui.login.routes
  (:require
   [lipas.ui.login.views :as views]))

(def routes
  ["kirjaudu"
   {:name   :lipas.ui.routes/login
    :tr-key :login/headline
    :view   views/main}])
