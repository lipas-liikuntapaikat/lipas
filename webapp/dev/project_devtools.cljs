(ns project-devtools
  (:require [re-frame.db]
            [reagent-dev-tools.core :as dev-tools]))

(dev-tools/start!
  {:state-atom re-frame.db/app-db})
