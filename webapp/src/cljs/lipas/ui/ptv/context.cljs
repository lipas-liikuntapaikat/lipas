(ns lipas.ui.ptv.context
  "Pure app-db readers describing PTV dialog state.

   Lives in the base module (no PTV requires) so the always-mounted AI
   assistant can snapshot PTV context without loading the lazy :ptv
   module — when the dialog has never been opened there is nothing to
   describe and `dialog-context` returns nil anyway.")

(def ^:private assistant-tab-labels
  "Dialog tab values → the labels the user sees (fi UI labels)."
  {"wizard" "Käyttöönotto (ohjattu vienti PTV:hen)"
   "services" "Palvelut"
   "sports-sites" "Liikuntapaikat"
   "audit" "Auditointi"})

(def ^:private assistant-wizard-step-labels
  ["1. Valitse liikuntapaikat"
   "2. Luo PTV-palvelut"
   "3. Luo liikuntapaikoista palvelupaikat"])

(defn dialog-context
  "Open PTV-export-dialog state for the assistant's context snapshot,
   or nil when the dialog is closed."
  [db]
  (when (get-in db [:ptv :dialog :open?])
    (let [ptv (:ptv db)
          tab (:selected-tab ptv)]
      (cond-> {:open? true}
        (-> ptv :selected-org :name)
        (assoc :org (-> ptv :selected-org :name))

        tab
        (assoc :tab (get assistant-tab-labels tab tab))

        (= "wizard" tab)
        (assoc :wizard-step (get assistant-wizard-step-labels
                                 (:selected-step ptv 0)))))))
