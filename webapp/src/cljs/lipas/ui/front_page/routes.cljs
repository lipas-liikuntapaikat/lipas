(ns lipas.ui.front-page.routes
  (:require
   [lipas.ui.front-page.views :as views]))

(def routes
  ["etusivu"
   {:name   :lipas.ui.routes/front-page
    :tr-key :home-page/headline
    :view   views/main}])
