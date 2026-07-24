(ns lipas.ui.excel
  "Excel export via zipcelx. Lives in the lazy :xlsx module (zipcelx
   bundles jszip, ~100KB) — reached only through the
   :lipas.ui.effects/download-excel! fx, which loads this on demand."
  (:require ["zipcelx$default" :as zipcelx]))

(defn download!
  [config]
  (zipcelx (clj->js config)))
