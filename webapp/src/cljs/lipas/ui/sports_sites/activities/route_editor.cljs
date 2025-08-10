(ns lipas.ui.sports-sites.activities.route-editor
  "Integrated route editor component that combines segment selection and ordering"
  (:require ["@mui/icons-material/FormatListNumbered$default" :as FormatListNumberedIcon]
            ["@mui/icons-material/TouchApp$default" :as TouchAppIcon]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/ToggleButton$default" :as ToggleButton]
            ["@mui/material/ToggleButtonGroup$default" :as ToggleButtonGroup]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.sports-sites.activities.route-ordering :as ordering]
            [lipas.ui.sports-sites.activities.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :refer [==>]]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defui route-editor-mode-selector
  "Toggle between selection and ordering modes"
  [{:keys [mode on-mode-change disabled]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])]
    ($ Box {:sx #js {:mb 2}}
       ($ ToggleButtonGroup
          {:value mode
           :exclusive true
           :onChange (fn [_ new-mode]
                       (when new-mode
                         (on-mode-change new-mode)))
           :size "small"
           :disabled disabled}

          ($ ToggleButton
             {:value "select"
              :sx #js {:px 3}}
             ($ TouchAppIcon {:sx #js {:mr 1 :fontSize "small"}})
             (tr :route/select-segments))

          ($ ToggleButton
             {:value "order"
              :sx #js {:px 3}}
             ($ FormatListNumberedIcon {:sx #js {:mr 1 :fontSize "small"}})
             (tr :route/order-segments))))))

(defui route-selection-mode
  "UI for selecting route segments on the map"
  [{:keys [selected-count on-finish]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])]
    ($ Box {:sx #js {:p 2}}
       ($ Typography
          {:variant "h6"
           :gutterBottom true
           :sx #js {:display "flex" :alignItems "center" :gap 1}}
          ($ TouchAppIcon {:color "primary"})
          (tr :route/selection-mode-title "Select Route Segments"))

       ($ Typography
          {:variant "body1"
           :color "text.secondary"
           :gutterBottom true}
          (tr :utp/select-route-parts-on-map))

       (if (= selected-count 0)
         ($ Alert
            {:severity "info"
             :sx #js {:mt 2 :mb 2}}
            (tr :route/no-segments-selected "Click on route segments on the map to select them"))
         ($ Box {:sx #js {:mt 2 :mb 2}}
            ($ Chip
               {:label (tr :route/segments-selected
                           (str selected-count " " (if (= selected-count 1) "segment" "segments") " selected"))
                :color "primary"
                :variant "filled"
                :icon ($ TouchAppIcon)})))

       ($ Box {:sx #js {:mt 3 :display "flex" :gap 2}}
          ($ Button
             {:variant "contained"
              :color "primary"
              :onClick on-finish
              :disabled (= selected-count 0)
              :size "large"
              :endIcon ($ FormatListNumberedIcon)}
             (tr :route/continue-to-ordering "Continue to Ordering"))

          (when (> selected-count 0)
            ($ Button
               {:variant "text"
                :color "inherit"
                :onClick #(==> [:lipas.ui.map.events/clear-selected-features])
                :size "small"}
               (tr :route/clear-selection "Clear Selection")))))))

(defui route-ordering-mode
  "UI for ordering selected segments"
  [{:keys [segments on-segments-change lipas-id activity-k locale read-only? route-id on-save on-cancel saving?]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])]
    ($ Box
       ($ ordering/ordered-segments-visualizer
          {:key (str "ordered-segments-" route-id)
           :segments segments
           :on-segments-change on-segments-change
           :route-id route-id
           :on-save (fn [ordered-segments route-id success-fn error-fn]
                      (on-save ordered-segments success-fn error-fn))
           :read-only? read-only?
           :saving? saving?
           :lipas-id lipas-id
           :activity-k activity-k})

       ($ Box {:sx #js {:mt 2 :display "flex" :gap 1}}
          ($ Button
             {:variant "outlined"
              :onClick on-cancel
              :disabled saving?}
             (tr :actions/cancel "Cancel"))))))

(defui integrated-route-editor-ui
  "Main component that integrates selection and ordering workflows"
  [{:keys [lipas-id activity-k locale read-only? route on-save on-cancel]}]
  (let [[editor-mode set-editor-mode!] (uix/use-state "select")
        [save-error set-save-error!] (uix/use-state nil)
        [save-success set-save-success!] (uix/use-state false)
        [saving? set-saving!] (uix/use-state false)
        [show-discard-dialog? set-show-discard-dialog!] (uix/use-state false)
        selected-fids (use-subscribe [::subs/selected-features])
        selected-count (count selected-fids)
        tr (use-subscribe [:lipas.ui.subs/translator])

        ;; Subscribe to ordered segments from map state
        ordered-segments-from-map (use-subscribe [:lipas.ui.map.subs/ordered-segments])

        ;; Initialize segments state
        [segments set-segments!] (uix/use-state nil)

        ;; Track if segments have been manually edited
        [manually-edited? set-manually-edited!] (uix/use-state false)

;; Track previous route ID to detect actual route changes
        prev-route-id-ref (uix/use-ref nil)

        ;; Extract route ID to avoid linter warnings and prevent unnecessary triggers
        route-id (:id route)

        ;; Clear ordered segments when route changes
        _ (uix/use-effect
           (fn []
             (let [prev-route-id (.-current prev-route-id-ref)]
               ;; Only clear if route ID actually changed from a non-nil value
               (when (and prev-route-id
                          route-id
                          (not= prev-route-id route-id))
                 ;; Clear ordered segments from map state
                 (==> [:lipas.ui.map.events/clear-ordered-segments])
                 ;; Clear selected features to avoid ghost segments
                 (==> [:lipas.ui.map.events/clear-selected-features])
                 ;; Reset local state
                 (set-segments! nil)
                 (set-editor-mode! "select")
                 (set-manually-edited! false))

               ;; Update ref with current route ID
               (when route-id
                 (set! (.-current prev-route-id-ref) route-id))))
           [route-id]) ; Depend on route ID to trigger when switching routes

        ;; When switching to order mode, use ordered segments from map if available,
        ;; otherwise create from selected features
        _ (uix/use-effect
           (fn []
             (when (= editor-mode "order")
               (cond
                 ;; Use existing ordered segments if available
                 (seq ordered-segments-from-map)
                 (let [segments-with-ids (mapv (fn [seg]
                                                 (if (:draggable-id seg)
                                                   seg
                                                   (assoc seg :draggable-id (str "drag-" (random-uuid)))))
                                               ordered-segments-from-map)]
                   (set-segments! segments-with-ids))

                 ;; Otherwise create from selected features
                 (seq selected-fids)
                 (let [selected-segments (mapv (fn [fid idx]
                                                 {:fid fid ; Consistently use :fid
                                                  :name (str "Segment " (inc idx))
                                                  :order idx
                                                  :direction "forward" ; Add default direction
                                                  :draggable-id (str "drag-" (random-uuid))})
                                               selected-fids
                                               (range))]
                   (==> [::ordering/initialize-ordered-segments-mode lipas-id selected-segments])
                   (set-segments! selected-segments)))))
           [editor-mode lipas-id selected-fids ordered-segments-from-map manually-edited?])

        ;; Reset manual edit flag when switching back to select mode
        _ (uix/use-effect
           (fn []
             (when (= editor-mode "select")
               (set-manually-edited! false)))
           [editor-mode])

        ;; Handle segment changes from child component
        handle-segments-change (fn [new-segments]
                                 (set-segments! new-segments)
                                 (set-manually-edited! true)
                                 ;; Dispatch to update map display
                                 (==> [:lipas.ui.map.events/set-ordered-segments new-segments]))

        ;; Handle save with error handling
        handle-save (fn [ordered-segments success-fn error-fn]
                      (set-saving! true)
                      (set-save-error! nil)
                      (set-save-success! false)

                      ;; Extract fids in order (using :fid key)
                      (let [ordered-fids (mapv :fid ordered-segments)]
                        (on-save
                         (assoc route
                                :segments ordered-segments ; Include full segment data
                                :fids ordered-fids ; For backwards compatibility
                                :ordering-method "manual")
                         (fn []
                           (set-saving! false)
                           (set-save-success! true)
                           (when success-fn (success-fn))
                           ;; Clear success message after 3 seconds
                           (js/setTimeout #(set-save-success! false) 3000))
                         (fn [error]
                           (set-saving! false)
                           (set-save-error! (or (:message error) "Failed to save route"))
                           (when error-fn (error-fn))))))

        ;; Handle cancel with confirmation
        handle-cancel (fn []
                        (if (> selected-count 0)
                          (set-show-discard-dialog! true)
                          (on-cancel)))]

    (uix/use-effect
     (fn []
       ;; Start map editing when component mounts
       (==> [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"])
       ;; Cleanup on unmount
       (fn []
         ;; Clear ordered segments when unmounting
         (==> [:lipas.ui.map.events/clear-ordered-segments])
         (==> [:lipas.ui.map.events/continue-editing])))
     [lipas-id])

    ($ Paper {:sx #js {:p 3}}
       ($ Typography
          {:variant "h6"
           :gutterBottom true}
          (tr :route/editor-title "Route Editor"))

       ($ Divider {:sx #js {:mb 2}})

       ;; Success/Error alerts
       (when save-success
         ($ Alert
            {:severity "success"
             :sx #js {:mb 2}
             :onClose #(set-save-success! false)}
            (tr :route/save-success "Route saved successfully!")))

       (when save-error
         ($ Alert
            {:severity "error"
             :sx #js {:mb 2}
             :onClose #(set-save-error! nil)
             :action ($ Button
                        {:color "inherit"
                         :size "small"
                         :onClick #(handle-save segments nil nil)}
                        (tr :route/retry "Retry"))}
            save-error))

       ;; Mode selector - only show when we have selected segments
       (when (> selected-count 0)
         ($ route-editor-mode-selector
            {:mode editor-mode
             :on-mode-change set-editor-mode!
             :disabled (or read-only? saving?)}))

       ;; Conditional content based on mode
       (case editor-mode
         "select"
         ($ route-selection-mode
            {:selected-count selected-count
             :on-finish (fn []
                          (set-editor-mode! "order"))})

         "order"
         (if segments
           ($ route-ordering-mode
              {:segments segments
               :on-segments-change handle-segments-change
               :lipas-id lipas-id
               :activity-k activity-k
               :locale locale
               :read-only? read-only?
               :route-id (:id route)
               :on-save handle-save
               :on-cancel handle-cancel
               :saving? saving?})
           ($ Alert {:severity "warning"}
              (tr :route/no-segments "No segments available for ordering")))

         nil)

       ;; Discard confirmation dialog
       (when show-discard-dialog?
         ($ Box
            {:sx #js {:position "fixed"
                      :top 0
                      :left 0
                      :right 0
                      :bottom 0
                      :backgroundColor "rgba(0,0,0,0.5)"
                      :display "flex"
                      :alignItems "center"
                      :justifyContent "center"
                      :zIndex 1300}}
            ($ Paper
               {:sx #js {:p 3 :maxWidth 400}}
               ($ Typography
                  {:variant "h6"
                   :gutterBottom true}
                  (tr :route/discard-title "Discard changes?"))
               ($ Typography
                  {:variant "body1"
                   :sx #js {:mb 3}}
                  (tr :route/discard-message "You have unsaved changes. Are you sure you want to discard them?"))
               ($ Box
                  {:sx #js {:display "flex" :gap 2 :justifyContent "flex-end"}}
                  ($ Button
                     {:variant "outlined"
                      :onClick #(set-show-discard-dialog! false)}
                     (tr :actions/cancel "Cancel"))
                  ($ Button
                     {:variant "contained"
                      :color "error"
                      :onClick (fn []
                                 (set-show-discard-dialog! false)
                                 (on-cancel))}
                     (tr :route/discard "Discard")))))))))

;; Reagent wrapper for use in Reagent components
(defn integrated-route-editor [props]
  (r/as-element
   ($ integrated-route-editor-ui props)))
