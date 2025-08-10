(ns lipas.ui.sports-sites.activities.route-ordering
  "Component for visualizing ordered route segments with drag-and-drop reordering"
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
    ["@mui/material/IconButton$default" :as IconButton]
    ["@mui/material/Tooltip$default" :as Tooltip]
    ["@mui/icons-material/ArrowForward$default" :as ArrowForwardIcon]
    ["@mui/icons-material/ArrowBack$default" :as ArrowBackIcon]
    ["@mui/icons-material/SwapVert$default" :as SwapVertIcon]
    ["@mui/icons-material/DragIndicator$default" :as DragIndicatorIcon]
    ["@mui/icons-material/ContentCopy$default" :as ContentCopyIcon]
    ["@mui/icons-material/Delete$default" :as DeleteIcon]
    ["@mui/icons-material/FormatListNumbered$default" :as FormatListNumberedIcon]
    ["@hello-pangea/dnd" :refer [DragDropContext Draggable Droppable] :as dnd]
    [ajax.core :as ajax]
    [re-frame.core :as rf]
    [lipas.ui.uix.hooks :refer [use-subscribe]]
    [lipas.ui.utils :refer [==>]]
    [uix.core :as uix :refer [$ defui]]))

;; Re-frame events

(rf/reg-event-db
  ::set-loading
  (fn [db [_ loading?]]
    (assoc-in db [:route-ordering :loading?] loading?)))

(rf/reg-event-fx
  ::initialize-ordered-segments-mode
  (fn [{:keys [db]} [_ lipas-id selected-segments]]
    {:fx [[:dispatch [:lipas.ui.map.events/set-ordered-segments-edit-mode lipas-id selected-segments]]]}))

(rf/reg-event-fx
  ::cleanup-ordered-segments-mode
  (fn [{:keys [db]} _]
    {:fx [[:dispatch [:lipas.ui.map.events/clear-ordered-segments]]]}))

(rf/reg-event-fx
  ::calculate-route-order-suggestion
  (fn [{:keys [db]} [_ lipas-id activity-type]]
    {:db (-> db
             (assoc-in [:route-ordering :loading?] true)
             (assoc-in [:route-ordering :error] nil))
     :http-xhrio {:method :post
                  :uri "/api/actions/suggest-route-order"
                  :params {:lipas-id (js/parseInt lipas-id)
                           :activity-type activity-type}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::calculate-route-order-suggestion-success]
                  :on-failure [::calculate-route-order-suggestion-failure]}}))

(rf/reg-event-fx
  ::calculate-route-order-suggestion-success
  (fn [{:keys [db]} [_ response]]
    (if (:success response)
      (let [raw-segments (:segments response)
           ;; Ensure all segments have required fields
            segments-with-ids (mapv (fn [seg idx]
                                      (merge {:direction "forward" ; Default if missing
                                              :order idx} ; Ensure order exists
                                             seg
                                             {:draggable-id (str "drag-" (or (:fid seg) (random-uuid)))}))
                                    raw-segments
                                    (range))
            confidence (:confidence response)
            warnings (:warnings response)]
        {:db (-> db
                 (assoc-in [:route-ordering :loading?] false)
                 (assoc-in [:route-ordering :segments] segments-with-ids)
                 (assoc-in [:route-ordering :confidence] confidence)
                 (assoc-in [:route-ordering :warnings] warnings))
         :fx [[:dispatch [:lipas.ui.map.events/set-ordered-segments segments-with-ids]]]})
      {:db (-> db
               (assoc-in [:route-ordering :loading?] false)
               (assoc-in [:route-ordering :error] (or (:error response) "Failed to order segments")))})))

(rf/reg-event-fx
  ::calculate-route-order-suggestion-failure
  (fn [{:keys [db]} [_ error]]
    {:db (-> db
             (assoc-in [:route-ordering :loading?] false)
             (assoc-in [:route-ordering :error] (or (get-in error [:response :error])
                                                    "Failed to load route data")))}))

;; Re-frame subscriptions
(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:route-ordering :loading?])))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:route-ordering :error])))

(rf/reg-sub
  ::segments
  (fn [db _]
    (get-in db [:route-ordering :segments])))

(rf/reg-sub
  ::confidence
  (fn [db _]
    (get-in db [:route-ordering :confidence])))

(rf/reg-sub
  ::warnings
  (fn [db _]
    (get-in db [:route-ordering :warnings])))

(defui segment-item
  "Displays a single draggable segment with its order and direction"
  [{:keys [segment index provided isDragging disabled on-toggle-direction on-duplicate on-delete]}]
  (let [{:keys [fid direction order id]} segment
        tr (use-subscribe [:lipas.ui.subs/translator])
        draggable-props (when provided
                          (js->clj (.-draggableProps provided) :keywordize-keys true))
        drag-handle-props (when provided
                            (js->clj (.-dragHandleProps provided) :keywordize-keys true))]

    ($ ListItem
       (merge
         {:ref (when provided (.-innerRef provided))
          :divider true
          :sx #js {:py 2
                   :backgroundColor (if isDragging "action.hover" "inherit")
                   :transform (if isDragging "scale(1.02)" "none")
                   :boxShadow (if isDragging 3 0)
                   :transition "all 0.2s ease"
                   :opacity (if disabled 0.6 1)}}
         draggable-props)

       ;; Drag handle
       ($ ListItemIcon
          (merge {:sx #js {:cursor (if disabled "not-allowed" "grab")
                           "&:active" #js {:cursor (if disabled "not-allowed" "grabbing")}}}
                 drag-handle-props)
          ($ DragIndicatorIcon {:color (if disabled "disabled" "action")}))

       ;; Order number
       ($ Chip
          {:label (str (inc index))
           :color "primary"
           :size "small"
           :sx #js {:minWidth 32 :mx 1}})

       ;; Segment info
       ($ ListItemText
          {:primary (or fid (str "Segment " (inc index)))
           :secondary (when order (str "Original order: " order))
           :sx #js {:mx 2}})

       ;; Actions
       ($ Box {:sx #js {:display "flex" :alignItems "center" :gap 1}}
          ;; Direction toggle button
          ($ Tooltip
             {:title (if disabled
                       (tr :route/action-disabled "Action disabled while saving")
                       (str (tr :route/click-to-change-direction "Click to change direction")
                            " (" (tr :route/currently "currently") " "
                            (if (= direction "backward")
                              (tr :route/backward "backward")
                              (tr :route/forward "forward")) ")"))
              :arrow true}
             ($ IconButton
                {:size "small"
                 :onClick #(when (and (not disabled) on-toggle-direction)
                             (on-toggle-direction index))
                 :disabled disabled
                 :sx #js {:p 0.5}}
                (cond
                  (= direction "forward") ($ ArrowForwardIcon {:color (if disabled "disabled" "primary")})
                  (= direction "backward") ($ ArrowBackIcon {:color (if disabled "disabled" "secondary")})
                  :else ($ SwapVertIcon {:color "disabled"}))))

          ;; Duplicate button
          ($ Tooltip
             {:title (if disabled
                       (tr :route/action-disabled "Action disabled while saving")
                       (tr :route/duplicate-segment "Duplicate segment"))
              :arrow true}
             ($ IconButton
                {:size "small"
                 :onClick #(when (and (not disabled) on-duplicate)
                             (on-duplicate index))
                 :disabled disabled
                 :sx #js {:p 0.5}}
                ($ ContentCopyIcon {:fontSize "small" :color (if disabled "disabled" "action")})))

          ;; Delete button (only show if more than one segment)
          ($ Tooltip
             {:title (if disabled
                       (tr :route/action-disabled "Action disabled while saving")
                       (tr :route/remove-segment "Remove segment"))
              :arrow true}
             ($ IconButton
                {:size "small"
                 :onClick #(when (and (not disabled) on-delete)
                             (on-delete index))
                 :disabled disabled
                 :sx #js {:p 0.5}}
                ($ DeleteIcon {:fontSize "small" :color (if disabled "disabled" "action")})))))))

(defui ordered-segments-visualizer
  "Component for visualizing and editing ordered segments"
  [{:keys [segments on-segments-change loading error read-only? on-save route-id saving? lipas-id activity-k]}]
  (let [;; Subscribe to confidence and warnings
        confidence (use-subscribe [::confidence])
        warnings (use-subscribe [::warnings])
        tr (use-subscribe [:lipas.ui.subs/translator])

        ;; Handler for drag end
        handle-drag-end (fn [result]
                          (when-let [dest-idx (.. result -destination -index)]
                            (let [source-idx (.. result -source -index)]
                              (when (not= source-idx dest-idx)
                                ;; Reorder segments
                                (let [segments-vec (vec segments)
                                      moved-segment (nth segments-vec source-idx)
                                      without-moved (into []
                                                          (concat (subvec segments-vec 0 source-idx)
                                                                  (subvec segments-vec (inc source-idx))))
                                      ;; Re-index all segments with their new positions
                                      reordered (into []
                                                      (map-indexed
                                                        (fn [idx seg]
                                                          (assoc seg :order idx))
                                                        (concat (subvec without-moved 0 dest-idx)
                                                                [moved-segment]
                                                                (subvec without-moved dest-idx))))]
                                  ;; Call parent callback
                                  (when on-segments-change
                                    (on-segments-change reordered)))))))]

    ($ Paper
       {:elevation 3
        :sx #js {:p 3 :maxWidth 600 :mx "auto" :mt 4}}

       ($ Typography
          {:variant "h5"
           :gutterBottom true
           :sx #js {:display "flex" :alignItems "center" :gap 1}}
          ($ FormatListNumberedIcon {:color "primary"})
          (tr :route/order-segments-title "Route Segments Order"))

       ($ Typography
          {:variant "body2"
           :color "text.secondary"
           :paragraph true}
          (tr :route/order-instructions "Drag segments to reorder. Click arrows to change direction."))

       ;; Display confidence level when available
       (when confidence
         ($ Box {:sx #js {:mb 2}}
            ($ Chip
               {:label (str (tr :route/algorithm-confidence "Algorithm Confidence") ": "
                            (case confidence
                              "high" (tr :route/confidence-high "High")
                              "medium" (tr :route/confidence-medium "Medium")
                              "low" (tr :route/confidence-low "Low")
                              confidence))
                :color (case confidence
                         "high" "success"
                         "medium" "warning"
                         "low" "error"
                         "default")
                :variant "filled"
                :size "small"})))

       (cond
         loading
         ($ Box {:sx #js {:display "flex"
                          :flexDirection "column"
                          :alignItems "center"
                          :justifyContent "center"
                          :p 4}}
            ($ CircularProgress {:size 48})
            ($ Typography
               {:variant "body1"
                :color "text.secondary"
                :sx #js {:mt 2}}
               (tr :route/loading-suggestions "Loading route suggestions...")))

         error
         ($ Alert
            {:severity "error"
             :sx #js {:mt 2}}
            ($ Typography {:variant "subtitle2" :sx #js {:fontWeight "bold"}}
               (tr :route/error-title "Error loading route data"))
            ($ Typography {:variant "body2" :sx #js {:mt 1}}
               error))

         (empty? segments)
         ($ Alert
            {:severity "info"
             :sx #js {:mt 2}}
            ($ Typography {:variant "body1"}
               (tr :route/no-segments-empty-state
                   "No segments to order. Please select segments on the map first.")))

         :else
         ($ :<>
            ($ DragDropContext
               {:onDragEnd handle-drag-end}

               ($ Droppable
                  {:droppableId "route-segments"}
                  (fn [provided snapshot]
                    (let [droppable-props (js->clj (.-droppableProps provided) :keywordize-keys true)]
                      ($ List
                         (merge
                           {:ref (.-innerRef provided)
                            :sx #js {:backgroundColor (if (.-isDraggingOver snapshot)
                                                        "action.hover"
                                                        "inherit")
                                     :transition "background-color 0.2s ease"}}
                           droppable-props)

                         ;; Render draggable segments
                         (map-indexed
                           (fn [idx segment]
                             ($ Draggable
                                {:key (:draggable-id segment)
                                 :draggableId (:draggable-id segment)
                                 :index idx}
                                (fn [provided snapshot]
                                  ($ segment-item
                                     {:segment segment
                                      :index idx
                                      :provided provided
                                      :isDragging (.-isDragging snapshot)
                                      :disabled read-only?
                                      :on-toggle-direction (fn [idx]
                                                             (let [updated (update (vec segments) idx
                                                                                   (fn [seg]
                                                                                     (assoc seg :direction
                                                                                            (if (= (:direction seg) "backward")
                                                                                              "forward"
                                                                                              "backward"))))]
                                                               (when on-segments-change
                                                                 (on-segments-change updated))))
                                      :on-duplicate (fn [idx]
                                                      (let [segment (nth segments idx)
                                                            duplicated (assoc segment
                                                                              :draggable-id (str "drag-" (random-uuid)))
                                                           ;; Re-index all segments including the new duplicate
                                                            updated (into []
                                                                          (map-indexed
                                                                            (fn [new-idx seg]
                                                                              (assoc seg :order new-idx))
                                                                            (concat (take (inc idx) segments)
                                                                                    [duplicated]
                                                                                    (drop (inc idx) segments))))]
                                                        (when on-segments-change
                                                          (on-segments-change updated))))
                                      :on-delete (fn [idx]
                                                   (when (> (count segments) 1)
                                                     (let [;; Re-index remaining segments
                                                           updated (into []
                                                                         (map-indexed
                                                                           (fn [new-idx seg]
                                                                             (assoc seg :order new-idx))
                                                                           (concat (take idx segments)
                                                                                   (drop (inc idx) segments))))]
                                                       (when on-segments-change
                                                         (on-segments-change updated)))))}))))
                           segments)

                         ;; Placeholder
                         (.-placeholder provided))))))

            ;; Save button
            (when (and on-save (not read-only?) (seq segments))
              ($ Box {:sx #js {:mt 3 :display "flex" :justifyContent "flex-end"}}
                 ($ Button
                    {:variant "contained"
                     :color "primary"
                     :disabled saving?
                     :onClick (fn []
                                (let [clean-segments (mapv #(dissoc % :draggable-id) segments)]
                                  (on-save clean-segments route-id nil nil)))}
                    (if saving?
                      ($ Box {:sx #js {:display "flex" :alignItems "center" :gap 1}}
                         ($ CircularProgress {:size 20 :color "inherit"})
                         (tr :route/saving "Saving..."))
                      (tr :route/save-order "Save Order"))))))))))
