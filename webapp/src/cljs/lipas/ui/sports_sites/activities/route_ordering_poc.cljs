(ns lipas.ui.sports-sites.activities.route-ordering-poc
  "POC component for visualizing ordered route segments"
  (:require
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/Paper$default" :as Paper]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/List$default" :as List]
   ["@mui/material/ListItem$default" :as ListItem]
   ["@mui/material/ListItemText$default" :as ListItemText]
   ["@mui/material/ListItemIcon$default" :as ListItemIcon]
   ["@mui/material/Chip$default" :as Chip]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Button$default" :as Button]
   ["@mui/material/CircularProgress$default" :as CircularProgress]
   ["@mui/material/Alert$default" :as Alert]
   ["@mui/icons-material/ArrowForward$default" :as ArrowForwardIcon]
   ["@mui/icons-material/ArrowBack$default" :as ArrowBackIcon]
   ["@mui/icons-material/SwapVert$default" :as SwapVertIcon]
   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [lipas.ui.uix.hooks :refer [use-subscribe]]
   [lipas.ui.utils :refer [==>]]
   [uix.core :as uix :refer [$ defui]]))

;; Re-frame events
(rf/reg-event-db
 ::set-loading
 (fn [db [_ loading?]]
   (assoc-in db [:route-ordering-poc :loading?] loading?)))

(rf/reg-event-db
 ::set-error
 (fn [db [_ error]]
   (assoc-in db [:route-ordering-poc :error] error)))

(rf/reg-event-db
 ::set-segments
 (fn [db [_ segments]]
   (assoc-in db [:route-ordering-poc :segments] segments)))

(rf/reg-event-fx
 ::load-route-data
 (fn [{:keys [db]} [_ lipas-id activity-type]]
   {:db (-> db
            (assoc-in [:route-ordering-poc :loading?] true)
            (assoc-in [:route-ordering-poc :error] nil))
    :http-xhrio {:method :post
                 :uri "/api/actions/poc-suggest-route-order"
                 :params {:lipas-id (js/parseInt lipas-id)
                          :activity-type activity-type}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::load-route-data-success]
                 :on-failure [::load-route-data-failure]}}))

(rf/reg-event-fx
 ::load-route-data-success
 (fn [{:keys [db]} [_ response]]
   (if (:success response)
     {:db (-> db
              (assoc-in [:route-ordering-poc :loading?] false)
              (assoc-in [:route-ordering-poc :segments] (:ordered-segments response)))}
     {:db (-> db
              (assoc-in [:route-ordering-poc :loading?] false)
              (assoc-in [:route-ordering-poc :error] (or (:error response) "Failed to order segments")))})))

(rf/reg-event-fx
 ::load-route-data-failure
 (fn [{:keys [db]} [_ error]]
   {:db (-> db
            (assoc-in [:route-ordering-poc :loading?] false)
            (assoc-in [:route-ordering-poc :error] (or (get-in error [:response :error])
                                                       "Failed to load route data")))}))

;; Re-frame subscriptions
(rf/reg-sub
 ::loading?
 (fn [db _]
   (get-in db [:route-ordering-poc :loading?])))

(rf/reg-sub
 ::error
 (fn [db _]
   (get-in db [:route-ordering-poc :error])))

(rf/reg-sub
 ::segments
 (fn [db _]
   (get-in db [:route-ordering-poc :segments])))

;; Mock data for testing - replace with actual backend data
(def mock-ordered-segments
  [{:fid "seg-1" :direction "forward" :order 0}
   {:fid "seg-2" :direction "backward" :order 1}
   {:fid "seg-3" :direction "forward" :order 2}
   {:fid "seg-4" :direction "forward" :order 3}
   {:fid "seg-5" :direction "backward" :order 4}])

(defui segment-item
  "Displays a single segment with its order and direction"
  [{:keys [segment index]}]
  (let [{:keys [fid direction order]} segment]
    ;; Log for debugging
    (js/console.log "Rendering segment:" (clj->js segment))

    ($ ListItem
       {:divider true
        :sx #js {:py 2}}

       ;; Order number
       ($ ListItemIcon
          ($ Chip
             {:label (str (inc index))
              :color "primary"
              :size "small"
              :sx #js {:minWidth 32}}))

       ;; Segment info
       ($ ListItemText
          {:primary fid
           :secondary (str "Order: " order)
           :sx #js {:ml 2}})

       ;; Direction indicator
       ($ Box {:sx #js {:display "flex" :alignItems "center" :gap 1}}
          ($ Typography
             {:variant "body2"
              :color "text.secondary"}
             direction)
          (cond
            (= direction "forward") ($ ArrowForwardIcon {:color "primary"})
            (= direction "backward") ($ ArrowBackIcon {:color "secondary"})
            :else ($ SwapVertIcon {:color "disabled"}))))))

(defui ordered-segments-visualizer
  "Main component for visualizing ordered route segments"
  [{:keys [segments loading error]
    :or {segments mock-ordered-segments}}]

  ;; Log the data structure for debugging
  (js/console.log "Ordered segments data:" (clj->js segments))

  ($ Paper
     {:elevation 3
      :sx #js {:p 3 :maxWidth 600 :mx "auto" :mt 4}}

     ($ Typography
        {:variant "h5"
         :gutterBottom true}
        "Route Segments Order (POC)")

     ($ Typography
        {:variant "body2"
         :color "text.secondary"
         :paragraph true}
        "Visualizing ordered segments with direction indicators")

     (cond
       loading
       ($ Box {:sx #js {:display "flex" :justifyContent "center" :p 3}}
          ($ CircularProgress))

       error
       ($ Alert {:severity "error" :sx #js {:mt 2}}
          error)

       :else
       ($ List
          (map-indexed
           (fn [idx segment]
             ($ segment-item
                {:key (:fid segment)
                 :segment segment
                 :index idx}))
           segments)))))

(defui demo-view
  "Demo view to test the POC component with API integration"
  []
  (let [[lipas-id set-lipas-id!] (uix/use-state "")
        [activity-type set-activity-type!] (uix/use-state "")
        loading? (use-subscribe [::loading?])
        error (use-subscribe [::error])
        segments (use-subscribe [::segments])]

    ($ Box {:sx #js {:p 2}}
       ($ Typography
          {:variant "h4"
           :gutterBottom true}
          "Route Ordering POC Demo")

       ;; Input fields
       ($ Box {:sx #js {:mb 3 :display "flex" :gap 2 :alignItems "flex-end"}}
          ($ TextField
             {:label "Lipas ID"
              :value lipas-id
              :onChange (fn [e] (set-lipas-id! (.. e -target -value)))
              :placeholder "e.g. 123456"
              :size "small"})

          ($ TextField
             {:label "Activity Type"
              :value activity-type
              :onChange (fn [e] (set-activity-type! (.. e -target -value)))
              :placeholder "e.g. hiihto-perinteinen"
              :size "small"})

          ($ Button
             {:variant "contained"
              :onClick #(==> [::load-route-data lipas-id activity-type])
              :disabled (or loading? (empty? lipas-id) (empty? activity-type))}
             "Load Real Route"))

       ;; Help text
       ($ Box {:sx #js {:mb 2}}
          ($ Typography
             {:variant "body2"
              :color "text.secondary"}
             "Enter a Lipas ID and activity type to load real route data from the database"))

       ;; The main visualizer
       ($ ordered-segments-visualizer
          {:segments segments
           :loading loading?
           :error error}))))

;; Export components for use in other namespaces
(def route-ordering-poc ordered-segments-visualizer)