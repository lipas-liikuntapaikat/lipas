(ns lipas.ui.ptv.db)

(def default-db
  {:dialog                        {:open? false}
   :loading-from-ptv              {:services            false
                                   :service-channels    false
                                   :service-collections false}
   :default-settings              {:sync-enabled                true
                                   ;; TODO: These are not used now, could be removed once made optional in the schemas:
                                   :service-integration         "lipas-managed"
                                   :service-channel-integration "lipas-managed"
                                   :descriptions-integration    "lipas-managed-ptv-fields"
                                   :integration-interval        "manual"}
   :selected-org                  nil
   :service-details-tab           "descriptions" ; descriptions|preview
   :org                           {}
   :selected-tab                  "wizard"
   :batch-descriptions-generation {:sports-sites-filter "sync-enabled"
                                   :halt?               false}})
