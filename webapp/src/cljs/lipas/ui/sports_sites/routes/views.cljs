(ns lipas.ui.sports-sites.routes.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.components :as lui]
            [lipas.ui.components.forms :refer [->display-tf]]
            [lipas.ui.components.text-fields :as lui-tf]
            [lipas.ui.map.utils :as map-utils]
            [lipas.ui.sports-sites.activities.events :as activities-events]
            [lipas.ui.sports-sites.activities.route-editor :as route-editor]
            [lipas.ui.sports-sites.activities.subs :as activities-subs]
            [lipas.ui.sports-sites.events :as sports-sites-events]
            [lipas.ui.sports-sites.routes.events :as routes-events]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [lipas.utils :as cljc-utils]
            [reagent.core :as r]))

(defn calculate-total-route-length
  "Calculate total length of all geometry segments"
  [geoms]
  (if (and geoms (seq (:features geoms)))
    (map-utils/calculate-length-km geoms)
    0.00))

(defn simple-route-view
  "Simple mode for managing a single route with all segments"
  [{:keys [tr lipas-id type-code edit-data display-data can-edit?]}]
  (let [locale (<== [:lipas.ui.subs/locale])
        ;; Get activity type from subscription
        activity-val (<== [::activities-subs/activity-value-for-type-code type-code])
        activity-k (some-> activity-val keyword)

        ;; Use the subscription that calculates route lengths properly
        routes-with-lengths (<== [::activities-subs/routes-with-calculated-lengths lipas-id activity-k])
        existing-route (first routes-with-lengths)

        ;; Get geometries for total calculation (all segments)
        geoms (or (-> edit-data :location :geometries)
                  (-> display-data :location :geometries))
        total-length (calculate-total-route-length geoms)
        all-fids (when geoms
                   (set (map :id (:features geoms))))

        route-id (or (:id existing-route)
                     (str "route-" (random-uuid)))

        ;; Get current route name from existing route
        route-name (or (:route-name existing-route) {})

        editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])

        ;; Helper function to update route properly
        update-route-name (fn [locale-value]
                            ;; Use the same approach as activities tab for consistency
                            ;; Dispatch the save-route-attributes event which properly handles vector structure
                            (==> [::activities-events/save-route-attributes
                                  {:lipas-id lipas-id
                                   :activity-k activity-k
                                   :route-id route-id
                                   :attributes {:id route-id
                                                :route-name (assoc route-name locale locale-value)
                                                :fids all-fids}}]))]

    [mui/grid {:container true :spacing 2}

     ;; Route name field
     [mui/grid {:item true :xs 12 :md 6}
      (if (or (not editing?) (not can-edit?))
        (->display-tf
         {:label (tr :route/name)
          :value (get route-name locale)
          :mui-props {:fullWidth true}}
         false
         1)
        [lui-tf/text-field
         {:label (tr :route/name)
          :value (get route-name locale "")
          :on-change update-route-name
          :variant "outlined"
          :fullWidth true}])]

     ;; Auto-calculated length display - use calculated length from subscription when available
     [mui/grid {:item true :xs 12 :md 6}
      [mui/box {:style {:padding-top "16px"}}
       [mui/typography {:variant "body1" :color "textSecondary"}
        (tr :route/length-km)]
       [mui/typography {:variant "h6"}
        (str (or (:route-length-km existing-route) total-length "0.00") " km")]]]

     ;; Info text
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "caption" :color "textSecondary"}
       (tr :route/auto-calculated-info)]]]))

(defn advanced-route-view
  "Advanced mode for managing multiple routes with ordering"
  [{:keys [tr lipas-id type-code activity-k display-data edit-data can-edit?
           routes mode selected-route-id selected-route ordering-mode?]}]
  (r/with-let [route-form-state (r/atom {})
               ;; Generate stable ID for new routes - only generate once
               new-route-id (r/atom nil)]
    (let [locale (<== [:lipas.ui.subs/locale])
          fids (<== [::activities-subs/selected-features])
          editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])

          ;; Get or create stable route ID
          stable-route-id (or selected-route-id
                              @new-route-id
                              (let [id (str (random-uuid))]
                                (reset! new-route-id id)
                                id))]

      [mui/grid {:container true :spacing 2}

       ;; Show existing routes list when in default mode
       (when (= :default mode)
         [:<>
          ;; Routes list
          (when (seq routes)
            [mui/grid {:item true :xs 12}
             [mui/typography {:variant "h6" :style {:margin-bottom "0.5em"}}
              (tr :route/headline)]
             [lui/table
              {:headers
               [[:_route-name (tr :general/name)]
                [:route-length-km (tr :route/length-km)]
                [:fids-count "Segmenttejä"]]
               :items (->> routes
                           (mapv (fn [m]
                                   (assoc m
                                          :_route-name (get-in m [:route-name locale])
                                          :fids-count (count (:fids m))))))
               :on-mouse-enter (fn [item]
                                 (==> [:lipas.ui.map.events/highlight-route-features lipas-id (:fids item)]))
               :on-mouse-leave (fn [_]
                                 (==> [:lipas.ui.map.events/highlight-route-features lipas-id #{}]))
               :on-select (fn [route]
                            (==> [::activities-events/select-route lipas-id (dissoc route :_route-name :fids-count)])
                            (reset! route-form-state (dissoc route :fids))
                            (reset! ordering-mode? true)
                            ;; Clear the new-route-id when selecting an existing route
                            (reset! new-route-id nil))}]])

          ;; Add new route button
          (when (and can-edit? editing?)
            [mui/grid {:item true :xs 12}
             [mui/button
              {:variant "contained"
               :color "secondary"
               :style {:margin-top "0.5em" :margin-bottom "0.5em"}
               :on-click (fn []
                           (reset! route-form-state {})
                           (reset! ordering-mode? true)
                           ;; Generate new ID when starting to add a new route
                           (reset! new-route-id (str (random-uuid)))
                           (==> [::activities-events/add-route lipas-id activity-k]))}
              (tr :route/add-new-route)]])])

       ;; Route editor when adding or editing a route
       (when (and editing? can-edit?
                  (or (= :add-route mode)
                      (and (= :route-details mode) @ordering-mode?)))
         [mui/grid {:item true :xs 12}
          [route-editor/integrated-route-editor
           {:lipas-id lipas-id
            :activity-k activity-k
            :locale locale
            :read-only? (not can-edit?)
            :route (merge @route-form-state
                          {:fids fids
                           :id stable-route-id}) ;; Use the stable ID
            :on-save (fn [route success-fn error-fn]
                       (==> [::activities-events/finish-route-details
                             {:fids (:fids route)
                              :activity-k activity-k
                              :id (:id route)
                              :route (dissoc route :fids :id)
                              :lipas-id lipas-id}])
                       (reset! ordering-mode? false)
                       ;; Clear new-route-id after successful save
                       (reset! new-route-id nil)
                       (when success-fn (success-fn)))
            :on-cancel (fn []
                         (==> [::activities-events/cancel-route-details])
                         (reset! ordering-mode? false)
                         ;; Clear new-route-id on cancel
                         (reset! new-route-id nil))}]])

       ;; Route details form (when not in ordering mode)
       (when (and editing? can-edit? (= :route-details mode) (not @ordering-mode?))
         [:<>
          [mui/grid {:item true :xs 12}
           [mui/typography {:variant "h6"}
            (tr :route/edit-route-details)]

           ;; Route name field
           [mui/grid {:container true :spacing 2 :style {:margin-top "1em"}}
            [mui/grid {:item true :xs 12 :md 6}
             [lui-tf/text-field
              {:label (tr :route/name)
               :value (get-in @route-form-state [:route-name locale] "")
               :on-change #(swap! route-form-state assoc-in [:route-name locale] %)
               :variant "outlined"
               :fullWidth true}]]

            ;; Route difficulty (if applicable)
            (when (get-in @route-form-state [:route-difficulty])
              [mui/grid {:item true :xs 12 :md 6}
               [lui-tf/text-field
                {:label (tr :route/difficulty)
                 :value (get @route-form-state :route-difficulty "")
                 :on-change #(swap! route-form-state assoc :route-difficulty %)
                 :variant "outlined"
                 :fullWidth true}]])]]

          ;; Action buttons for route details
          [mui/grid {:item true :xs 12}
           [mui/grid {:container true :spacing 1 :style {:margin-top "1em"}}

            ;; Order segments button
            [mui/grid {:item true}
             [mui/button
              {:variant "outlined"
               :color "primary"
               :on-click #(reset! ordering-mode? true)}
              (tr :route/order-segments)]]

            ;; Save button
            [mui/grid {:item true}
             [mui/button
              {:variant "contained"
               :color "secondary"
               :on-click #(==> [::activities-events/finish-route-details
                                {:fids fids
                                 :activity-k activity-k
                                 :id selected-route-id
                                 :route @route-form-state
                                 :lipas-id lipas-id}])}
              (tr :actions/save)]]

            ;; Delete button
            [mui/grid {:item true}
             [mui/button
              {:variant "contained"
               :color "error"
               :on-click #(==> [:lipas.ui.events/confirm
                                (tr :route/delete-route-prompt)
                                (fn []
                                  (==> [::activities-events/delete-route lipas-id activity-k selected-route-id]))])}
              (tr :actions/delete)]]

            ;; Cancel button
            [mui/grid {:item true}
             [mui/button
              {:variant "contained"
               :on-click #(==> [::activities-events/cancel-route-details])}
              (tr :actions/cancel)]]]]])])))

(defn routes-tab
  "Main component for the REITIT tab with simple/advanced mode toggle"
  [{:keys [facility tr lipas-id type-code display-data edit-data can-edit?]}]
  (r/with-let [;; Storage for mode state - will be updated based on route count
               simple-mode? (r/atom true)
               ;; Track if we're currently in ordering mode
               ordering-mode? (r/atom false)]
    (let [activity-val (<== [::activities-subs/activity-value-for-type-code type-code])
          activity-k (some-> activity-val keyword)

          ;; Check if any activities are defined at all
          has-activities? (or (and edit-data
                                   (-> edit-data :activities seq))
                              (and display-data
                                   (-> display-data :activities seq)))

          existing-routes (<== [::activities-subs/routes-with-calculated-lengths lipas-id activity-k])
          has-multiple-routes? (> (count existing-routes) 1)

          ;; Force advanced mode when there are multiple routes
          _ (when has-multiple-routes?
              (reset! simple-mode? false))

          ;; Get current mode from activities state
          mode (<== [::activities-subs/mode])
          selected-route-id (<== [::activities-subs/selected-route-id])
          selected-route (when selected-route-id
                           (first (filter #(= (:id %) selected-route-id) existing-routes)))

          ;; Check if we're editing
          editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])]

      [mui/box {:style {:padding "1em"}}

       ;; If no activities are defined and we're in read-only mode, show a message
       (if (and (not has-activities?) (not editing?))
         [mui/box {:style {:padding "2em" :text-align "center"}}
          [mui/typography {:variant "body1" :color "textSecondary"}
           (tr :route/no-activities-defined "No routes defined for any activities")]]

         ;; Otherwise show the normal interface
         [:<>
;; Info alert for geometry editing guidance when in edit mode
          #_(when (and editing? can-edit?)
              [mui/alert {:severity "info"
                          :sx #js {:mb 2}
                          :action (r/as-element
                                   [mui/button
                                    {:size "small"
                                     :color "inherit"
                                     :on-click #(==> [:lipas.ui.map.events/select-sports-site-tab 1])} ; Properties tab
                                    (tr :route/edit-geometry "Edit Geometry")])}
               (tr :route/selection-mode-info
                   "You're selecting segments to define routes. Need to modify the trail itself?")])

          ;; Only show the toggle in edit mode or when it's meaningful in read-only mode
          ;; (i.e., when there are multiple routes to switch between views)
          (when (or editing?
                    (and (not editing?) has-multiple-routes?))
            [:<>
             [mui/form-control-label
              {:control (r/as-element
                         [mui/switch
                          {:checked (not @simple-mode?)
                           :disabled (or has-multiple-routes? ;; Disable when multiple routes
                                         (not editing?)) ;; Also disable in read-only mode
                           :on-change #(do
                                         (reset! simple-mode? (not %2))
                                        ;; Clear any active route selection when switching modes
                                         (when @simple-mode?
                                           (==> [::activities-events/clear])))}])
               :label (tr :route/advanced-mode)
               :label-placement "start"}]

             [mui/typography {:variant "caption" :color "textSecondary" :style {:display "block" :margin-bottom "1em"}}
              (cond
                has-multiple-routes?
                (tr :route/multiple-routes-advanced-only "Multiple routes detected - advanced mode required")

                @simple-mode?
                (tr :route/simple-mode-help)

                :else
                (tr :route/advanced-mode-help))]])

          ;; Content based on mode
          [mui/box {:style {:margin-top "2em"}}
           (cond
             ;; No routes exist and in read-only mode
             (and (empty? existing-routes) (not editing?))
             [mui/box {:style {:padding "1em"}}
              [mui/typography {:variant "body1" :color "textSecondary"}
               (tr :route/no-routes-defined "No routes have been defined")]]

             ;; Simple mode
             (and @simple-mode? (not has-multiple-routes?))
             [simple-route-view
              {:tr tr
               :lipas-id lipas-id
               :type-code type-code
               :display-data display-data
               :edit-data edit-data
               :can-edit? can-edit?}]

             ;; Advanced mode
             :else
             [advanced-route-view
              {:tr tr
               :lipas-id lipas-id
               :type-code type-code
               :activity-k activity-k
               :display-data display-data
               :edit-data edit-data
               :can-edit? can-edit?
               :routes existing-routes
               :mode mode
               :selected-route-id selected-route-id
               :selected-route selected-route
               :ordering-mode? ordering-mode?}])]])])))
