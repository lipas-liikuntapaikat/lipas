(ns lipas.ui.admin.routes
  "Route definitions only — admin views/events live in the lazy :admin
   module, loaded by lipas.ui.routes/on-navigate before controllers run."
  (:require [lipas.ui.lazy :as lazy]
            [lipas.ui.utils :refer [==>]]))

(def routes
  ["admin"
   {:name   :lipas.ui.routes/admin
    :tr-key :lipas.admin/headline
    :view   lazy/admin-view
    :parameters {:query [:map
                         [:tab {:optional true} :keyword]
                         [:edit-id {:optional true} :string]]}
    ;; TODO: Move to effect hook(s)?
    :controllers [{:start
                   (fn [& _params]
                     (==> [:lipas.ui.admin.events/get-users])
                     (==> [:lipas.ui.admin.events/get-orgs]))}]}])
