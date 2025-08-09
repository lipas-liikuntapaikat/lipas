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
    "0.00"))

(defn simple-route-view
  "Simple mode for managing a single route with all segments"
  [{:keys [tr lipas-id type-code edit-data display-data can-edit?]}]
  (let [locale (<== [:lipas.ui.subs/locale])
        geoms (or (-> edit-data :location :geometries)
                  (-> display-data :location :geometries))
        total-length (calculate-total-route-length geoms)
        all-fids (when geoms
                   (set (map :id (:features geoms))))

        ;; Get activity type from subscription
        activity-val (<== [::activities-subs/activity-value-for-type-code type-code])
        activity-k (some-> activity-val keyword)

        ;; Get existing route or create structure for new one
        existing-route (or
                        (-> edit-data :activities activity-k :routes first)
                        (-> display-data :activities activity-k :routes first))

        route-id (or (:id existing-route)
                     (str "route-" (random-uuid)))

        ;; Get current route name from edit-data or display-data
        route-name (or (:route-name existing-route) {})

        editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])

        ;; Helper function to update route directly
        update-route-name (fn [locale-value]
                            (let [updated-route {:id route-id
                                                 :route-name (assoc route-name locale locale-value)
                                                 :fids all-fids
                                                 :route-length-km (js/parseFloat total-length)}]
                            ;; Update immediately in edit-data
                              (==> [:lipas.ui.sports-sites.events/edit-field
                                    lipas-id
                                    [:activities activity-k :routes]
                                    [updated-route]])))]

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

     ;; Auto-calculated length display
     [mui/grid {:item true :xs 12 :md 6}
      [mui/box {:style {:padding-top "16px"}}
       [mui/typography {:variant "body1" :color "textSecondary"}
        (tr :route/length-km)]
       [mui/typography {:variant "h6"}
        (str total-length " km")]]]

     ;; Info text
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "caption" :color "textSecondary"}
       (tr :route/auto-calculated-info)]]]))

(defn advanced-route-view
  "Advanced mode for managing multiple routes with ordering"
  [{:keys [tr lipas-id type-code activity-k display-data edit-data can-edit?
           routes mode selected-route-id selected-route ordering-mode?]}]
  (r/with-let [route-form-state (r/atom {})]
    (let [locale (<== [:lipas.ui.subs/locale])
          fids (<== [::activities-subs/selected-features])
          editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])]

      [mui/grid {:container true :spacing 2}

       ;; Show existing routes list when in default mode
       (when (= :default mode)
         [:<>
          ;; Routes list
          (when (seq routes)
            [mui/grid {:item true :xs 12}
             [mui/typography {:variant "h6" :style {:margin-bottom "0.5em"}}
              (tr :route/existing-routes)]
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
                                 (==> [:lipas.ui.map.events/highlight-features (:fids item)]))
               :on-mouse-leave (fn [_]
                                 (==> [:lipas.ui.map.events/highlight-features #{}]))
               :on-select (fn [route]
                            (==> [::activities-events/select-route lipas-id (dissoc route :_route-name :fids-count)])
                            (reset! route-form-state (dissoc route :fids))
                            (reset! ordering-mode? true))}]])

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
                           :id (or selected-route-id (str (random-uuid)))})
            :on-save (fn [route success-fn error-fn]
                       (==> [::activities-events/finish-route-details
                             {:fids (:fids route)
                              :activity-k activity-k
                              :id (:id route)
                              :route (dissoc route :fids :id)
                              :lipas-id lipas-id}])
                       (reset! ordering-mode? false)
                       (when success-fn (success-fn)))
            :on-cancel (fn []
                         (==> [::activities-events/cancel-route-details])
                         (reset! ordering-mode? false))}]])

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
  (r/with-let [simple-mode? (r/atom true)
               ;; Track if we're currently in ordering mode
               ordering-mode? (r/atom false)]
    (let [activity-val (<== [::activities-subs/activity-value-for-type-code type-code])
          activity-k (some-> activity-val keyword)
          existing-routes (or
                           (-> edit-data :activities activity-k :routes)
                           (-> display-data :activities activity-k :routes)
                           [])
          has-multiple-routes? (> (count existing-routes) 1)

          ;; Get current mode from activities state
          mode (<== [::activities-subs/mode])
          selected-route-id (<== [::activities-subs/selected-route-id])
          selected-route (when selected-route-id
                           (first (filter #(= (:id %) selected-route-id) existing-routes)))

          ;; Check if we're editing
          editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])

          ;; When tab is active, activate route selection mode on map
          _ (when (and editing? can-edit?)
              (==> [:lipas.ui.map.events/continue-editing :selecting "LineString"]))]

      [mui/box {:style {:padding "1em"}}

       ;; Mode toggle only if not already in multi-route mode
       (when (not has-multiple-routes?)
         [:<>
          [mui/form-control-label
           {:control (r/as-element
                      [mui/switch
                       {:checked (not @simple-mode?)
                        :disabled has-multiple-routes?
                        :on-change #(do
                                      (reset! simple-mode? (not %2))
                                     ;; Clear any active route selection when switching modes
                                      (when @simple-mode?
                                        (==> [::activities-events/clear])))}])
            :label (tr :route/advanced-mode)
            :label-placement "start"}]

          [mui/typography {:variant "caption" :color "textSecondary" :style {:display "block" :margin-bottom "1em"}}
           (if @simple-mode?
             (tr :route/simple-mode-help)
             (tr :route/advanced-mode-help))]])

       ;; Content based on mode
       [mui/box {:style {:margin-top "2em"}}
        (if (or @simple-mode? (and (not has-multiple-routes?) (empty? existing-routes)))
          ;; Simple mode content
          [simple-route-view
           {:tr tr
            :lipas-id lipas-id
            :type-code type-code
            :display-data display-data
            :edit-data edit-data
            :can-edit? can-edit?}]

          ;; Advanced mode content
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
            :ordering-mode? ordering-mode?}])]])))