(ns lipas.ui.ptv.db)

(def default-db
  {:dialog           {:open? false}
   :loading-from-ptv {:services            false
                      :service-channels    false
                      :service-collections false}
   :default-settings {:service-integration         "lipas-managed"
                      :service-channel-integration "lipas-managed"
                      :descriptions-integration    "lipas-managed"
                      :integration-interval        "manual"}
   :selected-org     nil
   :org              {}
   :selected-tab     "sports-sites"})
