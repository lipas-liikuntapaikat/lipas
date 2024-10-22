(ns lipas.ui.map2.subs 
  (:require [cljs-bean.core :refer [->clj ->js]]
            [re-frame.core :as rf]))

;; TODO: Fully coercing the search results into GeoJSON features might not be needed.
;; We might just render each result as React component, and let them add (and remove) themselves to the Vector Source.
(rf/reg-sub ::geometries
  ;; NOTE: This is JSON.parse JS result from the ajax call
  :<- [:lipas.ui.search.subs/search-results-fast]
  (fn [results _]
    (let [results (->clj results)
          ;; TODO:
          lipas-id' nil]
      (->> results
           :hits
           :hits
           (keep
             (fn [obj]
               (let [obj              (:_source obj)
                     ;; Hmm, consider cljs-bean here? Should be nearly as fast
                     geoms            (or
                                        ;; Full geoms
                                        (-> obj :location :geometries :features)
                                        ;; Simplified geoms
                                        (-> obj :search-meta :location :simple-geoms :features))
                     type-code        (-> obj :type :type-code)
                     lipas-id         (:lipas-id obj)
                     name             (:name obj)
                     status           (:status obj)
                     travel-direction (:travel-direction obj)]

                 ;; To avoid displaying duplicates when editing
                 (when-not (= lipas-id' lipas-id)
                   #js {:type     "FeatureCollection"
                        :features (->> geoms
                                       (map-indexed (fn [idx geom]
                                                      (->js (assoc geom
                                                                   :id (str lipas-id "-" idx)
                                                                   :properties #js {:lipas-id         lipas-id
                                                                                    :name             name
                                                                                    :type-code        type-code
                                                                                    :status           status
                                                                                    :travel-direction travel-direction}))))
                                       into-array)}))))
           not-empty))))


