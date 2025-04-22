(ns lipas.ui.admin.routes
  (:require [lipas.ui.admin.views :as views]
            [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["admin"
   {:name   :lipas.ui.routes/admin
    :tr-key :lipas.admin/headline
    :view   views/main
    :parameters {:query [:map
                         [:tab {:optional true} :keyword]
                         [:edit-id {:optional true} :string]]}
    ;; TODO: Move to effect hook(s)?
    :controllers [{:start
                   (fn [& _params]
                     (==> [:lipas.ui.admin.events/get-users])
                     (==> [:lipas.ui.sports-sites.events/get-by-type-code 3110])
                     (==> [:lipas.ui.sports-sites.events/get-by-type-code 3130])
                     (==> [:lipas.ui.sports-sites.events/get-by-type-code 2510])
                     (==> [:lipas.ui.sports-sites.events/get-by-type-code 2520]))}]}])
