(ns lipas.ui.map2.subs 
  (:require [cljs-bean.core :refer [->clj ->js]]
            [re-frame.core :as rf]))

(rf/reg-sub ::results
  ;; NOTE: This is JSON.parse JS result from the ajax call
  :<- [:lipas.ui.search.subs/search-results-fast]
  (fn [results _]
    (let [results (->clj results)]
      (->> results
           :hits
           :hits
           (keep
             (fn [obj]
               (:_source obj)))))))


