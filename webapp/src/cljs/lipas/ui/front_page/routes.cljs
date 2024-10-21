(ns lipas.ui.front-page.routes
  (:require [lipas.ui.front-page.events :as events]
            [lipas.ui.front-page.views :as views]
            [lipas.ui.utils :refer [==>] :as utils]))

(def routes
  ["etusivu"
   {:name   :lipas.ui.routes/front-page
    :tr-key :home-page/headline
    :view   views/main
    :controllers
    [{:start (fn [& params]
               (==> [::events/get-newsletter]))}]}])
