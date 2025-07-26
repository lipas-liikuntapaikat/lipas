(ns lipas.ui.sports-sites.activities.route-ordering-poc-example
  "Example integration for the route ordering POC"
  (:require
   [lipas.ui.sports-sites.activities.route-ordering-poc :as poc]
   [uix.core :as uix :refer [$ defui use-state use-effect]]))

(defui integration-example
  "Example showing how to integrate with backend ordering function"
  []
  (let [[ordered-segments set-ordered-segments!] (use-state nil)]

    ;; Simulate calling backend function
    (use-effect
     (fn []
       ;; This is where you would call your backend function
       ;; For now, simulating with a timeout
       (js/setTimeout
        (fn []
          (let [backend-result [{:fid "route-segment-123" :direction "forward" :order 0}
                                {:fid "route-segment-456" :direction "backward" :order 1}
                                {:fid "route-segment-789" :direction "forward" :order 2}]]
            (js/console.log "Backend result received:" (clj->js backend-result))
            (set-ordered-segments! backend-result)))
        1000)

       ;; Cleanup function
       (fn []))
     []) ; Empty deps array = run once on mount

    (if ordered-segments
      ;; Show the visualizer with real data
      ($ poc/route-ordering-poc {:segments ordered-segments})

      ;; Show loading state
      ($ :div "Loading ordered segments..."))))

;; Example of how to use with actual route data structure
(comment
  ;; Assuming you have a route with segments like:
  (def example-route
    {:geometries {:features [{:id "seg-1" :geometry {:type "LineString" :coordinates []}}
                             {:id "seg-2" :geometry {:type "LineString" :coordinates []}}
                             {:id "seg-3" :geometry {:type "LineString" :coordinates []}}]}})

  ;; And your backend function returns:
  (def ordering-result
    [{:fid "seg-1" :direction "forward" :order 0}
     {:fid "seg-3" :direction "backward" :order 1}
     {:fid "seg-2" :direction "forward" :order 2}])

  ;; You would use it like:
  ($ poc/route-ordering-poc {:segments ordering-result}))