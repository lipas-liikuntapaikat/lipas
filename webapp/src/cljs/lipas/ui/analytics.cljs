(ns lipas.ui.analytics
  (:require cljsjs.google-analytics
            autotrack
            [lipas.ui.utils :as utils]))

(def tracking-code (if (utils/prod?)
                     "TODO"
                     "UA-123820613-1"))

(defn track! []
  (js/ga "create" tracking-code "auto")
  (js/ga "require" "autotrack")
  (js/ga "send" "pageview"))
